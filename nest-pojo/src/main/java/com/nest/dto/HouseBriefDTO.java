package com.nest.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 房源简要信息（订单列表 / 详情批量补标题与封面，避免 N+1）。
 */
@Data
public class HouseBriefDTO {

    private Long houseId;
    private String houseTitle;
    /** 封面图：house_image.is_cover = 1 */
    private String houseCover;
    private BigDecimal housePrice;
    private BigDecimal houseDeposit;
}
