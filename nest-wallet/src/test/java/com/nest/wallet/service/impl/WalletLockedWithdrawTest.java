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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 押金锁定与提现额度单元测试。
 *
 * <p>规则（用户约定）：押金收到后留在房东钱包内（余额可见），但在租期内不可提现。
 * 所以「可提现余额 = 余额 − 在租订单押金总额」，提现只能作用在可提现余额上。
 *
 * <p>覆盖点：
 * <ul>
 *   <li>查钱包时能同时看到锁定金额与可提现余额</li>
 *   <li>提现在可提现余额内 → 走带锁定条件的原子扣款</li>
 *   <li>余额够但被押金占用 → 明确报「押金不可提现」而不是「余额不足」</li>
 *   <li>没有实现方（未引入 nest-order）→ 锁定恒为 0，行为与改造前一致</li>
 *   <li>租客不受锁定影响</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("钱包押金锁定单元测试")
class WalletLockedWithdrawTest {

    private static final String LANDLORD = JwtConstant.TYPE_LANDLORD;
    private static final String TENANT = JwtConstant.TYPE_TENANT;
    private static final Long USER_ID = 7L;
    private static final Long WALLET_ID = 101L;

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
        // 生产环境由 Spring 属性注入（required=false），这里单测手动塞进去
        walletService.lockedAmountProvider = lockedAmountProvider;
    }

    // ==================== 测试数据构造 ====================

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

    private void givenLocked(String userType, String locked) {
        // 用 thenAnswer 而不是 thenReturn：Mockito 的 thenReturn(null) 会因重载解析报错
        BigDecimal value = locked == null ? null : new BigDecimal(locked);
        when(lockedAmountProvider.lockedAmountOf(userType, USER_ID)).thenAnswer(invocation -> value);
    }

    // ==================== 1. 钱包视图 ====================

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

    // ==================== 2. 提现：有锁定 ====================

    @Test
    @DisplayName("提现：金额在可提现余额内 → 用带锁定条件的原子扣款并落处理中流水")
    void withdraw_withinAvailable_usesLockedUpdateAndWritesPendingTxn() {
        givenWallet(LANDLORD, "8000.00");
        givenLocked(LANDLORD, "3000.00");
        when(walletMapper.decreaseBalanceWithLock(WALLET_ID, new BigDecimal("2000.00"), new BigDecimal("3000.00")))
                .thenReturn(1);

        WalletVO vo = walletService.withdraw(LANDLORD, USER_ID, new BigDecimal("2000.00"));

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
        givenWallet(LANDLORD, "5000.00");
        givenLocked(LANDLORD, "3000.00");
        when(walletMapper.decreaseBalanceWithLock(WALLET_ID, new BigDecimal("2000.00"), new BigDecimal("3000.00")))
                .thenReturn(1);

        WalletVO vo = walletService.withdraw(LANDLORD, USER_ID, new BigDecimal("2000.00"));

        assertThat(vo.getAvailableBalance()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("提现：余额够但被押金占用 → 报「押金不可提现」，不写流水")
    void withdraw_exceedsAvailableButWithinBalance_throwsLockedMessage() {
        givenWallet(LANDLORD, "8000.00");
        givenLocked(LANDLORD, "3000.00");
        // 余额 8000 够 5000，但扣掉 3000 押金后只剩 5000 可提… 提 6000 时锁定条件不满足
        when(walletMapper.decreaseBalanceWithLock(WALLET_ID, new BigDecimal("6000.00"), new BigDecimal("3000.00")))
                .thenReturn(0);

        assertThatThrownBy(() -> walletService.withdraw(LANDLORD, USER_ID, new BigDecimal("6000.00")))
                .isInstanceOf(BusinessException.class)
                .hasMessage(MessageConstant.WALLET_WITHDRAW_LOCKED);

        verify(walletTransactionMapper, never()).insert(any(WalletTransaction.class));
    }

    @Test
    @DisplayName("提现：可提现余额不足时按下单条件扣款，不会先扣后校验")
    void withdraw_delegatesToConditionalUpdate_onlyOnce() {
        givenWallet(LANDLORD, "8000.00");
        givenLocked(LANDLORD, "3000.00");
        when(walletMapper.decreaseBalanceWithLock(WALLET_ID, new BigDecimal("1.00"), new BigDecimal("3000.00")))
                .thenReturn(1);

        walletService.withdraw(LANDLORD, USER_ID, new BigDecimal("1.00"));

        // 只走一次原子 UPDATE（含锁定条件），没有走普通扣款
        verify(walletMapper).decreaseBalanceWithLock(WALLET_ID, new BigDecimal("1.00"), new BigDecimal("3000.00"));
        verify(walletMapper, never()).decreaseBalance(any(), any());
    }

    // ==================== 3. 提现：无锁定 / 锁定为 0 ====================

    @Test
    @DisplayName("提现：锁定额为 0（如租客）→ 走原有扣款 SQL，不受押金逻辑影响")
    void withdraw_whenLockedIsZero_usesPlainDecrease() {
        givenWallet(TENANT, "500.00");
        givenLocked(TENANT, "0");
        when(walletMapper.decreaseBalance(WALLET_ID, new BigDecimal("200.00"))).thenReturn(1);

        WalletVO vo = walletService.withdraw(TENANT, USER_ID, new BigDecimal("200.00"));

        assertThat(vo.getAvailableBalance()).isEqualByComparingTo("300.00");
        verify(walletMapper).decreaseBalance(WALLET_ID, new BigDecimal("200.00"));
        verify(walletMapper, never()).decreaseBalanceWithLock(any(), any(), any());
    }

    @Test
    @DisplayName("提现：无锁定且扣款失败 → 报余额不足")
    void withdraw_whenPlainDecreaseFails_throwsInsufficient() {
        givenWallet(TENANT, "100.00");
        givenLocked(TENANT, "0");
        when(walletMapper.decreaseBalance(WALLET_ID, new BigDecimal("999.00"))).thenReturn(0);

        assertThatThrownBy(() -> walletService.withdraw(TENANT, USER_ID, new BigDecimal("999.00")))
                .isInstanceOf(BusinessException.class)
                .hasMessage(MessageConstant.WALLET_BALANCE_INSUFFICIENT);
    }

    @Test
    @DisplayName("提现：余额本身就低于提现额（且被锁定）→ 报余额不足而不是押金锁定")
    void withdraw_whenBalanceItselfInsufficient_throwsInsufficient() {
        givenWallet(LANDLORD, "1000.00");
        givenLocked(LANDLORD, "3000.00");
        when(walletMapper.decreaseBalanceWithLock(WALLET_ID, new BigDecimal("2000.00"), new BigDecimal("3000.00")))
                .thenReturn(0);

        assertThatThrownBy(() -> walletService.withdraw(LANDLORD, USER_ID, new BigDecimal("2000.00")))
                .isInstanceOf(BusinessException.class)
                .hasMessage(MessageConstant.WALLET_BALANCE_INSUFFICIENT);
    }

    // ==================== 4. 其它接口不受影响 ====================

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
