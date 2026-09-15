package com.nest.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 租房订单实体（状态机：待缴押金→租房中→退租中→已退租/取消） */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RentOrder {

    private Long id;
    private String orderNo;
    private Long appointmentId;
    private Long tenantId;
    private Long houseId;
    private Long landlordId;
    private BigDecimal deposit;
    private BigDecimal monthlyRent;
    private Integer status;
    private LocalDate startDate;
    private String nextDuePeriod;
    private Integer paidMonths;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
