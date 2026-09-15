package com.nest.chat.core;

/** 入站消息回调。由业务侧（nest-server）实现，聊天模块只负责收发与路由。 */
public interface MessageListener {

    /** 收到聊天消息：落库并返回消息 ID。 */
    Long onChat(String fromType, Long fromId, String toType, Long toId,
                String content, String msgType, String clientMsgId);

    /** 收到已读回执。 */
    void onReadReceipt(Long conversationId, String readerType, Long readerId, Long lastReadMsgId);
}
