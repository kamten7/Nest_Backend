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

    /**
     * 分页参数在入口收口：page ≥ 1（上限 1000 仅防极端 offset），pageSize ∈ [1, 100]。
     */
    public void setPage(Integer page) {
        this.page = (page == null || page < 1) ? 1 : Math.min(page, 1000);
    }

    public void setPageSize(Integer pageSize) {
        this.pageSize = (pageSize == null) ? 10 : Math.max(1, Math.min(pageSize, 100));
    }
}
