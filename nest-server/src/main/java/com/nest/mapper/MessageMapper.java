package com.nest.mapper;

import com.nest.entity.Message;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 聊天消息 Mapper */
@Mapper
public interface MessageMapper {

    int insert(Message message);

    List<Message> selectByConversation(@Param("conversationId") Long conversationId);

    long selectUnreadCount(@Param("conversationId") Long conversationId,
                           @Param("receiverType") String receiverType,
                           @Param("receiverId") Long receiverId);

    int updateReadByConversation(@Param("conversationId") Long conversationId,
                                 @Param("receiverType") String receiverType,
                                 @Param("receiverId") Long receiverId);

    int updateReadUpTo(@Param("conversationId") Long conversationId,
                       @Param("viewerType") String viewerType,
                       @Param("viewerId") Long viewerId,
                       @Param("upToId") Long upToId);

    Long selectMaxReadId(@Param("conversationId") Long conversationId,
                         @Param("viewerType") String viewerType,
                         @Param("viewerId") Long viewerId);

    Message selectByClientMsgId(@Param("senderType") String senderType,
                                @Param("senderId") Long senderId,
                                @Param("clientMsgId") String clientMsgId);
}
