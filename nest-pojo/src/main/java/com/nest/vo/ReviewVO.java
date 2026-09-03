package com.nest.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 房源评论展示视图 —— 评论 + 租客信息 + 回复列表。
 */
@Data
public class ReviewVO {

    /** 评论 ID */
    private Long id;
    /** 房源 ID */
    private Long houseId;
    /** 租客 ID */
    private Long tenantId;
    /** 租客昵称 */
    private String tenantName;
    /** 租客头像 */
    private String tenantAvatar;
    /** 评分 1-5 */
    private Integer rating;
    /** 评论内容 */
    private String content;
    /** 创建时间 */
    private LocalDateTime createTime;
    /** 该评论的回复列表 */
    private List<ReviewCommentVO> comments;
    /** 房源平均分（列表接口每页带一次，前端可显示） */
    private BigDecimal avgRating;
    /** 房源评论总数 */
    private long totalCount;
}
