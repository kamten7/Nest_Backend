package com.nest.mapper;

import com.nest.entity.Review;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;

/**
 * 房源评论 Mapper。
 */
@Mapper
public interface ReviewMapper {

    /** 发表评论，回填 ID */
    int insert(Review review);

    /** 按 ID 查询 */
    Review selectById(@Param("id") Long id);

    /** 某房源的全部评论（分页由 PageHelper 处理） */
    List<Review> selectByHouse(@Param("houseId") Long houseId);

    /** 判断某租客是否已评论某房源 */
    Review selectByTenantAndHouse(@Param("tenantId") Long tenantId, @Param("houseId") Long houseId);

    /** 房源评论数 */
    long selectCountByHouse(@Param("houseId") Long houseId);

    /** 房源平均评分 */
    BigDecimal selectAvgRatingByHouse(@Param("houseId") Long houseId);
}
