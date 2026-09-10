package com.nest.chat.jsr356;

import com.nest.chat.config.ApplicationContextHolder;
import com.nest.chat.core.MessageDispatcher;
import jakarta.websocket.CloseReason;
import jakarta.websocket.OnClose;
import jakarta.websocket.OnError;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.PathParam;
import jakarta.websocket.server.ServerEndpoint;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/** 聊天 WebSocket 端点，路径 /ws/chat/{userType}/{userId}?token=xxx。 */
@Slf4j
@Component
@ServerEndpoint(value = "/ws/chat/{userType}/{userId}", configurator = ChatWebSocketConfigurator.class)
public class ChatWebSocketServer {

    /** 在线用户池：key = tenant:123 / landlord:456，value = 该用户的所有连接。 */
    private static final Map<String, CopyOnWriteArrayList<Session>> ONLINE_SESSIONS = new ConcurrentHashMap<>();

    @OnOpen
    public void onOpen(Session session,
                       @PathParam("userType") String userType,
                       @PathParam("userId") String userId) {
        Object authType = session.getUserProperties().get(ChatWebSocketConfigurator.ATTR_USER_TYPE);
        Object authId = session.getUserProperties().get(ChatWebSocketConfigurator.ATTR_USER_ID);
        if (authType == null || authId == null) {
            log.warn("WebSocket 未通过鉴权，拒绝: {}/{}", userType, userId);
            closeQuietly(session, "未认证");
            return;
        }

        String key = authType + ":" + authId;
        ONLINE_SESSIONS.computeIfAbsent(key, k -> new CopyOnWriteArrayList<>()).add(session);
        log.info("WebSocket 连接建立: {}, 当前连接数={}", key, ONLINE_SESSIONS.get(key).size());
    }

    @OnClose
    public void onClose(Session session,
                        @PathParam("userType") String userType,
                        @PathParam("userId") String userId) {
        String key = keyOf(session, userType, userId);
        CopyOnWriteArrayList<Session> sessions = ONLINE_SESSIONS.get(key);
        if (sessions != null) {
            sessions.remove(session);
            if (sessions.isEmpty()) {
                ONLINE_SESSIONS.remove(key);
            }
        }
        log.info("WebSocket 连接断开: {}, 剩余连接数={}", key, sessions == null ? 0 : sessions.size());
    }

    @OnError
    public void onError(Session session, Throwable error) {
        log.error("WebSocket 异常", error);
    }

    @OnMessage
    public void onMessage(String message, Session session) {
        String fromType = (String) session.getUserProperties().get(ChatWebSocketConfigurator.ATTR_USER_TYPE);
        Long fromId = (Long) session.getUserProperties().get(ChatWebSocketConfigurator.ATTR_USER_ID);
        if (fromType == null || fromId == null) {
            return;
        }
        ApplicationContextHolder.getBean(MessageDispatcher.class).dispatch(message, fromType, fromId);
    }

    /** 推送给指定用户的所有在线连接，不在线则丢弃（消息已落库）。 */
    public static void sendToUser(String userType, Long userId, String message) {
        String key = userType + ":" + userId;
        CopyOnWriteArrayList<Session> sessions = ONLINE_SESSIONS.get(key);
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

    public static boolean isOnline(String userType, Long userId) {
        CopyOnWriteArrayList<Session> sessions = ONLINE_SESSIONS.get(userType + ":" + userId);
        return sessions != null && sessions.stream().anyMatch(Session::isOpen);
    }

    private static String keyOf(Session session, String userType, String userId) {
        Object authType = session.getUserProperties().get(ChatWebSocketConfigurator.ATTR_USER_TYPE);
        Object authId = session.getUserProperties().get(ChatWebSocketConfigurator.ATTR_USER_ID);
        return (authType == null ? userType : authType) + ":" + (authId == null ? userId : authId);
    }

    private static void closeQuietly(Session session, String reason) {
        try {
            session.close(new CloseReason(CloseReason.CloseCodes.VIOLATED_POLICY, reason));
        } catch (Exception ignored) {
        }
    }
}
