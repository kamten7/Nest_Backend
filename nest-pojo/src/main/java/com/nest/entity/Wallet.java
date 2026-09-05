package com.nest.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 用户钱包实体（租客/房东共用）。
 *
 * 每个用户一个钱包，租客付费、房东收款；user_type 区分两侧。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Wallet {

    /** 主键 */
    private Long id;
    /** 用户类型：tenant / landlord */
    private String userType;
    /** 用户 ID（对应 tenant.id / landlord.id） */
    private Long userId;
    /** 当前余额 */
    private BigDecimal balance;
    /** 状态：1 正常 / 0 冻结 */
    private Integer status;
    /** 创建时间 */
    private LocalDateTime createTime;
    /** 更新时间 */
    private LocalDateTime updateTime;
}
