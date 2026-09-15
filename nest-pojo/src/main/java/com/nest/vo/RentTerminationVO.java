package com.nest.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 退租信息视图。
 */
@Data
public class RentTerminationVO {

    private Long id;
    /** 生效的已购租期末周期 */
    private String effectiveEndPeriod;
    /** 从押金中扣除、归房东的金额 */
    private BigDecimal deductAmount;
    /** 实际退回租客的押金金额 */
    private BigDecimal refundAmount;
    /** 0 待退 / 1 已退 */
    private Integer refundStatus;
    /** 押金实际退回时间 */
    private LocalDateTime refundTime;
    private String remark;
    private LocalDateTime createTime;
}
