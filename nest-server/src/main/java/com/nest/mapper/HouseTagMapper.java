package com.nest.mapper;

import com.nest.entity.HouseTag;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 房源标签 Mapper。
 */
@Mapper
public interface HouseTagMapper {

    /** 批量插入标签 */
    int insertBatch(@Param("tags") List<HouseTag> tags);

    /** 删除房源的所有标签 */
    int deleteByHouseId(@Param("houseId") Long houseId);

    /** 查询房源的所有标签 */
    List<HouseTag> selectByHouseId(@Param("houseId") Long houseId);

    /** 批量查询多个房源的标签（用于列表页） */
    List<HouseTag> selectByHouseIds(@Param("houseIds") List<Long> houseIds);
}
