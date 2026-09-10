package com.nest.chat.config;

import com.nest.chat.transport.Jsr356Transport;
import com.nest.chat.transport.MessageTransport;
import com.nest.chat.transport.NettyTransport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.server.standard.ServerEndpointExporter;

/** 聊天模块装配：按配置选择传输层实现，并注册 JSR-356 端点导出器。 */
@Slf4j
@Configuration
public class ChatConfiguration {

    @Bean
    public MessageTransport messageTransport(ChatProperties properties) {
        boolean netty = "netty".equalsIgnoreCase(properties.getImplementation());
        log.info("WebSocket 传输层实现: {}", netty ? "netty" : "jsr356");
        return netty ? new NettyTransport() : new Jsr356Transport();
    }

    @Bean
    public ServerEndpointExporter serverEndpointExporter() {
        return new ServerEndpointExporter();
    }
}
