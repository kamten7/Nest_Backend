package com.nest.entity.review;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 评论回复点赞实体（防止重复点赞）。
 *  ⚠️ 必须同时用 user_type + user_id 定位点赞者：
 *  租客 id=3 与房东 id=3 是两个人，早期版本只用 tenant_id 会互相串赞。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewCommentLike {

    private Long id;
    private Long commentId;
    /** 点赞者类型 tenant/landlord */
    private String userType;
    private Long userId;
    private LocalDateTime createTime;
}
