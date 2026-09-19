package com.nest.wallet.service.impl;

import com.nest.constant.JwtConstant;
import com.nest.constant.MessageConstant;
import com.nest.constant.WalletConstant;
import com.nest.entity.Wallet;
import com.nest.entity.WalletTransaction;
import com.nest.exception.BusinessException;
import com.nest.vo.WalletVO;
import com.nest.wallet.mapper.WalletMapper;
import com.nest.wallet.mapper.WalletTransactionMapper;
import com.nest.wallet.service.LockedAmountProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** 押金锁定与提现额度单元测试。 */
@ExtendWith(MockitoExtension.class)
@DisplayName("钱包押金锁定单元测试")
class WalletLockedWithdrawTest {

    private static final String LANDLORD = JwtConstant.TYPE_LANDLORD;
    private static final String TENANT = JwtConstant.TYPE_TENANT;
    private static final Long USER_ID = 7L;
    private static final Long WALLET_ID = 101L;
    private static final String IDEM_KEY = "idem-20260918-0001";

    @Mock
    private WalletMapper walletMapper;

    @Mock
    private WalletTransactionMapper walletTransactionMapper;

    @Mock
    private LockedAmountProvider lockedAmountProvider;

    @InjectMocks
    private WalletServiceImpl walletService;

    @BeforeEach
    void injectOptionalProvider() {
        walletService.lockedAmountProvider = lockedAmountProvider;
        // recharge 的模拟充值开关默认 false（生产默认），本类只需它的成功路径 ⇒ 显式打开
        ReflectionTestUtils.setField(walletService, "simulateRechargeEnabled", true);
    }


    private Wallet wallet(String userType, String balance) {
        return Wallet.builder()
                .id(WALLET_ID)
                .userType(userType)
                .userId(USER_ID)
                .balance(new BigDecimal(balance))
                .status(1)
                .build();
    }

    private void givenWallet(String userType, String balance) {
        when(walletMapper.selectByUser(userType, USER_ID)).thenReturn(wallet(userType, balance));
    }

    /**
     * 提现链路：先 selectByUser 取钱包，再加行锁重读。
     * 返回值必须与入参余额一致，否则断言看到的会是"锁后重读"的那份快照。
     */
    private void givenWalletForWithdraw(String userType, String balance) {
        givenWallet(userType, balance);
        when(walletMapper.lockById(WALLET_ID)).thenReturn(wallet(userType, balance));
    }

    private void givenLocked(String userType, String locked) {
        BigDecimal value = locked == null ? null : new BigDecimal(locked);
        when(lockedAmountProvider.lockedAmountOf(userType, USER_ID)).thenAnswer(invocation -> value);
    }


    @Test
    @DisplayName("查钱包：余额含押金，同时给出锁定金额与可提现余额")
    void getWalletVO_withLockedDeposit_exposesAvailableBalance() {
        givenWallet(LANDLORD, "8000.00");
        givenLocked(LANDLORD, "3000.00");

        WalletVO vo = walletService.getWalletVO(LANDLORD, USER_ID);

        assertThat(vo.getBalance()).isEqualByComparingTo("8000.00");
        assertThat(vo.getLockedAmount()).isEqualByComparingTo("3000.00");
        assertThat(vo.getAvailableBalance()).isEqualByComparingTo("5000.00");
    }

    @Test
    @DisplayName("查钱包：无锁定实现方时锁定额为 0，可提现余额等于余额")
    void getWalletVO_withoutProvider_returnsZeroLocked() {
        walletService.lockedAmountProvider = null;
        givenWallet(LANDLORD, "8000.00");

        WalletVO vo = walletService.getWalletVO(LANDLORD, USER_ID);

        assertThat(vo.getLockedAmount()).isEqualByComparingTo("0");
        assertThat(vo.getAvailableBalance()).isEqualByComparingTo("8000.00");
    }

    @Test
    @DisplayName("查钱包：提供方返回 null 时按 0 处理，不抛 NPE")
    void getWalletVO_whenProviderReturnsNull_treatsAsZero() {
        givenWallet(LANDLORD, "100.00");
        givenLocked(LANDLORD, null);

        WalletVO vo = walletService.getWalletVO(LANDLORD, USER_ID);

        assertThat(vo.getLockedAmount()).isEqualByComparingTo("0");
        assertThat(vo.getAvailableBalance()).isEqualByComparingTo("100.00");
    }


