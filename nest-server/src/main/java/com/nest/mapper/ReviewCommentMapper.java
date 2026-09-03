package com.nest.mapper;

import com.nest.entity.ReviewComment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 评论回复 Mapper。
 */
@Mapper
public interface ReviewCommentMapper {

    /** 发表回复，回填 ID */
    int insert(ReviewComment comment);

    /** 某评论的全部回复（含嵌套，按时间正序） */
    List<ReviewComment> selectByReviewId(@Param("reviewId") Long reviewId);

    /** 按 ID 查询 */
    ReviewComment selectById(@Param("id") Long id);

    /** 删除回复 */
    int deleteById(@Param("id") Long id);

    /** 点赞数 +1 */
    int incrementLikeCount(@Param("id") Long id);

    /** 点赞数 -1（防止负数） */
    int decrementLikeCount(@Param("id") Long id);
}
