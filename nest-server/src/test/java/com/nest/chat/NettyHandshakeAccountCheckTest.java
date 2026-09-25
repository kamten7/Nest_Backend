package com.nest.chat;

import com.nest.chat.core.ChatAccountChecker;
import com.nest.chat.netty.NettyWebSocketServer;
import com.nest.constant.JwtConstant;
import com.nest.utils.JwtUtil;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** P1-7：Netty 握手鉴权——JWT 有效但账号被封禁时拒绝连接，且不写入 Channel 身份属性。 */
class NettyHandshakeAccountCheckTest {

    @BeforeAll
    static void initSecrets() {
        JwtConstant.initSecrets("test-admin-secret", "test-user-secret");
    }

    private String tenantToken(long userId) {
        return JwtUtil.createToken(JwtConstant.userSecretKey(), 60_000L, Map.of("userId", userId));
    }

    @Test
    void bannedAccountRejectedAndNoIdentityBound() {
        EmbeddedChannel channel = new EmbeddedChannel();
        ChatAccountChecker denyAll = (type, id) -> false;

        boolean ok = NettyWebSocketServer.authenticate(channel, "tenant", "5", tenantToken(5L), denyAll);

        assertThat(ok).isFalse();
        assertThat(channel.attr(NettyWebSocketServer.ATTR_USER_ID).get()).isNull();
        assertThat(channel.attr(NettyWebSocketServer.ATTR_USER_TYPE).get()).isNull();
        channel.finish();
    }

    @Test
    void activeAccountAcceptedAndIdentityBound() {
        EmbeddedChannel channel = new EmbeddedChannel();
        ChatAccountChecker allowAll = (type, id) -> true;

        boolean ok = NettyWebSocketServer.authenticate(channel, "tenant", "5", tenantToken(5L), allowAll);

        assertThat(ok).isTrue();
        assertThat(channel.attr(NettyWebSocketServer.ATTR_USER_ID).get()).isEqualTo(5L);
        assertThat(channel.attr(NettyWebSocketServer.ATTR_USER_TYPE).get()).isEqualTo("tenant");
        channel.finish();
    }

    @Test
    void tokenUserIdMismatchRejected() {
        EmbeddedChannel channel = new EmbeddedChannel();
        ChatAccountChecker allowAll = (type, id) -> true;

        boolean ok = NettyWebSocketServer.authenticate(channel, "tenant", "999", tenantToken(5L), allowAll);

        assertThat(ok).isFalse();
        assertThat(channel.attr(NettyWebSocketServer.ATTR_USER_ID).get()).isNull();
        channel.finish();
    }
}
