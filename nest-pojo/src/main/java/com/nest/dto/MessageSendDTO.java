package com.nest.dto;

import lombok.Data;

/**
 * 聊天消息发送请求体（WebSocket 或 REST 用）。
 */
@Data
public class MessageSendDTO {

    /** 接收方类型：tenant / landlord */
    private String toType;
    /** 接收方 ID */
    private Long toId;
    /** 消息内容 */
    private String content;
    /** 消息类型：text / image */
    private String msgType = "text";
    /** 客户端消息 ID（去重 + ACK） */
    private String clientMsgId;
}