    @Test
    @DisplayName("提现：先加行锁 → 重读锁定金额 → 条件扣款（顺序即 TOCTOU 的修复本身）")
    void withdraw_locksRowBeforeReadingLockedAmount() {
        givenWalletForWithdraw(LANDLORD, "8000.00");
        givenLocked(LANDLORD, "3000.00");
        when(walletMapper.decreaseBalanceWithLock(WALLET_ID, new BigDecimal("1000.00"), new BigDecimal("3000.00")))
                .thenReturn(1);

        walletService.withdraw(LANDLORD, USER_ID, new BigDecimal("1000.00"), IDEM_KEY);

        /* 锁定金额由另一张表的状态推导，必须在持有本行锁之后读取，
           否则「租客缴押金给房东加余额 + 订单转锁定态」的事务能挤进读和扣之间 */
        InOrder inOrder = inOrder(walletMapper, lockedAmountProvider);
        inOrder.verify(walletMapper).lockById(WALLET_ID);
        inOrder.verify(lockedAmountProvider).lockedAmountOf(LANDLORD, USER_ID);
        inOrder.verify(walletMapper).decreaseBalanceWithLock(
                WALLET_ID, new BigDecimal("1000.00"), new BigDecimal("3000.00"));

        verify(walletMapper, never()).decreaseBalance(any(), any());
    }

    @Test
    @DisplayName("提现：受理流水必须带上幂等键")
    void withdraw_writesIdempotencyKeyOnTxn() {
        givenWalletForWithdraw(LANDLORD, "8000.00");
        givenLocked(LANDLORD, "3000.00");
        when(walletMapper.decreaseBalanceWithLock(WALLET_ID, new BigDecimal("1000.00"), new BigDecimal("3000.00")))
                .thenReturn(1);

        walletService.withdraw(LANDLORD, USER_ID, new BigDecimal("1000.00"), IDEM_KEY);

        ArgumentCaptor<WalletTransaction> captor = ArgumentCaptor.forClass(WalletTransaction.class);
        verify(walletTransactionMapper).insert(captor.capture());
        assertThat(captor.getValue().getIdemKey()).isEqualTo(IDEM_KEY);
    }

    @Test
    @DisplayName("提现：金额在可提现余额内 → 用带锁定条件的原子扣款并落处理中流水")
    void withdraw_withinAvailable_usesLockedUpdateAndWritesPendingTxn() {
        givenWalletForWithdraw(LANDLORD, "8000.00");
        givenLocked(LANDLORD, "3000.00");
        when(walletMapper.decreaseBalanceWithLock(WALLET_ID, new BigDecimal("2000.00"), new BigDecimal("3000.00")))
                .thenReturn(1);

        WalletVO vo = walletService.withdraw(LANDLORD, USER_ID, new BigDecimal("2000.00"), IDEM_KEY);

        assertThat(vo.getBalance()).isEqualByComparingTo("6000.00");
        assertThat(vo.getLockedAmount()).isEqualByComparingTo("3000.00");
        assertThat(vo.getAvailableBalance()).isEqualByComparingTo("3000.00");

        ArgumentCaptor<WalletTransaction> captor = ArgumentCaptor.forClass(WalletTransaction.class);
        verify(walletTransactionMapper).insert(captor.capture());
        WalletTransaction txn = captor.getValue();
        assertThat(txn.getBizType()).isEqualTo(WalletConstant.BIZ_WITHDRAW);
        assertThat(txn.getDirection()).isEqualTo(WalletConstant.DIRECTION_OUT);
        assertThat(txn.getStatus()).isEqualTo(WalletConstant.STATUS_PENDING);
        assertThat(txn.getAmount()).isEqualByComparingTo("2000.00");
        assertThat(txn.getBalanceAfter()).isEqualByComparingTo("6000.00");
    }

