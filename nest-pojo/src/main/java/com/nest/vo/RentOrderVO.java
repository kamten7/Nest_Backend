package com.nest.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 租房订单视图（字段与前端miniapp/api/rent.js等同步） */
@Data
public class RentOrderVO {

    private Long id;
    private String orderNo;
    private Long houseId;
    private String houseTitle;
    private String houseCover;
    /** 房东 / 租客身份，供订单页「联系房东」「联系租客」入口使用 */
    private Long landlordId;
    private String landlordName;
    private Long tenantId;
    private String tenantName;
    private BigDecimal deposit;
    private BigDecimal monthlyRent;
    private Integer status;
    private LocalDate startDate;
    private String nextDuePeriod;
    private Integer paidMonths;
    private LocalDateTime createTime;
    private List<RentPaymentVO> payments;
    private RentTerminationVO termination;
}
