package com.nest.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 发表评论请求体。rating 可为空：退租租客发「评价」带星级，看房用户发「评论」可不打分。
 *  @Min/@Max 对 null 天然放行，因此无需 @NotNull。 */
@Data
public class ReviewCreateDTO {

    @NotNull(message = "房源 ID 不能为空")
    private Long houseId;
    /** 评分 1-5，可为空（空 = 纯评论/提问，不计入平均分） */
    @Min(value = 1, message = "评分范围为 1-5")
    @Max(value = 5, message = "评分范围为 1-5")
    private Integer rating;
    @NotBlank(message = "评论内容不能为空")
    @Size(max = 500, message = "评论内容长度不能超过 500 个字符")
    private String content;
}
