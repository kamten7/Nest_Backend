package com.nest.wallet.service;

import com.nest.common.PageResult;
import com.nest.entity.Wallet;
import com.nest.vo.WalletTransactionVO;
import com.nest.vo.WalletVO;

import java.math.BigDecimal;

/** 钱包服务。 */
public interface WalletService {

    /** 按 (userType,userId) 查询钱包，不存在则懒创建。 */
    Wallet getByUser(String userType, Long userId);

    /** 查询我的钱包（余额视图）。 */
    WalletVO getWalletVO(String userType, Long userId);

    /** 充值（模拟，预留微信支付）。 */
    WalletVO recharge(String userType, Long userId, BigDecimal amount);

    /** 提现（预留微信零钱到账，落"处理中"流水）。 */
    WalletVO withdraw(String userType, Long userId, BigDecimal amount);

    /** 分页查询钱包流水（bizType 为空=全部）。 */
    PageResult<WalletTransactionVO> listTransactions(String userType, Long userId,
                                                     String bizType, Integer page, Integer pageSize);

    /** 钱包间转账（双向记账）：扣付款方、入收款方，两笔流水 */
    Long[] transferPay(String payerType, Long payerId, String payeeType, Long payeeId,
                       BigDecimal amount, String bizTypePay, String bizTypeIncome, String bizNo);
}
