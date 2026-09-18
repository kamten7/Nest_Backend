package com.nest.order.service.impl;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.nest.chat.push.PushService;
import com.nest.common.PageResult;
import com.nest.constant.AppointmentStatus;
import com.nest.constant.HouseStatus;
import com.nest.constant.JwtConstant;
import com.nest.constant.MessageConstant;
import com.nest.constant.RentConstant;
import com.nest.constant.RentOrderStatus;
import com.nest.constant.WalletConstant;
import com.nest.dto.HouseBriefDTO;
import com.nest.dto.RentSourceDTO;
import com.nest.dto.TenantBriefDTO;
import com.nest.entity.RentOrder;
import com.nest.entity.RentPayment;
import com.nest.entity.RentReminderLog;
import com.nest.entity.RentTermination;
import com.nest.exception.BusinessException;
import com.nest.order.mapper.RentAppointmentMapper;
import com.nest.order.mapper.RentHouseMapper;
import com.nest.order.mapper.RentOrderMapper;
import com.nest.order.mapper.RentPaymentMapper;
import com.nest.order.mapper.RentReminderLogMapper;
import com.nest.order.mapper.RentSourceMapper;
import com.nest.order.mapper.RentTerminationMapper;
import com.nest.order.service.RentOrderService;
import com.nest.vo.RentOrderVO;
import com.nest.vo.RentPaymentVO;
import com.nest.vo.RentTerminationVO;
import com.nest.vo.WalletVO;
import com.nest.wallet.service.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/** 租房订单服务实现。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RentOrderServiceImpl implements RentOrderService {

    private static final DateTimeFormatter PERIOD_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final DateTimeFormatter NO_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final String NOTICE_TYPE_RENT = "rent_reminder";
    /** 待缴押金超时自动取消的通知类型（租客/房东共用）。 */
    private static final String NOTICE_TYPE_DEPOSIT_TIMEOUT = "rent_timeout";
    /** 放弃租房时回写预约的取消原因。 */
    private static final String CANCEL_REASON_GIVE_UP = "租房订单已取消（租客放弃租房）";

    private final RentOrderMapper rentOrderMapper;
    private final RentPaymentMapper rentPaymentMapper;
    private final RentTerminationMapper rentTerminationMapper;
    private final RentReminderLogMapper rentReminderLogMapper;
    private final RentSourceMapper rentSourceMapper;
    private final RentAppointmentMapper rentAppointmentMapper;
    private final RentHouseMapper rentHouseMapper;
    private final WalletService walletService;
    private final PushService pushService;


    @Override
    @Transactional
    public RentOrderVO confirmRent(Long tenantId, Long appointmentId) {
        String phone = rentSourceMapper.selectTenantPhone(tenantId);
        if (phone == null || phone.isBlank()) {
            throw new BusinessException(MessageConstant.RENT_PHONE_REQUIRED);
        }

        RentSourceDTO src = rentSourceMapper.selectSourceByAppointmentId(appointmentId);
        if (src == null) {
            throw new BusinessException(MessageConstant.APPOINTMENT_NOT_FOUND);
        }
        if (!Objects.equals(tenantId, src.getAppointmentTenantId())) {
            throw new BusinessException(MessageConstant.APPOINTMENT_NOT_OWNER);
        }
        if (src.getAppointmentStatus() == null || src.getAppointmentStatus() != AppointmentStatus.VISITED) {
            throw new BusinessException(MessageConstant.RENT_APPOINTMENT_NOT_VISITED);
        }
        RentOrder existed = rentOrderMapper.selectByAppointmentId(appointmentId);
        if (existed != null && existed.getStatus() != RentOrderStatus.CANCELLED) {
            throw new BusinessException(MessageConstant.RENT_ORDER_STATUS_INVALID);
        }
        // 房源必须仍在上架（未被他人租走、未下架）。下面的 markRentedIfAvailable 是原子兜底。
        if (src.getHouseStatus() == null || src.getHouseStatus() != HouseStatus.AVAILABLE) {
            throw new BusinessException(MessageConstant.HOUSE_NOT_RENTABLE);
        }

        BigDecimal monthlyRent = src.getHousePrice();
        if (monthlyRent == null || monthlyRent.signum() <= 0) {
            throw new BusinessException(MessageConstant.RENT_HOUSE_PRICE_INVALID);
        }
        BigDecimal deposit = (src.getHouseDeposit() != null && src.getHouseDeposit().signum() > 0)
                ? src.getHouseDeposit()
                : monthlyRent;

        RentOrder order = RentOrder.builder()
                .orderNo(genOrderNo())
                .appointmentId(appointmentId)
                .tenantId(tenantId)
                .houseId(src.getHouseId())
                .landlordId(src.getHouseLandlordId())
                .deposit(deposit)
                .monthlyRent(monthlyRent)
                .status(RentOrderStatus.PENDING_DEPOSIT)
                .paidMonths(0)
                .build();
        rentOrderMapper.insert(order);
        log.info("确认租房生成订单: orderId={}, orderNo={}, tenantId={}, houseId={}, deposit={}, monthlyRent={}",
                order.getId(), order.getOrderNo(), tenantId, src.getHouseId(), deposit, monthlyRent);

        // 预约收尾：已看房(3) → 已成交(5)。
        // 不改的话，预约列表里这条会一直停在「已看房」，前端「确认租房」按钮会一直挂着。
        // 条件更新，已被并发请求改过时影响行数为 0，属正常情况，只提示不报错。
        int dealRows = rentAppointmentMapper.markDealIfVisited(appointmentId);
        if (dealRows == 0) {
            log.warn("确认租房后预约未置为已成交（状态非 3 或已被并发修改）: appointmentId={}", appointmentId);
        }

        // 房源上架(1) → 在租中(2)。条件更新，rows==0 说明被并发抢先租走，整笔回滚。
        int rentRows = rentHouseMapper.markRentedIfAvailable(src.getHouseId());
        if (rentRows == 0) {
            throw new BusinessException(MessageConstant.HOUSE_NOT_RENTABLE);
        }

        // 通知房东：有租客确认租房了。推送失败不影响建单。
        try {
            pushService.pushNotice(JwtConstant.TYPE_LANDLORD, order.getLandlordId(), "rent_deal",
                    "新租房订单",
                    "租客已确认租下「" + src.getHouseTitle() + "」，待缴押金 "
                            + deposit.stripTrailingZeros().toPlainString() + " 元。");
        } catch (Exception e) {
            log.warn("确认租房通知房东失败: orderId={}, reason={}", order.getId(), e.getMessage());
        }

        HouseBriefDTO house = new HouseBriefDTO();
        house.setHouseId(src.getHouseId());
        house.setHouseTitle(src.getHouseTitle());
        house.setHouseCover(src.getHouseCover());
        return buildVO(order, house, null, null);
    }

    @Override
    @Transactional
    public RentOrderVO payDeposit(Long tenantId, Long orderId) {
        RentOrder order = requireOwnedOrder(tenantId, orderId);
        if (order.getStatus() != RentOrderStatus.PENDING_DEPOSIT) {
            throw new BusinessException(MessageConstant.RENT_ORDER_STATUS_INVALID);
        }
        BigDecimal deposit = nullToZero(order.getDeposit());
        assertBalanceEnough(tenantId, deposit);

        String bizNo = genBizNo(RentConstant.BIZ_PREFIX_DEPOSIT);
        Long[] txnIds = walletService.transferPay(
                JwtConstant.TYPE_TENANT, tenantId,
                JwtConstant.TYPE_LANDLORD, order.getLandlordId(),
                deposit,
                WalletConstant.BIZ_DEPOSIT_PAY, WalletConstant.BIZ_DEPOSIT_INCOME, bizNo);

        rentPaymentMapper.insert(RentPayment.builder()
                .orderId(orderId)
                .payType(RentConstant.PAY_TYPE_DEPOSIT)
                .amount(deposit)
                .payMethod(RentConstant.PAY_METHOD_WALLET)
                .status(WalletConstant.STATUS_SUCCESS)
                .bizNo(bizNo)
                .tenantTxnId(txnIds[0])
                .landlordTxnId(txnIds[1])
                .build());

        LocalDate startDate = LocalDate.now();
        String nextDuePeriod = startDate.format(PERIOD_FORMATTER);
        int rows = rentOrderMapper.activateAfterDeposit(orderId, startDate, nextDuePeriod);
        if (rows == 0) {
            throw new BusinessException(MessageConstant.RENT_ORDER_STATUS_INVALID);
        }
        log.info("缴纳押金成功: orderId={}, amount={}, startDate={}, nextDuePeriod={}",
                orderId, deposit, startDate, nextDuePeriod);

        order.setStatus(RentOrderStatus.RENTING);
        order.setStartDate(startDate);
        order.setNextDuePeriod(nextDuePeriod);
        order.setPaidMonths(0);
        return detailVO(order);
    }

    @Override
    public PageResult<RentOrderVO> listByTenant(Long tenantId, Integer status, Integer page, Integer pageSize) {
        int p = (page == null || page < 1) ? 1 : page;
        int ps = (pageSize == null || pageSize < 1) ? 10 : pageSize;
        PageHelper.startPage(p, ps);
        List<RentOrder> list = rentOrderMapper.selectByTenant(tenantId, status);
        PageInfo<RentOrder> pageInfo = new PageInfo<>(list);
        return PageResult.of(pageInfo.getTotal(), toVOs(list));
    }

    @Override
    public RentOrderVO getDetail(Long tenantId, Long orderId) {
        return detailVO(requireOwnedOrder(tenantId, orderId));
    }

    @Override
    @Transactional
    public RentOrderVO payRent(Long tenantId, Long orderId, String period) {
        RentOrder order = requireOwnedOrder(tenantId, orderId);
        requireRenting(order);

        String current = order.getNextDuePeriod();
        if (current == null || current.isBlank()) {
            throw new BusinessException(MessageConstant.RENT_ORDER_STATUS_INVALID);
        }
        String target = (period == null || period.isBlank()) ? current : period;
        if (!target.equals(current)) {
            throw new BusinessException(MessageConstant.RENT_PAY_PERIOD_INVALID);
        }

        BigDecimal amount = nullToZero(order.getMonthlyRent());
        String bizNo = payMonth(order, target, amount, 1);

        int rows = rentOrderMapper.advanceAfterRentPaid(orderId, current, shiftPeriod(current, 1), 1);
        if (rows == 0) {
            throw new BusinessException(MessageConstant.RENT_PAY_CONFLICT);
        }
        log.info("缴纳租金成功: orderId={}, period={}, amount={}, bizNo={}", orderId, target, amount, bizNo);

        return reloadAndDetail(orderId);
    }

    @Override
    @Transactional
    public RentOrderVO payAhead(Long tenantId, Long orderId, Integer months) {
        RentOrder order = requireOwnedOrder(tenantId, orderId);
        requireRenting(order);

        int n = (months == null) ? 0 : months;
        if (n < 1 || n > RentConstant.MAX_AHEAD_MONTHS) {
            throw new BusinessException("单次可提前支付 1–" + RentConstant.MAX_AHEAD_MONTHS + " 个月");
        }
        String first = order.getNextDuePeriod();
        if (first == null || first.isBlank()) {
            throw new BusinessException(MessageConstant.RENT_ORDER_STATUS_INVALID);
        }

        BigDecimal monthlyRent = nullToZero(order.getMonthlyRent());
        BigDecimal total = monthlyRent.multiply(BigDecimal.valueOf(n));
        String bizNo = payMonth(order, first, total, n, monthlyRent);

        int rows = rentOrderMapper.advanceAfterRentPaid(orderId, first, shiftPeriod(first, n), n);
        if (rows == 0) {
            throw new BusinessException(MessageConstant.RENT_PAY_CONFLICT);
        }
        log.info("提前支付租金成功: orderId={}, months={}, from={}, amount={}, bizNo={}",
                orderId, n, first, total, bizNo);

        return reloadAndDetail(orderId);
    }

    @Override
    @Transactional
    public RentOrderVO terminate(Long tenantId, Long orderId, String remark) {
        RentOrder order = requireOwnedOrder(tenantId, orderId);
        requireRenting(order);

        int paidMonths = order.getPaidMonths() == null ? 0 : order.getPaidMonths();
        if (paidMonths < 1) {
            throw new BusinessException("请先缴纳当期租金后再申请退租");
        }
        String effectiveEnd = shiftPeriod(order.getNextDuePeriod(), -1);

        RentTermination existed = rentTerminationMapper.selectByOrderId(orderId);
        if (existed != null && existed.getRefundStatus() != null && existed.getRefundStatus() == 0) {
            throw new BusinessException(MessageConstant.TERMINATION_ALREADY_APPLIED);
        }

        rentTerminationMapper.insert(RentTermination.builder()
                .orderId(orderId)
                .tenantId(tenantId)
                .applyTime(LocalDateTime.now())
                .effectiveEndPeriod(effectiveEnd)
                .deductAmount(ZERO)
                .refundAmount(ZERO)
                .refundStatus(0)
                .remark(remark)
                .build());

        int rows = rentOrderMapper.toTerminating(orderId);
        if (rows == 0) {
            throw new BusinessException(MessageConstant.RENT_ORDER_STATUS_INVALID);
        }
        log.info("退租申请已提交: orderId={}, effectiveEndPeriod={}, remark={}", orderId, effectiveEnd, remark);
        return reloadAndDetail(orderId);
    }

    @Override
    @Transactional
    public RentOrderVO cancelOrder(Long tenantId, Long orderId) {
        RentOrder order = requireOwnedOrder(tenantId, orderId);
        if (order.getStatus() != RentOrderStatus.PENDING_DEPOSIT) {
            throw new BusinessException(MessageConstant.RENT_ORDER_CANNOT_CANCEL);
        }
        // 待缴押金阶段尚未发生资金往来：直接置取消，并把房源从「在租中」恢复为「上架」。
        int rows = rentOrderMapper.toCancelled(orderId);
        if (rows == 0) {
            throw new BusinessException(MessageConstant.RENT_ORDER_CANNOT_CANCEL);
        }
        rentHouseMapper.markAvailableIfRented(order.getHouseId());
        // 预约收尾：已成交(5) → 已取消(4)。
        // 不回写会让预约列表里留下一条「已成交」却没有任何订单的记录（语义不实）；
        // 早前还把 5 当成「占用中」，直接导致该房源再也无法重新预约。条件更新，幂等。
        int apptRows = rentAppointmentMapper.markCancelledIfDeal(order.getAppointmentId(), CANCEL_REASON_GIVE_UP);
        if (apptRows == 0) {
            log.warn("放弃租房后预约未置为已取消（状态非 5 或已被并发修改）: orderId={}, appointmentId={}",
                    orderId, order.getAppointmentId());
        }
        log.info("租客放弃租房: orderId={}, houseId={}, appointmentId={}",
                orderId, order.getHouseId(), order.getAppointmentId());
        return reloadAndDetail(orderId);
    }


    @Override
    public PageResult<RentOrderVO> listByLandlord(Long landlordId, Integer status, Integer page, Integer pageSize) {
        int p = (page == null || page < 1) ? 1 : page;
        int ps = (pageSize == null || pageSize < 1) ? 10 : pageSize;
        PageHelper.startPage(p, ps);
        List<RentOrder> list = rentOrderMapper.selectByLandlord(landlordId, status);
        PageInfo<RentOrder> pageInfo = new PageInfo<>(list);
        return PageResult.of(pageInfo.getTotal(), toVOs(list));
    }

    @Override
    public RentOrderVO getDetailByLandlord(Long landlordId, Long orderId) {
        RentOrder order = rentOrderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException(MessageConstant.RENT_ORDER_NOT_FOUND);
        }
        if (!Objects.equals(landlordId, order.getLandlordId())) {
            throw new BusinessException(MessageConstant.RENT_ORDER_NOT_OWNER);
        }
        return detailVO(order);
    }

    @Override
    @Transactional
    public RentOrderVO settleRefund(Long landlordId, Long orderId, BigDecimal deductAmount, String remark) {
        RentOrder order = rentOrderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException(MessageConstant.RENT_ORDER_NOT_FOUND);
        }
        if (!Objects.equals(landlordId, order.getLandlordId())) {
            throw new BusinessException(MessageConstant.RENT_ORDER_NOT_OWNER);
        }
        if (order.getStatus() != RentOrderStatus.TERMINATING) {
            throw new BusinessException(MessageConstant.RENT_ORDER_STATUS_INVALID);
        }
        RentTermination termination = rentTerminationMapper.selectByOrderId(orderId);
        if (termination == null || termination.getRefundStatus() == null || termination.getRefundStatus() != 0) {
            throw new BusinessException(MessageConstant.RENT_ORDER_STATUS_INVALID);
        }
        LocalDate periodEnd = lastDayOfPeriod(termination.getEffectiveEndPeriod());
        if (!LocalDate.now().isAfter(periodEnd)) {
            throw new BusinessException(MessageConstant.RENT_SETTLE_NOT_DUE);
        }

        BigDecimal deduct = (deductAmount == null) ? ZERO : deductAmount.setScale(2, RoundingMode.HALF_UP);
        BigDecimal deposit = nullToZero(order.getDeposit());
        if (deduct.signum() < 0) {
            throw new BusinessException(MessageConstant.WALLET_AMOUNT_INVALID);
        }
        if (deduct.compareTo(deposit) > 0) {
            throw new BusinessException(MessageConstant.RENT_DEDUCT_EXCEED_DEPOSIT);
        }
        BigDecimal refund = deposit.subtract(deduct);

        doRefund(order, termination, deduct, refund,
                remark != null && !remark.isBlank() ? remark : WalletConstant.bizText(WalletConstant.BIZ_DEPOSIT_REFUND));
        // 退租结算完成：房源在租中(2) → 下架(0)。需房东手动重新发布。
        int offRows = rentHouseMapper.markOfflineIfRented(order.getHouseId());
        if (offRows == 0) {
            log.warn("退租结算后房源未置为下架（状态非 2 或已被改）: orderId={}, houseId={}", orderId, order.getHouseId());
        }
        log.info("退租结算完成(房东): orderId={}, deposit={}, deduct={}, refund={}", orderId, deposit, deduct, refund);
        return reloadAndDetail(orderId);
    }


    @Override
    public int remindDueOrders(LocalDate today) {
        List<RentOrder> due = rentOrderMapper.selectDueForReminder(today, RentConstant.REMIND_BEFORE_DAYS);
        if (due.isEmpty()) {
            return 0;
        }
        int pushed = 0;
        for (RentOrder order : due) {
            String period = order.getNextDuePeriod();
            if (period == null || period.isBlank()) {
                continue;
            }
            // 先落去重日志：唯一索引冲突即代表今天已提醒过，直接跳过（不再重复推送）
            try {
                rentReminderLogMapper.insert(RentReminderLog.builder()
                        .orderId(order.getId())
                        .remindPeriod(period)
                        .remindDate(today)
                        .build());
            } catch (DuplicateKeyException e) {
                continue;
            }
            try {
                pushService.pushNotice(JwtConstant.TYPE_TENANT, order.getTenantId(), NOTICE_TYPE_RENT,
                        "房租缴纳提醒",
                        "您的 " + period + " 租金 " + nullToZero(order.getMonthlyRent()).stripTrailingZeros().toPlainString()
                                + " 元待缴，请及时缴纳。");
                pushed++;
            } catch (Exception e) {
                log.warn("房租提醒推送失败: orderId={}, reason={}", order.getId(), e.getMessage());
            }
        }
        return pushed;
    }

    @Override
    public List<Long> listAutoRefundDueIds(LocalDate today) {
        List<RentTermination> list = rentTerminationMapper.selectAutoRefundDue(today, RentConstant.SETTLE_GRACE_DAYS);
        if (list.isEmpty()) {
            return Collections.emptyList();
        }
        return list.stream().map(RentTermination::getId).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public boolean autoRefundOne(Long terminationId) {
        RentTermination termination = rentTerminationMapper.selectById(terminationId);
        if (termination == null || termination.getRefundStatus() == null || termination.getRefundStatus() != 0) {
            return false;
        }
        // 兜底复核：确实已过宽限期（防止任务取到过期快照后延迟执行）
        LocalDate deadline = lastDayOfPeriod(termination.getEffectiveEndPeriod())
                .plusDays(RentConstant.SETTLE_GRACE_DAYS);
        if (!LocalDate.now().isAfter(deadline)) {
            return false;
        }
        RentOrder order = rentOrderMapper.selectById(termination.getOrderId());
        if (order == null || order.getStatus() != RentOrderStatus.TERMINATING) {
            return false;
        }
        BigDecimal deposit = nullToZero(order.getDeposit());
        doRefund(order, termination, ZERO, deposit, "房东超期未结算，系统自动全额退回押金");
        // 自动结算同样要把房源置为下架
        int offRows = rentHouseMapper.markOfflineIfRented(order.getHouseId());
        if (offRows == 0) {
            log.warn("自动退租结算后房源未置为下架: orderId={}, houseId={}", order.getId(), order.getHouseId());
        }
        log.info("退租结算完成(系统自动): orderId={}, terminationId={}, refund={}",
                order.getId(), terminationId, deposit);
        return true;
    }

    @Override
    public List<Long> listExpiredPendingDepositIds(int timeoutMinutes) {
        return rentOrderMapper.selectExpiredPendingDepositIds(timeoutMinutes);
    }

    /**
     * 超时未缴押金 → 自动取消订单并把房源释放回「上架」。
     */
    @Override
    @Transactional
    public boolean autoCancelExpiredOrder(Long orderId, int timeoutMinutes) {
        RentOrder order = rentOrderMapper.selectById(orderId);
        if (order == null || order.getStatus() == null
                || order.getStatus() != RentOrderStatus.PENDING_DEPOSIT) {
            return false;
        }
        // 兜底复核：确实已超时。防任务取到过期快照后延迟执行，也防调用方传了不一致的阈值。
        LocalDateTime createdAt = order.getCreateTime() == null ? LocalDateTime.now() : order.getCreateTime();
        if (LocalDateTime.now().isBefore(createdAt.plusMinutes(timeoutMinutes))) {
            return false;
        }

        int rows = rentOrderMapper.toCancelled(orderId);
        if (rows == 0) {
            // 已被并发处理（租客缴了押金 / 主动放弃），不重复动作
            log.info("超时取消跳过（订单状态已被并发变更）: orderId={}", orderId);
            return false;
        }
        // 房源「在租中」(2) → 「上架」(1)：回滚 confirmRent 的占房动作，让房源重新可被预约
        int houseRows = rentHouseMapper.markAvailableIfRented(order.getHouseId());
        if (houseRows == 0) {
            log.warn("超时取消后房源未恢复为上架（状态非 2 或已被改）: orderId={}, houseId={}",
                    orderId, order.getHouseId());
        }
        // 预约收尾：已成交(5) → 已取消(4)，与租客主动放弃保持一致
        int apptRows = rentAppointmentMapper.markCancelledIfDeal(
                order.getAppointmentId(), cancelReasonDepositTimeout(timeoutMinutes));
        if (apptRows == 0) {
            log.warn("超时取消后预约未置为已取消（状态非 5 或已被并发修改）: orderId={}, appointmentId={}",
                    orderId, order.getAppointmentId());
        }
        log.info("待缴押金超时自动取消: orderId={}, houseId={}, appointmentId={}, 阈值={}分钟",
                orderId, order.getHouseId(), order.getAppointmentId(), timeoutMinutes);

        // 双向通知：推送失败不影响取消结果（与 confirmRent 处理通知的方式一致）
        try {
            pushService.pushNotice(JwtConstant.TYPE_TENANT, order.getTenantId(), NOTICE_TYPE_DEPOSIT_TIMEOUT,
                    "租房订单已超时取消",
                    "您有一笔租房订单因超过 " + timeoutMinutes + " 分钟未缴纳押金已自动取消，房源已重新上架。"
                            + "如需继续租住，请重新预约并确认租房。");
        } catch (Exception e) {
            log.warn("超时取消通知租客失败: orderId={}, reason={}", orderId, e.getMessage());
        }
        try {
            pushService.pushNotice(JwtConstant.TYPE_LANDLORD, order.getLandlordId(), NOTICE_TYPE_DEPOSIT_TIMEOUT,
                    "租房订单已超时取消",
                    "租客超过 " + timeoutMinutes + " 分钟未缴纳押金，该订单已自动取消，房源已重新上架。");
        } catch (Exception e) {
            log.warn("超时取消通知房东失败: orderId={}, reason={}", orderId, e.getMessage());
        }
        return true;
    }

    @Override
    public BigDecimal lockedDepositOf(Long landlordId) {
        if (landlordId == null) {
            return ZERO;
        }
        BigDecimal locked = rentOrderMapper.sumLockedDeposit(landlordId, RentOrderStatus.DEPOSIT_LOCKED_STATUS);
        return locked == null ? ZERO : locked;
    }


    /** 执行押金退回：房东钱包 → 租客钱包，并标记退租记录、订单置「已退租」。 */
    private void doRefund(RentOrder order, RentTermination termination,
                          BigDecimal deduct, BigDecimal refund, String remark) {
        Long refundTxnId = null;
        if (refund.signum() > 0) {
            String bizNo = genBizNo("RFD");
            Long[] txnIds = walletService.transferPay(
                    JwtConstant.TYPE_LANDLORD, order.getLandlordId(),
                    JwtConstant.TYPE_TENANT, order.getTenantId(),
                    refund,
                    WalletConstant.BIZ_DEPOSIT_REFUND, WalletConstant.BIZ_DEPOSIT_REFUND, bizNo);
            refundTxnId = txnIds[1];
        }
        int rows = rentTerminationMapper.markRefunded(termination.getId(), deduct, refund,
                LocalDateTime.now(), refundTxnId, remark);
        if (rows == 0) {
            throw new BusinessException(MessageConstant.RENT_ORDER_STATUS_INVALID);
        }
        int orderRows = rentOrderMapper.toTerminated(order.getId());
        if (orderRows == 0) {
            throw new BusinessException(MessageConstant.RENT_ORDER_STATUS_INVALID);
        }
    }

    /** 租金扣款 + 落缴费记录。 */
    private String payMonth(RentOrder order, String firstPeriod, BigDecimal total, int records, BigDecimal perMonth) {
        assertBalanceEnough(order.getTenantId(), total);

        String bizNo = genBizNo(RentConstant.BIZ_PREFIX_RENT);
        Long[] txnIds = walletService.transferPay(
                JwtConstant.TYPE_TENANT, order.getTenantId(),
                JwtConstant.TYPE_LANDLORD, order.getLandlordId(),
                total,
                WalletConstant.BIZ_RENT_PAY, WalletConstant.BIZ_RENT_INCOME, bizNo);

        for (int i = 0; i < records; i++) {
            RentPayment payment = RentPayment.builder()
                    .orderId(order.getId())
                    .payType(RentConstant.PAY_TYPE_RENT)
                    .period(shiftPeriod(firstPeriod, i))
                    .amount(records == 1 ? total : perMonth)
                    .payMethod(RentConstant.PAY_METHOD_WALLET)
                    .status(WalletConstant.STATUS_SUCCESS)
                    .bizNo(bizNo)
                    .tenantTxnId(txnIds[0])
                    .landlordTxnId(txnIds[1])
                    .build();
            rentPaymentMapper.insert(payment);
        }
        return bizNo;
    }

    private String payMonth(RentOrder order, String period, BigDecimal amount, int records) {
        return payMonth(order, period, amount, records, amount);
    }

    private RentOrder requireOwnedOrder(Long tenantId, Long orderId) {
        RentOrder order = rentOrderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException(MessageConstant.RENT_ORDER_NOT_FOUND);
        }
        if (!Objects.equals(tenantId, order.getTenantId())) {
            throw new BusinessException(MessageConstant.RENT_ORDER_NOT_OWNER);
        }
        return order;
    }

    private void requireRenting(RentOrder order) {
        if (order.getStatus() != RentOrderStatus.RENTING) {
            throw new BusinessException(MessageConstant.RENT_ORDER_STATUS_INVALID);
        }
    }

    /** 超时自动取消时回写预约的取消原因（带上实际阈值，避免文案与规则脱节）。 */
    private static String cancelReasonDepositTimeout(int timeoutMinutes) {
        return "租房订单已超时取消（超过 " + timeoutMinutes + " 分钟未缴纳押金）";
    }

    /** 缴费前的余额预检，用于给出「请先充值」的友好提示；真正的防超扣在 wallet 的条件更新里。 */
    private void assertBalanceEnough(Long tenantId, BigDecimal amount) {
        WalletVO wallet = walletService.getWalletVO(JwtConstant.TYPE_TENANT, tenantId);
        BigDecimal balance = wallet == null ? ZERO : nullToZero(wallet.getBalance());
        if (balance.compareTo(amount) < 0) {
            throw new BusinessException(MessageConstant.WALLET_BALANCE_INSUFFICIENT + "，请先充值");
        }
    }

    private RentOrderVO reloadAndDetail(Long orderId) {
        return detailVO(rentOrderMapper.selectById(orderId));
    }

    /** 单个订单的完整详情（缴费记录 + 退租信息）。 */
    private RentOrderVO detailVO(RentOrder order) {
        HouseBriefDTO house = houseBriefOf(order.getHouseId());
        RentOrderVO vo = buildVO(order, house,
                rentPaymentMapper.selectByOrder(order.getId()),
                rentTerminationMapper.selectByOrderId(order.getId()));
        fillTenantNames(Collections.singletonList(vo));
        return vo;
    }

    /** 补租客昵称（批量查，避免 N+1）。房东侧订单页展示租客与「联系租客」入口用。 */
    private void fillTenantNames(List<RentOrderVO> vos) {
        Set<Long> tenantIds = vos.stream()
                .map(RentOrderVO::getTenantId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (tenantIds.isEmpty()) {
            return;
        }
        Map<Long, String> nameMap = rentSourceMapper.selectTenantBriefByIds(tenantIds).stream()
                .collect(Collectors.toMap(TenantBriefDTO::getTenantId,
                        t -> t.getTenantName() == null ? "租客" : t.getTenantName(), (a, b) -> a));
        vos.stream()
                .filter(v -> v.getTenantId() != null)
                .forEach(v -> v.setTenantName(nameMap.getOrDefault(v.getTenantId(), "租客")));
    }

    private HouseBriefDTO houseBriefOf(Long houseId) {
        if (houseId == null) {
            return null;
        }
        List<HouseBriefDTO> list = rentSourceMapper.selectHouseBriefByIds(List.of(houseId));
        return list.isEmpty() ? null : list.get(0);
    }

    /** 列表批量组装（房源标题/封面/房东名一次查完，避免 N+1）。 */
    private List<RentOrderVO> toVOs(List<RentOrder> orders) {
        if (orders == null || orders.isEmpty()) {
            return Collections.emptyList();
        }
        Set<Long> houseIds = orders.stream()
                .map(RentOrder::getHouseId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, HouseBriefDTO> houseMap = houseIds.isEmpty()
                ? Collections.emptyMap()
                : rentSourceMapper.selectHouseBriefByIds(houseIds).stream()
                        .collect(Collectors.toMap(HouseBriefDTO::getHouseId, h -> h, (a, b) -> a));

        List<RentOrderVO> vos = new ArrayList<>(orders.size());
        for (RentOrder order : orders) {
            vos.add(buildVO(order, houseMap.get(order.getHouseId()), null, null));
        }
        fillTenantNames(vos);
        return vos;
    }

    private RentOrderVO buildVO(RentOrder order, HouseBriefDTO house,
                                List<RentPayment> payments, RentTermination termination) {
        RentOrderVO vo = new RentOrderVO();
        vo.setId(order.getId());
        vo.setOrderNo(order.getOrderNo());
        vo.setHouseId(order.getHouseId());
        vo.setHouseTitle(house == null ? null : house.getHouseTitle());
        vo.setHouseCover(house == null ? null : house.getHouseCover());
        vo.setLandlordId(order.getLandlordId());
        vo.setLandlordName(house == null ? null : house.getLandlordName());
        vo.setTenantId(order.getTenantId());
        vo.setDeposit(nullToZero(order.getDeposit()));
        vo.setMonthlyRent(nullToZero(order.getMonthlyRent()));
        vo.setStatus(order.getStatus());
        vo.setStartDate(order.getStartDate());
        vo.setNextDuePeriod(order.getNextDuePeriod());
        vo.setPaidMonths(order.getPaidMonths() == null ? 0 : order.getPaidMonths());
        vo.setCreateTime(order.getCreateTime());
        if (payments != null) {
            vo.setPayments(payments.stream().map(this::toPaymentVO).collect(Collectors.toList()));
        }
        if (termination != null) {
            vo.setTermination(toTerminationVO(termination));
        }
        return vo;
    }

    private RentPaymentVO toPaymentVO(RentPayment p) {
        RentPaymentVO vo = new RentPaymentVO();
        vo.setId(p.getId());
        vo.setPayType(p.getPayType());
        vo.setPeriod(p.getPeriod());
        vo.setAmount(nullToZero(p.getAmount()));
        vo.setStatus(p.getStatus());
        vo.setBizNo(p.getBizNo());
        vo.setCreateTime(p.getCreateTime());
        return vo;
    }

    private RentTerminationVO toTerminationVO(RentTermination t) {
        RentTerminationVO vo = new RentTerminationVO();
        vo.setId(t.getId());
        vo.setEffectiveEndPeriod(t.getEffectiveEndPeriod());
        vo.setDeductAmount(nullToZero(t.getDeductAmount()));
        vo.setRefundAmount(nullToZero(t.getRefundAmount()));
        vo.setRefundStatus(t.getRefundStatus());
        vo.setRefundTime(t.getRefundTime());
        vo.setRemark(t.getRemark());
        vo.setCreateTime(t.getCreateTime());
        return vo;
    }

    private BigDecimal nullToZero(BigDecimal v) {
        return v == null ? ZERO : v;
    }

    /** 周期字符串（yyyy-MM）按月偏移。 */
    private String shiftPeriod(String period, int months) {
        if (period == null || period.isBlank()) {
            return period;
        }
        return LocalDate.parse(period + "-01", DateTimeFormatter.ISO_LOCAL_DATE)
                .plusMonths(months)
                .format(PERIOD_FORMATTER);
    }

    private LocalDate lastDayOfPeriod(String period) {
        if (period == null || period.isBlank()) {
            throw new BusinessException(MessageConstant.RENT_ORDER_STATUS_INVALID);
        }
        return LocalDate.parse(period + "-01", DateTimeFormatter.ISO_LOCAL_DATE)
                .with(TemporalAdjusters.lastDayOfMonth());
    }

    /** 订单号：RO + 时间戳 + 4 位随机（20 位，唯一约束在 order_no 上）。 */
    private String genOrderNo() {
        return RentConstant.ORDER_NO_PREFIX + LocalDateTime.now().format(NO_FORMATTER)
                + String.format("%04d", ThreadLocalRandom.current().nextInt(10_000));
    }

    /** 业务批次号：前缀 + 时间戳 + 4 位随机（≤32 位），提前支付 N 期共享同一批次号。 */
    private String genBizNo(String prefix) {
        return prefix + LocalDateTime.now().format(NO_FORMATTER)
                + String.format("%04d", ThreadLocalRandom.current().nextInt(10_000));
    }
}
