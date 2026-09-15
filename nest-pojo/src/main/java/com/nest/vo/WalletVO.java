package com.nest.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 钱包视图 —— 当前余额。
 */
@Data
public class WalletVO {

    /** 钱包 ID */
    private Long walletId;
    /** 当前余额（元） */
    private BigDecimal balance;
}
