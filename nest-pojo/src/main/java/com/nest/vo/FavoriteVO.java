package com.nest.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 收藏列表展示视图 —— 收藏 + 房源关键信息。
 */
@Data
public class FavoriteVO {

    /** 收藏记录 ID */
    private Long favoriteId;
    /** 房源 ID */
    private Long houseId;
    /** 房源标题 */
    private String houseTitle;
    /** 封面图 URL */
    private String coverImage;
    /** 月租金 */
    private BigDecimal price;
    /** 面积(㎡) */
    private BigDecimal area;
    /** 区域 */
    private String district;
    /** 详细地址 */
    private String address;
    /** 收藏时间 */
    private LocalDateTime createTime;
}
