package com.nest.order.service.impl;

import com.nest.chat.push.PushService;
import com.nest.constant.JwtConstant;
import com.nest.constant.RentOrderStatus;
import com.nest.constant.WalletConstant;
import com.nest.entity.RentOrder;
import com.nest.entity.RentTermination;
import com.nest.exception.BusinessException;
import com.nest.order.mapper.RentAppointmentMapper;
import com.nest.order.mapper.RentHouseMapper;
import com.nest.order.mapper.RentOrderMapper;
import com.nest.order.mapper.RentPaymentMapper;
import com.nest.order.mapper.RentReminderLogMapper;
import com.nest.order.mapper.RentSourceMapper;
import com.nest.order.mapper.RentTerminationMapper;
import com.nest.vo.RentOrderVO;
import com.nest.wallet.service.WalletService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 退租资金线单元测试：预付租金退回口径 + 7 天冷却期结算门槛。 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("退租结算资金线单元测试")
class RentTerminateRefundTest {

    private static final Long ORDER_ID = 1L;
    private static final Long TENANT_ID = 10L;
    private static final Long LANDLORD_ID = 20L;
    private static final BigDecimal MONTHLY_RENT = new BigDecimal("1000.00");
    private static final BigDecimal DEPOSIT = new BigDecimal("2000.00");

    @Mock private RentOrderMapper rentOrderMapper;
    @Mock private RentPaymentMapper rentPaymentMapper;
    @Mock private RentTerminationMapper rentTerminationMapper;
    @Mock private RentReminderLogMapper rentReminderLogMapper;
    @Mock private RentSourceMapper rentSourceMapper;
    @Mock private RentAppointmentMapper rentAppointmentMapper;
    @Mock private RentHouseMapper rentHouseMapper;
    @Mock private WalletService walletService;
    @Mock private PushService pushService;

    @InjectMocks private RentOrderServiceImpl rentOrderService;

    @BeforeEach
    void stubReadOnly() {
        when(rentPaymentMapper.selectByOrder(any())).thenReturn(List.of());
        when(rentSourceMapper.selectHouseBriefByIds(any())).thenReturn(List.of());
        when(rentSourceMapper.selectTenantBriefByIds(any())).thenReturn(List.of());
    }

    /** 构造订单：nextDuePeriod 是「下一个待缴期」，所以已缴到的最后一期 = 它减 1。 */
    private RentOrder rentingOrder(int paidMonths, YearMonth nextDue) {
        return RentOrder.builder()
                .id(ORDER_ID).orderNo("RO1").tenantId(TENANT_ID).landlordId(LANDLORD_ID)
                .houseId(100L).appointmentId(1L)
                .monthlyRent(MONTHLY_RENT).deposit(DEPOSIT)
                .status(RentOrderStatus.RENTING)
                .paidMonths(paidMonths)
                .nextDuePeriod(nextDue.toString())
                .build();
    }

    private RentTermination pendingTermination(YearMonth effectiveEnd, int prepaidMonths, BigDecimal prepaidAmount) {
        return RentTermination.builder()
                .id(7L).orderId(ORDER_ID).tenantId(TENANT_ID)
                .applyTime(LocalDateTime.now().minusDays(8))
                .effectiveEndPeriod(effectiveEnd.toString())
                .deductAmount(BigDecimal.ZERO)
                .refundAmount(BigDecimal.ZERO)
                .prepaidMonths(prepaidMonths)
                .prepaidRefundAmount(prepaidAmount)
                .refundStatus(0)
                .build();
    }

