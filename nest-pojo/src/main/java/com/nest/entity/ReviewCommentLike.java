package com.nest.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 评论回复点赞实体（防止重复点赞） */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewCommentLike {

    private Long id;
    private Long commentId;
    private Long tenantId;
    private LocalDateTime createTime;
}
