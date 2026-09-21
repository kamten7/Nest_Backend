package com.nest.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 退租信息视图 */
@Data
public class RentTerminationVO {

    private Long id;
    /** 退租生效期（住满当月的周期 yyyy-MM）。 */
    private String effectiveEndPeriod;
    private BigDecimal deductAmount;
    /** 押金退回金额。 */
    private BigDecimal refundAmount;
    /** 未消耗的预付整月数。 */
    private Integer prepaidMonths;
    /** 预付租金退回金额。 */
    private BigDecimal prepaidRefundAmount;
    /** 租客可退回总额 = 押金退回 + 预付租金退回。 */
    private BigDecimal totalRefundAmount;
    private Integer refundStatus;
    private LocalDateTime applyTime;
    /** 冷却期到期时间 = applyTime + 7 天，到点房东方可结算（超时由定时任务兜底全额退）。 */
    private LocalDateTime settleAvailableTime;
    private LocalDateTime refundTime;
    private String remark;
    private LocalDateTime createTime;
}
