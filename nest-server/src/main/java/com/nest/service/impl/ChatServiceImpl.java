package com.nest.service.impl;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.nest.chat.push.PushService;
import com.nest.common.PageParam;
import com.nest.common.PageResult;
import com.nest.constant.JwtConstant;
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
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 聊天服务实现。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    /** 允许的会话双方身份类型：只可能是租客与房东 */
    private static final Set<String> VALID_USER_TYPES =
            Set.of(JwtConstant.TYPE_TENANT, JwtConstant.TYPE_LANDLORD);

    /** 允许的消息类型（当前前端只用 text，保留 image 供后续图片消息） */
    private static final Set<String> VALID_MSG_TYPES = Set.of("text", "image");

    /** 单条消息最大长度，与评论模块保持一致 */
    private static final int CONTENT_MAX_LENGTH = 500;

    private static final String DEFAULT_MSG_TYPE = "text";

    private final ConversationMapper conversationMapper;
    private final MessageMapper messageMapper;
    private final TenantMapper tenantMapper;
    private final LandlordMapper landlordMapper;
    private final PushService pushService;

    /** 发送聊天消息：校验→幂等查重→找/建会话→落库→更新会话 → netty推送 + 发送方 ack。 */
    @Override
    @Transactional
    public Long send(String fromType, Long fromId, String toType, Long toId,
                     String content, String msgType, String clientMsgId) {
        validateContent(content);
        String type = validateAndNormalizeMsgType(msgType);
        validatePeer(fromType, fromId, toType, toId);

        String idemKey = clientMsgId == null || clientMsgId.isBlank() ? null : clientMsgId;
        if (idemKey != null) {
            Message dup = messageMapper.selectByClientMsgId(fromType, fromId, idemKey);
            if (dup != null) {
                Long dupId = dup.getId();
                registerAfterCommitPush(() -> pushService.pushMsgAck(fromType, fromId, idemKey, dupId));
                return dupId;
            }
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
                .msgType(type)
                .clientMsgId(idemKey)
                .isRead(0)
                .build();
        try {
            messageMapper.insert(message);
        } catch (DuplicateKeyException e) {
            Message dup = messageMapper.selectByClientMsgId(fromType, fromId, idemKey);
            if (dup == null) {
                throw e;
            }
            Long dupId = dup.getId();
            registerAfterCommitPush(() -> pushService.pushMsgAck(fromType, fromId, idemKey, dupId));
            return dupId;
        }

        conversationMapper.updateLastMessage(conversation.getId(),
                content.length() > 50 ? content.substring(0, 50) : content,
                LocalDateTime.now());

        MessageVO vo = buildMessageVO(message, fromType, fromId, false);

        /** 先落库、后推送：推送延到事务提交后执行，避免回滚致对方看到不存在的消息。 */
        Long conversationId = conversation.getId();
        Long messageId = message.getId();
        registerAfterCommitPush(() -> pushService.pushChat(toType, toId, conversationId, messageId,
                fromType, fromId, vo.getSenderName(), content, vo.getMsgType()));
        if (idemKey != null) {
            registerAfterCommitPush(() -> pushService.pushMsgAck(fromType, fromId, idemKey, messageId));
        }

        log.info("聊天消息: convId={}, from={}:{}, to={}:{}, content='{}'",
                conversationId, fromType, fromId, toType, toId,
                content.length() > 30 ? content.substring(0, 30) + "..." : content);
        return messageId;
    }

    /** 获取用户会话列表（分页）。 */
    @Override
    public PageResult<ConversationVO> listConversations(String userType, Long userId, Integer page, Integer pageSize) {
        PageHelper.startPage(PageParam.pageOf(page), PageParam.pageSizeOf(pageSize));
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

    /** 历史消息分页：先校验成员归属，再标记已读并推回执；startPage 须紧贴分页查询。 */
    @Override
    public PageResult<MessageVO> getMessages(Long conversationId, String userType, Long userId,
                                             Integer page, Integer pageSize) {
        Conversation conversation = conversationMapper.selectByIdAndMember(conversationId, userType, userId);
        if (conversation == null) {
            throw new BusinessException(MessageConstant.CONVERSATION_NOT_FOUND);
        }

        PageHelper.startPage(PageParam.pageOf(page), PageParam.pageSizeOf(pageSize));
        List<Message> messages = messageMapper.selectByConversation(conversationId);
        PageInfo<Message> pageInfo = new PageInfo<>(messages);

        messageMapper.updateReadByConversation(conversationId, userType, userId);
        for (Message m : messages) {
            if (!m.getSenderType().equals(userType) || !m.getSenderId().equals(userId)) {
                m.setIsRead(1);
            }
        }
        notifyPeerRead(conversation, userType, userId);

        Map<String, String> senderNames = loadSenderNames(messages);
        List<MessageVO> vos = new ArrayList<>();
        for (Message m : messages) {
            vos.add(buildMessageVO(m, userType, userId, true, senderNames));
        }
        return PageResult.of(pageInfo.getTotal(), vos);
    }

    /** 获取或创建会话 ID。 */
    @Override
    public Long getOrCreateConversationId(String myType, Long myId, String otherType, Long otherId) {
        validatePeer(myType, myId, otherType, otherId);
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
        Conversation conversation = conversationMapper.selectByIdAndMember(conversationId, readerType, readerId);
        if (conversation == null) {
            return;
        }
        messageMapper.updateReadUpTo(conversationId, readerType, readerId, upToMsgId);
        registerAfterCommitPush(() -> notifyPeerRead(conversation, readerType, readerId));
    }

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

    /* ---------- 入参校验 ----------
       聊天是唯一「接收方 ID 直接来自报文」的入口。对话双方身份由握手层保证可信
       （路径 userType 与 token 签名密钥绑定），但接收方完全由客户端指定，
       不校验就等于开放了「向任意用户发消息」和「为不存在的用户建会话」两种能力。 */

    private void validateContent(String content) {
        if (content == null || content.isBlank()) {
            throw new BusinessException(MessageConstant.MESSAGE_CONTENT_EMPTY);
        }
        if (content.length() > CONTENT_MAX_LENGTH) {
            throw new BusinessException(MessageConstant.MESSAGE_CONTENT_TOO_LONG);
        }
    }

    /** 校验并归一化消息类型：空值按 text 处理，非白名单直接拒绝。 */
    private String validateAndNormalizeMsgType(String msgType) {
        if (msgType == null || msgType.isBlank()) {
            return DEFAULT_MSG_TYPE;
        }
        String type = msgType.trim();
        if (!VALID_MSG_TYPES.contains(type)) {
            throw new BusinessException(MessageConstant.MESSAGE_TYPE_INVALID);
        }
        return type;
    }

    /** 校验接收方身份合法、账号真实存在，且不是发给自己。 */
    private void validatePeer(String fromType, Long fromId, String toType, Long toId) {
        if (!VALID_USER_TYPES.contains(toType)) {
            throw new BusinessException(MessageConstant.MESSAGE_TO_TYPE_INVALID);
        }
        if (toId == null || toId <= 0) {
            throw new BusinessException(MessageConstant.MESSAGE_TO_USER_INVALID);
        }
        if (toType.equals(fromType) && toId.equals(fromId)) {
            throw new BusinessException(MessageConstant.MESSAGE_TO_SELF);
        }
        boolean exists = JwtConstant.TYPE_LANDLORD.equals(toType)
                ? landlordMapper.selectById(toId) != null
                : tenantMapper.selectById(toId) != null;
        if (!exists) {
            throw new BusinessException(MessageConstant.MESSAGE_TO_USER_INVALID);
        }
    }

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

    private MessageVO buildMessageVO(Message m, String viewerType, Long viewerId, boolean includeMine) {
        return buildMessageVO(m, viewerType, viewerId, includeMine, null);
    }

    private MessageVO buildMessageVO(Message m, String viewerType, Long viewerId, boolean includeMine,
                                     Map<String, String> senderNames) {
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
        vo.setSenderName(resolveSenderName(m, senderNames));
        return vo;
    }

    private String resolveSenderName(Message m, Map<String, String> senderNames) {
        if (senderNames != null) {
            String cached = senderNames.get(senderKey(m.getSenderType(), m.getSenderId()));
            if (cached != null) {
                return cached;
            }
        }
        if ("landlord".equals(m.getSenderType())) {
            Landlord landlord = landlordMapper.selectById(m.getSenderId());
            return landlord != null && landlord.getName() != null ? landlord.getName() : "房东";
        }
        Tenant tenant = tenantMapper.selectById(m.getSenderId());
        return tenant != null && tenant.getNickname() != null ? tenant.getNickname() : "租客";
    }

    /** 批量预加载发送者昵称消除 N+1；key 带 userType 前缀避免 tenant/landlord ID 撞车。 */
    private Map<String, String> loadSenderNames(List<Message> messages) {
        Set<Long> tenantIds = new HashSet<>();
        Set<Long> landlordIds = new HashSet<>();
        for (Message m : messages) {
            if ("landlord".equals(m.getSenderType())) {
                landlordIds.add(m.getSenderId());
            } else {
                tenantIds.add(m.getSenderId());
            }
        }

        Map<String, String> names = new HashMap<>();
        if (!landlordIds.isEmpty()) {
            for (Landlord l : landlordMapper.selectByIds(landlordIds)) {
                names.put(senderKey("landlord", l.getId()), l.getName() != null ? l.getName() : "房东");
            }
            for (Long id : landlordIds) {
                names.putIfAbsent(senderKey("landlord", id), "房东");
            }
        }
        if (!tenantIds.isEmpty()) {
            for (Tenant t : tenantMapper.selectByIds(tenantIds)) {
                names.put(senderKey("tenant", t.getId()), t.getNickname() != null ? t.getNickname() : "租客");
            }
            for (Long id : tenantIds) {
                names.putIfAbsent(senderKey("tenant", id), "租客");
            }
        }
        return names;
    }

    private static String senderKey(String userType, Long userId) {
        return userType + ":" + userId;
    }

    /** 推送挂到事务提交后：先存后发，推送失败不影响已提交数据。 */
    private void registerAfterCommitPush(Runnable task) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            safePush(task);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                safePush(task);
            }
        });
    }

    private void safePush(Runnable task) {
        try {
            task.run();
        } catch (Exception e) {
            log.error("事务提交后推送失败（数据已落库，不影响一致性）", e);
        }
    }
}
