package com.nest.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 会话实体（参考抖音私信设计） */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Conversation {

    private Long id;
    private String user1Type;
    private Long user1Id;
    private String user2Type;
    private Long user2Id;
    private String lastMessage;
    private LocalDateTime lastMessageTime;
    private LocalDateTime createTime;
}
