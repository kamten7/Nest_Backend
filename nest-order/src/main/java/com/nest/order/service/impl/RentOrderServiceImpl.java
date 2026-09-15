package com.nest.order.service.impl;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.nest.chat.push.PushService;
import com.nest.common.PageResult;
import com.nest.constant.AppointmentStatus;
import com.nest.constant.JwtConstant;
import com.nest.constant.MessageConstant;
import com.nest.constant.RentConstant;
import com.nest.constant.RentOrderStatus;
import com.nest.constant.WalletConstant;
import com.nest.dto.HouseBriefDTO;
import com.nest.dto.RentSourceDTO;
import com.nest.entity.RentOrder;
import com.nest.entity.RentPayment;
import com.nest.entity.RentReminderLog;
import com.nest.entity.RentTermination;
import com.nest.exception.BusinessException;
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

/**
 * 租房订单服务实现。
 *
 * <p>资金规则（押金）：
 * <ul>
 *   <li>缴押金：租客钱包 → 房东钱包，押金<b>留在房东钱包内但不可提现</b>；</li>
 *   <li>房东可提现额度 = 房东余额 − Σ(在租订单押金)，由
 *       {@link com.nest.wallet.service.LockedAmountProvider} 注入钱包模块执行；</li>
 *   <li>退租结算：房东可扣款（物品损坏），扣款部分原地归房东变为可提现，
 *       剩余部分由房东钱包退回租客钱包。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RentOrderServiceImpl implements RentOrderService {

    private static final DateTimeFormatter PERIOD_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final DateTimeFormatter NO_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final String NOTICE_TYPE_RENT = "rent_reminder";

    private final RentOrderMapper rentOrderMapper;
    private final RentPaymentMapper rentPaymentMapper;
    private final RentTerminationMapper rentTerminationMapper;
    private final RentReminderLogMapper rentReminderLogMapper;
    private final RentSourceMapper rentSourceMapper;
    private final WalletService walletService;
    private final PushService pushService;

    // ==================== 租客端 ====================

    @Override
    @Transactional
    public RentOrderVO confirmRent(Long tenantId, Long appointmentId) {
        // 前置条件：租房必须已绑定手机号（房东需据此联系租客）。
        // ⚠️ 当前只判断「有没有绑」，不校验号码是否真实/是否本人 —— 短信验证码校验属
        // 「上线并投入使用后」才启用的能力，与微信支付一样先预留（届时在此处插入验证码校验即可）。
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
        // 防重复：同一预约只能生成一个有效订单（仅靠预约状态置已成交不够，这里再兜一道）
        RentOrder existed = rentOrderMapper.selectByAppointmentId(appointmentId);
        if (existed != null && existed.getStatus() != RentOrderStatus.CANCELLED) {
            throw new BusinessException(MessageConstant.RENT_ORDER_STATUS_INVALID);
        }

        BigDecimal monthlyRent = src.getHousePrice();
        if (monthlyRent == null || monthlyRent.signum() <= 0) {
            throw new BusinessException(MessageConstant.RENT_HOUSE_PRICE_INVALID);
        }
        // 押金缺省按「押一」处理：房源没填押金时取一个月租金
        BigDecimal deposit = (src.getHouseDeposit() != null && src.getHouseDeposit().signum() > 0)
                ? src.getHouseDeposit()
                : monthlyRent;

        RentOrder order = RentOrder.builder()
                .orderNo(genOrderNo())
                .appointmentId(appointmentId)
                .tenantId(tenantId)
                .houseId(src.getHouseId())
                // 房东 ID 一律以库中房源为准，不信任前端
                .landlordId(src.getHouseLandlordId())
                .deposit(deposit)
                .monthlyRent(monthlyRent)
                .status(RentOrderStatus.PENDING_DEPOSIT)
                .paidMonths(0)
                .build();
        rentOrderMapper.insert(order);
        log.info("确认租房生成订单: orderId={}, orderNo={}, tenantId={}, houseId={}, deposit={}, monthlyRent={}",
                order.getId(), order.getOrderNo(), tenantId, src.getHouseId(), deposit, monthlyRent);

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

        // 起租日 = 缴押金当天；首个待缴周期 = 起租月（首月租金走正常缴租流程）
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

        int rows = rentOrderMapper.advanceAfterRentPaid(orderId, shiftPeriod(current, 1), 1);
        if (rows == 0) {
            throw new BusinessException(MessageConstant.RENT_ORDER_STATUS_INVALID);
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
        // 一次扣款、N 条记录共享同一批次号
        String bizNo = payMonth(order, first, total, n, monthlyRent);

        int rows = rentOrderMapper.advanceAfterRentPaid(orderId, shiftPeriod(first, n), n);
        if (rows == 0) {
            throw new BusinessException(MessageConstant.RENT_ORDER_STATUS_INVALID);
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

        // 「已购租期的末周期」= 待缴周期的上一个周期。若一期租金都没缴，则已购租期为空，
        // 押金会被立刻退掉而租客仍欠租 —— 因此要求至少缴过 1 期。
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

    // ==================== 房东端 ====================

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
        // 已购租期必须已结束：租客还在住的时候不能结算
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
        log.info("退租结算完成(房东): orderId={}, deposit={}, deduct={}, refund={}", orderId, deposit, deduct, refund);
        return reloadAndDetail(orderId);
    }

    // ==================== 定时任务支撑 ====================

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
                // 推送失败只记日志，不影响已写入的去重记录与其它订单
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
        // 房东超期未操作 ⇒ 全额退还，不扣款
        BigDecimal deposit = nullToZero(order.getDeposit());
        doRefund(order, termination, ZERO, deposit, "房东超期未结算，系统自动全额退回押金");
        log.info("退租结算完成(系统自动): orderId={}, terminationId={}, refund={}",
                order.getId(), terminationId, deposit);
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

    // ==================== 内部方法 ====================

    /**
     * 执行押金退回：房东钱包 → 租客钱包，并标记退租记录、订单置「已退租」。
     *
     * <p>金额为 0 时<b>跳过钱包转账</b>（全额扣款场景）——{@code transferPay} 要求金额严格大于 0。
     */
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
            // ids[1] = 租客侧收入流水
            refundTxnId = txnIds[1];
        }
        int rows = rentTerminationMapper.markRefunded(termination.getId(), deduct, refund,
                LocalDateTime.now(), refundTxnId, remark);
        if (rows == 0) {
            // 已被其它请求结算（并发/任务重跑）
            throw new BusinessException(MessageConstant.RENT_ORDER_STATUS_INVALID);
        }
        int orderRows = rentOrderMapper.toTerminated(order.getId());
        if (orderRows == 0) {
            throw new BusinessException(MessageConstant.RENT_ORDER_STATUS_INVALID);
        }
    }

    /**
     * 租金扣款 + 落缴费记录。
     *
     * @param records   要生成的缴费记录条数（提前支付 N 期时为 N）
     * @param total     本次实际扣款总额
     * @param perMonth  单期金额（records > 1 时用于拆分记录）
     */
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
        return buildVO(order, house,
                rentPaymentMapper.selectByOrder(order.getId()),
                rentTerminationMapper.selectByOrderId(order.getId()));
    }

    private HouseBriefDTO houseBriefOf(Long houseId) {
        if (houseId == null) {
            return null;
        }
        List<HouseBriefDTO> list = rentSourceMapper.selectHouseBriefByIds(List.of(houseId));
        return list.isEmpty() ? null : list.get(0);
    }

    /** 列表批量组装（房源标题/封面一次查完，避免 N+1）。 */
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
