package com.nest.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 按评论推荐的房源视图（含平均评分与评论数）。
 */
@Data
public class HouseReviewVO {

    /** 房源 ID */
    private Long id;
    /** 标题 */
    private String title;
    /** 月租 */
    private BigDecimal price;
    /** 城市 */
    private String city;
    /** 区域 */
    private String district;
    /** 详细地址 */
    private String address;
    /** 面积 */
    private BigDecimal area;
    /** 室 */
    private Integer roomCount;
    /** 厅 */
    private Integer hallCount;
    /** 卫 */
    private Integer bathroomCount;
    /** 平均评分（无评论为 0） */
    private Double avgRating;
    /** 评论数 */
    private Integer reviewCount;
}
