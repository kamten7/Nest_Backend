package com.nest.dto;

import lombok.Data;

/**
 * 发表评论请求体。
 */
@Data
public class ReviewCreateDTO {

    /** 房源 ID */
    private Long houseId;
    /** 评分 1-5 */
    private Integer rating;
    /** 评论内容 */
    private String content;
}
