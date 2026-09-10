package com.nest.chat.transport;

import com.nest.chat.jsr356.ChatWebSocketServer;

/** JSR-356（Servlet 容器内嵌 WebSocket）传输层实现。 */
public class Jsr356Transport implements MessageTransport {

    @Override
    public String name() {
        return "jsr356";
    }

    @Override
    public void send(String userType, Long userId, String payload) {
        ChatWebSocketServer.sendToUser(userType, userId, payload);
    }

    @Override
    public boolean isOnline(String userType, Long userId) {
        return ChatWebSocketServer.isOnline(userType, userId);
    }
}
