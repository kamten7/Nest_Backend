package com.nest.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 钱包流水展示视图。
 */
@Data
public class WalletTransactionVO {

    /** 流水 ID */
    private Long id;
    /** 业务类型：RECHARGE / WITHDRAW / DEPOSIT_PAY ... */
    private String bizType;
    /** 金额（恒为正） */
    private BigDecimal amount;
    /** 方向：1 收入 / -1 支出 */
    private Integer direction;
    /** 交易后余额快照 */
    private BigDecimal balanceAfter;
    /** 状态：1 成功 / 0 处理中 / 2 失败 */
    private Integer status;
    /** 备注 */
    private String remark;
    /** 创建时间 */
    private LocalDateTime createTime;
}
