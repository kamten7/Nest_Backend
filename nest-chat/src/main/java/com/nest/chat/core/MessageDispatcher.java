package com.nest.chat.core;

import com.alibaba.fastjson2.JSONObject;
import com.nest.chat.push.PushService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/** 入站消息统一路由：chat / read_receipt / typing / heartbeat。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MessageDispatcher {

    public static final String TYPE_CHAT = "chat";
    public static final String TYPE_READ_RECEIPT = "read_receipt";
    public static final String TYPE_TYPING = "typing";
    public static final String TYPE_HEARTBEAT = "heartbeat";

    private final PushService pushService;
    private final ObjectProvider<MessageListener> listenerProvider;

    public void dispatch(String payload, String fromType, Long fromId) {
        try {
            JSONObject json = JSONObject.parseObject(payload);
            String type = json.getString("type");
            if (type == null) {
                return;
            }
            switch (type) {
                case TYPE_CHAT -> handleChat(json, fromType, fromId);
                case TYPE_READ_RECEIPT -> handleReadReceipt(json, fromType, fromId);
                case TYPE_TYPING -> pushService.pushRaw(json.getString("toType"), json.getLong("toId"), payload);
                case TYPE_HEARTBEAT -> { }
                default -> log.debug("未知消息类型: {}", type);
            }
        } catch (Exception e) {
            log.error("消息处理失败: {}", payload, e);
        }
    }

    private void handleChat(JSONObject json, String fromType, Long fromId) {
        String toType = json.getString("toType");
        Long toId = json.getLong("toId");
        if (toType == null || toId == null) {
            log.warn("聊天消息缺少 toType/toId");
            return;
        }
        String content = json.getString("content");
        if (content == null || content.isBlank()) {
            log.warn("聊天消息内容为空，已丢弃: from={}:{}", fromType, fromId);
            return;
        }
        MessageListener listener = listenerProvider.getIfAvailable();
        if (listener == null) {
            log.warn("未注册 MessageListener，聊天消息被丢弃");
            return;
        }
        listener.onChat(fromType, fromId, toType, toId, content,
                json.getString("msgType"), json.getString("clientMsgId"));
    }

    private void handleReadReceipt(JSONObject json, String fromType, Long fromId) {
        Long conversationId = json.getLong("conversationId");
        Long lastReadMsgId = json.getLong("lastReadMsgId");
        if (conversationId == null || lastReadMsgId == null) {
            log.warn("已读回执缺少 conversationId/lastReadMsgId");
            return;
        }
        MessageListener listener = listenerProvider.getIfAvailable();
        if (listener == null) {
            return;
        }
        listener.onReadReceipt(conversationId, fromType, fromId, lastReadMsgId);
    }
}
