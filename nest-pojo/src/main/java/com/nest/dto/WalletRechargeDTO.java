package com.nest.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 充值请求体。
 */
@Data
public class WalletRechargeDTO {

    /** 充值金额（大于 0） */
    private BigDecimal amount;
}
