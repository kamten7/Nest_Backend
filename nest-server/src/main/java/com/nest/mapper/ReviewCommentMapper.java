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

    ReviewComment selectById(@Param("id") Long id);

    int deleteById(@Param("id") Long id);

    int incrementLikeCount(@Param("id") Long id);

    int decrementLikeCount(@Param("id") Long id);
}
