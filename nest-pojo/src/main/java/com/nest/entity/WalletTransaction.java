package com.nest.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 钱包流水实体（双向记账） */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WalletTransaction {

    private Long id;
    private Long walletId;
    private String userType;
    private Long userId;
    private String bizType;
    private BigDecimal amount;
    private Integer direction;
    private BigDecimal balanceAfter;
    private String source;
    private Integer status;
    private String bizNo;
    /** 幂等键（提现防重；其余流水为 NULL，MySQL 唯一索引对 NULL 不去重） */
    private String idemKey;
    private Long peerTxnId;
    private String remark;
    private LocalDateTime createTime;
}
