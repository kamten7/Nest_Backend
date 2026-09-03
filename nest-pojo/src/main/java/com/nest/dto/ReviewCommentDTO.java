package com.nest.dto;

import lombok.Data;

/**
 * 回复评论请求体。
 */
@Data
public class ReviewCommentDTO {

    /** 回复内容 */
    private String content;
    /** 父回复 ID（NULL = 一级回复，用于嵌套） */
    private Long parentId;
}
