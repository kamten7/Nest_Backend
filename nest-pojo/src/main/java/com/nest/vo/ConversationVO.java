package com.nest.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 会话列表展示视图 —— 会话 + 对方信息 + 未读数。
 */
@Data
public class ConversationVO {

    /** 会话 ID */
    private Long id;
    /** 对方类型：tenant / landlord */
    private String otherType;
    /** 对方 ID */
    private Long otherId;
    /** 对方昵称 */
    private String otherName;
    /** 对方头像 */
    private String otherAvatar;
    /** 最后一条消息摘要 */
    private String lastMessage;
    /** 最后消息时间 */
    private LocalDateTime lastMessageTime;
    /** 未读数（当前用户视角） */
    private Long unreadCount;
}
