package com.nest.dto;

import lombok.Data;

import java.math.BigDecimal;

/** 确认租房源数据；房东ID/押金/月租以库为准，不信任前端 */
@Data
public class RentSourceDTO {

    private Long appointmentId;
    private Long appointmentTenantId;
    private Integer appointmentStatus;

    private Long houseId;
    private Long houseLandlordId;
    private String houseTitle;
    private String houseCover;
    private BigDecimal housePrice;
    private BigDecimal houseDeposit;
    private Integer houseStatus;
}
