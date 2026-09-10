package com.nest.chat.transport;

/** 消息传输层抽象：屏蔽 Netty / JSR-356 差异。 */
public interface MessageTransport {

    String name();

    void send(String userType, Long userId, String payload);

    boolean isOnline(String userType, Long userId);
}
