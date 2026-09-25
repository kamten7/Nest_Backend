package com.nest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** AI 找房对话请求体。 */
@Data
public class AiChatDTO {

    @NotBlank(message = "请输入找房需求")
    @Size(max = 200, message = "找房需求不能超过 200 个字符")
    private String message;
}
