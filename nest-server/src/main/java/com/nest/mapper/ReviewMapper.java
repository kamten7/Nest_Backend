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

    BigDecimal selectAvgRatingByHouse(@Param("houseId") Long houseId);
}
