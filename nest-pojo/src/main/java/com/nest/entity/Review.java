package com.nest.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 房源评论实体。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Review {

    private Long id;
    /** 评论者（租客）ID */
    private Long tenantId;
    /** 房源ID */
    private Long houseId;
    /** 评分 1-5 */
    private Integer rating;
    /** 评论内容 */
    private String content;
    /** 创建时间 */
    private LocalDateTime createTime;
}
