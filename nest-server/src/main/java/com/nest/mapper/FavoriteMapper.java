package com.nest.mapper;

import com.nest.entity.Favorite;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 收藏 Mapper。
 */
@Mapper
public interface FavoriteMapper {

    /** 添加收藏 */
    int insert(Favorite favorite);

    /** 取消收藏（按租客+房源） */
    int deleteByTenantAndHouse(@Param("tenantId") Long tenantId, @Param("houseId") Long houseId);

    /** 查询某租客的所有收藏（分页由 PageHelper 处理） */
    java.util.List<Favorite> selectByTenant(@Param("tenantId") Long tenantId);

    /** 查询某租客是否收藏了某房源（用于判断/防重复） */
    Favorite selectByTenantAndHouse(@Param("tenantId") Long tenantId, @Param("houseId") Long houseId);
}
