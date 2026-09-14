package com.nest.chat.netty;

import com.nest.chat.core.MessageDispatcher;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Netty 通道处理器：在线登记 + 报文转交 MessageDispatcher。 */
@Slf4j
@RequiredArgsConstructor
public class NettyChannelHandler extends SimpleChannelInboundHandler<TextWebSocketFrame> {

    private final MessageDispatcher dispatcher;


    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        NettyWebSocketServer.unregister(ctx.channel());
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, TextWebSocketFrame frame) {
        String fromType = ctx.channel().attr(NettyWebSocketServer.ATTR_USER_TYPE).get();
        Long fromId = ctx.channel().attr(NettyWebSocketServer.ATTR_USER_ID).get();
        if (fromType == null || fromId == null) {
            log.warn("Netty 收到未鉴权连接的消息，已丢弃: {}", ctx.channel().remoteAddress());
            return;
        }
        dispatcher.dispatch(frame.text(), fromType, fromId);
    }

    /** 处理用户事件：握手完成则登记通道，心跳超时则关闭连接。 */
    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        // 握手完成才是登记的时机：此时鉴权已通过、属性已写入
        if (evt instanceof WebSocketServerProtocolHandler.HandshakeComplete) {
            NettyWebSocketServer.register(ctx.channel());
            return;
        }
        // reader-idle：客户端 idleTimeoutSeconds 内没发任何东西（含心跳）即判定死连接。
        // 不能用 ALL_IDLE —— 服务端每次推送都会产生"写"，会把死连接的计时器一直刷新。
        if (evt instanceof IdleStateEvent event && event.state() == IdleState.READER_IDLE) {
            log.debug("读空闲超时，关闭连接: {}", ctx.channel().remoteAddress());
            ctx.close();
            return;
        }
        super.userEventTriggered(ctx, evt);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("Netty 连接异常: {}", ctx.channel().remoteAddress(), cause);
        ctx.close();
    }
}
