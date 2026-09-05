package com.nest.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 提现请求体。
 */
@Data
public class WalletWithdrawDTO {

    /** 提现金额（大于 0 且不超过余额） */
    private BigDecimal amount;
}
