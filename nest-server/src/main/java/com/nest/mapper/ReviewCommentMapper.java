package com.nest.mapper;

import com.nest.entity.ReviewComment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 评论回复 Mapper */
@Mapper
public interface ReviewCommentMapper {

    int insert(ReviewComment comment);

    List<ReviewComment> selectByReviewId(@Param("reviewId") Long reviewId);

    /** 批量取多条评价下的回复，避免逐条评价查询（N+1）。 */
    List<ReviewComment> selectByReviewIds(@Param("reviewIds") List<Long> reviewIds);

    ReviewComment selectById(@Param("id") Long id);

    int deleteById(@Param("id") Long id);

    /** 某条评价下的全部回复 id（删评价时级联清理用） */
    List<Long> selectIdsByReviewId(@Param("reviewId") Long reviewId);

    /** 某条回复的子回复 id（删回复时级联清理用） */
    List<Long> selectIdsByParentId(@Param("parentId") Long parentId);

    /** 批量删除回复 */
    int deleteByIds(@Param("ids") List<Long> ids);

    int incrementLikeCount(@Param("id") Long id);

    int decrementLikeCount(@Param("id") Long id);
}
