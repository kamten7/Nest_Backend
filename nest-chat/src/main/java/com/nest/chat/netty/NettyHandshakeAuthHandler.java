package com.nest.chat.netty;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.util.ReferenceCountUtil;
import com.nest.chat.core.ChatAccountChecker;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/** WebSocket 握手鉴权。 */
@Slf4j
public class NettyHandshakeAuthHandler extends ChannelInboundHandlerAdapter {

    private final String basePath;
    private final ChatAccountChecker accountChecker;

    public NettyHandshakeAuthHandler(String basePath, ChatAccountChecker accountChecker) {
        this.basePath = basePath;
        this.accountChecker = accountChecker;
    }

    /** 处理 HTTP 请求，校验路径参数与 token。 */
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (!(msg instanceof FullHttpRequest request)) {
            ctx.fireChannelRead(msg);
            return;
        }

        QueryStringDecoder decoder = new QueryStringDecoder(request.uri());
        String[] segs = extractIdentity(decoder.path());
        if (segs == null) {
            reject(ctx, request, "非法连接路径");
            return;
        }

        String token = firstParam(decoder.parameters(), "token");
        if (token == null) {
            token = firstHeader(request, "authentication");
        }
        if (token == null) {
            token = firstHeader(request, "token");
        }

        if (token == null || !NettyWebSocketServer.authenticate(ctx.channel(), segs[0], segs[1], token, accountChecker)) {
            reject(ctx, request, "鉴权失败");
            return;
        }

        ctx.fireChannelRead(request);
    }

    /** 解析 /ws/chat/tenant/1 → {userType, userId}；不匹配返回 null。 */
    private String[] extractIdentity(String path) {
        if (path == null || !path.startsWith(basePath + "/")) {
            return null;
        }
        String[] parts = path.substring(basePath.length() + 1).split("/");
        if (parts.length != 2 || parts[0].isEmpty() || parts[1].isEmpty()) {
            return null;
        }
        return parts;
    }

    private String firstParam(Map<String, List<String>> params, String name) {
        List<String> values = params.get(name);
        if (values == null || values.isEmpty()) {
            return null;
        }
        String v = values.get(0);
        return (v == null || v.isEmpty()) ? null : v;
    }

    private String firstHeader(FullHttpRequest request, String name) {
        String v = request.headers().get(name);
        return (v == null || v.isEmpty()) ? null : v;
    }

    /** 鉴权失败：直接返回 401 并关闭，不再进入握手流程。 */
    private void reject(ChannelHandlerContext ctx, FullHttpRequest request, String reason) {
        log.warn("Netty WebSocket 握手被拒: uri={}, reason={}", request.uri(), reason);

        FullHttpResponse response = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1,
                HttpResponseStatus.UNAUTHORIZED,
                Unpooled.copiedBuffer("{\"code\":0,\"msg\":\"未认证\"}", StandardCharsets.UTF_8));
        response.headers()
                .set(HttpHeaderNames.CONTENT_TYPE, "application/json; charset=utf-8")
                .setInt(HttpHeaderNames.CONTENT_LENGTH, response.content().readableBytes())
                .set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);

        ReferenceCountUtil.release(request);
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }
}