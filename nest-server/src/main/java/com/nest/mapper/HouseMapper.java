package com.nest.mapper;

import com.nest.dto.HouseQueryDTO;
import com.nest.entity.House;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 房源 Mapper */
@Mapper
public interface HouseMapper {

    int insert(House house);

    int update(House house);

    int updateStatus(@Param("id") Long id, @Param("status") Integer status);

    House selectById(@Param("id") Long id);

    List<House> selectByLandlord(@Param("landlordId") Long landlordId);

    List<House> selectByCondition(HouseQueryDTO dto);

    int deleteById(@Param("id") Long id);

    int incrementViewCount(@Param("id") Long id);

    List<com.nest.vo.HouseMarkerVO> selectByBounds(@Param("minLat") double minLat,
                                                     @Param("maxLat") double maxLat,
                                                     @Param("minLng") double minLng,
                                                     @Param("maxLng") double maxLng);

    List<com.nest.vo.HouseMarkerVO> selectByLandlordMap(@Param("landlordId") Long landlordId);
}
