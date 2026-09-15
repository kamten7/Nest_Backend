package com.nest.chat.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** 聊天模块配置，对应 application.yml 的 nest.websocket.*。 */
@Data
@Component
@ConfigurationProperties(prefix = "nest.websocket")
public class ChatProperties {

    private String implementation = "jsr356";

    private Netty netty = new Netty();

    /** Netty 传输层参数。 */
    @Data
    public static class Netty {
        private int port = 8081;
        private String path = "/ws/chat";
        private int bossThreads = 1;
        private int workerThreads = 0;
        private int idleTimeoutSeconds = 60;
    }
}
