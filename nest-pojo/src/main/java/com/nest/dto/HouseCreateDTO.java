package com.nest.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** 发布/编辑房源请求体（图片先上传拿URL再传入） */
@Data
public class    HouseCreateDTO {

    @NotBlank(message = "房源标题不能为空")
    @Size(max = 100, message = "房源标题长度不能超过 100 个字符")
    private String title;
    @Size(max = 2000, message = "房源描述长度不能超过 2000 个字符")
    private String description;
    @NotBlank(message = "详细地址不能为空")
    @Size(max = 200, message = "详细地址长度不能超过 200 个字符")
    private String address;
    @Size(max = 50, message = "省份名称过长")
    private String province;
    @NotBlank(message = "城市不能为空")
    @Size(max = 50, message = "城市名称过长")
    private String city;
    @Size(max = 50, message = "区县名称过长")
    private String district;
    private Double latitude;
    private Double longitude;
    @NotNull(message = "月租金不能为空")
    @DecimalMin(value = "0", inclusive = false, message = "月租金必须大于 0")
    private BigDecimal price;
    @DecimalMin(value = "0", message = "押金不能为负数")
    private BigDecimal deposit;
    @DecimalMin(value = "0", inclusive = false, message = "面积必须大于 0")
    private BigDecimal area;
    private Integer roomCount;
    private Integer hallCount;
    private Integer bathroomCount;
    private Integer floor;
    private Integer totalFloor;
    @Size(max = 50, message = "朝向长度过长")
    private String orientation;
    @Size(max = 20, message = "出租方式长度过长")
    private String rentType;
    private LocalDate availableDate;
    @Size(max = 500, message = "水电燃气说明过长")
    private String utilities;
    @Size(max = 500, message = "租客要求过长")
    private String requirements;

    private List<String> images;
    private List<String> tags;
}
