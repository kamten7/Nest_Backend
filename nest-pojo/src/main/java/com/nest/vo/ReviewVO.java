package com.nest.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** 房源评论展示视图 —— 评论 + 租客信息 + 回复列表 */
@Data
public class ReviewVO {

    private Long id;
    private Long houseId;
    private Long tenantId;
    private String tenantName;
    private String tenantAvatar;
    private Integer rating;
    private String content;
    private LocalDateTime createTime;
    private Integer likeCount;
    private Boolean liked;
    /** 是否当前请求者本人发的（前端据此显示「删除」） */
    private Boolean mine;
    private List<ReviewCommentVO> comments;
    private BigDecimal avgRating;
    /** 评价总数（含未打分的纯评论） */
    private long totalCount;
    /** 已打分评价数（平均分只统计这些） */
    private long ratedCount;
}
