package com.nest.dto;

import lombok.Data;

import java.math.BigDecimal;

/** 房源简要信息（批量补标题封面，避免 N+1） */
@Data
public class HouseBriefDTO {

    private Long houseId;
    private String houseTitle;
    private String houseCover;
    private BigDecimal housePrice;
    private BigDecimal houseDeposit;
}
