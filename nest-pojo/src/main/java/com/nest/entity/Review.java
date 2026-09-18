package com.nest.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 房源评论实体（顶楼帖：带星评价 或 无星纯评论） */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Review {

    private Long id;
    private Long tenantId;
    private Long houseId;
    /** 评分 1-5；null 表示未打分的纯评论，不参与平均分统计 */
    private Integer rating;
    private String content;
    private Integer likeCount;
    private LocalDateTime createTime;
}
