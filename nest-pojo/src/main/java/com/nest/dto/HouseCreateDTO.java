package com.nest.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 发布/编辑房源请求体。
 * 图片先通过 /admin/house/upload 上传拿到 URL 再传入。
 */
@Data
public class HouseCreateDTO {

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

    /** 图片 URL 列表（已上传到 MinIO 的完整 URL），第一张为封面 */
    private List<String> images;
    /** 标签列表，如 ["近地铁", "朝南", "精装修"] */
    private List<String> tags;
}
