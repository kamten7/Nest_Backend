package com.nest.wallet.service.impl;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.nest.common.PageResult;
import com.nest.constant.MessageConstant;
import com.nest.constant.WalletConstant;
import com.nest.entity.Wallet;
import com.nest.entity.WalletTransaction;
import com.nest.exception.BusinessException;
import com.nest.wallet.mapper.WalletMapper;
import com.nest.wallet.mapper.WalletTransactionMapper;
import com.nest.wallet.service.WalletService;
import com.nest.vo.WalletTransactionVO;
import com.nest.vo.WalletVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 钱包服务实现 —— 懒创建 + 双向记账 + 条件扣款防超扣。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WalletServiceImpl implements WalletService {

    private static final DateTimeFormatter BIZ_NO_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private final WalletMapper walletMapper;
    private final WalletTransactionMapper walletTransactionMapper;

    // ==================== 查询 ====================

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
            // 并发下已被其他请求创建，重新查询即可
            return walletMapper.selectByUser(userType, userId);
        }
    }

    /** 查询钱包余额视图。 */
    @Override
    public WalletVO getWalletVO(String userType, Long userId) {
        return buildVO(getByUser(userType, userId));
    }

    // ==================== 充值 / 提现 ====================

    /** 充值（模拟）：余额 +amount，写一条 RECHARGE 收入流水。 */
    @Override
    @Transactional
    public WalletVO recharge(String userType, Long userId, BigDecimal amount) {
        checkAmount(amount);
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

    /** 提现（预留）：条件扣款，写一条 WITHDRAW 状态=处理中 的支出流水。 */
    @Override
    @Transactional
    public WalletVO withdraw(String userType, Long userId, BigDecimal amount) {
        checkAmount(amount);
        Wallet wallet = getByUser(userType, userId);
        checkUsable(wallet);

        int rows = walletMapper.decreaseBalance(wallet.getId(), amount);
        if (rows == 0) {
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
                .remark("提现申请（待打款）")
                .build();
        walletTransactionMapper.insert(txn);

        log.info("钱包提现申请: walletId={}, amount={}, balanceAfter={}", wallet.getId(), amount, balanceAfter);
        return buildVO(wallet.getId(), balanceAfter);
    }

    // ==================== 流水 ====================

    /** 分页查询钱包流水（按 create_time 倒序）。 */
    @Override
    public PageResult<WalletTransactionVO> listTransactions(String userType, Long userId,
                                                            String bizType, Integer page, Integer pageSize) {
        int p = (page == null || page < 1) ? 1 : page;
        int ps = (pageSize == null || pageSize < 1) ? 20 : pageSize;
        PageHelper.startPage(p, ps);
        List<WalletTransaction> list = walletTransactionMapper.selectByUser(userType, userId, bizType);
        PageInfo<WalletTransaction> pageInfo = new PageInfo<>(list);
        return PageResult.of(pageInfo.getTotal(), toVOs(list));
    }

    // ==================== 转账（双向记账） ====================

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

        // 条件扣款：余额不足则 rows=0，事务回滚
        int rows = walletMapper.decreaseBalance(payer.getId(), amount);
        if (rows == 0) {
            throw new BusinessException(MessageConstant.WALLET_BALANCE_INSUFFICIENT);
        }
        walletMapper.increaseBalance(payee.getId(), amount);

        BigDecimal payerAfter = nullToZero(payer.getBalance()).subtract(amount);
        BigDecimal payeeAfter = nullToZero(payee.getBalance()).add(amount);
        String finalBizNo = (bizNo == null || bizNo.isBlank()) ? genBizNo("TX") : bizNo;
        String remark = WalletConstant.bizText(bizTypePay);

        // 付款方（支出）
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

        // 收款方（收入）
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

        // 付款流水回填对端，形成互指
        walletTransactionMapper.updatePeerTxn(payTxn.getId(), incomeTxn.getId());

        log.info("钱包转账: {}#{} -> {}#{}, amount={}, bizType={}->{}, bizNo={}",
                payerType, payerId, payeeType, payeeId, amount, bizTypePay, bizTypeIncome, finalBizNo);
        return new Long[]{payTxn.getId(), incomeTxn.getId()};
    }

    // ==================== 内部方法 ====================

    private void checkAmount(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new BusinessException(MessageConstant.WALLET_AMOUNT_INVALID);
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

    /** 业务单号：前缀 + 时间戳 + 6 位随机数（≤32 位）。 */
    private String genBizNo(String prefix) {
        return prefix + LocalDateTime.now().format(BIZ_NO_FORMATTER)
                + String.format("%06d", ThreadLocalRandom.current().nextInt(1_000_000));
    }

    private WalletVO buildVO(Wallet wallet) {
        return buildVO(wallet.getId(), wallet.getBalance());
    }

    private WalletVO buildVO(Long walletId, BigDecimal balance) {
        WalletVO vo = new WalletVO();
        vo.setWalletId(walletId);
        vo.setBalance(nullToZero(balance));
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
