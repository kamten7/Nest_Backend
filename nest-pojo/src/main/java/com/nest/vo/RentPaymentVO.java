package com.nest.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 缴费记录视图 */
@Data
public class RentPaymentVO {

    private Long id;
    private String payType;
    private String period;
    private BigDecimal amount;
    private Integer status;
    private String bizNo;
    private LocalDateTime createTime;
}
