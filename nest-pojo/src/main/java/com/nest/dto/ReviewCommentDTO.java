package com.nest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 回复评论请求体 */
@Data
public class ReviewCommentDTO {

    @NotBlank(message = "回复内容不能为空")
    @Size(max = 500, message = "回复内容长度不能超过 500 个字符")
    private String content;
    private Long parentId;
}
