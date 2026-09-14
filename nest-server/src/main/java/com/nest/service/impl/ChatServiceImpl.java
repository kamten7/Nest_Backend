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

    private final ConversationMapper conversationMapper;
    private final MessageMapper messageMapper;
    private final TenantMapper tenantMapper;
    private final LandlordMapper landlordMapper;
    private final PushService pushService;

    /** 发送聊天消息：校验→找/建会话→落库→更新会话 → netty推送。 */
    @Override
    @Transactional// 事务管理
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

        // 更新会话的最后消息内容和时间
        conversationMapper.updateLastMessage(conversation.getId(),
                content.length() > 50 ? content.substring(0, 50) : content,
                LocalDateTime.now());

        MessageVO vo = buildMessageVO(message, fromType, fromId, false);

        // 先落库、后推送：推送注册到事务提交之后执行。
        // 放在事务里推的话，一旦本次事务回滚，对方会看到数据库里并不存在的消息。
        Long conversationId = conversation.getId();
        Long messageId = message.getId();
        registerAfterCommitPush(() -> pushService.pushChat(toType, toId, conversationId, messageId,
                fromType, fromId, vo.getSenderName(), content, vo.getMsgType()));

        log.info("聊天消息: convId={}, from={}:{}, to={}:{}, content='{}'",
                conversationId, fromType, fromId, toType, toId,
                content.length() > 30 ? content.substring(0, 30) + "..." : content);
        return messageId;
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

    /** 获取会话历史消息（分页），先做成员归属校验，再标记已读并推送已读回执。 */
    @Override
    public PageResult<MessageVO> getMessages(Long conversationId, String userType, Long userId,
                                             Integer page, Integer pageSize) {
        // 1. 归属校验：当前用户必须是会话成员；不是成员一律按"不存在"处理，不暴露会话是否真的存在
        Conversation conversation = conversationMapper.selectByIdAndMember(conversationId, userType, userId);
        if (conversation == null) {
            throw new BusinessException(MessageConstant.CONVERSATION_NOT_FOUND);
        }

        // 2. 分页拉消息（注意：startPage 必须紧贴它要分页的那条查询，中间不能再插别的 Mapper 调用）
        PageHelper.startPage(page, pageSize);
        List<Message> messages = messageMapper.selectByConversation(conversationId);
        PageInfo<Message> pageInfo = new PageInfo<>(messages);

        // 3. 批量置已读（仅对方发的）+ 回推已读回执给对方
        messageMapper.updateReadByConversation(conversationId, userType, userId);
        for (Message m : messages) {
            if (!m.getSenderType().equals(userType) || !m.getSenderId().equals(userId)) {
                m.setIsRead(1);
            }
        }
        // 标记已读并推送已读回执给对方
        notifyPeerRead(conversation, userType, userId);   // 直接复用第 1 步查出的会话，省一次 selectById

        // 4. 组装 VO（发送者昵称批量预加载，避免逐条查询的 N+1）
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
        // 同样先存后发：已读回执也等事务提交后再推
        registerAfterCommitPush(() -> notifyPeerRead(conversation, readerType, readerId));
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

    /** 构建消息 VO（单条场景：发送者昵称直接查库）。 */
    private MessageVO buildMessageVO(Message m, String viewerType, Long viewerId, boolean includeMine) {
        return buildMessageVO(m, viewerType, viewerId, includeMine, null);
    }

    /**
     * 构建消息 VO。
     *
     * @param senderNames 发送者昵称缓存，key = {@code userType + ":" + userId}；
     *                    传入时不再逐条查库（列表场景消除 N+1），为 {@code null} 时回退单条查询。
     */
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

    /** 取发送者昵称：优先命中缓存，未命中再回退单条查询。 */
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

    /**
     * 批量预加载一批消息的发送者昵称，消除逐条查库的 N+1（一页消息最多 2 次 SQL）。
     * <p>key 带上 userType 前缀 —— 否则 tenant 5 与 landlord 5 会互相覆盖。
     * <p>所有请求到的 ID 都会写入缓存（查不到则填默认昵称），保证调用方不再回退查库。
     */
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

    /** 昵称缓存的 key，避免 tenant / landlord 的 ID 撞车。 */
    private static String senderKey(String userType, Long userId) {
        return userType + ":" + userId;
    }

    /**
     * 把推送任务挂到当前事务的 afterCommit 上 —— "先存后发"。
     * <p>在事务内推送的话，一旦后续操作失败回滚，对方会看到数据库里并不存在的消息，双方状态永久不一致。
     * <p>推送只是"尽力而为的通知"，落库才是唯一事实来源，因此推送失败绝不能影响已提交的数据。
     */
    private void registerAfterCommitPush(Runnable task) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            // 无事务时（理论上不会走到）直接执行
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

    /** 执行推送并吞掉异常：数据已落库，推送失败只记日志。 */
    private void safePush(Runnable task) {
        try {
            task.run();
        } catch (Exception e) {
            log.error("事务提交后推送失败（数据已落库，不影响一致性）", e);
        }
    }
}
