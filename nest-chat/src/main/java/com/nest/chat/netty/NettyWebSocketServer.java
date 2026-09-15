package com.nest.chat.netty;

import com.nest.chat.config.ChatProperties;
import com.nest.chat.core.MessageDispatcher;
import com.nest.constant.JwtConstant;
import com.nest.utils.JwtUtil;
import io.jsonwebtoken.Claims;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolConfig;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.AttributeKey;
import io.netty.util.concurrent.DefaultEventExecutorGroup;
import io.netty.util.concurrent.EventExecutorGroup;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/** Netty WebSocket 服务器：启动、握手鉴权、Channel 池管理。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NettyWebSocketServer {

    public static final AttributeKey<String> ATTR_USER_TYPE = AttributeKey.valueOf("userType");
    public static final AttributeKey<Long> ATTR_USER_ID = AttributeKey.valueOf("userId");

    /** 在线 Channel 池：key = userType:userId。 */
    private static final Map<String, CopyOnWriteArrayList<Channel>> ONLINE_CHANNELS = new ConcurrentHashMap<>();

    private final ChatProperties properties;
    private final MessageDispatcher dispatcher;

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel serverChannel;

    /** 业务线程组：把 DB / MinIO 等阻塞操作挪出 IO 线程（EventLoop）。 */
    private final EventExecutorGroup businessGroup = new DefaultEventExecutorGroup(
            Math.max(4, Runtime.getRuntime().availableProcessors() * 2));

    @PostConstruct
    public void start() {
        if (!"netty".equalsIgnoreCase(properties.getImplementation())) {
            log.info("传输层实现为 {}，Netty 未启用", properties.getImplementation());
            return;
        }
        doStart();   // 同步启动：端口被占用等问题在启动期直接暴露，而不是在子线程里静默写日志
    }

    @PreDestroy
    public void shutdown() {
        if (serverChannel != null) {
            serverChannel.close();
        }
        businessGroup.shutdownGracefully();
        if (bossGroup != null) {
            bossGroup.shutdownGracefully();
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
        }
        log.info("Netty WebSocket 服务器已关闭");
    }

    private void doStart() {
        ChatProperties.Netty netty = properties.getNetty();
        bossGroup = new NioEventLoopGroup(netty.getBossThreads());
        workerGroup = new NioEventLoopGroup(netty.getWorkerThreads());

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
                            p.addLast(new IdleStateHandler(netty.getIdleTimeoutSeconds(), 0, 0));
                            p.addLast(new NettyHandshakeAuthHandler(netty.getPath()));
                            p.addLast(new WebSocketServerProtocolHandler(
                                    WebSocketServerProtocolConfig.newBuilder()
                                            .websocketPath(netty.getPath())
                                            .checkStartsWith(true)
                                            .handshakeTimeoutMillis(10_000L)
                                            .maxFramePayloadLength(64 * 1024)
                                            .build()));
                            p.addLast(businessGroup, new NettyChannelHandler(dispatcher));
                        }
                    });

            serverChannel = bootstrap.bind(netty.getPort()).sync().channel();
            log.info("Netty WebSocket 服务器启动: port={}, path={}", netty.getPort(), netty.getPath());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Netty WebSocket 启动被中断", e);
        } catch (Exception e) {
            throw new IllegalStateException("Netty WebSocket 启动失败: port=" + netty.getPort(), e);
        }
    }

    /** 握手鉴权：校验 JWT 与路径 userId 是否一致，通过后写入 Channel 属性。 */
    public static boolean authenticate(Channel channel, String userType, String userId, String token) {
        String secretKey;
        if (JwtConstant.TYPE_TENANT.equals(userType)) {
            secretKey = JwtConstant.userSecretKey();
        } else if (JwtConstant.TYPE_LANDLORD.equals(userType)) {
            secretKey = JwtConstant.adminSecretKey();
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
            channel.attr(ATTR_USER_ID).set(tokenUserId);
            return true;
        } catch (Exception e) {
            log.warn("Netty WebSocket 鉴权失败: {}", e.getMessage());
            return false;
        }
    }

    /** 登记在线 Channel。 */
    public static void register(Channel channel) {
        String key = keyOf(channel);
        if (key == null) {
            return;
        }
        ONLINE_CHANNELS.computeIfAbsent(key, k -> new CopyOnWriteArrayList<>()).add(channel);
        log.info("Netty 连接建立: {}, 在线连接数={}", key, ONLINE_CHANNELS.get(key).size());
    }

    /** 移除离线 Channel。 */
    public static void unregister(Channel channel) {
        String key = keyOf(channel);
        if (key == null) {
            return;
        }
        CopyOnWriteArrayList<Channel> channels = ONLINE_CHANNELS.get(key);
        if (channels != null) {
            channels.remove(channel);
            if (channels.isEmpty()) {
                ONLINE_CHANNELS.remove(key);
            }
        }
        log.info("Netty 连接断开: {}, 剩余连接数={}", key, channels == null ? 0 : channels.size());
    }

    /** 推送给指定用户的所有在线连接，不在线则丢弃（消息已落库）。 */
    public static void sendToUser(String userType, Long userId, String message) {
        String key = userType + ":" + userId;
        CopyOnWriteArrayList<Channel> channels = ONLINE_CHANNELS.get(key);
        if (channels == null || channels.isEmpty()) {
            log.debug("Netty 发送失败：用户不在线 key={}", key);
            return;
        }
        int sent = 0;
        for (Channel channel : channels) {
            if (channel.isActive()) {
                channel.writeAndFlush(new TextWebSocketFrame(message));
                sent++;
            }
        }
        log.debug("Netty 推送完成: key={}, 送达连接数={}", key, sent);
    }

    public static boolean isOnline(String userType, Long userId) {
        CopyOnWriteArrayList<Channel> channels = ONLINE_CHANNELS.get(userType + ":" + userId);
        return channels != null && channels.stream().anyMatch(Channel::isActive);
    }

    private static String keyOf(Channel channel) {
        String userType = channel.attr(ATTR_USER_TYPE).get();
        Long userId = channel.attr(ATTR_USER_ID).get();
        return userType == null || userId == null ? null : userType + ":" + userId;
    }
}
