package com.nest.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 用户钱包实体（租客/房东共用，靠userType区分） */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Wallet {

    private Long id;
    private String userType;
    private Long userId;
    private BigDecimal balance;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
