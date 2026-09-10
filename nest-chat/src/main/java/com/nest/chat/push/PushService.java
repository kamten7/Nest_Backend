package com.nest.chat.push;

import com.nest.chat.transport.MessageTransport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 消息推送服务。业务侧只需注入本类调用，无需关心底层传输实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PushService {

    public static final String TYPE_CHAT = "chat";
    public static final String TYPE_APPOINTMENT = "appointment";
    public static final String TYPE_COMMENT = "comment";
    public static final String TYPE_LIKE = "like";
    public static final String TYPE_READ_RECEIPT = "read_receipt";

    private final MessageTransport transport;

    /** 用户是否在线。 */
    public boolean isOnline(String userType, Long userId) {
        return userType != null && userId != null && transport.isOnline(userType, userId);
    }

    /** 通用推送。 */
    public void send(String userType, Long userId, PushMessage message) {
        pushRaw(userType, userId, message.toJson());
    }

    /** 原始报文推送（typing 等客户端透传场景）。 */
    public void pushRaw(String userType, Long userId, String payload) {
        if (userType == null || userId == null || payload == null) {
            return;
        }
        transport.send(userType, userId, payload);
    }

    /** 通知类消息（预约、评论、点赞、公告等）。 */
    public void pushNotice(String toType, Long toId, String type, String title, String content) {
        send(toType, toId, PushMessage.of(type).put("title", title).put("content", content));
    }

    /** 聊天消息。 */
    public void pushChat(String toType, Long toId, Long conversationId, Long msgId,
                         String fromType, Long fromId, String senderName, String content, String msgType) {
        send(toType, toId, PushMessage.of(TYPE_CHAT)
                .put("msgId", msgId)
                .put("conversationId", conversationId)
                .put("fromType", fromType)
                .put("fromId", fromId)
                .put("senderName", senderName == null ? "" : senderName)
                .put("content", content)
                .put("msgType", msgType)
                .put("timestamp", System.currentTimeMillis()));
    }

    /** 已读回执。 */
    public void pushReadReceipt(String toType, Long toId, Long conversationId,
                                String readerType, Long readerId, Long lastReadMsgId) {
        send(toType, toId, PushMessage.of(TYPE_READ_RECEIPT)
                .put("conversationId", conversationId)
                .put("readerType", readerType)
                .put("readerId", readerId)
                .put("lastReadMsgId", lastReadMsgId));
    }

    /** 评论通知。 */
    public void pushComment(String toType, Long toId, String title, String content,
                            Long houseId, Long reviewId) {
        send(toType, toId, PushMessage.of(TYPE_COMMENT)
                .put("title", title)
                .put("content", content)
                .put("houseId", houseId)
                .put("reviewId", reviewId));
    }

    /** 点赞通知。 */
    public void pushLike(String toType, Long toId, String title, String content, Long commentId) {
        send(toType, toId, PushMessage.of(TYPE_LIKE)
                .put("title", title)
                .put("content", content)
                .put("commentId", commentId));
    }
}
