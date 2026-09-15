package com.nest.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 用户钱包实体（租客 / 房东共用，靠 userType 区分）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Wallet {

    private Long id;
    /** 账户归属类型：tenant / landlord */
    private String userType;
    /** 账户归属用户 ID（tenant.id / landlord.id） */
    private Long userId;
    /** 当前余额（元） */
    private BigDecimal balance;
    /** 账户状态：1 正常，0 冻结 */
    private Integer status;
    /** 创建时间 */
    private LocalDateTime createTime;
    /** 更新时间 */
    private LocalDateTime updateTime;
}
