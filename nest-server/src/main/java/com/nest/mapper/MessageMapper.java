package com.nest.mapper;

import com.nest.entity.Message;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 聊天消息 Mapper。
 */
@Mapper
public interface MessageMapper {

    /** 保存消息，回填 ID */
    int insert(Message message);

    /** 某会话的历史消息（分页由 PageHelper 处理） */
    List<Message> selectByConversation(@Param("conversationId") Long conversationId);

    /** 某会话的未读数（接收方视角） */
    long selectUnreadCount(@Param("conversationId") Long conversationId,
                           @Param("receiverType") String receiverType,
                           @Param("receiverId") Long receiverId);

    /** 标记某会话的全部消息为已读（接收方视角） */
    int updateReadByConversation(@Param("conversationId") Long conversationId,
                                 @Param("receiverType") String receiverType,
                                 @Param("receiverId") Long receiverId);

    /** 已读回执：把对方发来的、id ≤ upToId 的未读消息置为已读 */
    int updateReadUpTo(@Param("conversationId") Long conversationId,
                       @Param("viewerType") String viewerType,
                       @Param("viewerId") Long viewerId,
                       @Param("upToId") Long upToId);

    /** 对方视角下已读的最大消息 id（用于生成已读回执） */
    Long selectMaxReadId(@Param("conversationId") Long conversationId,
                         @Param("viewerType") String viewerType,
                         @Param("viewerId") Long viewerId);
}
