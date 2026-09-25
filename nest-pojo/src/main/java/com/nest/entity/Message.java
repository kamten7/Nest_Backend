package com.nest.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 聊天消息实体 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Message {

    private Long id;
    private Long conversationId;
    private String senderType;
    private Long senderId;
    private String content;
    private String msgType;
    private String clientMsgId;
    private Integer isRead;
    private LocalDateTime createTime;
}
