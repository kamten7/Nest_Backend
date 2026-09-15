package com.nest.mapper;

import com.nest.entity.Tenant;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;

/** 租客 Mapper */
@Mapper
public interface TenantMapper {

    Tenant selectByOpenid(@Param("openid") String openid);

    Tenant selectByPhone(@Param("phone") String phone);

    Tenant selectById(@Param("id") Long id);

    List<Tenant> selectByIds(@Param("ids") Collection<Long> ids);

    int insert(Tenant tenant);

    int update(Tenant tenant);
}
