package com.nest.service;

import com.nest.common.PageResult;
import com.nest.vo.ConversationVO;
import com.nest.vo.MessageVO;

/** 聊天服务接口 —— 会话 + 消息 + 未读。 */
public interface ChatService {

    /** 发送消息：找/建会话→落库→更新会话→推 WebSocket。 */
    Long send(String fromType, Long fromId, String toType, Long toId,
              String content, String msgType, String clientMsgId);

    /** 我的会话列表（含对方信息 + 未读数）。 */
    PageResult<ConversationVO> listConversations(String userType, Long userId,
                                                 Integer page, Integer pageSize);

    /** 找或创建与某人的会话，返回会话 ID。 */
    Long getOrCreateConversationId(String myType, Long myId, String otherType, Long otherId);

    /** 某会话的历史消息（分页），打开时标记已读。 */
    PageResult<MessageVO> getMessages(Long conversationId, String userType, Long userId,
                                      Integer page, Integer pageSize);

    /** 总未读数。 */
    long getUnreadCount(String userType, Long userId);

    /** 已读回执：标记已读并推给对方。 */
    void markReadByReceipt(Long conversationId, String readerType, Long readerId, Long upToMsgId);
}
