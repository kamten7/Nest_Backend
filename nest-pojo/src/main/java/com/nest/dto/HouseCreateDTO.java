package com.nest.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 发布/编辑房源请求体。
 * 图片先通过 /admin/house/upload 上传拿到 URL 再传入。
 */
@Data
public class    HouseCreateDTO {

    /** 房源标题 */
    @NotBlank(message = "房源标题不能为空")
    @Size(max = 100, message = "房源标题长度不能超过 100 个字符")
    private String title;
    /** 房源描述 */
    @Size(max = 2000, message = "房源描述长度不能超过 2000 个字符")
    private String description;
    /** 详细地址 */
    @NotBlank(message = "详细地址不能为空")
    @Size(max = 200, message = "详细地址长度不能超过 200 个字符")
    private String address;
    /** 省 */
    @Size(max = 50, message = "省份名称过长")
    private String province;
    /** 市 */
    @NotBlank(message = "城市不能为空")
    @Size(max = 50, message = "城市名称过长")
    private String city;
    /** 区 */
    @Size(max = 50, message = "区县名称过长")
    private String district;
    /** 纬度 */
    private Double latitude;
    /** 经度 */
    private Double longitude;
    /** 月租金 */
    @NotNull(message = "月租金不能为空")
    @DecimalMin(value = "0", inclusive = false, message = "月租金必须大于 0")
    private BigDecimal price;
    /** 押金 */
    @DecimalMin(value = "0", message = "押金不能为负数")
    private BigDecimal deposit;
    /** 面积(㎡) */
    @DecimalMin(value = "0", inclusive = false, message = "面积必须大于 0")
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
    @Size(max = 50, message = "朝向长度过长")
    private String orientation;
    /** 出租方式：整租/合租/短租 */
    @Size(max = 20, message = "出租方式长度过长")
    private String rentType;
    /** 可入住日期 */
    private LocalDate availableDate;
    /** 水电燃气说明 */
    @Size(max = 500, message = "水电燃气说明过长")
    private String utilities;
    /** 租客要求 */
    @Size(max = 500, message = "租客要求过长")
    private String requirements;

    /** 图片 URL 列表（已上传到 MinIO 的完整 URL），第一张为封面 */
    private List<String> images;
    /** 标签列表，如 ["近地铁", "朝南", "精装修"] */
    private List<String> tags;
}
