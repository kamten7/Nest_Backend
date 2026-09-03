package com.nest.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 房源实体。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class House {

    private Long id;
    /** 房东ID */
    private Long landlordId;
    /** 房源标题 */
    private String title;
    /** 房源描述 */
    private String description;
    /** 详细地址 */
    private String address;
    /** 省 */
    private String province;
    /** 市 */
    private String city;
    /** 区 */
    private String district;
    /** 纬度 */
    private Double latitude;
    /** 经度 */
    private Double longitude;
    /** 月租金 */
    private BigDecimal price;
    /** 押金 */
    private BigDecimal deposit;
    /** 面积(㎡) */
    private BigDecimal area;
    /** 室 */
    private Integer roomCount;
    /** 厅 */
    private Integer hallCount;
    /** 卫 */
    private Integer bathroomCount;
    /** 楼层 */
    private Integer floor;
    /** 总楼层 */
    private Integer totalFloor;
    /** 朝向 */
    private String orientation;
    /** 出租方式：整租/合租/短租 */
    private String rentType;
    /** 可入住日期 */
    private LocalDate availableDate;
    /** 水电燃气说明 */
    private String utilities;
    /** 租客要求 */
    private String requirements;
    /** 状态：1 上架，0 下架 */
    private Integer status;
    /** 浏览次数 */
    private Integer viewCount;
    /** 创建时间 */
    private LocalDateTime createTime;
    /** 更新时间 */
    private LocalDateTime updateTime;
}
