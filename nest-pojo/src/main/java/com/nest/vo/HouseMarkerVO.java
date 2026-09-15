package com.nest.vo;

import lombok.Data;

import java.math.BigDecimal;

/** 地图标记点视图（仅含Leaflet渲染所需字段） */
@Data
public class HouseMarkerVO {

    private Long id;
    private String title;
    private BigDecimal price;
    private Double latitude;
    private Double longitude;
    private String coverImage;
}
