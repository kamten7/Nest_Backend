package com.nest.wallet.service.impl;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.nest.common.PageParam;
import com.nest.common.PageResult;
import com.nest.constant.MessageConstant;
import com.nest.constant.WalletConstant;
import com.nest.entity.Wallet;
import com.nest.entity.WalletTransaction;
import com.nest.exception.BusinessException;
import com.nest.wallet.mapper.WalletMapper;
import com.nest.wallet.mapper.WalletTransactionMapper;
import com.nest.wallet.service.LockedAmountProvider;
import com.nest.wallet.service.WalletService;
import com.nest.vo.WalletTransactionVO;
import com.nest.vo.WalletVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;

/** 钱包服务实现 —— 懒创建 + 双向记账 + 条件扣款防超扣。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WalletServiceImpl implements WalletService {

    private static final DateTimeFormatter BIZ_NO_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final BigDecimal ZERO = BigDecimal.ZERO;

    /** 只为把"provider 缺失"这条 ERROR 打一次，避免每次查钱包都刷屏 */
    private final AtomicBoolean lockProviderWarned = new AtomicBoolean(false);

    private final WalletMapper walletMapper;
    private final WalletTransactionMapper walletTransactionMapper;

    @Autowired(required = false)
    LockedAmountProvider lockedAmountProvider;

    /**
     * 模拟充值开关（默认关闭）。
     * <p>recharge 目前是「直接给余额加钱」的模拟实现，没有支付回调、没有幂等键 ⇒
     * 生产环境开着等于任何人都能凭空造钱。因此默认关闭，只允许本地/测试显式打开。
     * 接入真实支付后应改为「下单 + 回调 + 幂等」链路。</p>
     */
    @Value("${nest.wallet.simulate-recharge-enabled:false}")
    private boolean simulateRechargeEnabled;


    /** 按用户查询钱包，不存在则懒创建。 */
    @Override
    public Wallet getByUser(String userType, Long userId) {
        Wallet wallet = walletMapper.selectByUser(userType, userId);
        if (wallet != null) {
            return wallet;
        }
        Wallet created = Wallet.builder()
                .userType(userType)
                .userId(userId)
                .balance(ZERO)
                .status(1)
                .build();
        try {
            walletMapper.insert(created);
            log.info("懒创建钱包: id={}, userType={}, userId={}", created.getId(), userType, userId);
            return created;
        } catch (DuplicateKeyException e) {
            return walletMapper.selectByUser(userType, userId);
        }
    }

    /** 查询钱包余额视图（含锁定金额与可提现余额）。 */
    @Override
    public WalletVO getWalletVO(String userType, Long userId) {
        Wallet wallet = getByUser(userType, userId);
        BigDecimal locked = lockedAmount(userType, userId);
        if (locked.signum() > 0) {
            log.info("钱包带锁定金额: userType={}, userId={}, balance={}, locked={}",
                    userType, userId, wallet.getBalance(), locked);
        }
        return buildVO(wallet.getId(), wallet.getBalance(), locked);
    }


    /** 充值（模拟）：余额 +amount，写一条 RECHARGE 收入流水。 */
    @Override
    @Transactional
    public WalletVO recharge(String userType, Long userId, BigDecimal amount) {
        if (!simulateRechargeEnabled) {
            log.warn("拒绝模拟充值：nest.wallet.simulate-recharge-enabled=false, userType={}, userId={}",
                    userType, userId);
            throw new BusinessException(MessageConstant.RECHARGE_DISABLED);
        }
        checkAmount(amount);
        checkRechargeAmount(amount);
        Wallet wallet = getByUser(userType, userId);
        checkUsable(wallet);

        walletMapper.increaseBalance(wallet.getId(), amount);
        BigDecimal balanceAfter = nullToZero(wallet.getBalance()).add(amount);

        WalletTransaction txn = WalletTransaction.builder()
                .walletId(wallet.getId())
                .userType(userType)
                .userId(userId)
                .bizType(WalletConstant.BIZ_RECHARGE)
                .amount(amount)
                .direction(WalletConstant.DIRECTION_IN)
                .balanceAfter(balanceAfter)
                .source(WalletConstant.SOURCE_SIMULATE)
                .status(WalletConstant.STATUS_SUCCESS)
                .bizNo(genBizNo("RC"))
                .remark("模拟充值")
                .build();
        walletTransactionMapper.insert(txn);

        log.info("钱包充值: walletId={}, amount={}, balanceAfter={}", wallet.getId(), amount, balanceAfter);
        return buildVO(wallet.getId(), balanceAfter);
    }

    /** 提现（预留）：幂等受理 + 行锁下条件扣款，写一条 WITHDRAW 状态=处理中 的支出流水。 */
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public WalletVO withdraw(String userType, Long userId, BigDecimal amount, String idempotencyKey) {
        checkAmount(amount);
        /* 锁定金额由订单模块经 SPI 提供；缺了它就等于"押金不设防"，资金操作宁可拒绝也不能放行 */
        requireLockProvider();
        String idemKey = requireIdempotencyKey(idempotencyKey);

        Wallet wallet = getByUser(userType, userId);
        checkUsable(wallet);

        /* 幂等快路径：同键已受理过就直接拒绝，省掉后面的加锁与扣款。
           并发窗口由 uk_idem 唯一索引兜底（见下方 insert 的 catch） */
        if (walletTransactionMapper.selectByIdemKey(idemKey) != null) {
            log.warn("提现重复提交（快路径命中）: userType={}, userId={}, idemKey={}", userType, userId, idemKey);
            throw new BusinessException(MessageConstant.WALLET_IDEM_DUPLICATE);
        }
        wallet = walletMapper.lockById(wallet.getId());
        checkUsable(wallet);

        BigDecimal locked = lockedAmount(userType, userId);
        /* 无条件走带锁定校验的 SQL：绝不允许退化成只校验余额的 decreaseBalance */
        int rows = walletMapper.decreaseBalanceWithLock(wallet.getId(), amount, locked);
        if (rows == 0) {
            /* 余额本身够、却仍扣不动 ⇒ 只可能是被锁定金额挡住（balance − locked < amount） */
            if (nullToZero(wallet.getBalance()).compareTo(amount) >= 0) {
                throw new BusinessException(MessageConstant.WALLET_WITHDRAW_LOCKED);
            }
            throw new BusinessException(MessageConstant.WALLET_BALANCE_INSUFFICIENT);
        }
        BigDecimal balanceAfter = nullToZero(wallet.getBalance()).subtract(amount);

        WalletTransaction txn = WalletTransaction.builder()
                .walletId(wallet.getId())
                .userType(userType)
                .userId(userId)
                .bizType(WalletConstant.BIZ_WITHDRAW)
                .amount(amount)
                .direction(WalletConstant.DIRECTION_OUT)
                .balanceAfter(balanceAfter)
                .source(WalletConstant.SOURCE_SIMULATE)
                .status(WalletConstant.STATUS_PENDING)
                .bizNo(genBizNo("WD"))
                .idemKey(idemKey)
                .remark("提现申请（待打款）")
                .build();
        try {
            walletTransactionMapper.insert(txn);
        } catch (DuplicateKeyException e) {
            /* 快路径（selectByIdemKey）拦不住并发双击：两个请求都通过了预检查，
               串行拿到行锁后第二个 insert 必撞 uk_idem ⇒ 抛业务异常让整个事务回滚，
               余额扣减一并撤销，效果等同"这笔请求从没发生过" */
            log.warn("提现幂等命中: walletId={}, idemKey={}", wallet.getId(), idemKey);
            throw new BusinessException(MessageConstant.WALLET_IDEM_DUPLICATE);
        }

        log.info("钱包提现申请: walletId={}, amount={}, balanceAfter={}, locked={}",
                wallet.getId(), amount, balanceAfter, locked);
        return buildVO(wallet.getId(), balanceAfter, locked);
    }


    /** 分页查询钱包流水（按 create_time 倒序）。 */
    @Override
    public PageResult<WalletTransactionVO> listTransactions(String userType, Long userId,
                                                            String bizType, Integer page, Integer pageSize) {
        PageHelper.startPage(PageParam.pageOf(page), PageParam.pageSizeOf(pageSize));
        List<WalletTransaction> list = walletTransactionMapper.selectByUser(userType, userId, bizType);
        PageInfo<WalletTransaction> pageInfo = new PageInfo<>(list);
        return PageResult.of(pageInfo.getTotal(), toVOs(list));
    }


    /** 钱包间转账：扣付款方 + 入收款方，两笔流水 peer 互指、同一 biz_no。 */
    @Override
    @Transactional
    public Long[] transferPay(String payerType, Long payerId, String payeeType, Long payeeId,
                              BigDecimal amount, String bizTypePay, String bizTypeIncome, String bizNo) {
        checkAmount(amount);

        Wallet payer = getByUser(payerType, payerId);
        Wallet payee = getByUser(payeeType, payeeId);
        checkUsable(payer);
        checkUsable(payee);

        int rows = walletMapper.decreaseBalance(payer.getId(), amount);
        if (rows == 0) {
            throw new BusinessException(MessageConstant.WALLET_BALANCE_INSUFFICIENT);
        }
        walletMapper.increaseBalance(payee.getId(), amount);

        BigDecimal payerAfter = nullToZero(payer.getBalance()).subtract(amount);
        BigDecimal payeeAfter = nullToZero(payee.getBalance()).add(amount);
        String finalBizNo = (bizNo == null || bizNo.isBlank()) ? genBizNo("TX") : bizNo;
        String remark = WalletConstant.bizText(bizTypePay);

        WalletTransaction payTxn = WalletTransaction.builder()
                .walletId(payer.getId())
                .userType(payerType)
                .userId(payerId)
                .bizType(bizTypePay)
                .amount(amount)
                .direction(WalletConstant.DIRECTION_OUT)
                .balanceAfter(payerAfter)
                .source(WalletConstant.SOURCE_SIMULATE)
                .status(WalletConstant.STATUS_SUCCESS)
                .bizNo(finalBizNo)
                .remark(remark)
                .build();
        walletTransactionMapper.insert(payTxn);

        WalletTransaction incomeTxn = WalletTransaction.builder()
                .walletId(payee.getId())
                .userType(payeeType)
                .userId(payeeId)
                .bizType(bizTypeIncome)
                .amount(amount)
                .direction(WalletConstant.DIRECTION_IN)
                .balanceAfter(payeeAfter)
                .source(WalletConstant.SOURCE_SIMULATE)
                .status(WalletConstant.STATUS_SUCCESS)
                .bizNo(finalBizNo)
                .peerTxnId(payTxn.getId())
                .remark(WalletConstant.bizText(bizTypeIncome))
                .build();
        walletTransactionMapper.insert(incomeTxn);

        walletTransactionMapper.updatePeerTxn(payTxn.getId(), incomeTxn.getId());

        log.info("钱包转账: {}#{} -> {}#{}, amount={}, bizType={}->{}, bizNo={}",
                payerType, payerId, payeeType, payeeId, amount, bizTypePay, bizTypeIncome, finalBizNo);
        return new Long[]{payTxn.getId(), incomeTxn.getId()};
    }


    private void checkAmount(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new BusinessException(MessageConstant.WALLET_AMOUNT_INVALID);
        }
    }

    /** 资金操作的 fail-fast：锁定来源缺失时绝不放行（展示可以降级，扣钱不可以）。 */
    private void requireLockProvider() {
        if (lockedAmountProvider == null) {
            log.error("LockedAmountProvider 未装配：无法计算在租押金锁定额，已拒绝资金操作。");
            throw new BusinessException(MessageConstant.WALLET_LOCK_SOURCE_UNAVAILABLE);
        }
    }

    /** 幂等键必填：没有它就无法区分"同一笔请求"和"第二笔提现"。 */
    private String requireIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new BusinessException(MessageConstant.WALLET_IDEM_KEY_REQUIRED);
        }
        return idempotencyKey.trim();
    }

    /** 充值单笔上限校验（防止误输入或恶意构造天文数字）。 */
    private void checkRechargeAmount(BigDecimal amount) {
        if (amount.compareTo(WalletConstant.RECHARGE_AMOUNT_MAX) > 0) {
            throw new BusinessException(MessageConstant.WALLET_AMOUNT_EXCEED
                    + "（单笔上限 " + WalletConstant.RECHARGE_AMOUNT_MAX.stripTrailingZeros().toPlainString() + " 元）");
        }
    }

    private void checkUsable(Wallet wallet) {
        if (wallet == null) {
            throw new BusinessException(MessageConstant.WALLET_NOT_FOUND);
        }
        if (wallet.getStatus() != null && wallet.getStatus() == 0) {
            throw new BusinessException(MessageConstant.WALLET_FROZEN);
        }
    }

    private BigDecimal nullToZero(BigDecimal v) {
        return v == null ? ZERO : v;
    }

    /** 查询某用户的锁定金额（不可提现部分）。实现方返回 null 时视为 0。 */
    private BigDecimal lockedAmount(String userType, Long userId) {
        if (lockedAmountProvider == null) {
            /* 展示路径允许降级为 0（资金操作已在 requireLockProvider 拦下），
               但必须喊出来——静默按 0 等于把"押金不设防"藏起来 */
            if (lockProviderWarned.compareAndSet(false, true)) {
                log.error("LockedAmountProvider 未装配：锁定金额按 0 展示（仅影响展示，资金操作已被拒绝）");
            }
            return ZERO;
        }
        return nullToZero(lockedAmountProvider.lockedAmountOf(userType, userId));
    }

    /** 业务单号：前缀 + 时间戳 + 6 位随机数（≤32 位）。 */
    private String genBizNo(String prefix) {
        return prefix + LocalDateTime.now().format(BIZ_NO_FORMATTER)
                + String.format("%06d", ThreadLocalRandom.current().nextInt(1_000_000));
    }

    private WalletVO buildVO(Wallet wallet) {
        return buildVO(wallet.getId(), wallet.getBalance(), ZERO);
    }

    private WalletVO buildVO(Long walletId, BigDecimal balance) {
        return buildVO(walletId, balance, ZERO);
    }

    private WalletVO buildVO(Long walletId, BigDecimal balance, BigDecimal lockedAmount) {
        BigDecimal b = nullToZero(balance);
        BigDecimal locked = nullToZero(lockedAmount);
        WalletVO vo = new WalletVO();
        vo.setWalletId(walletId);
        vo.setBalance(b);
        vo.setLockedAmount(locked);
        BigDecimal available = b.subtract(locked);
        vo.setAvailableBalance(available.signum() < 0 ? ZERO : available);
        return vo;
    }

    private List<WalletTransactionVO> toVOs(List<WalletTransaction> list) {
        if (list == null || list.isEmpty()) {
            return Collections.emptyList();
        }
        List<WalletTransactionVO> vos = new ArrayList<>(list.size());
        for (WalletTransaction t : list) {
            WalletTransactionVO vo = new WalletTransactionVO();
            vo.setId(t.getId());
            vo.setBizType(t.getBizType());
            vo.setBizTypeText(WalletConstant.bizText(t.getBizType()));
            vo.setAmount(t.getAmount());
            vo.setDirection(t.getDirection());
            vo.setBalanceAfter(t.getBalanceAfter());
            vo.setSource(t.getSource());
            vo.setStatus(t.getStatus());
            vo.setRemark(t.getRemark());
            vo.setCreateTime(t.getCreateTime());
            vos.add(vo);
        }
        return vos;
    }
}
