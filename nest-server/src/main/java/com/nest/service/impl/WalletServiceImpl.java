package com.nest.service.impl;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.nest.common.PageResult;
import com.nest.constant.MessageConstant;
import com.nest.constant.WalletConstant;
import com.nest.entity.Wallet;
import com.nest.entity.WalletTransaction;
import com.nest.exception.BusinessException;
import com.nest.mapper.WalletMapper;
import com.nest.mapper.WalletTransactionMapper;
import com.nest.service.WalletService;
import com.nest.vo.WalletTransactionVO;
import com.nest.vo.WalletTransferVO;
import com.nest.vo.WalletVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * 钱包服务实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WalletServiceImpl implements WalletService {

    private final WalletMapper walletMapper;
    private final WalletTransactionMapper walletTransactionMapper;

    // ==================== 对外接口 ====================

    /**
     * 查询我的钱包。
     *
     * 流程：按用户查钱包，不存在则懒创建（初始余额 0）。
     *
     * @param userType 用户类型：tenant / landlord
     * @param userId   用户 ID
     * @return 钱包视图（ID + 余额）
     */
    @Override
    public WalletVO getMyWallet(String userType, Long userId) {
        Wallet wallet = getOrCreate(userType, userId);
        return buildWalletVO(wallet);
    }

    /**
     * 充值（当前模拟）。
     *
     * 流程：校验金额 > 0 → 原子加余额 → 重新查询余额做快照 → 写一条充值流水（source=SIMULATE）。
     * 后期接入微信支付：把 source 换成 WECHAT_PAY，并改为「先下单 → 回调后加余额」。
     *
     * @param userType 用户类型
     * @param userId   用户 ID
     * @param amount   充值金额（必须大于 0）
     * @return 充值后的钱包视图
     */
    @Override
    @Transactional
    public WalletVO recharge(String userType, Long userId, BigDecimal amount) {
        // 1. 校验金额合法
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(MessageConstant.WALLET_AMOUNT_INVALID);
        }
        // 2. 找或创建钱包
        Wallet wallet = getOrCreate(userType, userId);
        // 3. 原子加余额（balance = balance + amount）
        walletMapper.increaseBalance(wallet.getId(), amount);
        // 4. 重新查询余额（作为流水快照，保证准确）
        Wallet after = walletMapper.selectById(wallet.getId());
        // 5. 写充值流水
        walletTransactionMapper.insert(buildTxn(after, userType, userId,
                WalletConstant.BIZ_RECHARGE, amount, WalletConstant.DIRECTION_IN,
                WalletConstant.SOURCE_SIMULATE, WalletConstant.STATUS_SUCCESS, "充值"));

        log.info("钱包充值: type={}, userId={}, amount={}, balance={}",
                userType, userId, amount, after.getBalance());
        return buildWalletVO(after);
    }

    /**
     * 提现（未对接实际到账）。
     *
     * 流程：校验金额 > 0 → 原子扣减余额（余额不足直接失败）→ 重新查询余额快照 →
     * 写一条「处理中」流水。后期对接微信零钱：到账回调后把流水状态置为成功。
     *
     * @param userType 用户类型
     * @param userId   用户 ID
     * @param amount   提现金额
     * @return 提现后的钱包视图
     */
    @Override
    @Transactional
    public WalletVO withdraw(String userType, Long userId, BigDecimal amount) {
        // 1. 校验金额合法
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(MessageConstant.WALLET_AMOUNT_INVALID);
        }
        // 2. 找或创建钱包
        Wallet wallet = getOrCreate(userType, userId);
        // 3. 原子扣减余额（SQL 里带 balance >= amount，余额不足则影响行数为 0）
        int rows = walletMapper.decreaseBalanceIfSufficient(wallet.getId(), amount);
        if (rows == 0) {
            log.warn("钱包提现余额不足: type={}, userId={}, amount={}, balance={}",
                    userType, userId, amount, wallet.getBalance());
            throw new BusinessException(MessageConstant.WALLET_BALANCE_INSUFFICIENT);
        }
        // 4. 重新查询余额做快照
        Wallet after = walletMapper.selectById(wallet.getId());
        // 5. 写提现流水（状态=处理中，预留打款）
        walletTransactionMapper.insert(buildTxn(after, userType, userId,
                WalletConstant.BIZ_WITHDRAW, amount, WalletConstant.DIRECTION_OUT,
                WalletConstant.SOURCE_SIMULATE, WalletConstant.STATUS_PROCESSING, "提现到微信零钱"));

        log.info("钱包提现: type={}, userId={}, amount={}, balance={}",
                userType, userId, amount, after.getBalance());
        return buildWalletVO(after);
    }

    /**
     * 我的钱包流水（分页）。
     *
     * 流程：PageHelper 开启分页 → 查询流水 → PageInfo 取总数 → 转 VO。
     *
     * @param userType 用户类型
     * @param userId   用户 ID
     * @param bizType  业务类型过滤（可空，空=全部）
     * @param page     页码
     * @param pageSize 每页条数
     */
    @Override
    public PageResult<WalletTransactionVO> listTransactions(String userType, Long userId,
                                                            String bizType, Integer page, Integer pageSize) {
        // 1. 开启 PageHelper 分页
        PageHelper.startPage(page, pageSize);
        // 2. 查询流水
        List<WalletTransaction> txns = walletTransactionMapper.selectByUser(userType, userId, bizType);
        PageInfo<WalletTransaction> pageInfo = new PageInfo<>(txns);
        // 3. 转 VO
        List<WalletTransactionVO> vos = txns.stream().map(this::buildTxnVO).toList();
        return PageResult.of(pageInfo.getTotal(), vos);
    }

    /**
     * 双端转账（押金/房租收付）。
     *
     * 流程：校验金额 → 双方钱包（无则懒创建）→ 原子扣付款方（余额不足直接失败）→
     * 原子加收款方 → 记付款方流水 → 记收款方流水（peer 互指）→ 回填付款方 peer。
     * 整体必须在同一事务内，保证资金与流水一致。
     *
     * @return 转账结果（两侧流水 ID）
     */
    @Override
    @Transactional
    public WalletTransferVO transfer(String payerType, Long payerId, String payeeType, Long payeeId,
                                     BigDecimal amount, String payerBizType, String payeeBizType,
                                     String bizNo, String remark) {
        // 1. 校验金额合法
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(MessageConstant.WALLET_AMOUNT_INVALID);
        }
        // 2. 双方钱包（收款方可能首次收款，懒创建）
        Wallet payer = getOrCreate(payerType, payerId);
        Wallet payee = getOrCreate(payeeType, payeeId);
        // 3. 原子扣减付款方（SQL 带 balance >= amount，余额不足则影响行数为 0）
        int rows = walletMapper.decreaseBalanceIfSufficient(payer.getId(), amount);
        if (rows == 0) {
            log.warn("钱包转账余额不足: from={}:{}, to={}:{}, amount={}",
                    payerType, payerId, payeeType, payeeId, amount);
            throw new BusinessException(MessageConstant.WALLET_BALANCE_INSUFFICIENT);
        }
        // 4. 原子增加收款方
        walletMapper.increaseBalance(payee.getId(), amount);
        // 5. 重新查账做快照
        Wallet payerAfter = walletMapper.selectById(payer.getId());
        Wallet payeeAfter = walletMapper.selectById(payee.getId());
        // 6. 记付款方流水（支出，peer 先占位后回填）
        WalletTransaction payerTxn = buildTxn(payerAfter, payerType, payerId, payerBizType,
                amount, WalletConstant.DIRECTION_OUT, WalletConstant.SOURCE_SIMULATE,
                WalletConstant.STATUS_SUCCESS, remark);
        payerTxn.setBizNo(bizNo);
        walletTransactionMapper.insert(payerTxn);
        // 7. 记收款方流水（收入，peer 指向付款方）
        WalletTransaction payeeTxn = buildTxn(payeeAfter, payeeType, payeeId, payeeBizType,
                amount, WalletConstant.DIRECTION_IN, WalletConstant.SOURCE_SIMULATE,
                WalletConstant.STATUS_SUCCESS, remark);
        payeeTxn.setBizNo(bizNo);
        payeeTxn.setPeerTxnId(payerTxn.getId());
        walletTransactionMapper.insert(payeeTxn);
        // 8. 回填付款方 peer（双端互指，便于审计）
        walletTransactionMapper.updatePeerTxn(payerTxn.getId(), payeeTxn.getId());

        log.info("钱包转账: from={}:{}, to={}:{}, amount={}, bizNo={}",
                payerType, payerId, payeeType, payeeId, amount, bizNo);

        WalletTransferVO vo = new WalletTransferVO();
        vo.setPayerTxnId(payerTxn.getId());
        vo.setPayeeTxnId(payeeTxn.getId());
        return vo;
    }

    // ==================== 内部方法 ====================

    /** 按用户查钱包，不存在则创建（初始余额 0） */
    private Wallet getOrCreate(String userType, Long userId) {
        Wallet wallet = walletMapper.selectByUser(userType, userId);
        if (wallet == null) {
            wallet = Wallet.builder()
                    .userType(userType)
                    .userId(userId)
                    .balance(BigDecimal.ZERO)
                    .status(1)
                    .build();
            walletMapper.insert(wallet);
            log.info("钱包首次创建: type={}, userId={}", userType, userId);
        }
        return wallet;
    }

    /** 组装一条钱包流水（统一方向/快照/来源/状态） */
    private WalletTransaction buildTxn(Wallet wallet, String userType, Long userId,
                                       String bizType, BigDecimal amount, int direction,
                                       String source, int status, String remark) {
        return WalletTransaction.builder()
                .walletId(wallet.getId())
                .userType(userType)
                .userId(userId)
                .bizType(bizType)
                .amount(amount)
                .direction(direction)
                .balanceAfter(wallet.getBalance())
                .source(source)
                .status(status)
                .remark(remark)
                .build();
    }

    /** 钱包实体 → 钱包 VO */
    private WalletVO buildWalletVO(Wallet wallet) {
        WalletVO vo = new WalletVO();
        vo.setWalletId(wallet.getId());
        vo.setBalance(wallet.getBalance());
        return vo;
    }

    /** 流水实体 → 流水 VO */
    private WalletTransactionVO buildTxnVO(WalletTransaction txn) {
        WalletTransactionVO vo = new WalletTransactionVO();
        vo.setId(txn.getId());
        vo.setBizType(txn.getBizType());
        vo.setAmount(txn.getAmount());
        vo.setDirection(txn.getDirection());
        vo.setBalanceAfter(txn.getBalanceAfter());
        vo.setStatus(txn.getStatus());
        vo.setRemark(txn.getRemark());
        vo.setCreateTime(txn.getCreateTime());
        return vo;
    }
}
