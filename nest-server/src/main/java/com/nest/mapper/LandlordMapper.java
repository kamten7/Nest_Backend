package com.nest.mapper;

import com.nest.entity.Landlord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 房东 Mapper。
 */
@Mapper
public interface LandlordMapper {

    /** 根据手机号查询房东 */
    Landlord selectByPhone(@Param("phone") String phone);

    /** 根据 ID 查询 */
    Landlord selectById(@Param("id") Long id);

    /** 注册新房东 */
    int insert(Landlord landlord);
}
