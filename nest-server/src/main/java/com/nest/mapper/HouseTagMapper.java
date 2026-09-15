package com.nest.mapper;

import com.nest.entity.HouseTag;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 房源标签 Mapper */
@Mapper
public interface HouseTagMapper {

    int insertBatch(@Param("tags") List<HouseTag> tags);

    int deleteByHouseId(@Param("houseId") Long houseId);

    List<HouseTag> selectByHouseId(@Param("houseId") Long houseId);

    List<HouseTag> selectByHouseIds(@Param("houseIds") List<Long> houseIds);
}
