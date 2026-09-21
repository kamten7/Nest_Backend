package com.nest.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 退租申请与结算记录（提交即停提醒与缴费） */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RentTermination {

    private Long id;
    private Long orderId;
    private Long tenantId;
    private LocalDateTime applyTime;
    /** 退租生效期（住满当月的周期 yyyy-MM）：该期及之前的租金视为已消耗，不退。 */
    private String effectiveEndPeriod;
    private BigDecimal deductAmount;
    /** 押金退回部分 = 订单押金 - deduct_amount。 */
    private BigDecimal refundAmount;
    /** 申请退租时未消耗的预付整月数（effectiveEndPeriod 之后、已缴到的期数）。 */
    private Integer prepaidMonths;
    /** 预付租金退回部分 = prepaidMonths × 月租，与押金退回分两笔走流水。 */
    private BigDecimal prepaidRefundAmount;
    private Integer refundStatus;
    private LocalDateTime refundTime;
    private Long refundTxnId;
    /** 预付租金退回流水 ID。 */
    private Long prepaidTxnId;
    private String remark;
    private LocalDateTime createTime;
}
