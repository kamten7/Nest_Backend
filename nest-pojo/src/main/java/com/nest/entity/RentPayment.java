package com.nest.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 租金/押金支付记录实体。
 *
 * 押金一笔；租金每月一笔；提前支付则一次生成多笔，各对应一个周期并共享同一 bizNo。
 * 同一笔支付串起「租客扣款流水」与「房东入账流水」。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RentPayment {

    /** 主键 */
    private Long id;
    /** 所属订单 ID */
    private Long orderId;
    /** 支付类型：DEPOSIT / RENT */
    private String payType;
    /** 租金所属周期（押金记录为空） */
    private String period;
    /** 支付金额 */
    private BigDecimal amount;
    /** 支付方式：WALLET / WECHAT_PAY */
    private String payMethod;
    /** 状态：1 成功 / 0 处理中 */
    private Integer status;
    /** 业务号（提前支付多笔共享同一 bizNo） */
    private String bizNo;
    /** 租客侧钱包流水 id */
    private Long tenantTxnId;
    /** 房东侧钱包流水 id */
    private Long landlordTxnId;
    /** 创建时间 */
    private LocalDateTime createTime;
}
