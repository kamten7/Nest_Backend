package com.nest.mapper;

import com.nest.entity.ReviewCommentLike;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 评论回复点赞 Mapper */
@Mapper
public interface ReviewCommentLikeMapper {

    int insert(ReviewCommentLike like);

    int deleteByCommentAndTenant(@Param("commentId") Long commentId, @Param("tenantId") Long tenantId);

    ReviewCommentLike selectByCommentAndTenant(@Param("commentId") Long commentId, @Param("tenantId") Long tenantId);
}
