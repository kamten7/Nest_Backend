package com.nest.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 租金 / 押金支付记录实体。
 *
 * <p>同一笔支付串起「租客扣款流水」与「房东入账流水」（tenant_txn_id / landlord_txn_id）。
 * 提前支付 N 期时一次生成 N 条 RENT 记录，共享同一 biz_no。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RentPayment {

    private Long id;
    /** 所属订单 */
    private Long orderId;
    /** 支付类型：DEPOSIT 押金 / RENT 租金 */
    private String payType;
    /** 租金所属周期 2026-09（押金记录为空） */
    private String period;
    /** 支付金额 */
    private BigDecimal amount;
    /** 支付方式：WALLET 钱包 / WECHAT_PAY 微信支付（预留） */
    private String payMethod;
    /** 状态：1 成功 / 0 处理中 */
    private Integer status;
    /** 业务号：提前支付 N 期共享同一 biz_no */
    private String bizNo;
    /** 租客侧钱包流水 ID */
    private Long tenantTxnId;
    /** 房东侧钱包流水 ID */
    private Long landlordTxnId;
    private LocalDateTime createTime;
}
