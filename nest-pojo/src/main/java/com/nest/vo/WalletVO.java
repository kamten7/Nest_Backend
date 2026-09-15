package com.nest.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 钱包视图 —— 当前余额、锁定金额与可提现余额。
 */
@Data
public class WalletVO {

    /** 钱包 ID */
    private Long walletId;
    /** 当前余额（元） */
    private BigDecimal balance;
    /** 锁定金额（元）：在租订单的押金，房东可见但不可提现 */
    private BigDecimal lockedAmount;
    /** 可提现余额（元）= balance − lockedAmount */
    private BigDecimal availableBalance;
}
