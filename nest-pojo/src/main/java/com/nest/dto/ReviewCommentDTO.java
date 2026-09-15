package com.nest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 回复评论请求体。
 */
@Data
public class ReviewCommentDTO {

    /** 回复内容 */
    @NotBlank(message = "回复内容不能为空")
    @Size(max = 500, message = "回复内容长度不能超过 500 个字符")
    private String content;
    /** 父回复 ID（NULL = 一级回复，用于嵌套） */
    private Long parentId;
}
