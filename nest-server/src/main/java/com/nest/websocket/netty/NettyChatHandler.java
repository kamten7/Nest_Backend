package com.nest.websocket.netty;

import com.alibaba.fastjson2.JSONObject;
import com.nest.config.ApplicationContextHolder;
import com.nest.service.ChatService;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import lombok.extern.slf4j.Slf4j;

/** Netty WebSocket 消息处理器 —— 路由 chat/read_receipt/typing/heartbeat。 */
@Slf4j
public class NettyChatHandler extends SimpleChannelInboundHandler<TextWebSocketFrame> {

    /** 首次连接：登记在线 Channel。 */
    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        NettyWebSocketServer.register(ctx.channel());
    }

    /** 连接关闭：移除在线 Channel。 */
    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        NettyWebSocketServer.unregister(ctx.channel());
    }

    /** 收到消息：按类型分发。 */
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, TextWebSocketFrame frame) {
        String fromType = ctx.channel().attr(NettyWebSocketServer.ATTR_USER_TYPE).get();
        Long fromId = ctx.channel().attr(NettyWebSocketServer.ATTR_USER_ID).get();
        if (fromType == null || fromId == null) return;

        try {
            JSONObject json = JSONObject.parseObject(frame.text());
            String type = json.getString("type");

            if ("chat".equals(type)) {
                handleChat(json, fromType, fromId);
            } else if ("read_receipt".equals(type)) {
                handleReadReceipt(json, fromType, fromId);
            } else if ("typing".equals(type)) {
                handleTyping(json);
            } else if ("heartbeat".equals(type)) {
                // 心跳无需处理
            } else {
                log.debug("未知消息类型: {}", type);
            }
        } catch (Exception e) {
            log.error("消息处理失败: {}", frame.text(), e);
        }
    }

    /** 处理聊天消息：调用 ChatService 落库+推送。 */
    private void handleChat(JSONObject json, String fromType, Long fromId) {
        ChatService chatService = ApplicationContextHolder.getBean(ChatService.class);
        String toType = json.getString("toType");
        Long toId = json.getLong("toId");
        String content = json.getString("content");
        String msgType = json.getString("msgType");
        String clientMsgId = json.getString("clientMsgId");

        if (toType == null || toId == null) {
            log.warn("聊天消息缺少 toType/toId");
            return;
        }
        chatService.send(fromType, fromId, toType, toId, content, msgType, clientMsgId);
    }

    /** 处理已读回执。 */
    private void handleReadReceipt(JSONObject json, String fromType, Long fromId) {
        ChatService chatService = ApplicationContextHolder.getBean(ChatService.class);
        Long conversationId = json.getLong("conversationId");
        Long lastReadMsgId = json.getLong("lastReadMsgId");
        if (conversationId == null || lastReadMsgId == null) {
            log.warn("已读回执缺少参数: {}", json);
            return;
        }
        chatService.markReadByReceipt(conversationId, fromType, fromId, lastReadMsgId);
    }

    /** 转发输入状态。 */
    private void handleTyping(JSONObject json) {
        String toType = json.getString("toType");
        Long toId = json.getLong("toId");
        if (toType != null && toId != null) {
            NettyWebSocketServer.sendToUser(toType, toId, json.toJSONString());
        }
    }

    /** 空闲事件：心跳超时关闭连接。 */
    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent idleEvent) {
            if (idleEvent.state() == IdleState.ALL_IDLE) {
                log.debug("心跳超时，关闭连接: {}", ctx.channel().remoteAddress());
                ctx.close();
            }
        } else {
            super.userEventTriggered(ctx, evt);
        }
    }

    /** 异常处理。 */
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("WebSocket 异常: {}", ctx.channel().remoteAddress(), cause);
        ctx.close();
    }
}
