package com.nest.mapper;

import com.nest.entity.ReviewLike;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 顶楼评价点赞 Mapper */
@Mapper
public interface ReviewLikeMapper {

    int insert(ReviewLike like);

    int deleteByReviewAndUser(@Param("reviewId") Long reviewId,
                              @Param("userType") String userType,
                              @Param("userId") Long userId);

    ReviewLike selectByReviewAndUser(@Param("reviewId") Long reviewId,
                                     @Param("userType") String userType,
                                     @Param("userId") Long userId);

    /** 批量取当前用户在一批评价上的点赞记录，避免逐条查询（N+1）。 */
    List<ReviewLike> selectByUserAndReviewIds(@Param("reviewIds") List<Long> reviewIds,
                                              @Param("userType") String userType,
                                              @Param("userId") Long userId);

    /** 删除某条评价下的全部点赞（删评价时级联清理用） */
    int deleteByReviewId(@Param("reviewId") Long reviewId);
}
