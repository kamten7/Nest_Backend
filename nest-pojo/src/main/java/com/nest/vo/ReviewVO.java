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
    private List<ReviewCommentVO> comments;
    private BigDecimal avgRating;
    private long totalCount;
}
