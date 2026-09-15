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
    private Long peerTxnId;
    private String remark;
    private LocalDateTime createTime;
}
