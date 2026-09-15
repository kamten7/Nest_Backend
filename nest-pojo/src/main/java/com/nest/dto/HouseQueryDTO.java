package com.nest.dto;

import lombok.Data;

import java.math.BigDecimal;

/** 租客端房源列表查询参数（全部可选） */
@Data
public class HouseQueryDTO {

    private String city;
    private String district;
    private BigDecimal minPrice;
    private BigDecimal maxPrice;
    private String rentType;
    private Integer roomCount;
    private String keyword;
    private String sortBy;
    private Integer page = 1;
    private Integer pageSize = 10;
    private Double userLat;
    private Double userLng;
}
