package com.nest.chat;

import com.nest.chat.core.MessageDispatcher;
import com.nest.chat.core.MessageListener;
import com.nest.chat.push.PushService;
import com.nest.constant.MessageConstant;
import com.nest.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** P1-6：落库被拒时向发送方回 msg_error 帧，携带 clientMsgId 与业务原因，避免客户端无限重试。 */
class MessageDispatcherErrorFrameTest {

    private PushService pushService;
    private MessageListener listener;
    private MessageDispatcher dispatcher;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        pushService = mock(PushService.class);
        listener = mock(MessageListener.class);
        ObjectProvider<MessageListener> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(listener);
        dispatcher = new MessageDispatcher(pushService, provider);
    }

    @Test
    void businessRejectionPushesMsgErrorWithReason() {
        when(listener.onChat(anyString(), anyLong(), anyString(), anyLong(), anyString(), any(), anyString()))
                .thenThrow(new BusinessException(MessageConstant.MESSAGE_CONTENT_TOO_LONG));

        dispatcher.dispatch(
                "{\"type\":\"chat\",\"toType\":\"landlord\",\"toId\":2,\"content\":\"x\",\"clientMsgId\":\"cm-9\"}",
                "tenant", 1L);

        verify(pushService, times(1))
                .pushMsgError("tenant", 1L, "cm-9", MessageConstant.MESSAGE_CONTENT_TOO_LONG);
    }

    @Test
    void unexpectedExceptionPushesGenericMsgError() {
        when(listener.onChat(anyString(), anyLong(), anyString(), anyLong(), anyString(), any(), anyString()))
                .thenThrow(new RuntimeException("db down"));

        dispatcher.dispatch(
                "{\"type\":\"chat\",\"toType\":\"landlord\",\"toId\":2,\"content\":\"x\",\"clientMsgId\":\"cm-10\"}",
                "tenant", 1L);

        verify(pushService, times(1)).pushMsgError("tenant", 1L, "cm-10", "消息发送失败，请重试");
    }

    @Test
    void successPathNeverPushesMsgError() {
        when(listener.onChat(anyString(), anyLong(), anyString(), anyLong(), anyString(), any(), anyString()))
                .thenReturn(1L);

        dispatcher.dispatch(
                "{\"type\":\"chat\",\"toType\":\"landlord\",\"toId\":2,\"content\":\"x\",\"clientMsgId\":\"cm-11\"}",
                "tenant", 1L);

        verify(pushService, never()).pushMsgError(anyString(), anyLong(), anyString(), anyString());
    }
}
