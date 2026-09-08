package com.nest.service.impl;

import com.alibaba.fastjson2.JSON;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
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
import com.nest.websocket.ChatWebSocketServer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 聊天服务实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    private final ConversationMapper conversationMapper;
    private final MessageMapper messageMapper;
    private final TenantMapper tenantMapper;
    private final LandlordMapper landlordMapper;

    /**
     * 发送一条聊天消息（核心方法）。
     *
     * 流程：校验内容非空 → 找/建会话 → 消息落库 → 更新会话最后消息 → WebSocket 推给接收方。
     * 会话按 (from,to) 双向匹配，无论谁发起都映射到同一条（见 conversation 表唯一约束）。
     *
     * @param fromType    发送者类型：tenant / landlord
     * @param fromId      发送者 ID
     * @param toType      接收者类型：tenant / landlord
     * @param toId        接收者 ID
     * @param content     消息内容（不允许空白）
     * @param msgType     消息类型：text / image（为空默认 text）
     * @param clientMsgId 客户端消息 ID（用于去重 + ACK，当前预留）
     * @return 落库后的消息主键 ID
     */
    @Override
    @Transactional
    public Long send(String fromType, Long fromId, String toType, Long toId,
                     String content, String msgType, String clientMsgId) {
        if (content == null || content.isBlank()) {
            throw new BusinessException(MessageConstant.MESSAGE_CONTENT_EMPTY);   // 空消息拒绝
        }

        // 1. 找或创建会话（无论谁发起都映射到同一条）
        // 双向查询：正序/反序都找到同一会话，保证两人只对应一条会话记录
        Conversation conversation = conversationMapper.selectByPair(fromType, fromId, toType, toId);
        if (conversation == null) {
            // 首次聊天才创建会话，固定按"发起方在前、接收方在后"存储
            conversation = Conversation.builder()
                    .user1Type(fromType)
                    .user1Id(fromId)
                    .user2Type(toType)
                    .user2Id(toId)
                    .build();
            conversationMapper.insert(conversation);
        }

        // 2. 落库消息（新消息默认未读 isRead=0）
        Message message = Message.builder()
                .conversationId(conversation.getId())   // 归属会话
                .senderType(fromType)
                .senderId(fromId)
                .content(content)
                .msgType(msgType != null ? msgType : "text")   // 缺省文本消息
                .isRead(0)   // 默认未读
                .build();
        messageMapper.insert(message);

        // 3. 更新会话最后消息（会话列表展示用，超长内容截断到 50 字）
        conversationMapper.updateLastMessage(conversation.getId(),
                content.length() > 50 ? content.substring(0, 50) : content,
                LocalDateTime.now());

        // 4. 推 WebSocket 给接收方（在线则实时推送；离线则消息已在库中，下次上线拉取）
        MessageVO vo = buildMessageVO(message, fromType, fromId, false);   // 推给对方的消息不算"我发的"
        ChatWebSocketServer.sendToUser(toType, toId, buildPushJson(vo, conversation.getId()));

        log.info("聊天消息: convId={}, from={}:{}, to={}:{}, content='{}'",
                conversation.getId(), fromType, fromId, toType, toId,
                content.length() > 30 ? content.substring(0, 30) + "..." : content);
        return message.getId();
    }

    /**
     * 获取用户会话列表。
     * 主要是根据用户类型和 ID，查询该用户的所有会话记录。
     * @param userType
     * @param userId
     * @param page
     * @param pageSize
     * @return
     */
    @Override
    public PageResult<ConversationVO> listConversations(
            String userType, // 用户类型：tenant / landlord
            Long userId, // 当前的房东或租客
            Integer page, // 当前页码
            Integer pageSize // 每页记录数
    ) {
        PageHelper.startPage(page, pageSize);
        List<Conversation> conversations = conversationMapper.selectByUser(userType, userId);
        PageInfo<Conversation> pageInfo = new PageInfo<>(conversations);

        if (conversations.isEmpty()) {
            //不存在会话
            return PageResult.of(0L, Collections.emptyList());
        }

        // 构建会话列表，vos--最后返回给前端的会话列表
        List<ConversationVO> vos = new ArrayList<>();
        for (Conversation c : conversations) {
            // 确定对方
            String otherType, otherId;
            if (c.getUser1Type().equals(userType) && c.getUser1Id().equals(userId)) {
                otherType = c.getUser2Type();
                otherId = String.valueOf(c.getUser2Id());
            } else {
                otherType = c.getUser1Type();
                otherId = String.valueOf(c.getUser1Id());
            }
            //确认对方基本信息后去查询其他信息然后构建vo并添加到vos中
            // 构建会话VO
            ConversationVO vo = new ConversationVO();
            vo.setId(c.getId());
            vo.setOtherType(otherType);
            vo.setOtherId(Long.valueOf(otherId));
            // 填充对方昵称和头像
            fillOtherInfo(vo);
            // 填充最后一条消息摘要
            vo.setLastMessage(c.getLastMessage());
            // 填充最后一条消息时间
            vo.setLastMessageTime(c.getLastMessageTime());
            // 未读数
            vo.setUnreadCount(messageMapper.selectUnreadCount(c.getId(), userType, userId));
            vos.add(vo);
        }
        return PageResult.of(pageInfo.getTotal(), vos);
    }

    /**
     * 获取会话历史消息。
     * 这里是1对1聊天
     * 跟某个用户的会话历史消息。
     * 主要是根据会话 ID，查询该会话的所有消息记录。
     * @param conversationId
     * @param userType
     * @param userId
     * @param page
     * @param pageSize
     * @return
     */
    @Override
    public PageResult<MessageVO> getMessages(
            Long conversationId,
            String userType,
            Long userId,
            Integer page,
            Integer pageSize
    ) {
        PageHelper.startPage(page, pageSize);
        // 分页查询消息
        List<Message> messages = messageMapper.selectByConversation(conversationId);
        PageInfo<Message> pageInfo = new PageInfo<>(messages);

        // 打开会话 → 标记本轮会话的所有消息为已读
        messageMapper.updateReadByConversation(conversationId, userType, userId);
        // 上面先查询后置读，内存对象还是旧值：把"对方发给我的"回填为已读，
        // 避免本次接口返回的历史消息 isRead 与实际状态不一致
        for (Message m : messages) {
            if (!m.getSenderType().equals(userType) || !m.getSenderId().equals(userId)) {
                m.setIsRead(1);
            }
        }

        // 把"已读"事件推给对方（发送方），让对方看到自己的消息已被读
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

    /**
     * 获取或创建会话 ID。
     * 主要是根据用户类型和 ID，查询或创建会话记录。
     * @param myType
     * @param myId
     * @param otherType
     * @param otherId
     * @return
     */
    @Override
    public Long getOrCreateConversationId(
            String myType,
            Long myId,
            String otherType,
            Long otherId
    ) {
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

    @Override
    public long getUnreadCount(String userType, Long userId) {
        List<Conversation> conversations = conversationMapper.selectByUser(userType, userId);
        long total = 0;
        for (Conversation c : conversations) {
            total += messageMapper.selectUnreadCount(c.getId(), userType, userId);
        }
        return total;
    }

    /**
     * 已读回执处理（WebSocket）：把对方发来的、id ≤ upToMsgId 的消息置为已读，
     * 并把已读事件回推给发送方，让发送方能展示"已读/未读"。
     */
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

    /** 打开会话（REST）或收到已读回执（WS）后，向消息发送方推送已读事件 */
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
            return; // 非法身份，不处理
        }

        Long lastReadMsgId = messageMapper.selectMaxReadId(conversation.getId(), viewerType, viewerId);
        if (lastReadMsgId == null || lastReadMsgId <= 0) {
            return; // 没有可通知的已读消息
        }

        String json = JSON.toJSONString(java.util.Map.of(
                "type", "read_receipt",
                "conversationId", conversation.getId(),
                "readerType", viewerType,
                "readerId", viewerId,
                "lastReadMsgId", lastReadMsgId
        ));
        ChatWebSocketServer.sendToUser(peerType, peerId, json);
        log.info("已读回执推送: convId={}, reader={}:{}, peer={}:{}, lastReadMsgId={}",
                conversation.getId(), viewerType, viewerId, peerType, peerId, lastReadMsgId);
    }

    // ==================== 内部方法 ====================

    /** 组装对方昵称/头像 */
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

    /** 组装消息 VO */
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
        // 发送者昵称
        if ("landlord".equals(m.getSenderType())) {
            Landlord landlord = landlordMapper.selectById(m.getSenderId());
            vo.setSenderName(landlord != null && landlord.getName() != null ? landlord.getName() : "房东");
        } else {
            Tenant tenant = tenantMapper.selectById(m.getSenderId());
            vo.setSenderName(tenant != null && tenant.getNickname() != null ? tenant.getNickname() : "租客");
        }
        return vo;
    }

    /** 组装 WebSocket 推送 JSON（服务端 → 客户端） */
    private String buildPushJson(MessageVO vo, Long conversationId) {
        return JSON.toJSONString(java.util.Map.of(
                "type", "chat",
                "msgId", vo.getId(),
                "conversationId", conversationId,
                "fromType", vo.getSenderType(),
                "fromId", vo.getSenderId(),
                "senderName", vo.getSenderName() == null ? "" : vo.getSenderName(),
                "content", vo.getContent(),
                "msgType", vo.getMsgType(),
                "timestamp", System.currentTimeMillis()
        ));
    }
}
