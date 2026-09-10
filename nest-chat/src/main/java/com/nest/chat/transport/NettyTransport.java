package com.nest.chat.transport;

import com.nest.chat.netty.NettyWebSocketServer;

/** Netty 传输层实现。 */
public class NettyTransport implements MessageTransport {

    @Override
    public String name() {
        return "netty";
    }

    @Override
    public void send(String userType, Long userId, String payload) {
        NettyWebSocketServer.sendToUser(userType, userId, payload);
    }

    @Override
    public boolean isOnline(String userType, Long userId) {
        return NettyWebSocketServer.isOnline(userType, userId);
    }
}
