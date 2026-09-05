package com.nest.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 钱包流水实体（双向记账，用于对账与审计）。
 *
 * 金额恒为正数，用 direction 区分收入/支出；
 * 押金/房租由「租客出 → 房东收」构成一对 peer 流水，便于追溯同一笔钱。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WalletTransaction {

    /** 主键 */
    private Long id;
    /** 所属钱包 ID */
    private Long walletId;
    /** 用户类型（冗余，便于按用户查询） */
    private String userType;
    /** 用户 ID（冗余，便于按用户查询） */
    private Long userId;
    /** 业务类型：RECHARGE / WITHDRAW / DEPOSIT_PAY / DEPOSIT_INCOME / RENT_PAY ... */
    private String bizType;
    /** 金额（恒为正） */
    private BigDecimal amount;
    /** 方向：1 收入 / -1 支出 */
    private Integer direction;
    /** 交易后余额快照 */
    private BigDecimal balanceAfter;
    /** 资金来源：SIMULATE / WECHAT_PAY */
    private String source;
    /** 状态：1 成功 / 0 处理中 / 2 失败 */
    private Integer status;
    /** 业务单号（关联支付记录，或多笔共享） */
    private String bizNo;
    /** 对端流水 ID（同笔转账双端关联） */
    private Long peerTxnId;
    /** 备注 */
    private String remark;
    /** 创建时间 */
    private LocalDateTime createTime;
}
