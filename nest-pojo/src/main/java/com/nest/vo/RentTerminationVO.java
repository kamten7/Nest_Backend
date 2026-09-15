package com.nest.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 退租信息视图 */
@Data
public class RentTerminationVO {

    private Long id;
    private String effectiveEndPeriod;
    private BigDecimal deductAmount;
    private BigDecimal refundAmount;
    private Integer refundStatus;
    private LocalDateTime refundTime;
    private String remark;
    private LocalDateTime createTime;
}
