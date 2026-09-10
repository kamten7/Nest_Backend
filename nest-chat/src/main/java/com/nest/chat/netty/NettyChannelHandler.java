package com.nest.chat.netty;

import com.nest.chat.core.MessageDispatcher;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
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
    public void channelActive(ChannelHandlerContext ctx) {
        NettyWebSocketServer.register(ctx.channel());
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        NettyWebSocketServer.unregister(ctx.channel());
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, TextWebSocketFrame frame) {
        String fromType = ctx.channel().attr(NettyWebSocketServer.ATTR_USER_TYPE).get();
        Long fromId = ctx.channel().attr(NettyWebSocketServer.ATTR_USER_ID).get();
        if (fromType == null || fromId == null) {
            return;
        }
        dispatcher.dispatch(frame.text(), fromType, fromId);
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent event && event.state() == IdleState.ALL_IDLE) {
            log.debug("心跳超时，关闭连接: {}", ctx.channel().remoteAddress());
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
