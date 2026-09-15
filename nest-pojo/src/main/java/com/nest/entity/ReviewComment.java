package com.nest.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 评论回复实体（parent_id为NULL即一级回复） */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewComment {

    private Long id;
    private Long reviewId;
    private String userType;
    private Long userId;
    private String content;
    private Long parentId;
    private Integer likeCount;
    private LocalDateTime createTime;
}
