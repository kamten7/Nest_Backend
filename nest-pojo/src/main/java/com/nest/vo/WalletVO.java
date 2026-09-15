package com.nest.vo;

import lombok.Data;

import java.math.BigDecimal;

/** 钱包视图 —— 当前余额、锁定金额与可提现余额 */
@Data
public class WalletVO {

    private Long walletId;
    private BigDecimal balance;
    private BigDecimal lockedAmount;
    private BigDecimal availableBalance;
}
