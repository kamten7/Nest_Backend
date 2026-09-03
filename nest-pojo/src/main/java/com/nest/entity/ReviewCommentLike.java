package com.nest.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 评论回复点赞实体（防止重复点赞）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewCommentLike {

    private Long id;
    /** 回复ID */
    private Long commentId;
    /** 点赞用户ID */
    private Long tenantId;
    /** 创建时间 */
    private LocalDateTime createTime;
}
