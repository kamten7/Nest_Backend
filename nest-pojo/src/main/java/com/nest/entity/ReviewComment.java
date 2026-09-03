package com.nest.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 评论回复实体（支持嵌套：parent_id 为 NULL 表示一级回复）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewComment {

    private Long id;
    /** 所属评论ID */
    private Long reviewId;
    /** 评论者类型：tenant / landlord */
    private String userType;
    /** 评论者ID */
    private Long userId;
    /** 回复内容 */
    private String content;
    /** 父回复ID（NULL = 一级回复） */
    private Long parentId;
    /** 点赞数 */
    private Integer likeCount;
    /** 创建时间 */
    private LocalDateTime createTime;
}
