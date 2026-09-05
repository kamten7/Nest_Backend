package com.nest.service.impl;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.nest.common.BaseContext;
import com.nest.common.PageResult;
import com.nest.constant.AppointmentStatus;
import com.nest.constant.JwtConstant;
import com.nest.constant.MessageConstant;
import com.nest.constant.RentConstant;
import com.nest.constant.WalletConstant;
import com.nest.entity.Appointment;
import com.nest.entity.House;
import com.nest.entity.HouseImage;
import com.nest.entity.RentOrder;
import com.nest.entity.RentPayment;
import com.nest.entity.RentTermination;
import com.nest.exception.BusinessException;
import com.nest.mapper.AppointmentMapper;
import com.nest.mapper.HouseImageMapper;
import com.nest.mapper.HouseMapper;
import com.nest.mapper.RentOrderMapper;
import com.nest.mapper.RentPaymentMapper;
import com.nest.mapper.RentTerminationMapper;
import com.nest.service.RentService;
import com.nest.service.WalletService;
import com.nest.utils.RentPeriodUtil;
import com.nest.vo.RentOrderVO;
import com.nest.vo.WalletTransferVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 租房订单服务实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RentServiceImpl implements RentService {

    private final RentOrderMapper rentOrderMapper;
    private final RentPaymentMapper rentPaymentMapper;
    private final RentTerminationMapper rentTerminationMapper;
    private final AppointmentMapper appointmentMapper;
    private final HouseMapper houseMapper;
    private final HouseImageMapper houseImageMapper;
    private final WalletService walletService;

    // ==================== 对外接口 ====================

    /**
     * 确认租房（创建订单）。
     *
     * 流程：校验预约归属且已看房 → 取房源押金/月租/房东 → 防重复进行中订单 →
     * 生成订单号并落库（status=1 待缴押金，nextDuePeriod=当月）。
     */
    @Override
    @Transactional
    public RentOrderVO confirm(Long appointmentId) {
        Long tenantId = BaseContext.getCurrentId();
        // 1. 校验预约存在且属于当前租客
        if (appointmentId == null) {
            throw new BusinessException(MessageConstant.APPOINTMENT_NOT_FOUND);
        }
        Appointment appt = appointmentMapper.selectById(appointmentId);
        if (appt == null) {
            throw new BusinessException(MessageConstant.APPOINTMENT_NOT_FOUND);
        }
        if (!appt.getTenantId().equals(tenantId)) {
            throw new BusinessException(MessageConstant.NO_PERMISSION);
        }
        // 2. 预约须已完成看房
        if (appt.getStatus() != AppointmentStatus.VISITED) {
            throw new BusinessException(MessageConstant.RENT_APPOINTMENT_INVALID);
        }
        // 3. 取房源信息（押金、月租、房东）
        House house = houseMapper.selectById(appt.getHouseId());
        if (house == null) {
            throw new BusinessException(MessageConstant.HOUSE_NOT_FOUND);
        }
        // 4. 防重复：同一租客对同一房源不允许重复进行中订单
        RentOrder exist = rentOrderMapper.selectActiveByTenantAndHouse(tenantId, house.getId());
        if (exist != null) {
            throw new BusinessException(MessageConstant.RENT_ALREADY_EXISTS);
        }
        // 5. 构建并落库（押金可能为空，兜底为 0）
        RentOrder order = RentOrder.builder()
                .orderNo(genOrderNo())
                .appointmentId(appt.getId())
                .tenantId(tenantId)
                .houseId(house.getId())
                .landlordId(house.getLandlordId())
                .deposit(house.getDeposit() != null ? house.getDeposit() : BigDecimal.ZERO)
                .monthlyRent(house.getPrice())
                .status(RentConstant.ORDER_PENDING_DEPOSIT)
                .startDate(java.time.LocalDate.now())
                .nextDuePeriod(RentPeriodUtil.currentPeriod())
                .paidMonths(0)
                .build();
        rentOrderMapper.insert(order);

        log.info("确认租房: orderId={}, tenantId={}, houseId={}", order.getId(), tenantId, house.getId());
        return buildDetailVO(order);
    }

    /**
     * 缴纳押金。
     *
     * 流程：校验订单归属且待缴押金 → 钱包转账（租客 → 房东）→ 生成押金支付记录 →
     * 订单状态转为租房中(2)。
     */
    @Override
    @Transactional
    public RentOrderVO payDeposit(Long orderId) {
        Long tenantId = BaseContext.getCurrentId();
        // 1. 校验订单
        RentOrder order = mustGetOwnedOrder(orderId, tenantId);
        if (order.getStatus() != RentConstant.ORDER_PENDING_DEPOSIT) {
            throw new BusinessException(MessageConstant.RENT_ORDER_STATUS_INVALID);
        }
        // 2. 钱包转账：租客 → 房东，缴押金
        WalletTransferVO tf = walletService.transfer(
                JwtConstant.TYPE_TENANT, tenantId,
                JwtConstant.TYPE_LANDLORD, order.getLandlordId(),
                order.getDeposit(), WalletConstant.BIZ_DEPOSIT_PAY, WalletConstant.BIZ_DEPOSIT_INCOME,
                order.getOrderNo(), "缴纳押金");
        // 3. 生成押金支付记录
        rentPaymentMapper.insert(buildPayment(order, RentConstant.PAY_DEPOSIT, null,
                order.getDeposit(), order.getOrderNo(), tf));
        // 4. 订单状态 → 租房中
        rentOrderMapper.updateStatus(order.getId(), RentConstant.ORDER_RENTING);

        log.info("缴纳押金: orderId={}, amount={}", orderId, order.getDeposit());
        return buildDetailVO(rentOrderMapper.selectById(orderId));
    }

    /**
     * 缴纳当月租金。
     *
     * 流程：校验订单在租房中 → 校验周期为 nextDuePeriod → 防重复缴租 →
     * 钱包转账（租客 → 房东）→ 生成租金支付记录并前移 nextDuePeriod。
     */
    @Override
    @Transactional
    public RentOrderVO payRent(Long orderId, String period) {
        Long tenantId = BaseContext.getCurrentId();
        // 1. 校验订单
        RentOrder order = mustGetOwnedOrder(orderId, tenantId);
        if (order.getStatus() != RentConstant.ORDER_RENTING) {
            throw new BusinessException(MessageConstant.RENT_ORDER_STATUS_INVALID);
        }
        // 2. 校验目标周期（缺省取 nextDuePeriod）
        String target = (period == null || period.isBlank()) ? order.getNextDuePeriod() : period;
        if (!target.equals(order.getNextDuePeriod())) {
            throw new BusinessException(MessageConstant.RENT_PERIOD_INVALID);
        }
        // 3. 防重复缴租
        if (rentPaymentMapper.countByOrderAndPayType(order.getId(), RentConstant.PAY_RENT, target) > 0) {
            throw new BusinessException(MessageConstant.RENT_ALREADY_PAID);
        }
        // 4. 钱包转账：租客 → 房东，缴当月租
        String bizNo = order.getOrderNo() + "-" + target;
        WalletTransferVO tf = walletService.transfer(
                JwtConstant.TYPE_TENANT, tenantId,
                JwtConstant.TYPE_LANDLORD, order.getLandlordId(),
                order.getMonthlyRent(), WalletConstant.BIZ_RENT_PAY, WalletConstant.BIZ_RENT_INCOME,
                bizNo, "缴纳房租 " + target);
        // 5. 生成租金支付记录，并前移 nextDuePeriod
        rentPaymentMapper.insert(buildPayment(order, RentConstant.PAY_RENT, target,
                order.getMonthlyRent(), bizNo, tf));
        rentOrderMapper.advancePeriod(order.getId(), RentPeriodUtil.advance(target, 1), order.getPaidMonths() + 1);

        log.info("缴纳房租: orderId={}, period={}, amount={}", orderId, target, order.getMonthlyRent());
        return buildDetailVO(rentOrderMapper.selectById(orderId));
    }

    /**
     * 提前支付未来房租。
     *
     * 流程：校验月数 1-5 → 校验订单在租房中 → 一次转账总额 →
     * 生成 N 条租金支付记录（共享 bizNo）→ nextDuePeriod 前移 N 月、已缴+N。
     */
    @Override
    @Transactional
    public RentOrderVO payAhead(Long orderId, Integer months) {
        Long tenantId = BaseContext.getCurrentId();
        // 1. 校验月数
        if (months == null || months < 1 || months > RentConstant.MAX_AHEAD_MONTHS) {
            throw new BusinessException(MessageConstant.RENT_AHEAD_MONTHS_INVALID);
        }
        // 2. 校验订单
        RentOrder order = mustGetOwnedOrder(orderId, tenantId);
        if (order.getStatus() != RentConstant.ORDER_RENTING) {
            throw new BusinessException(MessageConstant.RENT_ORDER_STATUS_INVALID);
        }
        // 3. 一次转账总额（月租 × 月数）
        BigDecimal total = order.getMonthlyRent().multiply(BigDecimal.valueOf(months));
        String bizNo = order.getOrderNo() + "-ahead";
        WalletTransferVO tf = walletService.transfer(
                JwtConstant.TYPE_TENANT, tenantId,
                JwtConstant.TYPE_LANDLORD, order.getLandlordId(),
                total, WalletConstant.BIZ_RENT_PAY, WalletConstant.BIZ_RENT_INCOME,
                bizNo, "提前支付 " + months + " 个月房租");
        // 4. 生成 N 条租金支付记录（各对应一个周期，共享 bizNo）
        String basePeriod = order.getNextDuePeriod();
        for (int i = 0; i < months; i++) {
            rentPaymentMapper.insert(buildPayment(order, RentConstant.PAY_RENT,
                    RentPeriodUtil.advance(basePeriod, i), order.getMonthlyRent(), bizNo, tf));
        }
        // 5. 前移 nextDuePeriod + 已缴月数
        rentOrderMapper.advancePeriod(order.getId(), RentPeriodUtil.advance(basePeriod, months),
                order.getPaidMonths() + months);

        log.info("提前支付: orderId={}, months={}, total={}", orderId, months, total);
        return buildDetailVO(rentOrderMapper.selectById(orderId));
    }

    /**
     * 申请退租。
     *
     * 流程：校验订单在租房中 → 生效末周期 = nextDuePeriod 上一个周期（已购到的那一期）→
     * 写入退租申请（refund_status=0）→ 订单状态转为退租申请中(3)，此后停止提醒与缴费。
     */
    @Override
    @Transactional
    public RentOrderVO terminate(Long orderId, String remark) {
        Long tenantId = BaseContext.getCurrentId();
        // 1. 校验订单
        RentOrder order = mustGetOwnedOrder(orderId, tenantId);
        if (order.getStatus() != RentConstant.ORDER_RENTING) {
            throw new BusinessException(MessageConstant.RENT_ORDER_STATUS_INVALID);
        }
        // 2. 生效末周期：nextDuePeriod 的上一个周期（用户已购到的那一期）
        String effectiveEnd = RentPeriodUtil.advance(order.getNextDuePeriod(), -1);
        // 3. 写入退租申请
        RentTermination termination = RentTermination.builder()
                .orderId(order.getId())
                .tenantId(tenantId)
                .effectiveEndPeriod(effectiveEnd)
                .refundStatus(0)
                .remark(remark)
                .build();
        rentTerminationMapper.insert(termination);
        // 4. 订单状态 → 退租申请中
        rentOrderMapper.updateStatus(order.getId(), RentConstant.ORDER_TERMINATING);

        log.info("申请退租: orderId={}, effectiveEnd={}", orderId, effectiveEnd);
        return buildDetailVO(rentOrderMapper.selectById(orderId));
    }

    /**
     * 我的租房订单（分页）。
     */
    @Override
    public PageResult<RentOrderVO> listMyOrders(Integer status, Integer page, Integer pageSize) {
        Long tenantId = BaseContext.getCurrentId();
        // 1. 开启分页
        PageHelper.startPage(page, pageSize);
        // 2. 查询
        List<RentOrder> orders = rentOrderMapper.selectByTenant(tenantId, status);
        PageInfo<RentOrder> pageInfo = new PageInfo<>(orders);
        // 3. 转 VO（列表不带缴费/退租明细）
        List<RentOrderVO> vos = new ArrayList<>();
        for (RentOrder order : orders) {
            vos.add(buildListVO(order));
        }
        return PageResult.of(pageInfo.getTotal(), vos);
    }

    /**
     * 订单详情（含缴费记录 + 退租信息）。
     */
    @Override
    public RentOrderVO getDetail(Long orderId) {
        Long tenantId = BaseContext.getCurrentId();
        RentOrder order = mustGetOwnedOrder(orderId, tenantId);
        return buildDetailVO(order);
    }

    // ==================== 房东端 ====================

    /**
     * 房东名下租房订单（分页）。
     * 流程：PageHelper 分页 → 按 landlordId 查询 → 转列表 VO。
     */
    @Override
    public PageResult<RentOrderVO> listByLandlord(Integer status, Integer page, Integer pageSize) {
        Long landlordId = BaseContext.getCurrentId();
        // 1. 开启分页
        PageHelper.startPage(page, pageSize);
        // 2. 查询
        List<RentOrder> orders = rentOrderMapper.selectByLandlord(landlordId, status);
        PageInfo<RentOrder> pageInfo = new PageInfo<>(orders);
        // 3. 转 VO
        List<RentOrderVO> vos = new ArrayList<>();
        for (RentOrder order : orders) {
            vos.add(buildListVO(order));
        }
        return PageResult.of(pageInfo.getTotal(), vos);
    }

    /**
     * 房东查看订单详情（含收款记录 + 退租信息）。
     */
    @Override
    public RentOrderVO getDetailAsLandlord(Long orderId) {
        Long landlordId = BaseContext.getCurrentId();
        RentOrder order = mustGetLandlordOrder(orderId, landlordId);
        return buildDetailVO(order);
    }

    /**
     * 房东触发押金退回。
     *
     * 流程：校验归属且订单为退租申请中 → 校验已购租期已结束 → 押金由房东退回租客 → 更新状态为已退租。
     */
    @Override
    @Transactional
    public RentOrderVO refundDeposit(Long orderId) {
        Long landlordId = BaseContext.getCurrentId();
        // 1. 校验订单归属房东 + 状态为退租申请中
        RentOrder order = mustGetLandlordOrder(orderId, landlordId);
        if (order.getStatus() != RentConstant.ORDER_TERMINATING) {
            throw new BusinessException(MessageConstant.RENT_ORDER_STATUS_INVALID);
        }
        RentTermination termination = rentTerminationMapper.selectByOrderId(orderId);
        if (termination == null) {
            throw new BusinessException(MessageConstant.RENT_ORDER_STATUS_INVALID);
        }
        // 2. 仅当已购租期结束后才允许退款
        LocalDate end = RentPeriodUtil.endOf(termination.getEffectiveEndPeriod());
        if (LocalDate.now().isBefore(end)) {
            throw new BusinessException(MessageConstant.RENT_ORDER_STATUS_INVALID);
        }
        // 3. 押金：房东钱包 → 租客钱包
        WalletTransferVO tf = walletService.transfer(
                JwtConstant.TYPE_LANDLORD, landlordId,
                JwtConstant.TYPE_TENANT, order.getTenantId(),
                order.getDeposit(), WalletConstant.BIZ_DEPOSIT_REFUND, WalletConstant.BIZ_DEPOSIT_REFUND,
                order.getOrderNo() + "-refund", "押金退回");
        // 4. 更新退租状态 + 订单为已退租
        rentTerminationMapper.updateRefund(termination.getId(), 1, LocalDateTime.now(), tf.getPayerTxnId());
        rentOrderMapper.updateStatus(orderId, RentConstant.ORDER_TERMINATED);

        log.info("房东退回押金: orderId={}, tenantId={}, deposit={}", orderId, order.getTenantId(), order.getDeposit());
        return buildDetailVO(rentOrderMapper.selectById(orderId));
    }

    // ==================== 内部方法 ====================

    /** 校验订单存在且属于当前租客 */
    private RentOrder mustGetOwnedOrder(Long orderId, Long tenantId) {
        if (orderId == null) {
            throw new BusinessException(MessageConstant.RENT_ORDER_NOT_FOUND);
        }
        RentOrder order = rentOrderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException(MessageConstant.RENT_ORDER_NOT_FOUND);
        }
        if (!order.getTenantId().equals(tenantId)) {
            throw new BusinessException(MessageConstant.RENT_ORDER_NOT_OWNER);
        }
        return order;
    }

    /** 校验订单存在且属于当前房东 */
    private RentOrder mustGetLandlordOrder(Long orderId, Long landlordId) {
        if (orderId == null) {
            throw new BusinessException(MessageConstant.RENT_ORDER_NOT_FOUND);
        }
        RentOrder order = rentOrderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException(MessageConstant.RENT_ORDER_NOT_FOUND);
        }
        if (!order.getLandlordId().equals(landlordId)) {
            throw new BusinessException(MessageConstant.RENT_ORDER_NOT_OWNER);
        }
        return order;
    }

    /** 组装支付记录（押金/租金，记录两侧流水 id） */
    private RentPayment buildPayment(RentOrder order, String payType, String period,
                                     BigDecimal amount, String bizNo, WalletTransferVO tf) {
        return RentPayment.builder()
                .orderId(order.getId())
                .payType(payType)
                .period(period)
                .amount(amount)
                .payMethod("WALLET")
                .status(1)
                .bizNo(bizNo)
                .tenantTxnId(tf.getPayerTxnId())
                .landlordTxnId(tf.getPayeeTxnId())
                .build();
    }

    /** 订单 → 列表 VO（基础字段 + 房源标题/封面） */
    private RentOrderVO buildListVO(RentOrder order) {
        RentOrderVO vo = new RentOrderVO();
        fillBase(vo, order);
        return vo;
    }

    /** 订单 → 详情 VO（基础 + 缴费记录 + 退租信息） */
    private RentOrderVO buildDetailVO(RentOrder order) {
        RentOrderVO vo = new RentOrderVO();
        fillBase(vo, order);
        vo.setPayments(rentPaymentMapper.selectByOrderId(order.getId()));
        RentTermination termination = rentTerminationMapper.selectByOrderId(order.getId());
        if (termination != null) {
            vo.setTermination(termination);
        }
        return vo;
    }

    /** 填充 VO 基础字段 */
    private void fillBase(RentOrderVO vo, RentOrder order) {
        vo.setId(order.getId());
        vo.setOrderNo(order.getOrderNo());
        vo.setHouseId(order.getHouseId());
        vo.setDeposit(order.getDeposit());
        vo.setMonthlyRent(order.getMonthlyRent());
        vo.setStatus(order.getStatus());
        vo.setStartDate(order.getStartDate());
        vo.setNextDuePeriod(order.getNextDuePeriod());
        vo.setPaidMonths(order.getPaidMonths());
        vo.setCreateTime(order.getCreateTime());
        // 房源标题 + 封面
        House house = houseMapper.selectById(order.getHouseId());
        if (house != null) {
            vo.setHouseTitle(house.getTitle());
        }
        vo.setHouseCover(queryCover(order.getHouseId()));
    }

    /** 查询房源封面图（is_cover=1 优先，否则第一张） */
    private String queryCover(Long houseId) {
        List<HouseImage> images = houseImageMapper.selectByHouseId(houseId);
        if (images == null || images.isEmpty()) {
            return null;
        }
        return images.stream()
                .filter(img -> img.getIsCover() != null && img.getIsCover() == 1)
                .map(HouseImage::getUrl)
                .findFirst()
                .orElse(images.get(0).getUrl());
    }

    /** 生成订单号：R + 毫秒时间戳 + 3 位随机（降低重复概率，uk_order_no 兜底） */
    private String genOrderNo() {
        return "R" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"))
                + (int) (Math.random() * 900 + 100);
    }
}
