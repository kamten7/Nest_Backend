package com.nest.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 收藏列表展示视图 —— 收藏 + 房源关键信息 */
@Data
public class FavoriteVO {

    private Long favoriteId;
    private Long houseId;
    private String houseTitle;
    private String coverImage;
    private BigDecimal price;
    private BigDecimal area;
    private String district;
    private String address;
    private LocalDateTime createTime;
}
