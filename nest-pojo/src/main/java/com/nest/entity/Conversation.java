package com.nest.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 会话实体（参考抖音私信设计）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Conversation {

    private Long id;
    /** 参与者1类型：tenant / landlord */
    private String user1Type;
    /** 参与者1 ID */
    private Long user1Id;
    /** 参与者2类型：tenant / landlord */
    private String user2Type;
    /** 参与者2 ID */
    private Long user2Id;
    /** 最后一条消息摘要 */
    private String lastMessage;
    /** 最后消息时间 */
    private LocalDateTime lastMessageTime;
    /** 创建时间 */
    private LocalDateTime createTime;
}
