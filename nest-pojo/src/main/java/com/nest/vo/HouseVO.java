package com.nest.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 房源列表展示视图（不含完整图片列表和房东详情）。
 */
@Data
public class HouseVO {

    private Long id;
    private Long landlordId;
    private String landlordName;
    private String landlordAvatar;
    private String title;
    /** 房源描述（详情接口返回） */
    private String description;
    private String address;
    private String province;
    private String city;
    private String district;
    private Double latitude;
    private Double longitude;
    private BigDecimal price;
    private BigDecimal deposit;
    private BigDecimal area;
    private Integer roomCount;
    private Integer hallCount;
    private Integer bathroomCount;
    private Integer floor;
    private Integer totalFloor;
    private String orientation;
    private String rentType;
    private LocalDate availableDate;
    private String utilities;
    private String requirements;
    private Integer status;
    private Integer viewCount;

    /** 封面图 URL */
    private String coverImage;
    /** 所有图片 URL（仅详情接口返回，列表接口为空） */
    private List<String> images;
    /** 标签列表 */
    private List<String> tags;
    /** 距离文本（如 "1.2km"，仅当用户提供坐标时计算） */
    private String distanceText;
    /** 创建时间 */
    private LocalDateTime createTime;
}
