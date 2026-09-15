package com.nest.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 钱包流水视图 */
@Data
public class WalletTransactionVO {

    private Long id;
    private String bizType;
    private String bizTypeText;
    private BigDecimal amount;
    private Integer direction;
    private BigDecimal balanceAfter;
    private String source;
    private Integer status;
    private String remark;
    private LocalDateTime createTime;
}
