package com.nest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 聊天消息发送请求体（WebSocket 或 REST 用） */
@Data
public class MessageSendDTO {

    @NotBlank(message = "接收方类型不能为空")
    private String toType;
    @NotNull(message = "接收方 ID 不能为空")
    private Long toId;
    @NotBlank(message = "消息内容不能为空")
    @Size(max = 2000, message = "消息内容长度不能超过 2000 个字符")
    private String content;
    @Size(max = 20, message = "消息类型长度不合法")
    private String msgType = "text";
    @Size(max = 64, message = "客户端消息 ID 过长")
    private String clientMsgId;
}
