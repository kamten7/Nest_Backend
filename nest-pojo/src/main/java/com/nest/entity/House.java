package com.nest.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 房源实体 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class House {

    private Long id;
    private Long landlordId;
    private String title;
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
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
