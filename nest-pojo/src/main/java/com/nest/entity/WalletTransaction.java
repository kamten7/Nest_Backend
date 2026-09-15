package com.nest.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 钱包流水实体（双向记账）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WalletTransaction {

    private Long id;
    /** 产生流水的钱包 ID */
    private Long walletId;
    /** 用户类型（冗余，便于按用户查询） */
    private String userType;
    /** 用户 ID（冗余，便于按用户查询） */
    private Long userId;
    /** 业务类型：RECHARGE/WITHDRAW/DEPOSIT_PAY/DEPOSIT_INCOME/DEPOSIT_REFUND/RENT_PAY/RENT_INCOME */
    private String bizType;
    /** 金额（恒正数，方向由 direction 表达） */
    private BigDecimal amount;
    /** 方向：1 收入（+）/ -1 支出（−） */
    private Integer direction;
    /** 交易后的余额快照（用于对账） */
    private BigDecimal balanceAfter;
    /** 资金来源：SIMULATE / WECHAT_PAY */
    private String source;
    /** 状态：1 成功 / 0 处理中 / 2 失败 */
    private Integer status;
    /** 业务单号：关联业务与流水 */
    private String bizNo;
    /** 对端流水 ID（同笔转账双端互指） */
    private Long peerTxnId;
    /** 备注 */
    private String remark;
    /** 创建时间 */
    private LocalDateTime createTime;
}
