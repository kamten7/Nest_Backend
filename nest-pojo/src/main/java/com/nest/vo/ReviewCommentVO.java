package com.nest.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 评论回复展示视图。
 */
@Data
public class ReviewCommentVO {

    /** 回复 ID */
    private Long id;
    /** 所属评论 ID */
    private Long reviewId;
    /** 回复者类型：tenant / landlord */
    private String userType;
    /** 回复者 ID */
    private Long userId;
    /** 回复者昵称 */
    private String userName;
    /** 回复内容 */
    private String content;
    /** 父回复 ID */
    private Long parentId;
    /** 点赞数 */
    private Integer likeCount;
    /** 当前用户是否已赞 */
    private Boolean liked;
    /** 创建时间 */
    private LocalDateTime createTime;
}
