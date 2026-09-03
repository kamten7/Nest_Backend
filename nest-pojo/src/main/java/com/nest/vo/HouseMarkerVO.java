package com.nest.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 地图标记点视图 —— 仅包含 Leaflet 渲染标记所需的字段。
 */
@Data
public class HouseMarkerVO {

    /** 房源 ID */
    private Long id;
    /** 房源标题 */
    private String title;
    /** 月租金 */
    private BigDecimal price;
    /** 纬度 */
    private Double latitude;
    /** 经度 */
    private Double longitude;
    /** 封面图 URL */
    private String coverImage;
}
