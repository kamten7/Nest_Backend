package com.nest.service.impl;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.nest.chat.push.PushService;
import com.nest.common.PageResult;
import com.nest.constant.MessageConstant;
import com.nest.entity.Conversation;
import com.nest.entity.Landlord;
import com.nest.entity.Message;
import com.nest.entity.Tenant;
import com.nest.exception.BusinessException;
import com.nest.mapper.ConversationMapper;
import com.nest.mapper.LandlordMapper;
import com.nest.mapper.MessageMapper;
import com.nest.mapper.TenantMapper;
import com.nest.service.ChatService;
import com.nest.vo.ConversationVO;
import com.nest.vo.MessageVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 聊天服务实现。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    private final ConversationMapper conversationMapper;
    private final MessageMapper messageMapper;
    private final TenantMapper tenantMapper;
    private final LandlordMapper landlordMapper;
    private final PushService pushService;

    /** 发送聊天消息：校验→找/建会话→落库→更新会话→WebSocket 推送。 */
    @Override
    @Transactional
    public Long send(String fromType, Long fromId, String toType, Long toId,
                     String content, String msgType, String clientMsgId) {
        if (content == null || content.isBlank()) {
            throw new BusinessException(MessageConstant.MESSAGE_CONTENT_EMPTY);
        }

        Conversation conversation = conversationMapper.selectByPair(fromType, fromId, toType, toId);
        if (conversation == null) {
            conversation = Conversation.builder()
                    .user1Type(fromType)
                    .user1Id(fromId)
                    .user2Type(toType)
                    .user2Id(toId)
                    .build();
            conversationMapper.insert(conversation);
        }

        Message message = Message.builder()
                .conversationId(conversation.getId())
                .senderType(fromType)
                .senderId(fromId)
                .content(content)
                .msgType(msgType != null ? msgType : "text")
                .isRead(0)
                .build();
        messageMapper.insert(message);

        conversationMapper.updateLastMessage(conversation.getId(),
                content.length() > 50 ? content.substring(0, 50) : content,
                LocalDateTime.now());

        MessageVO vo = buildMessageVO(message, fromType, fromId, false);
        pushService.pushChat(toType, toId, conversation.getId(), vo.getId(),
                fromType, fromId, vo.getSenderName(), content, vo.getMsgType());

        log.info("聊天消息: convId={}, from={}:{}, to={}:{}, content='{}'",
                conversation.getId(), fromType, fromId, toType, toId,
                content.length() > 30 ? content.substring(0, 30) + "..." : content);
        return message.getId();
    }

    /** 获取用户会话列表（分页）。 */
    @Override
    public PageResult<ConversationVO> listConversations(String userType, Long userId, Integer page, Integer pageSize) {
        PageHelper.startPage(page, pageSize);
        List<Conversation> conversations = conversationMapper.selectByUser(userType, userId);
        PageInfo<Conversation> pageInfo = new PageInfo<>(conversations);

        if (conversations.isEmpty()) {
            return PageResult.of(0L, Collections.emptyList());
        }

        List<ConversationVO> vos = new ArrayList<>();
        for (Conversation c : conversations) {
            String otherType, otherId;
            if (c.getUser1Type().equals(userType) && c.getUser1Id().equals(userId)) {
                otherType = c.getUser2Type();
                otherId = String.valueOf(c.getUser2Id());
            } else {
                otherType = c.getUser1Type();
                otherId = String.valueOf(c.getUser1Id());
            }
            ConversationVO vo = new ConversationVO();
            vo.setId(c.getId());
            vo.setOtherType(otherType);
            vo.setOtherId(Long.valueOf(otherId));
            fillOtherInfo(vo);
            vo.setLastMessage(c.getLastMessage());
            vo.setLastMessageTime(c.getLastMessageTime());
            vo.setUnreadCount(messageMapper.selectUnreadCount(c.getId(), userType, userId));
            vos.add(vo);
        }
        return PageResult.of(pageInfo.getTotal(), vos);
    }

    /** 获取会话历史消息（分页），同时标记已读并推送已读回执。 */
    @Override
    public PageResult<MessageVO> getMessages(Long conversationId, String userType, Long userId,
                                              Integer page, Integer pageSize) {
        PageHelper.startPage(page, pageSize);
        List<Message> messages = messageMapper.selectByConversation(conversationId);
        PageInfo<Message> pageInfo = new PageInfo<>(messages);

        messageMapper.updateReadByConversation(conversationId, userType, userId);
        for (Message m : messages) {
            if (!m.getSenderType().equals(userType) || !m.getSenderId().equals(userId)) {
                m.setIsRead(1);
            }
        }

        Conversation conversation = conversationMapper.selectById(conversationId);
        if (conversation != null) {
            notifyPeerRead(conversation, userType, userId);
        }

        List<MessageVO> vos = new ArrayList<>();
        for (Message m : messages) {
            vos.add(buildMessageVO(m, userType, userId, true));
        }
        return PageResult.of(pageInfo.getTotal(), vos);
    }

    /** 获取或创建会话 ID。 */
    @Override
    public Long getOrCreateConversationId(String myType, Long myId, String otherType, Long otherId) {
        Conversation conversation = conversationMapper.selectByPair(myType, myId, otherType, otherId);
        if (conversation == null) {
            conversation = Conversation.builder()
                    .user1Type(myType)
                    .user1Id(myId)
                    .user2Type(otherType)
                    .user2Id(otherId)
                    .build();
            conversationMapper.insert(conversation);
            log.info("创建会话: {}/{} <-> {}/{}", myType, myId, otherType, otherId);
        }
        return conversation.getId();
    }

    /** 获取用户所有会话的未读消息总数。 */
    @Override
    public long getUnreadCount(String userType, Long userId) {
        List<Conversation> conversations = conversationMapper.selectByUser(userType, userId);
        long total = 0;
        for (Conversation c : conversations) {
            total += messageMapper.selectUnreadCount(c.getId(), userType, userId);
        }
        return total;
    }

    /** 已读回执处理：置已读并回推给发送方。 */
    @Override
    @Transactional
    public void markReadByReceipt(Long conversationId, String readerType, Long readerId, Long upToMsgId) {
        if (conversationId == null || upToMsgId == null || readerType == null || readerId == null) {
            return;
        }
        Conversation conversation = conversationMapper.selectById(conversationId);
        if (conversation == null) {
            return;
        }
        messageMapper.updateReadUpTo(conversationId, readerType, readerId, upToMsgId);
        notifyPeerRead(conversation, readerType, readerId);
    }

    /** 向消息发送方推送已读事件。 */
    private void notifyPeerRead(Conversation conversation, String viewerType, Long viewerId) {
        String peerType;
        Long peerId;
        if (conversation.getUser1Type().equals(viewerType) && conversation.getUser1Id().equals(viewerId)) {
            peerType = conversation.getUser2Type();
            peerId = conversation.getUser2Id();
        } else if (conversation.getUser2Type().equals(viewerType) && conversation.getUser2Id().equals(viewerId)) {
            peerType = conversation.getUser1Type();
            peerId = conversation.getUser1Id();
        } else {
            return;
        }

        Long lastReadMsgId = messageMapper.selectMaxReadId(conversation.getId(), viewerType, viewerId);
        if (lastReadMsgId == null || lastReadMsgId <= 0) {
            return;
        }

        pushService.pushReadReceipt(peerType, peerId, conversation.getId(),
                viewerType, viewerId, lastReadMsgId);
        log.info("已读回执推送: convId={}, reader={}:{}, peer={}:{}, lastReadMsgId={}",
                conversation.getId(), viewerType, viewerId, peerType, peerId, lastReadMsgId);
    }

    /** 填充对方昵称/头像。 */
    private void fillOtherInfo(ConversationVO vo) {
        if ("landlord".equals(vo.getOtherType())) {
            Landlord landlord = landlordMapper.selectById(vo.getOtherId());
            if (landlord != null) {
                vo.setOtherName(landlord.getName() != null ? landlord.getName() : "房东");
                vo.setOtherAvatar(landlord.getAvatar());
            }
        } else {
            Tenant tenant = tenantMapper.selectById(vo.getOtherId());
            if (tenant != null) {
                vo.setOtherName(tenant.getNickname() != null ? tenant.getNickname() : "租客");
                vo.setOtherAvatar(tenant.getAvatar());
            }
        }
    }

    /** 构建消息 VO。 */
    private MessageVO buildMessageVO(Message m, String viewerType, Long viewerId, boolean includeMine) {
        MessageVO vo = new MessageVO();
        vo.setId(m.getId());
        vo.setConversationId(m.getConversationId());
        vo.setSenderType(m.getSenderType());
        vo.setSenderId(m.getSenderId());
        vo.setContent(m.getContent());
        vo.setMsgType(m.getMsgType());
        vo.setIsRead(m.getIsRead());
        vo.setCreateTime(m.getCreateTime());
        if (includeMine) {
            vo.setMine(m.getSenderType().equals(viewerType) && m.getSenderId().equals(viewerId));
        }
        if ("landlord".equals(m.getSenderType())) {
            Landlord landlord = landlordMapper.selectById(m.getSenderId());
            vo.setSenderName(landlord != null && landlord.getName() != null ? landlord.getName() : "房东");
        } else {
            Tenant tenant = tenantMapper.selectById(m.getSenderId());
            vo.setSenderName(tenant != null && tenant.getNickname() != null ? tenant.getNickname() : "租客");
        }
        return vo;
    }
}
