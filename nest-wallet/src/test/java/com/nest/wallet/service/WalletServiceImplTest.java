package com.nest.wallet.service;

import com.github.pagehelper.PageHelper;
import com.nest.common.PageResult;
import com.nest.constant.JwtConstant;
import com.nest.constant.MessageConstant;
import com.nest.constant.WalletConstant;
import com.nest.entity.Wallet;
import com.nest.entity.WalletTransaction;
import com.nest.exception.BusinessException;
import com.nest.vo.WalletTransactionVO;
import com.nest.vo.WalletVO;
import com.nest.wallet.mapper.WalletMapper;
import com.nest.wallet.mapper.WalletTransactionMapper;
import com.nest.wallet.service.impl.WalletServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 钱包服务单元测试。 */
@ExtendWith(MockitoExtension.class)
@DisplayName("钱包服务单元测试")
class WalletServiceImplTest {

    private static final String TENANT = JwtConstant.TYPE_TENANT;
    private static final Long USER_ID = 7L;
    private static final Long WALLET_ID = 101L;

    @Mock
    private WalletMapper walletMapper;

    @Mock
    private WalletTransactionMapper walletTransactionMapper;

    @InjectMocks
    private WalletServiceImpl walletService;

    /**
     * 模拟充值开关默认是 false（生产必须如此，见 nest.wallet.simulate-recharge-enabled）。
     * 纯单测不起 Spring，@Value 不生效 ⇒ 这里显式打开，让原有的 recharge 成功路径用例仍可验证。
     */
    @BeforeEach
    void enableSimulateRecharge() {
        ReflectionTestUtils.setField(walletService, "simulateRechargeEnabled", true);
    }

    @Test
    @DisplayName("充值开关关闭时：直接拒绝，且不碰任何 mapper（零副作用）")
    void recharge_whenDisabled_throwsAndTouchesNothing() {
        ReflectionTestUtils.setField(walletService, "simulateRechargeEnabled", false);

        assertThatThrownBy(() -> walletService.recharge(TENANT, USER_ID, new BigDecimal("100.00")))
                .isInstanceOf(BusinessException.class)
                .hasMessage(MessageConstant.RECHARGE_DISABLED);

        // 开关是"安全默认"：必须在最前面拦住，连钱包都不该去查
        verify(walletMapper, never()).selectByUser(any(), any());
        verify(walletTransactionMapper, never()).insert(any());
    }

    @AfterEach
    void clearPageHelper() {
        PageHelper.clearPage();
    }


    private Wallet wallet(Long id, String balance, Integer status) {
        return Wallet.builder()
                .id(id)
                .userType(TENANT)
                .userId(USER_ID)
                .balance(balance == null ? null : new BigDecimal(balance))
                .status(status)
                .build();
    }

    /** 钱包已存在，余额为 balance。 */
    private void givenWalletExists(String balance) {
        when(walletMapper.selectByUser(TENANT, USER_ID)).thenReturn(wallet(WALLET_ID, balance, 1));
    }

    /** 钱包不存在：首次 selectByUser 返回 null，insert 时模拟主键回填。 */
    private void givenWalletAbsentThenCreated() {
        when(walletMapper.selectByUser(TENANT, USER_ID)).thenReturn(null);
        when(walletMapper.insert(any(Wallet.class))).thenAnswer(invocation -> {
            Wallet w = invocation.getArgument(0);
            w.setId(WALLET_ID);
            return 1;
        });
    }

    private ArgumentCaptor<WalletTransaction> captureTxns(int expectedCount) {
        ArgumentCaptor<WalletTransaction> captor = ArgumentCaptor.forClass(WalletTransaction.class);
        verify(walletTransactionMapper, times(expectedCount)).insert(captor.capture());
        return captor;
    }


    @Test
    @DisplayName("查余额：钱包已存在时直接返回余额，不重复创建")
    void getWalletVO_whenExists_returnsBalanceAndDoesNotCreate() {
        givenWalletExists("88.50");

        WalletVO vo = walletService.getWalletVO(TENANT, USER_ID);

        assertThat(vo.getWalletId()).isEqualTo(WALLET_ID);
        assertThat(vo.getBalance()).isEqualByComparingTo("88.50");
        verify(walletMapper, never()).insert(any(Wallet.class));
    }

