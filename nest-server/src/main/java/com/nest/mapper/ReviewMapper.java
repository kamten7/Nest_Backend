package com.nest.mapper;

import com.nest.entity.Review;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;

/** 房源评论 Mapper */
@Mapper
public interface ReviewMapper {

    int insert(Review review);

    Review selectById(@Param("id") Long id);

    List<Review> selectByHouse(@Param("houseId") Long houseId);

    Review selectByTenantAndHouse(@Param("tenantId") Long tenantId, @Param("houseId") Long houseId);

    long selectCountByHouse(@Param("houseId") Long houseId);

    /** 已打分评价数（rating 非空的条数），用于「X 人评分」。 */
    long selectRatedCountByHouse(@Param("houseId") Long houseId);

    BigDecimal selectAvgRatingByHouse(@Param("houseId") Long houseId);

    int incrementLikeCount(@Param("id") Long id);

    int decrementLikeCount(@Param("id") Long id);

    /** 删除评价（连带回复、点赞由 Service 负责清理） */
    int deleteById(@Param("id") Long id);
}
