package com.nest.mapper;

import com.nest.entity.ReviewCommentLike;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 评论回复点赞 Mapper。
 */
@Mapper
public interface ReviewCommentLikeMapper {

    /** 点赞，回填 ID */
    int insert(ReviewCommentLike like);

    /** 取消点赞 */
    int deleteByCommentAndTenant(@Param("commentId") Long commentId, @Param("tenantId") Long tenantId);

    /** 判断是否已赞 */
    ReviewCommentLike selectByCommentAndTenant(@Param("commentId") Long commentId, @Param("tenantId") Long tenantId);
}
