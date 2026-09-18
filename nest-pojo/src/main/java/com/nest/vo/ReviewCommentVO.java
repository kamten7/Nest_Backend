package com.nest.vo;

import lombok.Data;

import java.time.LocalDateTime;

/** 评论回复展示视图 */
@Data
public class ReviewCommentVO {

    private Long id;
    private Long reviewId;
    private String userType;
    private Long userId;
    private String userName;
    private String content;
    private Long parentId;
    /** 被回复人昵称（parent_id 非空时有值，用于展示「A 回复 B：」） */
    private String parentUserName;
    private Integer likeCount;
    private Boolean liked;
    /** 是否当前请求者本人发的（前端据此显示「删除」） */
    private Boolean mine;
    private LocalDateTime createTime;
}
