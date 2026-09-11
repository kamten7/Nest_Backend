package com.nest.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Netty WebSocket 配置。 */
@Component
@ConfigurationProperties(prefix = "nest.websocket.netty")
@Data
public class NettyWebSocketConfig {

    /** 监听端口（默认8081，避免与主应用8080冲突）。 */
    private int port = 8081;

    /** WebSocket 路径前缀。 */
    private String path = "/ws/chat";

    /** Boss 线程数（接受连接）。 */
    private int bossThreads = 1;

    /** Worker 线程数（0=默认，2*CPU核数）。 */
    private int workerThreads = 0;

    /** 空闲超时秒数（心跳检测）。 */
    private int idleTimeoutSeconds = 60;
}
