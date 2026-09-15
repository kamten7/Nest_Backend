package com.nest.mapper;

import com.nest.entity.Conversation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 会话 Mapper */
@Mapper
public interface ConversationMapper {

    int insert(Conversation conversation);

    Conversation selectByPair(@Param("user1Type") String user1Type,
                              @Param("user1Id") Long user1Id,
                              @Param("user2Type") String user2Type,
                              @Param("user2Id") Long user2Id);

    List<Conversation> selectByUser(@Param("userType") String userType, @Param("userId") Long userId);

    Conversation selectById(@Param("id") Long id);

    Conversation selectByIdAndMember(@Param("id") Long id,
                                     @Param("userType") String userType,
                                     @Param("userId") Long userId);


    int updateLastMessage(@Param("id") Long id,
                          @Param("lastMessage") String lastMessage,
                          @Param("lastMessageTime") java.time.LocalDateTime time);
}
