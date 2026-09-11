package com.nest.service;

import com.nest.config.NettyWebSocketConfig;
import com.nest.websocket.ChatWebSocketServer;
import com.nest.websocket.netty.NettyWebSocketServer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** 通知服务 —— 封装 WebSocket 推送，支持 JSR-356/Netty 双实现切换。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NettyWebSocketConfig nettyConfig;

    @Value("${nest.websocket.implementation:jsr356}")
    private String implementation;

    /** 向指定用户推送消息。 */
    public void sendToUser(String userType, Long userId, String message) {
        if ("netty".equalsIgnoreCase(implementation)) {
            NettyWebSocketServer.sendToUser(userType, userId, message);
        } else {
            ChatWebSocketServer.sendToUser(userType, userId, message);
        }
    }

    /** 判断用户是否在线。 */
    public boolean isOnline(String userType, Long userId) {
        if ("netty".equalsIgnoreCase(implementation)) {
            return NettyWebSocketServer.isOnline(userType, userId);
        } else {
            return ChatWebSocketServer.isOnline(userType, userId);
        }
    }
}
