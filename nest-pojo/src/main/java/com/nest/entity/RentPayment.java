package com.nest.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 租金/押金支付记录（串起租客扣款与房东入账） */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RentPayment {

    private Long id;
    private Long orderId;
    private String payType;
    private String period;
    private BigDecimal amount;
    private String payMethod;
    private Integer status;
    private String bizNo;
    private Long tenantTxnId;
    private Long landlordTxnId;
    private LocalDateTime createTime;
}