    @Test
    @DisplayName("提现：刚好提完可提现余额 → 允许")
    void withdraw_exactlyAvailable_isAllowed() {
        givenWalletForWithdraw(LANDLORD, "5000.00");
        givenLocked(LANDLORD, "3000.00");
        when(walletMapper.decreaseBalanceWithLock(WALLET_ID, new BigDecimal("2000.00"), new BigDecimal("3000.00")))
                .thenReturn(1);

        WalletVO vo = walletService.withdraw(LANDLORD, USER_ID, new BigDecimal("2000.00"), IDEM_KEY);

        assertThat(vo.getAvailableBalance()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("提现：余额够但被押金占用 → 报「押金不可提现」，不写流水")
    void withdraw_exceedsAvailableButWithinBalance_throwsLockedMessage() {
        givenWalletForWithdraw(LANDLORD, "8000.00");
        givenLocked(LANDLORD, "3000.00");
        when(walletMapper.decreaseBalanceWithLock(WALLET_ID, new BigDecimal("6000.00"), new BigDecimal("3000.00")))
                .thenReturn(0);

        assertThatThrownBy(() -> walletService.withdraw(LANDLORD, USER_ID, new BigDecimal("6000.00"), IDEM_KEY))
                .isInstanceOf(BusinessException.class)
                .hasMessage(MessageConstant.WALLET_WITHDRAW_LOCKED);

        verify(walletTransactionMapper, never()).insert(any(WalletTransaction.class));
    }

    @Test
    @DisplayName("提现：只发一条条件扣款语句，不会先扣后校验")
    void withdraw_delegatesToConditionalUpdate_onlyOnce() {
        givenWalletForWithdraw(LANDLORD, "8000.00");
        givenLocked(LANDLORD, "3000.00");
        when(walletMapper.decreaseBalanceWithLock(WALLET_ID, new BigDecimal("1.00"), new BigDecimal("3000.00")))
                .thenReturn(1);

        walletService.withdraw(LANDLORD, USER_ID, new BigDecimal("1.00"), IDEM_KEY);

        verify(walletMapper).decreaseBalanceWithLock(WALLET_ID, new BigDecimal("1.00"), new BigDecimal("3000.00"));
        verify(walletMapper, never()).decreaseBalance(any(), any());
    }

    @Test
    @DisplayName("提现：锁定额为 0 也必须走带锁定条件的 SQL（不允许退化成只校验余额）")
    void withdraw_whenLockedIsZero_stillUsesLockedUpdate() {
        givenWalletForWithdraw(TENANT, "500.00");
        givenLocked(TENANT, "0");
        when(walletMapper.decreaseBalanceWithLock(WALLET_ID, new BigDecimal("200.00"), BigDecimal.ZERO))
                .thenReturn(1);

        WalletVO vo = walletService.withdraw(TENANT, USER_ID, new BigDecimal("200.00"), IDEM_KEY);

        assertThat(vo.getAvailableBalance()).isEqualByComparingTo("300.00");
        verify(walletMapper).decreaseBalanceWithLock(WALLET_ID, new BigDecimal("200.00"), BigDecimal.ZERO);
        verify(walletMapper, never()).decreaseBalance(any(), any());
    }

    @Test
    @DisplayName("提现：无锁定且扣款失败 → 报余额不足")
    void withdraw_whenConditionalUpdateFails_throwsInsufficient() {
        givenWalletForWithdraw(TENANT, "100.00");
        givenLocked(TENANT, "0");
        when(walletMapper.decreaseBalanceWithLock(WALLET_ID, new BigDecimal("999.00"), BigDecimal.ZERO))
                .thenReturn(0);

        assertThatThrownBy(() -> walletService.withdraw(TENANT, USER_ID, new BigDecimal("999.00"), IDEM_KEY))
                .isInstanceOf(BusinessException.class)
                .hasMessage(MessageConstant.WALLET_BALANCE_INSUFFICIENT);
    }

    @Test
    @DisplayName("提现：余额本身就低于提现额（且被锁定）→ 报余额不足而不是押金锁定")
    void withdraw_whenBalanceItselfInsufficient_throwsInsufficient() {
        givenWalletForWithdraw(LANDLORD, "1000.00");
        givenLocked(LANDLORD, "3000.00");
        when(walletMapper.decreaseBalanceWithLock(WALLET_ID, new BigDecimal("2000.00"), new BigDecimal("3000.00")))
                .thenReturn(0);

        assertThatThrownBy(() -> walletService.withdraw(LANDLORD, USER_ID, new BigDecimal("2000.00"), IDEM_KEY))
                .isInstanceOf(BusinessException.class)
                .hasMessage(MessageConstant.WALLET_BALANCE_INSUFFICIENT);
    }

    @Test
    @DisplayName("提现：钱包不存在（加锁读不到）→ 报钱包不存在")
    void withdraw_whenLockedRowMissing_throwsWalletNotFound() {
        givenWallet(LANDLORD, "8000.00");
        when(walletMapper.lockById(WALLET_ID)).thenAnswer(invocation -> null);

        assertThatThrownBy(() -> walletService.withdraw(LANDLORD, USER_ID, new BigDecimal("1.00"), IDEM_KEY))
                .isInstanceOf(BusinessException.class)
                .hasMessage(MessageConstant.WALLET_NOT_FOUND);

        verify(walletMapper, never()).decreaseBalanceWithLock(any(), any(), any());
    }


    @Test
    @DisplayName("提现：无幂等键直接拒绝，不碰任何 mapper")
    void withdraw_withoutIdempotencyKey_throwsAndTouchesNothing() {
        assertThatThrownBy(() -> walletService.withdraw(LANDLORD, USER_ID, new BigDecimal("1.00"), " "))
                .isInstanceOf(BusinessException.class)
                .hasMessage(MessageConstant.WALLET_IDEM_KEY_REQUIRED);

        verifyNoInteractions(walletMapper, walletTransactionMapper);
    }

    @Test
    @DisplayName("提现：同一幂等键已受理过 → 拒绝且不加锁、不扣款")
    void withdraw_whenIdemKeyAlreadyUsed_throwsWithoutLocking() {
        when(walletTransactionMapper.selectByIdemKey(IDEM_KEY)).thenReturn(new WalletTransaction());

        assertThatThrownBy(() -> walletService.withdraw(LANDLORD, USER_ID, new BigDecimal("1.00"), IDEM_KEY))
                .isInstanceOf(BusinessException.class)
                .hasMessage(MessageConstant.WALLET_IDEM_DUPLICATE);

        verify(walletMapper, never()).lockById(any());
        verify(walletMapper, never()).decreaseBalanceWithLock(any(), any(), any());
    }

    @Test
    @DisplayName("提现：并发双击都过了快路径 → 后落库的撞 uk_idem 唯一索引，报重复提交")
    void withdraw_whenConcurrentDuplicateHitsUniqueIndex_throwsDuplicate() {
        givenWalletForWithdraw(LANDLORD, "8000.00");
        givenLocked(LANDLORD, "3000.00");
        when(walletMapper.decreaseBalanceWithLock(WALLET_ID, new BigDecimal("1000.00"), new BigDecimal("3000.00")))
                .thenReturn(1);
        when(walletTransactionMapper.selectByIdemKey(IDEM_KEY)).thenReturn(null);
        when(walletTransactionMapper.insert(any(WalletTransaction.class)))
                .thenThrow(new DuplicateKeyException("Duplicate entry '" + IDEM_KEY + "' for key 'uk_idem'"));

        assertThatThrownBy(() -> walletService.withdraw(LANDLORD, USER_ID, new BigDecimal("1000.00"), IDEM_KEY))
                .isInstanceOf(BusinessException.class)
                .hasMessage(MessageConstant.WALLET_IDEM_DUPLICATE);
    }

    @Test
    @DisplayName("提现：锁定来源缺失（订单模块未装配）→ fail-fast 拒绝，绝不放行")
    void withdraw_withoutLockProvider_failsFast() {
        walletService.lockedAmountProvider = null;

        assertThatThrownBy(() -> walletService.withdraw(LANDLORD, USER_ID, new BigDecimal("1.00"), IDEM_KEY))
                .isInstanceOf(BusinessException.class)
                .hasMessage(MessageConstant.WALLET_LOCK_SOURCE_UNAVAILABLE);

        verifyNoInteractions(walletMapper, walletTransactionMapper);
    }


    @Test
    @DisplayName("充值：不查询锁定额，余额正常累加（锁定只作用于提现）")
    void recharge_doesNotTouchLockedAmount() {
        givenWallet(LANDLORD, "100.00");
        when(walletMapper.increaseBalance(WALLET_ID, new BigDecimal("50.00"))).thenReturn(1);

        WalletVO vo = walletService.recharge(LANDLORD, USER_ID, new BigDecimal("50.00"));

        assertThat(vo.getBalance()).isEqualByComparingTo("150.00");
        assertThat(vo.getLockedAmount()).isEqualByComparingTo("0");
        verifyNoInteractions(lockedAmountProvider);
    }

    @Test
    @DisplayName("转账：押金退回不受锁定限制（房东支出押金是结算行为，不是提现）")
    void transferPay_isNotBlockedByLock() {
        when(walletMapper.selectByUser(LANDLORD, USER_ID)).thenReturn(wallet(LANDLORD, "8000.00"));
        when(walletMapper.selectByUser(TENANT, 8L)).thenReturn(
                Wallet.builder().id(202L).userType(TENANT).userId(8L)
                        .balance(new BigDecimal("0.00")).status(1).build());
        when(walletMapper.decreaseBalance(WALLET_ID, new BigDecimal("3000.00"))).thenReturn(1);
        when(walletTransactionMapper.insert(any(WalletTransaction.class))).thenAnswer(inv -> {
            WalletTransaction t = inv.getArgument(0);
            t.setId(1000L);
            return 1;
        });

        Long[] ids = walletService.transferPay(LANDLORD, USER_ID, TENANT, 8L,
                new BigDecimal("3000.00"),
                WalletConstant.BIZ_DEPOSIT_REFUND, WalletConstant.BIZ_DEPOSIT_REFUND, null);

        assertThat(ids).hasSize(2);
        verifyNoInteractions(lockedAmountProvider);
    }
}
