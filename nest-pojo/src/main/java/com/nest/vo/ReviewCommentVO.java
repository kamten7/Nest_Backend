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
    private Integer likeCount;
    private Boolean liked;
    private LocalDateTime createTime;
}
