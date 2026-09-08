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

/**
 * 聊天 WebSocket 端点。
 *
 * 连接路径：{@code ws://localhost:8080/ws/chat/{userType}/{userId}?token=xxx}
 * 握手时通过 {@link ChatWebSocketConfigurator} 校验 JWT。
 *
 * 在线用户池设计
 * 每个用户可能开多个连接（多标签页/断线重连），key 对应一组 Session，
 * 关闭时只移除自己，不影响其他连接。
 *
 * 消息处理（onMessage）
 * 客户端发 {@code {type, toType, toId, content, msgType, clientMsgId}}：
 *
 * type=chat → {@link ChatService#send}（找会话/落库/转发）
 * type=read_receipt → {@link ChatService#markReadByReceipt}（置已读并把回执回推给发送方）
 * type=typing/heartbeat → 转发
 *
 * 注意：JSR-356 端点非 Spring Bean，Service 通过 {@link ApplicationContextHolder} 获取。
 */
@Slf4j
@Component
@ServerEndpoint(value = "/ws/chat/{userType}/{userId}", configurator = ChatWebSocketConfigurator.class)
public class ChatWebSocketServer {

    /** 在线用户池：key = "tenant:123" 或 "landlord:456"，value = 该用户的所有连接 */
    private static final Map<String, CopyOnWriteArrayList<Session>> ONLINE_USERS = new ConcurrentHashMap<>();

    /**
     * 连接建立回调：校验握手鉴权身份，并把连接登记进在线用户池。
     *
     * @param session  当前连接的 Session（从中读取握手阶段存入的可信身份）
     * @param userType 路径参数 userType（仅作日志展示，不作可信身份）
     * @param userId   路径参数 userId（仅作日志展示，不作可信身份）
     */
    @OnOpen
    public void onOpen(
            Session session,// 当前连接的 Session（从中读取握手阶段存入的可信身份）
            @PathParam("userType") String userType,// 路径参数 userType（仅作日志展示，不作可信身份）
            @PathParam("userId") String userId// 路径参数 userId（仅作日志展示，不作可信身份）
    ) {
        // 从握手鉴权存储的身份取（configurator 里存的），防止路径参数被伪造
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
        // 把连接者身份存到 session 属性，onMessage 用
        session.getUserProperties().put(ChatWebSocketConfigurator.ATTR_USER_TYPE, authType);
        session.getUserProperties().put(ChatWebSocketConfigurator.ATTR_USER_ID, authId);

        ONLINE_USERS.computeIfAbsent(key, k -> new CopyOnWriteArrayList<>()).add(session);
        log.info("WebSocket 连接建立: {}, 当前在线连接数={}", key, ONLINE_USERS.get(key).size());
    }

    /**
     * 连接关闭回调：从在线用户池移除当前连接。
     *
     * @param session  已关闭的 Session
     * @param userType 路径参数 userType（Session 属性缺失时兜底用）
     * @param userId   路径参数 userId（Session 属性缺失时兜底用）
     */
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

    @OnError
    public void onError(Session session, Throwable error) {
        log.error("WebSocket 异常", error);
    }

    /**
     * 收到客户端消息 → 解析 JSON → 按消息类型分发处理。
     *
     * @param message 客户端发送的原始消息（JSON 字符串）
     * @param session 当前连接（从中取出握手鉴权后的发送者身份）
     */
    @OnMessage
    public void onMessage(String message, Session session) {
        try {
            // 1. 连接者身份（握手时存的）
            String fromType = (String) session.getUserProperties().get(ChatWebSocketConfigurator.ATTR_USER_TYPE);
            Long fromId = (Long) session.getUserProperties().get(ChatWebSocketConfigurator.ATTR_USER_ID);
            if (fromType == null || fromId == null) {
                return;
            }

            // 2. 解析 JSON
            JSONObject json = JSONObject.parseObject(message);
            String type = json.getString("type");

            if ("chat".equals(type)) {
                handleChat(json, fromType, fromId);
            } else if ("read_receipt".equals(type)) {
                // 已读回执：置已读并回推给消息发送方（不再简单转发）
                Long conversationId = json.getLong("conversationId");
                Long lastReadMsgId = json.getLong("lastReadMsgId");
                if (conversationId == null || lastReadMsgId == null) {
                    log.warn("已读回执缺少 conversationId/lastReadMsgId: {}", message);
                    return;
                }
                ChatService chatService = ApplicationContextHolder.getBean(ChatService.class);
                chatService.markReadByReceipt(conversationId, fromType, fromId, lastReadMsgId);
            } else if ("typing".equals(type)) {
                //正在输入ing状态
                // 转发输入状态
                String toType = json.getString("toType");
                Long toId = json.getLong("toId");
                //只有目标用户存在才发送输入状态，否则不发送，避免空指针异常
                if (toType != null && toId != null) {
                    sendToUser(toType, toId, message);
                }
            } else if ("heartbeat".equals(type)) {
                // 心跳，无需处理（连接保持即证明在线）
            } else {
                log.debug("未知 WebSocket 消息类型: {}", type);
            }
        } catch (Exception e) {
            log.error("WebSocket 消息处理失败: {}", message, e);
        }
    }

    /** 处理聊天消息：调 ChatService 落库 + 转发
     * 处理聊天消息：调 ChatService 落库 + 转发（静态方法，业务层也可调用）。
     *
     * @param json     客户端消息 JSON（含 toType/toId/content/msgType/clientMsgId）
     * @param fromType 发送者类型
     * @param fromId   发送者 ID
     */
    private void handleChat(JSONObject json, String fromType, Long fromId) {
        ChatService chatService = ApplicationContextHolder.getBean(ChatService.class);

        //获取聊天消息参数
        String toType = json.getString("toType");
        Long toId = json.getLong("toId");
        String content = json.getString("content");
        String msgType = json.getString("msgType");
        String clientMsgId = json.getString("clientMsgId");

        // 如果缺少 toType/toId，直接返回
        if (toType == null || toId == null) {
            log.warn("聊天消息缺少 toType/toId");
            return;
        }

        // 落库 + 转发（ChatService.send 内部会 sendToUser）
        chatService.send(fromType, fromId, toType, toId, content, msgType, clientMsgId);
    }

    /**
     * 向指定用户的所有在线连接发送消息。
     */
    /**
     * 向指定用户的所有在线连接发送消息（静态方法，供业务层直接调用）。
     *
     * 用户可能有多个连接（多标签页/断线重连），全部推送；单连接失败不影响其他连接。
     * 若用户不在线则静默丢弃（消息已由业务层落库，用户上线后可拉取补齐）。
     *
     * @param userType 接收方类型：tenant / landlord+
     * @param userId   接收方 ID
     * @param message  消息内容（JSON 字符串）
     */
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

    /** 判断用户是否有在线连接 */
    /**
     * 判断用户是否在线（是否存在至少一个打开的连接）。
     *
     * @param userType 用户类型：tenant / landlord
     * @param userId   用户 ID
     * @return true=在线
     */
    public static boolean isOnline(String userType, Long userId) {
        var sessions = ONLINE_USERS.get(userType + ":" + userId);
        return sessions != null && !sessions.isEmpty()
                && sessions.stream().anyMatch(Session::isOpen);
    }
}
