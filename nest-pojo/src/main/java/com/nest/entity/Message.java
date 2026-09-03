package com.nest.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 聊天消息实体。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Message {

    private Long id;
    /** 会话ID */
    private Long conversationId;
    /** 发送者类型：tenant / landlord */
    private String senderType;
    /** 发送者ID */
    private Long senderId;
    /** 消息内容 */
    private String content;
    /** 消息类型：text / image */
    private String msgType;
    /** 是否已读 */
    private Integer isRead;
    /** 创建时间 */
    private LocalDateTime createTime;
}
