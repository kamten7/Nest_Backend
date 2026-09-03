package com.nest.mapper;

import com.nest.entity.Tenant;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 租客 Mapper。
 */
@Mapper
public interface TenantMapper {

    /** 根据 openid 查询租客 */
    Tenant selectByOpenid(@Param("openid") String openid);

    /** 根据手机号查询 */
    Tenant selectByPhone(@Param("phone") String phone);

    /** 根据 ID 查询 */
    Tenant selectById(@Param("id") Long id);

    /** 注册新租客 */
    int insert(Tenant tenant);

    /** 更新租客信息 */
    int update(Tenant tenant);
}
