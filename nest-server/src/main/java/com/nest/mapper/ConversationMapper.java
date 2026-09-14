package com.nest.mapper;

import com.nest.entity.Conversation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 会话 Mapper。
 */
@Mapper
public interface ConversationMapper {

    /** 创建会话，回填 ID */
    int insert(Conversation conversation);

    /** 按 (user1,user2) 双向查找已有会话 */
    Conversation selectByPair(@Param("user1Type") String user1Type,
                              @Param("user1Id") Long user1Id,
                              @Param("user2Type") String user2Type,
                              @Param("user2Id") Long user2Id);

    /** 某用户参与的所有会话（分页由 PageHelper 处理） */
    List<Conversation> selectByUser(@Param("userType") String userType, @Param("userId") Long userId);

    /** 按主键查会话（已读回执/推送需要确定对方身份） */
    Conversation selectById(@Param("id") Long id);

    /** 按主键查会话，并校验指定用户是否为会话成员（越权防护）。非成员返回 null。 */
    Conversation selectByIdAndMember(@Param("id") Long id,
                                     @Param("userType") String userType,
                                     @Param("userId") Long userId);


    /** 更新最后消息摘要和时间 */
    int updateLastMessage(@Param("id") Long id,
                          @Param("lastMessage") String lastMessage,
                          @Param("lastMessageTime") java.time.LocalDateTime time);
}
