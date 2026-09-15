package com.nest.vo;

import lombok.Data;

import java.time.LocalDateTime;

/** 会话列表展示视图 —— 会话 + 对方信息 + 未读数 */
@Data
public class ConversationVO {

    private Long id;
    private String otherType;
    private Long otherId;
    private String otherName;
    private String otherAvatar;
    private String lastMessage;
    private LocalDateTime lastMessageTime;
    private Long unreadCount;
}
