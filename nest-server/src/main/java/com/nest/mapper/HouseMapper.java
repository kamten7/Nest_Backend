package com.nest.mapper;

import com.nest.dto.HouseQueryDTO;
import com.nest.entity.House;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 房源 Mapper。
 */
@Mapper
public interface HouseMapper {

    /** 插入房源，回填 ID */
    int insert(House house);

    /** 动态更新（仅非空字段） */
    int update(House house);

    /** 上下架 */
    int updateStatus(@Param("id") Long id, @Param("status") Integer status);

    /** 按 ID 查询 */
    House selectById(@Param("id") Long id);

    /** 房东自己的房源列表 */
    List<House> selectByLandlord(@Param("landlordId") Long landlordId);

    /** 按条件分页查询（用户端） */
    List<House> selectByCondition(HouseQueryDTO dto);

    /** 删除房源 */
    int deleteById(@Param("id") Long id);

    /** 浏览量 +1 */
    int incrementViewCount(@Param("id") Long id);

    /** 按经纬度范围查询已上架房源标记点（用户端地图），LEFT JOIN 封面图 */
    List<com.nest.vo.HouseMarkerVO> selectByBounds(@Param("minLat") double minLat,
                                                     @Param("maxLat") double maxLat,
                                                     @Param("minLng") double minLng,
                                                     @Param("maxLng") double maxLng);

    /** 查询房东自己的所有房源标记点（管理端地图） */
    List<com.nest.vo.HouseMarkerVO> selectByLandlordMap(@Param("landlordId") Long landlordId);
}
