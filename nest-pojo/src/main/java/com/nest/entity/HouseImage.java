package com.nest.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 房源图片实体。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HouseImage {

    private Long id;
    /** 房源ID */
    private Long houseId;
    /** 图片URL（MinIO） */
    private String url;
    /** 是否封面图 */
    private Integer isCover;
    /** 排序 */
    private Integer sortOrder;
    /** 创建时间 */
    private LocalDateTime createTime;
}
