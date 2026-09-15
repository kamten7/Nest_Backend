package com.nest.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 房源图片实体 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HouseImage {

    private Long id;
    private Long houseId;
    private String url;
    private Integer isCover;
    private Integer sortOrder;
    private LocalDateTime createTime;
}
