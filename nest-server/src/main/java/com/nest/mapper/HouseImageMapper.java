package com.nest.mapper;

import com.nest.entity.HouseImage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 房源图片 Mapper */
@Mapper
public interface HouseImageMapper {

    int insertBatch(@Param("images") List<HouseImage> images);

    int deleteByHouseId(@Param("houseId") Long houseId);

    List<HouseImage> selectByHouseId(@Param("houseId") Long houseId);
}
