package com.nest.listener;

import com.nest.chat.core.MessageListener;
import com.nest.service.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 通信模块入站消息的业务实现：转交 ChatService 落库并回推。 */
@Component
@RequiredArgsConstructor
public class ChatMessageListener implements MessageListener {

    private final ChatService chatService;

    @Override
    public Long onChat(String fromType, Long fromId, String toType, Long toId,
                       String content, String msgType, String clientMsgId) {
        return chatService.send(fromType, fromId, toType, toId, content, msgType, clientMsgId);
    }

    @Override
    public void onReadReceipt(Long conversationId, String readerType, Long readerId, Long lastReadMsgId) {
        chatService.markReadByReceipt(conversationId, readerType, readerId, lastReadMsgId);
    }
}
