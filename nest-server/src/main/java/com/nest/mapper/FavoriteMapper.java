package com.nest.mapper;

import com.nest.entity.Favorite;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 收藏 Mapper */
@Mapper
public interface FavoriteMapper {

    int insert(Favorite favorite);

    int deleteByTenantAndHouse(@Param("tenantId") Long tenantId, @Param("houseId") Long houseId);

    java.util.List<Favorite> selectByTenant(@Param("tenantId") Long tenantId);

    Favorite selectByTenantAndHouse(@Param("tenantId") Long tenantId, @Param("houseId") Long houseId);
}
