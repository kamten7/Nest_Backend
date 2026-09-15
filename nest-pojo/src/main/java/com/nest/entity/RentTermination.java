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
    private String effectiveEndPeriod;
    private BigDecimal deductAmount;
    private BigDecimal refundAmount;
    private Integer refundStatus;
    private LocalDateTime refundTime;
    private Long refundTxnId;
    private String remark;
    private LocalDateTime createTime;
}
