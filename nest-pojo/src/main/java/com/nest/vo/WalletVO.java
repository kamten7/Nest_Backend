package com.nest.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 钱包展示视图 —— 余额。
 */
@Data
public class WalletVO {

    /** 钱包 ID */
    private Long walletId;
    /** 当前余额 */
    private BigDecimal balance;
}