    @Test
    @DisplayName("查余额：钱包不存在时懒创建，返回余额 0")
    void getWalletVO_whenAbsent_lazyCreatesWalletWithZeroBalance() {
        givenWalletAbsentThenCreated();

        WalletVO vo = walletService.getWalletVO(TENANT, USER_ID);

        assertThat(vo.getWalletId()).isEqualTo(WALLET_ID);
        assertThat(vo.getBalance()).isEqualByComparingTo(BigDecimal.ZERO);

        ArgumentCaptor<Wallet> captor = ArgumentCaptor.forClass(Wallet.class);
        verify(walletMapper, times(1)).insert(captor.capture());
        Wallet created = captor.getValue();
        assertThat(created.getUserType()).isEqualTo(TENANT);
        assertThat(created.getUserId()).isEqualTo(USER_ID);
        assertThat(created.getBalance()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(created.getStatus()).isEqualTo(1);
    }

    @Test
    @DisplayName("查余额：并发下 insert 撞唯一索引，回落重查而不是报错")
    void getWalletVO_whenConcurrentInsertConflict_requeriesAndSucceeds() {
        when(walletMapper.selectByUser(TENANT, USER_ID))
                .thenReturn(null, wallet(WALLET_ID, "12.00", 1));
        when(walletMapper.insert(any(Wallet.class)))
                .thenThrow(new DuplicateKeyException("uk_wallet_user"));

        WalletVO vo = walletService.getWalletVO(TENANT, USER_ID);

        assertThat(vo.getWalletId()).isEqualTo(WALLET_ID);
        assertThat(vo.getBalance()).isEqualByComparingTo("12.00");
        verify(walletMapper, times(2)).selectByUser(TENANT, USER_ID);
    }

    @Test
    @DisplayName("查余额：余额字段为 null 时按 0 展示，不抛 NPE")
    void getWalletVO_whenBalanceNull_returnsZero() {
        givenWalletExists(null);

        WalletVO vo = walletService.getWalletVO(TENANT, USER_ID);

        assertThat(vo.getBalance()).isEqualByComparingTo(BigDecimal.ZERO);
    }


    @Test
    @DisplayName("充值：余额累加并落一条 RECHARGE 成功收入流水")
    void recharge_success_increasesBalanceAndWritesIncomeTxn() {
        givenWalletExists("10.00");
        when(walletMapper.increaseBalance(WALLET_ID, new BigDecimal("50.00"))).thenReturn(1);

        WalletVO vo = walletService.recharge(TENANT, USER_ID, new BigDecimal("50.00"));

        assertThat(vo.getBalance()).isEqualByComparingTo("60.00");
        verify(walletMapper, times(1)).increaseBalance(WALLET_ID, new BigDecimal("50.00"));

        WalletTransaction txn = captureTxns(1).getValue();
        assertThat(txn.getWalletId()).isEqualTo(WALLET_ID);
        assertThat(txn.getUserType()).isEqualTo(TENANT);
        assertThat(txn.getUserId()).isEqualTo(USER_ID);
        assertThat(txn.getBizType()).isEqualTo(WalletConstant.BIZ_RECHARGE);
        assertThat(txn.getAmount()).isEqualByComparingTo("50.00");
        assertThat(txn.getDirection()).isEqualTo(WalletConstant.DIRECTION_IN);
        assertThat(txn.getBalanceAfter()).isEqualByComparingTo("60.00");
        assertThat(txn.getSource()).isEqualTo(WalletConstant.SOURCE_SIMULATE);
        assertThat(txn.getStatus()).isEqualTo(WalletConstant.STATUS_SUCCESS);
        assertThat(txn.getBizNo()).startsWith("RC").hasSize(22);
        assertThat(txn.getRemark()).isEqualTo("模拟充值");
    }

    @Test
    @DisplayName("充值：金额为空/0/负数一律拒绝，且不动余额、不写流水")
    void recharge_whenAmountNotPositive_throwsAndTouchesNothing() {
        BigDecimal[] badAmounts = {null, BigDecimal.ZERO, new BigDecimal("-0.01")};

        for (BigDecimal bad : badAmounts) {
            assertThatThrownBy(() -> walletService.recharge(TENANT, USER_ID, bad))
                    .as("amount=%s 应被拒绝", bad)
                    .isInstanceOf(BusinessException.class)
                    .hasMessage(MessageConstant.WALLET_AMOUNT_INVALID);
        }

        verify(walletMapper, never()).selectByUser(any(), any());
        verify(walletMapper, never()).increaseBalance(any(), any());
        verify(walletTransactionMapper, never()).insert(any(WalletTransaction.class));
    }

    @Test
    @DisplayName("充值：钱包冻结时拒绝，且不动余额、不写流水")
    void recharge_whenWalletFrozen_throwsAndDoesNotIncrease() {
        when(walletMapper.selectByUser(TENANT, USER_ID)).thenReturn(wallet(WALLET_ID, "10.00", 0));

        assertThatThrownBy(() -> walletService.recharge(TENANT, USER_ID, new BigDecimal("10.00")))
                .isInstanceOf(BusinessException.class)
                .hasMessage(MessageConstant.WALLET_FROZEN);

        verify(walletMapper, never()).increaseBalance(any(), any());
        verify(walletTransactionMapper, never()).insert(any(WalletTransaction.class));
    }

    @Test
    @DisplayName("充值：首次充值自动建钱包，再入账并落流水")
    void recharge_firstTime_lazyCreatesWalletThenCredits() {
        givenWalletAbsentThenCreated();
        when(walletMapper.increaseBalance(WALLET_ID, new BigDecimal("20.00"))).thenReturn(1);

        WalletVO vo = walletService.recharge(TENANT, USER_ID, new BigDecimal("20.00"));

        assertThat(vo.getWalletId()).isEqualTo(WALLET_ID);
        assertThat(vo.getBalance()).isEqualByComparingTo("20.00");
        verify(walletMapper, times(1)).insert(any(Wallet.class));
        verify(walletMapper, times(1)).increaseBalance(WALLET_ID, new BigDecimal("20.00"));
        verify(walletTransactionMapper, times(1)).insert(any(WalletTransaction.class));
    }


    @Test
    @DisplayName("提现：余额扣减并落一条 WITHDRAW 处理中支出流水")
    void withdraw_success_decreasesBalanceAndWritesPendingTxn() {
        givenWalletExists("100.00");
        when(walletMapper.lockById(WALLET_ID)).thenReturn(wallet(WALLET_ID, "100.00", 1));
        when(walletMapper.decreaseBalanceWithLock(WALLET_ID, new BigDecimal("30.00"), BigDecimal.ZERO)).thenReturn(1);

        WalletVO vo = walletService.withdraw(TENANT, USER_ID, new BigDecimal("30.00"));

        assertThat(vo.getBalance()).isEqualByComparingTo("70.00");

        WalletTransaction txn = captureTxns(1).getValue();
        assertThat(txn.getBizType()).isEqualTo(WalletConstant.BIZ_WITHDRAW);
        assertThat(txn.getDirection()).isEqualTo(WalletConstant.DIRECTION_OUT);
        assertThat(txn.getAmount()).isEqualByComparingTo("30.00");
        assertThat(txn.getBalanceAfter()).isEqualByComparingTo("70.00");
        assertThat(txn.getStatus()).isEqualTo(WalletConstant.STATUS_PENDING);
        assertThat(txn.getBizNo()).startsWith("WD");
        assertThat(txn.getRemark()).isEqualTo("提现申请（待打款）");
    }

    @Test
    @DisplayName("提现：条件扣款影响行数为 0 时抛出余额不足，且绝不落流水")
    void withdraw_whenInsufficient_throwsAndWritesNoTxn() {
        givenWalletExists("5.00");
        when(walletMapper.lockById(WALLET_ID)).thenReturn(wallet(WALLET_ID, "5.00", 1));
        when(walletMapper.decreaseBalanceWithLock(WALLET_ID, new BigDecimal("30.00"), BigDecimal.ZERO)).thenReturn(0);

        assertThatThrownBy(() -> walletService.withdraw(TENANT, USER_ID, new BigDecimal("30.00")))
                .isInstanceOf(BusinessException.class)
                .hasMessage(MessageConstant.WALLET_BALANCE_INSUFFICIENT);

        verify(walletTransactionMapper, never()).insert(any(WalletTransaction.class));
        verify(walletMapper, never()).increaseBalance(any(), any());
    }

    @Test
    @DisplayName("提现：金额非法直接拒绝，不查询钱包也不加锁")
    void withdraw_whenAmountNotPositive_throwsAndTouchesNothing() {
        assertThatThrownBy(() -> walletService.withdraw(TENANT, USER_ID, null))
                .isInstanceOf(BusinessException.class)
                .hasMessage(MessageConstant.WALLET_AMOUNT_INVALID);

        verify(walletMapper, never()).lockById(any());
        verify(walletMapper, never()).decreaseBalanceWithLock(any(), any(), any());
        verify(walletTransactionMapper, never()).insert(any(WalletTransaction.class));
    }

    @Test
    @DisplayName("提现：钱包冻结时拒绝（在加锁前就拦下）")
    void withdraw_whenWalletFrozen_throws() {
        when(walletMapper.selectByUser(TENANT, USER_ID)).thenReturn(wallet(WALLET_ID, "100.00", 0));

        assertThatThrownBy(() -> walletService.withdraw(TENANT, USER_ID, new BigDecimal("1.00")))
                .isInstanceOf(BusinessException.class)
                .hasMessage(MessageConstant.WALLET_FROZEN);

        verify(walletMapper, never()).lockById(any());
        verify(walletMapper, never()).decreaseBalanceWithLock(any(), any(), any());
    }


    @Test
    @DisplayName("流水：字段透传 + bizType 翻译成中文，direction 保持原值")
    void listTransactions_mapsFieldsAndTranslatesBizType() {
        WalletTransaction recharge = WalletTransaction.builder()
                .id(1L).walletId(WALLET_ID).userType(TENANT).userId(USER_ID)
                .bizType(WalletConstant.BIZ_RECHARGE).amount(new BigDecimal("50.00"))
                .direction(WalletConstant.DIRECTION_IN).balanceAfter(new BigDecimal("60.00"))
                .source(WalletConstant.SOURCE_SIMULATE).status(WalletConstant.STATUS_SUCCESS)
                .remark("模拟充值").build();
        WalletTransaction rentPay = WalletTransaction.builder()
                .id(2L).walletId(WALLET_ID).userType(TENANT).userId(USER_ID)
                .bizType(WalletConstant.BIZ_RENT_PAY).amount(new BigDecimal("2000.00"))
                .direction(WalletConstant.DIRECTION_OUT).balanceAfter(new BigDecimal("60.00"))
                .source(WalletConstant.SOURCE_SIMULATE).status(WalletConstant.STATUS_SUCCESS)
                .remark("缴纳房租").build();
        when(walletTransactionMapper.selectByUser(TENANT, USER_ID, null))
                .thenReturn(List.of(recharge, rentPay));

        PageResult<WalletTransactionVO> result =
                walletService.listTransactions(TENANT, USER_ID, null, 1, 20);

        assertThat(result.getTotal()).isEqualTo(2L);
        assertThat(result.getRecords()).hasSize(2);

        WalletTransactionVO first = result.getRecords().get(0);
        assertThat(first.getId()).isEqualTo(1L);
        assertThat(first.getBizType()).isEqualTo(WalletConstant.BIZ_RECHARGE);
        assertThat(first.getBizTypeText()).isEqualTo("充值");
        assertThat(first.getDirection()).isEqualTo(WalletConstant.DIRECTION_IN);
        assertThat(first.getBalanceAfter()).isEqualByComparingTo("60.00");
        assertThat(first.getSource()).isEqualTo(WalletConstant.SOURCE_SIMULATE);
        assertThat(first.getStatus()).isEqualTo(WalletConstant.STATUS_SUCCESS);

        assertThat(result.getRecords().get(1).getBizTypeText()).isEqualTo("缴纳房租");
        assertThat(result.getRecords().get(1).getDirection()).isEqualTo(WalletConstant.DIRECTION_OUT);
    }

    @Test
    @DisplayName("流水：按业务类型筛选时把 bizType 透传给 Mapper")
    void listTransactions_passesBizTypeFilterToMapper() {
        when(walletTransactionMapper.selectByUser(TENANT, USER_ID, WalletConstant.BIZ_WITHDRAW))
                .thenReturn(List.of());

        PageResult<WalletTransactionVO> result =
                walletService.listTransactions(TENANT, USER_ID, WalletConstant.BIZ_WITHDRAW, 1, 20);

        assertThat(result.getRecords()).isEmpty();
        verify(walletTransactionMapper, times(1))
                .selectByUser(TENANT, USER_ID, WalletConstant.BIZ_WITHDRAW);
    }

    @Test
    @DisplayName("流水：无数据时返回空列表而不是 null")
    void listTransactions_whenNoData_returnsEmptyList() {
        when(walletTransactionMapper.selectByUser(TENANT, USER_ID, null)).thenReturn(List.of());

        PageResult<WalletTransactionVO> result =
                walletService.listTransactions(TENANT, USER_ID, null, 1, 20);

        assertThat(result.getTotal()).isZero();
        assertThat(result.getRecords()).isNotNull().isEmpty();
    }

    @Test
    @DisplayName("流水：page/pageSize 非法（null、0、负数）时归一化为 1/20")
    void listTransactions_normalizesInvalidPageParams() {
        when(walletTransactionMapper.selectByUser(TENANT, USER_ID, null)).thenReturn(List.of());

        walletService.listTransactions(TENANT, USER_ID, null, 0, -5);

        assertThat(PageHelper.getLocalPage()).isNotNull();
        assertThat(PageHelper.getLocalPage().getPageNum()).isEqualTo(1);
        assertThat(PageHelper.getLocalPage().getPageSize()).isEqualTo(20);
    }


    @Test
    @DisplayName("转账：扣付款方、入收款方，两笔流水同 bizNo 且 peer 互指")
    void transferPay_movesMoneyAndLinksBothTxns() {
        Wallet payerWallet = wallet(WALLET_ID, "100.00", 1);
        Wallet payeeWallet = Wallet.builder()
                .id(202L).userType(JwtConstant.TYPE_LANDLORD).userId(1L)
                .balance(new BigDecimal("0.00")).status(1).build();
        String bizNo = "ORDER-20260915001";

        when(walletMapper.selectByUser(TENANT, USER_ID)).thenReturn(payerWallet);
        when(walletMapper.selectByUser(JwtConstant.TYPE_LANDLORD, 1L)).thenReturn(payeeWallet);
        when(walletMapper.decreaseBalance(WALLET_ID, new BigDecimal("100.00"))).thenReturn(1);
        when(walletMapper.increaseBalance(202L, new BigDecimal("100.00"))).thenReturn(1);

        AtomicLong seq = new AtomicLong(200L);
        when(walletTransactionMapper.insert(any(WalletTransaction.class))).thenAnswer(invocation -> {
            WalletTransaction t = invocation.getArgument(0);
            t.setId(seq.incrementAndGet());
            return 1;
        });

        Long[] ids = walletService.transferPay(
                TENANT, USER_ID, JwtConstant.TYPE_LANDLORD, 1L, new BigDecimal("100.00"),
                WalletConstant.BIZ_DEPOSIT_PAY, WalletConstant.BIZ_DEPOSIT_INCOME, bizNo);

        assertThat(ids).containsExactly(201L, 202L);
        verify(walletMapper, times(1)).decreaseBalance(WALLET_ID, new BigDecimal("100.00"));
        verify(walletMapper, times(1)).increaseBalance(202L, new BigDecimal("100.00"));

        List<WalletTransaction> txns = captureTxns(2).getAllValues();

        WalletTransaction pay = txns.get(0);
        assertThat(pay.getWalletId()).isEqualTo(WALLET_ID);
        assertThat(pay.getBizType()).isEqualTo(WalletConstant.BIZ_DEPOSIT_PAY);
        assertThat(pay.getDirection()).isEqualTo(WalletConstant.DIRECTION_OUT);
        assertThat(pay.getBalanceAfter()).isEqualByComparingTo("0.00");
        assertThat(pay.getBizNo()).isEqualTo(bizNo);
        assertThat(pay.getRemark()).isEqualTo("缴纳押金");

        WalletTransaction income = txns.get(1);
        assertThat(income.getWalletId()).isEqualTo(202L);
        assertThat(income.getUserType()).isEqualTo(JwtConstant.TYPE_LANDLORD);
        assertThat(income.getBizType()).isEqualTo(WalletConstant.BIZ_DEPOSIT_INCOME);
        assertThat(income.getDirection()).isEqualTo(WalletConstant.DIRECTION_IN);
        assertThat(income.getBalanceAfter()).isEqualByComparingTo("100.00");
        assertThat(income.getBizNo()).isEqualTo(bizNo);
        assertThat(income.getRemark()).isEqualTo("收取押金");
        assertThat(income.getPeerTxnId()).isEqualTo(201L);

        verify(walletTransactionMapper, times(1)).updatePeerTxn(201L, 202L);
    }

    @Test
    @DisplayName("转账：付款方余额不足时抛异常，绝不给出款方加钱、不落任何流水")
    void transferPay_whenInsufficient_throwsAndNeverCreditsPayee() {
        when(walletMapper.selectByUser(TENANT, USER_ID)).thenReturn(wallet(WALLET_ID, "10.00", 1));
        when(walletMapper.selectByUser(JwtConstant.TYPE_LANDLORD, 1L))
                .thenReturn(Wallet.builder().id(202L).userType(JwtConstant.TYPE_LANDLORD)
                        .userId(1L).balance(BigDecimal.ZERO).status(1).build());
        when(walletMapper.decreaseBalance(WALLET_ID, new BigDecimal("999.00"))).thenReturn(0);

        assertThatThrownBy(() -> walletService.transferPay(
                TENANT, USER_ID, JwtConstant.TYPE_LANDLORD, 1L, new BigDecimal("999.00"),
                WalletConstant.BIZ_DEPOSIT_PAY, WalletConstant.BIZ_DEPOSIT_INCOME, null))
                .isInstanceOf(BusinessException.class)
                .hasMessage(MessageConstant.WALLET_BALANCE_INSUFFICIENT);

        verify(walletMapper, never()).increaseBalance(any(), any());
        verify(walletTransactionMapper, never()).insert(any(WalletTransaction.class));
        verify(walletTransactionMapper, never()).updatePeerTxn(any(), any());
    }

    @Test
    @DisplayName("转账：未传 bizNo 时自动生成 TX 前缀单号，两笔流水共用同一单号")
    void transferPay_whenBizNoBlank_generatesSharedBizNo() {
        when(walletMapper.selectByUser(TENANT, USER_ID)).thenReturn(wallet(WALLET_ID, "100.00", 1));
        when(walletMapper.selectByUser(JwtConstant.TYPE_LANDLORD, 1L))
                .thenReturn(Wallet.builder().id(202L).userType(JwtConstant.TYPE_LANDLORD)
                        .userId(1L).balance(BigDecimal.ZERO).status(1).build());
        when(walletMapper.decreaseBalance(WALLET_ID, new BigDecimal("50.00"))).thenReturn(1);
        when(walletMapper.increaseBalance(202L, new BigDecimal("50.00"))).thenReturn(1);

        AtomicLong seq = new AtomicLong(300L);
        when(walletTransactionMapper.insert(any(WalletTransaction.class))).thenAnswer(invocation -> {
            WalletTransaction t = invocation.getArgument(0);
            t.setId(seq.incrementAndGet());
            return 1;
        });

        walletService.transferPay(TENANT, USER_ID, JwtConstant.TYPE_LANDLORD, 1L,
                new BigDecimal("50.00"), WalletConstant.BIZ_RENT_PAY,
                WalletConstant.BIZ_RENT_INCOME, "   ");

        List<WalletTransaction> txns = captureTxns(2).getAllValues();
        assertThat(txns.get(0).getBizNo()).startsWith("TX");
        assertThat(txns.get(1).getBizNo()).isEqualTo(txns.get(0).getBizNo());
    }

    @Test
    @DisplayName("转账：金额非法时不查钱包、不扣款")
    void transferPay_whenAmountNotPositive_throwsAndTouchesNothing() {
        assertThatThrownBy(() -> walletService.transferPay(
                TENANT, USER_ID, JwtConstant.TYPE_LANDLORD, 1L, BigDecimal.ZERO,
                WalletConstant.BIZ_RENT_PAY, WalletConstant.BIZ_RENT_INCOME, null))
                .isInstanceOf(BusinessException.class)
                .hasMessage(MessageConstant.WALLET_AMOUNT_INVALID);

        verify(walletMapper, never()).decreaseBalance(any(), any());
        verify(walletMapper, never()).selectByUser(any(), any());
        verify(walletTransactionMapper, never()).insert(any(WalletTransaction.class));
    }

    @Test
    @DisplayName("转账：收款方钱包冻结时拒绝交易，不扣付款方")
    void transferPay_whenPayeeFrozen_throwsBeforeDeductingPayer() {
        when(walletMapper.selectByUser(TENANT, USER_ID)).thenReturn(wallet(WALLET_ID, "100.00", 1));
        when(walletMapper.selectByUser(JwtConstant.TYPE_LANDLORD, 1L))
                .thenReturn(Wallet.builder().id(202L).userType(JwtConstant.TYPE_LANDLORD)
                        .userId(1L).balance(BigDecimal.ZERO).status(0).build());

        assertThatThrownBy(() -> walletService.transferPay(
                TENANT, USER_ID, JwtConstant.TYPE_LANDLORD, 1L, new BigDecimal("10.00"),
                WalletConstant.BIZ_RENT_PAY, WalletConstant.BIZ_RENT_INCOME, null))
                .isInstanceOf(BusinessException.class)
                .hasMessage(MessageConstant.WALLET_FROZEN);

        verify(walletMapper, never()).decreaseBalance(any(), any());
        verify(walletTransactionMapper, never()).insert(any(WalletTransaction.class));
    }


    @Test
    @DisplayName("bizType 未知时中文映射回退为原值，不返回 null")
    void bizText_unknownType_fallsBackToRawValue() {
        assertThat(WalletConstant.bizText("SOMETHING_NEW")).isEqualTo("SOMETHING_NEW");
        assertThat(WalletConstant.bizText(null)).isEmpty();
    }

    @Test
    @DisplayName("查余额：冻结钱包允许查看余额（冻结只拦截资金变动）")
    void getWalletVO_whenFrozen_stillReturnsBalance() {
        when(walletMapper.selectByUser(TENANT, USER_ID)).thenReturn(wallet(WALLET_ID, "66.00", 0));

        WalletVO vo = walletService.getWalletVO(TENANT, USER_ID);

        assertThat(vo.getBalance()).isEqualByComparingTo("66.00");
    }

    @Test
    @DisplayName("查余额：租客与房东钱包按 userType 隔离，互不串号")
    void getWalletVO_usesUserTypeToIsolateWallets() {
        when(walletMapper.selectByUser(TENANT, USER_ID)).thenReturn(wallet(WALLET_ID, "10.00", 1));
        when(walletMapper.selectByUser(JwtConstant.TYPE_LANDLORD, USER_ID))
                .thenReturn(Wallet.builder().id(999L).userType(JwtConstant.TYPE_LANDLORD)
                        .userId(USER_ID).balance(new BigDecimal("20.00")).status(1).build());

        WalletVO tenantVo = walletService.getWalletVO(TENANT, USER_ID);
        WalletVO landlordVo = walletService.getWalletVO(JwtConstant.TYPE_LANDLORD, USER_ID);

        assertThat(tenantVo.getWalletId()).isEqualTo(WALLET_ID);
        assertThat(landlordVo.getWalletId()).isEqualTo(999L);
        assertThat(tenantVo.getBalance()).isEqualByComparingTo("10.00");
        assertThat(landlordVo.getBalance()).isEqualByComparingTo("20.00");
        verify(walletMapper, times(1)).selectByUser(eq(TENANT), eq(USER_ID));
        verify(walletMapper, times(1)).selectByUser(eq(JwtConstant.TYPE_LANDLORD), eq(USER_ID));
    }
}
