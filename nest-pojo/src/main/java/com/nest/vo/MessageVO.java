package com.nest.vo;

import lombok.Data;

import java.time.LocalDateTime;

/** 聊天消息展示视图 */
@Data
public class MessageVO {

    private Long id;
    private Long conversationId;
    private String senderType;
    private Long senderId;
    private String senderName;
    private String content;
    private String msgType;
    private Boolean mine;
    private Integer isRead;
    private LocalDateTime createTime;
}
