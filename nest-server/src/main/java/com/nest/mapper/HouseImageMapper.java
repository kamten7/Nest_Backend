package com.nest.mapper;

import com.nest.entity.HouseImage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 房源图片 Mapper。
 */
@Mapper
public interface HouseImageMapper {

    /** 批量插入图片 */
    int insertBatch(@Param("images") List<HouseImage> images);

    /** 删除房源的所有图片 */
    int deleteByHouseId(@Param("houseId") Long houseId);

    /** 查询房源的所有图片 */
    List<HouseImage> selectByHouseId(@Param("houseId") Long houseId);
}
