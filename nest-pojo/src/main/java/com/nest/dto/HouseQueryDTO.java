package com.nest.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 租客端房源列表查询参数（全部可选）。
 */
@Data
public class HouseQueryDTO {

    /** 城市 */
    private String city;
    /** 区域 */
    private String district;
    /** 最低租金 */
    private BigDecimal minPrice;
    /** 最高租金 */
    private BigDecimal maxPrice;
    /** 出租方式：整租/合租/短租 */
    private String rentType;
    /** 户型-室 */
    private Integer roomCount;
    /** 搜索关键词（标题/描述） */
    private String keyword;
    /** 排序：default / price_asc / price_desc / newest */
    private String sortBy;
    /** 页码（从 1 开始） */
    private Integer page = 1;
    /** 每页条数 */
    private Integer pageSize = 10;
    /** 用户当前纬度（用于计算距离） */
    private Double userLat;
    /** 用户当前经度（用于计算距离） */
    private Double userLng;
}
