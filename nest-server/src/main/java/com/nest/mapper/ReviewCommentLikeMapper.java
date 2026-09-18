package com.nest.mapper;

import com.nest.entity.ReviewCommentLike;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 评论回复点赞 Mapper。
 *  ⚠️ 点赞者身份必须用 (userType, userId) 二元组定位，只看 userId 会让租客/房东串号。 */
@Mapper
public interface ReviewCommentLikeMapper {

    int insert(ReviewCommentLike like);

    int deleteByCommentAndUser(@Param("commentId") Long commentId,
                               @Param("userType") String userType,
                               @Param("userId") Long userId);

    ReviewCommentLike selectByCommentAndUser(@Param("commentId") Long commentId,
                                             @Param("userType") String userType,
                                             @Param("userId") Long userId);

    /** 批量取当前用户在一批回复上的点赞记录，避免逐条查询（N+1）。 */
    List<ReviewCommentLike> selectByUserAndCommentIds(@Param("commentIds") List<Long> commentIds,
                                                      @Param("userType") String userType,
                                                      @Param("userId") Long userId);

    /** 删除一批回复下的全部点赞（删回复 / 删评价时级联清理用） */
    int deleteByCommentIds(@Param("commentIds") List<Long> commentIds);
}
