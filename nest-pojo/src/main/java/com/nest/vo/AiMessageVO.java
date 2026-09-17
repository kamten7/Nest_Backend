package com.nest.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** AI 对话历史展示视图（role 与小程序气泡对齐：user / ai） */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiMessageVO {

    private String role;
    private String content;
}
