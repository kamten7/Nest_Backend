package com.nest.websocket.netty;

import com.nest.config.NettyWebSocketConfig;
import com.nest.constant.JwtConstant;
import com.nest.utils.JwtUtil;
import io.jsonwebtoken.Claims;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.AttributeKey;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ConcurrentHashMap;

/** Netty WebSocket 服务器 —— 启动、鉴权、Channel 池管理。 */
@Slf4j
@Component
public class NettyWebSocketServer {

    /** Channel 属性：用户类型。 */
    public static final AttributeKey<String> ATTR_USER_TYPE = AttributeKey.valueOf("userType");
    /** Channel 属性：用户ID。 */
    public static final AttributeKey<Long> ATTR_USER_ID = AttributeKey.valueOf("userId");

    /** 在线用户 Channel 池：key=userType:userId。 */
    private static final Map<String, CopyOnWriteArrayList<Channel>> ONLINE_USERS = new ConcurrentHashMap<>();

    @Autowired
    private NettyWebSocketConfig config;

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel serverChannel;

    /** 启动 Netty 服务器。 */
    @PostConstruct
    public void start() {
        new Thread(this::doStart, "netty-ws-init").start();
    }

    /** 关闭 Netty 服务器。 */
    @PreDestroy
    public void shutdown() {
        if (serverChannel != null) serverChannel.close();
        if (bossGroup != null) bossGroup.shutdownGracefully();
        if (workerGroup != null) workerGroup.shutdownGracefully();
        log.info("Netty WebSocket 服务器已关闭");
    }

    /** 实际启动逻辑。 */
    private void doStart() {
        bossGroup = new NioEventLoopGroup(config.getBossThreads());
        workerGroup = new NioEventLoopGroup(config.getWorkerThreads());

        try {
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .handler(new LoggingHandler(LogLevel.INFO))
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ChannelPipeline p = ch.pipeline();
                            p.addLast(new HttpServerCodec());
                            p.addLast(new HttpObjectAggregator(65536));
                            p.addLast(new IdleStateHandler(0, 0, config.getIdleTimeoutSeconds()));
                            p.addLast(new WebSocketServerProtocolHandler(config.getPath() + "/{userType}/{userId}"));
                            p.addLast(new NettyChatHandler());
                        }
                    });

            ChannelFuture future = bootstrap.bind(config.getPort()).sync();
            serverChannel = future.channel();
            log.info("Netty WebSocket 服务器启动: port={}, path={}", config.getPort(), config.getPath());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Netty WebSocket 启动失败", e);
        }
    }

    /** 握手鉴权：解析 JWT 并写入 Channel 属性。 */
    public static boolean authenticate(Channel channel, String userType, String userId, String token) {
        String secretKey;
        if ("tenant".equals(userType)) {
            secretKey = JwtConstant.USER_SECRET_KEY;
        } else if ("landlord".equals(userType)) {
            secretKey = JwtConstant.ADMIN_SECRET_KEY;
        } else {
            return false;
        }

        try {
            Claims claims = JwtUtil.parseToken(secretKey, token);
            Long tokenUserId = claims.get("userId", Long.class);
            if (tokenUserId == null || !String.valueOf(tokenUserId).equals(userId)) {
                return false;
            }
            channel.attr(ATTR_USER_TYPE).set(userType);
            channel.attr(ATTR_USER_ID).set(Long.valueOf(userId));
            return true;
        } catch (Exception e) {
            log.warn("Netty WebSocket 鉴权失败: {}", e.getMessage());
            return false;
        }
    }

    /** 注册在线 Channel。 */
    public static void register(Channel channel) {
        String userType = channel.attr(ATTR_USER_TYPE).get();
        Long userId = channel.attr(ATTR_USER_ID).get();
        if (userType == null || userId == null) return;

        String key = userType + ":" + userId;
        ONLINE_USERS.computeIfAbsent(key, k -> new CopyOnWriteArrayList<>()).add(channel);
        log.info("Netty 连接建立: {}, 在线连接数={}", key, ONLINE_USERS.get(key).size());
    }

    /** 移除离线 Channel。 */
    public static void unregister(Channel channel) {
        String userType = channel.attr(ATTR_USER_TYPE).get();
        Long userId = channel.attr(ATTR_USER_ID).get();
        if (userType == null || userId == null) return;

        String key = userType + ":" + userId;
        var channels = ONLINE_USERS.get(key);
        if (channels != null) {
            channels.remove(channel);
            if (channels.isEmpty()) ONLINE_USERS.remove(key);
        }
        log.info("Netty 连接断开: {}, 剩余连接数={}", key, channels == null ? 0 : channels.size());
    }

    /** 向指定用户发送消息。不在线则静默丢弃（消息已落库）。 */
    public static void sendToUser(String userType, Long userId, String message) {
        String key = userType + ":" + userId;
        var channels = ONLINE_USERS.get(key);
        if (channels == null || channels.isEmpty()) {
            log.debug("Netty 发送失败：用户不在线 key={}", key);
            return;
        }
        int sent = 0;
        for (Channel ch : channels) {
            if (ch.isActive()) {
                try {
                    ch.writeAndFlush(new TextWebSocketFrame(message));
                    sent++;
                } catch (Exception e) {
                    log.error("Netty 发送失败: key={}", key, e);
                }
            }
        }
        log.debug("Netty 推送完成: key={}, 送达连接数={}", key, sent);
    }

    /** 判断用户是否在线。 */
    public static boolean isOnline(String userType, Long userId) {
        var channels = ONLINE_USERS.get(userType + ":" + userId);
        return channels != null && !channels.isEmpty()
                && channels.stream().anyMatch(Channel::isActive);
    }
}
