package com.nest.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 缴费记录视图。
 */
@Data
public class RentPaymentVO {

    private Long id;
    /** 支付类型：DEPOSIT 押金 / RENT 租金 */
    private String payType;
    /** 租金所属周期（押金为空） */
    private String period;
    private BigDecimal amount;
    /** 1 成功 / 0 处理中 */
    private Integer status;
    private String bizNo;
    private LocalDateTime createTime;
}
