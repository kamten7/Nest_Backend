package com.nest.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 聊天消息展示视图。
 */
@Data
public class MessageVO {

    /** 消息 ID */
    private Long id;
    /** 会话 ID */
    private Long conversationId;
    /** 发送者类型：tenant / landlord */
    private String senderType;
    /** 发送者 ID */
    private Long senderId;
    /** 发送者昵称 */
    private String senderName;
    /** 消息内容 */
    private String content;
    /** 消息类型：text / image */
    private String msgType;
    /** 是否自己发的（当前用户视角） */
    private Boolean mine;
    /** 是否已读 */
    private Integer isRead;
    /** 创建时间 */
    private LocalDateTime createTime;
}
