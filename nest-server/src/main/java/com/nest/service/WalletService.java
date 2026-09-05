package com.nest.service;

import com.nest.common.PageResult;
import com.nest.vo.WalletTransactionVO;
import com.nest.vo.WalletTransferVO;
import com.nest.vo.WalletVO;

import java.math.BigDecimal;

/**
 * 钱包服务接口 —— 余额 / 充值 / 提现 / 流水。
 *
 * 说明：userType 与 userId 由 Controller 从登录态传入，不信任前端；
 * 余额变更与流水写入必须在同一事务内，保证账实一致。
 */
public interface WalletService {

    /** 查询我的钱包（无则懒创建），返回余额 */
    WalletVO getMyWallet(String userType, Long userId);

    /**
     * 充值：直接加余额并记流水（当前模拟，预留微信支付）。
     *
     * @return 充值后的钱包视图
     */
    WalletVO recharge(String userType, Long userId, BigDecimal amount);

    /**
     * 提现：扣减余额并记一条「处理中」流水（未对接实际到账）。
     *
     * @return 提现后的钱包视图
     */
    WalletVO withdraw(String userType, Long userId, BigDecimal amount);

    /** 我的钱包流水（分页，可按业务类型筛选） */
    PageResult<WalletTransactionVO> listTransactions(String userType, Long userId,
                                                     String bizType, Integer page, Integer pageSize);

    /**
     * 双端转账（押金/房租收付）：扣付款方钱包、加收款方钱包，记一对 peer 流水。
     *
     * @param payerType     付款方类型（如 tenant）
     * @param payerId       付款方 ID
     * @param payeeType     收款方类型（如 landlord）
     * @param payeeId       收款方 ID
     * @param amount        转账金额（>0）
     * @param payerBizType  付款方流水业务类型（如 DEPOSIT_PAY）
     * @param payeeBizType  收款方流水业务类型（如 DEPOSIT_INCOME）
     * @param bizNo         业务单号（同笔转账两侧共享）
     * @param remark        备注
     * @return 转账结果（两侧流水 ID）
     */
    WalletTransferVO transfer(String payerType, Long payerId, String payeeType, Long payeeId,
                              BigDecimal amount, String payerBizType, String payeeBizType,
                              String bizNo, String remark);
}