    @Test
    @DisplayName("提前缴了 3 个月、第 2 个月退租：生效期为当月，仅第 3 个月算可退预付")
    void terminate_countsOnlyUnspentPrepaidMonths() {
        // 已缴到「本月 + 1」（即本月、下月都在预付范围内），当月视为已住满
        RentOrder order = rentingOrder(2, YearMonth.now().plusMonths(2));
        when(rentOrderMapper.selectById(ORDER_ID)).thenReturn(order);
        when(rentTerminationMapper.selectByOrderId(ORDER_ID)).thenReturn(null);
        when(rentOrderMapper.toTerminating(ORDER_ID)).thenReturn(1);

        rentOrderService.terminate(TENANT_ID, ORDER_ID, "工作变动");

        ArgumentCaptor<RentTermination> captor = ArgumentCaptor.forClass(RentTermination.class);
        verify(rentTerminationMapper).insert(captor.capture());
        RentTermination saved = captor.getValue();
        assertThat(saved.getEffectiveEndPeriod()).isEqualTo(YearMonth.now().toString());
        assertThat(saved.getPrepaidMonths()).isEqualTo(1);
        assertThat(saved.getPrepaidRefundAmount()).isEqualByComparingTo("1000.00");
    }

    @Test
    @DisplayName("只缴到当月就退租：无可退预付，退款只剩押金")
    void terminate_withoutPrepaid_hasNothingToRefund() {
        RentOrder order = rentingOrder(1, YearMonth.now().plusMonths(1));
        when(rentOrderMapper.selectById(ORDER_ID)).thenReturn(order);
        when(rentTerminationMapper.selectByOrderId(ORDER_ID)).thenReturn(null);
        when(rentOrderMapper.toTerminating(ORDER_ID)).thenReturn(1);

        rentOrderService.terminate(TENANT_ID, ORDER_ID, null);

        ArgumentCaptor<RentTermination> captor = ArgumentCaptor.forClass(RentTermination.class);
        verify(rentTerminationMapper).insert(captor.capture());
        assertThat(captor.getValue().getPrepaidMonths()).isZero();
        assertThat(captor.getValue().getPrepaidRefundAmount()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("冷却期满结算：押金退回与预付租金退回分两笔转账，金额分别为 1500 与 1000")
    void settleRefund_afterCooldown_transfersDepositAndPrepaidSeparately() {
        RentOrder order = rentingOrder(2, YearMonth.now().plusMonths(2));
        order.setStatus(RentOrderStatus.TERMINATING);
        RentTermination termination = pendingTermination(YearMonth.now(), 1, new BigDecimal("1000.00"));
        when(rentOrderMapper.selectById(ORDER_ID)).thenReturn(order);
        when(rentTerminationMapper.selectByOrderId(ORDER_ID)).thenReturn(termination);
        when(walletService.transferPay(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new Long[]{11L, 12L});
        when(rentTerminationMapper.markRefunded(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(1);
        when(rentOrderMapper.toTerminated(ORDER_ID)).thenReturn(1);
        when(rentHouseMapper.markOfflineIfRented(100L)).thenReturn(1);

        rentOrderService.settleRefund(LANDLORD_ID, ORDER_ID, new BigDecimal("500.00"), "墙面打孔扣款");

        verify(walletService).transferPay(eq(JwtConstant.TYPE_LANDLORD), eq(LANDLORD_ID),
                eq(JwtConstant.TYPE_TENANT), eq(TENANT_ID), eq(new BigDecimal("1500.00")),
                eq(WalletConstant.BIZ_DEPOSIT_REFUND), eq(WalletConstant.BIZ_DEPOSIT_REFUND), any());
        verify(walletService).transferPay(eq(JwtConstant.TYPE_LANDLORD), eq(LANDLORD_ID),
                eq(JwtConstant.TYPE_TENANT), eq(TENANT_ID), eq(new BigDecimal("1000.00")),
                eq(WalletConstant.BIZ_RENT_REFUND), eq(WalletConstant.BIZ_RENT_REFUND), any());
        verify(rentTerminationMapper).markRefunded(eq(7L), eq(new BigDecimal("500.00")),
                eq(new BigDecimal("1500.00")), eq(new BigDecimal("1000.00")),
                eq(12L), eq(12L), any(LocalDateTime.class), eq("墙面打孔扣款"));
        verify(rentOrderMapper).toTerminated(ORDER_ID);
    }

    @Test
    @DisplayName("冷却期未满 7 天：拒绝结算，且不动任何资金")
    void settleRefund_beforeCooldown_rejectsWithoutMovingMoney() {
        RentOrder order = rentingOrder(2, YearMonth.now().plusMonths(2));
        order.setStatus(RentOrderStatus.TERMINATING);
        RentTermination termination = pendingTermination(YearMonth.now(), 1, new BigDecimal("1000.00"));
        termination.setApplyTime(LocalDateTime.now().minusDays(2));
        when(rentOrderMapper.selectById(ORDER_ID)).thenReturn(order);
        when(rentTerminationMapper.selectByOrderId(ORDER_ID)).thenReturn(termination);

        assertThatThrownBy(() -> rentOrderService.settleRefund(LANDLORD_ID, ORDER_ID, BigDecimal.ZERO, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("退租冷却期未满");

        verify(walletService, never()).transferPay(any(), any(), any(), any(), any(), any(), any(), any());
        verify(rentTerminationMapper, never()).markRefunded(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("房源到期未结算的兜底任务：押金全额退回 + 预付租金一并退回")
    void autoRefundOne_afterCooldown_refundsFullDepositAndPrepaid() {
        RentOrder order = rentingOrder(2, YearMonth.now().plusMonths(2));
        order.setStatus(RentOrderStatus.TERMINATING);
        RentTermination termination = pendingTermination(YearMonth.now(), 1, new BigDecimal("1000.00"));
        when(rentTerminationMapper.selectById(7L)).thenReturn(termination);
        when(rentOrderMapper.selectById(ORDER_ID)).thenReturn(order);
        when(walletService.transferPay(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new Long[]{11L, 12L});
        when(rentTerminationMapper.markRefunded(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(1);
        when(rentOrderMapper.toTerminated(ORDER_ID)).thenReturn(1);
        when(rentHouseMapper.markOfflineIfRented(100L)).thenReturn(1);

        assertThat(rentOrderService.autoRefundOne(7L)).isTrue();

        verify(walletService, times(2)).transferPay(any(), any(), any(), any(), any(), any(), any(), any());
        verify(rentTerminationMapper).markRefunded(eq(7L), eq(BigDecimal.ZERO),
                eq(new BigDecimal("2000.00")), eq(new BigDecimal("1000.00")),
                any(), any(), any(LocalDateTime.class), any());
    }

    @Test
    @DisplayName("兜底任务取到过期快照（未满冷却期）时不动资金")
    void autoRefundOne_beforeCooldown_skips() {
        RentTermination termination = pendingTermination(YearMonth.now(), 1, new BigDecimal("1000.00"));
        termination.setApplyTime(LocalDateTime.now().minusDays(1));
        when(rentTerminationMapper.selectById(7L)).thenReturn(termination);

        assertThat(rentOrderService.autoRefundOne(7L)).isFalse();

        verify(rentOrderMapper, never()).selectById(ORDER_ID);
        verify(walletService, never()).transferPay(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("详情返回结算时点与应退总额，供前端展示冷却倒计时")
    void detail_exposesCooldownAndExpectedTotal() {
        RentOrder order = rentingOrder(2, YearMonth.now().plusMonths(2));
        order.setStatus(RentOrderStatus.TERMINATING);
        RentTermination termination = pendingTermination(YearMonth.now(), 1, new BigDecimal("1000.00"));
        termination.setRefundAmount(new BigDecimal("1500.00"));
        when(rentOrderMapper.selectById(ORDER_ID)).thenReturn(order);
        when(rentTerminationMapper.selectByOrderId(ORDER_ID)).thenReturn(termination);

        RentOrderVO vo = rentOrderService.getDetail(TENANT_ID, ORDER_ID);

        assertThat(vo.getTermination().getPrepaidRefundAmount()).isEqualByComparingTo("1000.00");
        assertThat(vo.getTermination().getTotalRefundAmount()).isEqualByComparingTo("2500.00");
        assertThat(vo.getTermination().getSettleAvailableTime())
                .isEqualTo(termination.getApplyTime().plusDays(7));
    }
}
