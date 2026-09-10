package com.nest.websocket;

import com.alibaba.fastjson2.JSONObject;
import com.nest.config.ApplicationContextHolder;
import com.nest.service.ChatService;
import jakarta.websocket.*;
import jakarta.websocket.server.PathParam;
import jakarta.websocket.server.ServerEndpoint;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/** 聊天 WebSocket 端点。连接路径: /ws/chat/{userType}/{userId}?token=xxx */
@Slf4j
@Component
@ServerEndpoint(value = "/ws/chat/{userType}/{userId}", configurator = ChatWebSocketConfigurator.class)
public class ChatWebSocketServer {

    /** 在线用户池：key = "tenant:123" / "landlord:456"，value = 该用户的所有连接。 */
    private static final Map<String, CopyOnWriteArrayList<Session>> ONLINE_USERS = new ConcurrentHashMap<>();

    /** 连接建立：校验握手鉴权身份，登记进在线用户池。 */
    @OnOpen
    public void onOpen(Session session,
                       @PathParam("userType") String userType,
                       @PathParam("userId") String userId) {
        Object authType = session.getUserProperties().get(ChatWebSocketConfigurator.ATTR_USER_TYPE);
        Object authId = session.getUserProperties().get(ChatWebSocketConfigurator.ATTR_USER_ID);
        if (authType == null || authId == null) {
            log.warn("WebSocket 未通过鉴权，拒绝: {}/{}", userType, userId);
            try {
                session.close(new CloseReason(CloseReason.CloseCodes.VIOLATED_POLICY, "未认证"));
            } catch (Exception ignored) {
            }
            return;
        }

        String key = authType + ":" + authId;
        session.getUserProperties().put(ChatWebSocketConfigurator.ATTR_USER_TYPE, authType);
        session.getUserProperties().put(ChatWebSocketConfigurator.ATTR_USER_ID, authId);

        ONLINE_USERS.computeIfAbsent(key, k -> new CopyOnWriteArrayList<>()).add(session);
        log.info("WebSocket 连接建立: {}, 当前在线连接数={}", key, ONLINE_USERS.get(key).size());
    }

    /** 连接关闭：从在线用户池移除。 */
    @OnClose
    public void onClose(Session session,
                        @PathParam("userType") String userType,
                        @PathParam("userId") String userId) {
        Object authId = session.getUserProperties().get(ChatWebSocketConfigurator.ATTR_USER_ID);
        Object authType = session.getUserProperties().get(ChatWebSocketConfigurator.ATTR_USER_TYPE);
        String key = (authType == null ? userType : authType) + ":"
                + (authId == null ? userId : authId);

        var sessions = ONLINE_USERS.get(key);
        if (sessions != null) {
            sessions.remove(session);
            if (sessions.isEmpty()) {
                ONLINE_USERS.remove(key);
            }
        }
        log.info("WebSocket 连接断开: {}, 剩余连接数={}", key, sessions == null ? 0 : sessions.size());
    }

    /** 连接异常。 */
    @OnError
    public void onError(Session session, Throwable error) {
        log.error("WebSocket 异常", error);
    }

    /** 收到消息：按类型分发（chat / read_receipt / typing / heartbeat）。 */
    @OnMessage
    public void onMessage(String message, Session session) {
        try {
            String fromType = (String) session.getUserProperties().get(ChatWebSocketConfigurator.ATTR_USER_TYPE);
            Long fromId = (Long) session.getUserProperties().get(ChatWebSocketConfigurator.ATTR_USER_ID);
            if (fromType == null || fromId == null) {
                return;
            }

            JSONObject json = JSONObject.parseObject(message);
            String type = json.getString("type");

            if ("chat".equals(type)) {
                handleChat(json, fromType, fromId);
            } else if ("read_receipt".equals(type)) {
                Long conversationId = json.getLong("conversationId");
                Long lastReadMsgId = json.getLong("lastReadMsgId");
                if (conversationId == null || lastReadMsgId == null) {
                    log.warn("已读回执缺少 conversationId/lastReadMsgId: {}", message);
                    return;
                }
                ChatService chatService = ApplicationContextHolder.getBean(ChatService.class);
                chatService.markReadByReceipt(conversationId, fromType, fromId, lastReadMsgId);
            } else if ("typing".equals(type)) {
                String toType = json.getString("toType");
                Long toId = json.getLong("toId");
                if (toType != null && toId != null) {
                    sendToUser(toType, toId, message);
                }
            } else if ("heartbeat".equals(type)) {
                // 心跳，无需处理
            } else {
                log.debug("未知 WebSocket 消息类型: {}", type);
            }
        } catch (Exception e) {
            log.error("WebSocket 消息处理失败: {}", message, e);
        }
    }

    /** 处理聊天消息：调 ChatService 落库 + 转发。 */
    private void handleChat(JSONObject json, String fromType, Long fromId) {
        ChatService chatService = ApplicationContextHolder.getBean(ChatService.class);

        String toType = json.getString("toType");
        Long toId = json.getLong("toId");
        String content = json.getString("content");
        String msgType = json.getString("msgType");
        String clientMsgId = json.getString("clientMsgId");

        if (toType == null || toId == null) {
            log.warn("聊天消息缺少 toType/toId");
            return;
        }

        chatService.send(fromType, fromId, toType, toId, content, msgType, clientMsgId);
    }

    /** 向指定用户的所有在线连接发送消息。不在线则静默丢弃（消息已落库）。 */
    public static void sendToUser(String userType, Long userId, String message) {
        String key = userType + ":" + userId;
        var sessions = ONLINE_USERS.get(key);
        if (sessions == null || sessions.isEmpty()) {
            log.debug("发送失败：用户不在线 key={}", key);
            return;
        }
        int sent = 0;
        for (Session session : sessions) {
            if (session.isOpen()) {
                try {
                    session.getBasicRemote().sendText(message);
                    sent++;
                } catch (Exception e) {
                    log.error("WebSocket 发送失败: key={}", key, e);
                }
            }
        }
        log.debug("WebSocket 推送完成: key={}, 送达连接数={}", key, sent);
    }

    /** 判断用户是否在线。 */
    public static boolean isOnline(String userType, Long userId) {
        var sessions = ONLINE_USERS.get(userType + ":" + userId);
        return sessions != null && !sessions.isEmpty()
                && sessions.stream().anyMatch(Session::isOpen);
    }
}
