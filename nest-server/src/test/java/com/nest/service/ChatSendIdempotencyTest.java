package com.nest.service;

import com.nest.chat.push.PushService;
import com.nest.entity.Conversation;
import com.nest.entity.Landlord;
import com.nest.entity.Message;
import com.nest.mapper.ConversationMapper;
import com.nest.mapper.LandlordMapper;
import com.nest.mapper.MessageMapper;
import com.nest.mapper.TenantMapper;
import com.nest.service.impl.ChatServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** P1-6：clientMsgId 幂等——断线重发不产生重复消息，且每次都回 ack。 */
class ChatSendIdempotencyTest {

    private ConversationMapper conversationMapper;
    private MessageMapper messageMapper;
    private LandlordMapper landlordMapper;
    private PushService pushService;
    private ChatServiceImpl chatService;

    @BeforeEach
    void setUp() {
        conversationMapper = mock(ConversationMapper.class);
        messageMapper = mock(MessageMapper.class);
        TenantMapper tenantMapper = mock(TenantMapper.class);
        landlordMapper = mock(LandlordMapper.class);
        pushService = mock(PushService.class);
        chatService = new ChatServiceImpl(conversationMapper, messageMapper, tenantMapper, landlordMapper, pushService);

        when(landlordMapper.selectById(2L)).thenReturn(new Landlord());
        when(conversationMapper.selectByPair("tenant", 1L, "landlord", 2L))
                .thenReturn(Conversation.builder().id(9L).build());
    }

    @Test
    void duplicateClientMsgIdShortCircuitsWithoutInsert() {
        when(messageMapper.selectByClientMsgId("tenant", 1L, "cm-1"))
                .thenReturn(Message.builder().id(77L).build());

        Long id = chatService.send("tenant", 1L, "landlord", 2L, "hi", "text", "cm-1");

        assertThat(id).isEqualTo(77L);
        verify(messageMapper, never()).insert(any());
        verify(pushService, never()).pushChat(any(), any(), any(), any(), any(), any(), any(), any(), any());
        verify(pushService, times(1)).pushMsgAck("tenant", 1L, "cm-1", 77L);
    }

    @Test
    void freshClientMsgIdInsertsOnceAndAcks() {
        when(messageMapper.selectByClientMsgId("tenant", 1L, "cm-2")).thenReturn(null);
        doAnswer(inv -> {
            inv.getArgument(0, Message.class).setId(88L);
            return 1;
        }).when(messageMapper).insert(any(Message.class));

        Long id = chatService.send("tenant", 1L, "landlord", 2L, "hi", "text", "cm-2");

        assertThat(id).isEqualTo(88L);
        verify(messageMapper, times(1)).insert(any(Message.class));
        verify(pushService, times(1)).pushChat(any(), any(), any(), any(), any(), any(), any(), any(), any());
        verify(pushService, times(1)).pushMsgAck("tenant", 1L, "cm-2", 88L);
    }

    @Test
    void noClientMsgIdNeverAcks() {
        doAnswer(inv -> {
            inv.getArgument(0, Message.class).setId(99L);
            return 1;
        }).when(messageMapper).insert(any(Message.class));

        Long id = chatService.send("tenant", 1L, "landlord", 2L, "hi", "text", null);

        assertThat(id).isEqualTo(99L);
        verify(messageMapper, never()).selectByClientMsgId(any(), any(), any());
        verify(pushService, times(1)).pushChat(any(), any(), any(), any(), any(), any(), any(), any(), any());
        verify(pushService, never()).pushMsgAck(any(), any(), any(), any());
    }
}
