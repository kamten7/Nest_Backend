package com.nest.mapper;

import com.nest.dto.house.HouseQueryDTO;
import com.nest.entity.house.House;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface HouseMapper {

    int insert(House house);

    int update(House house);

    int updateStatus(@Param("id") Long id, @Param("status") Integer status);

    House selectById(@Param("id") Long id);

    House selectByIdForUpdate(@Param("id") Long id);

    List<House> selectByLandlord(@Param("landlordId") Long landlordId);

    List<House> selectByCondition(HouseQueryDTO dto);

    int deleteById(@Param("id") Long id);

    int incrementViewCount(@Param("id") Long id);

    List<com.nest.vo.house.HouseMarkerVO> selectByBounds(@Param("minLat") double minLat,
                                                   @Param("maxLat") double maxLat,
                                                   @Param("minLng") double minLng,
                                                   @Param("maxLng") double maxLng,
                                                   @Param("limit") int limit);

    List<com.nest.vo.house.HouseMarkerVO> selectByLandlordMap(@Param("landlordId") Long landlordId);
}
