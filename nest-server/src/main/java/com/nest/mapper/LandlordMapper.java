package com.nest.mapper;

import com.nest.entity.Landlord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;

/**
 * 房东 Mapper。
 */
@Mapper
public interface LandlordMapper {

    /** 根据手机号查询房东 */
    Landlord selectByPhone(@Param("phone") String phone);

    /** 根据 ID 查询 */
    Landlord selectById(@Param("id") Long id);

    /** 批量根据 ID 查询（只取昵称/头像所需字段，用于避免 N+1） */
    List<Landlord> selectByIds(@Param("ids") Collection<Long> ids);

    /** 注册新房东 */
    int insert(Landlord landlord);

    /** 更新密码（用于 MD5 自动升级 BCrypt） */
    int updatePassword(@Param("id") Long id, @Param("password") String password);
}
