package com.nest.mapper;

import com.nest.entity.Landlord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;

/** 房东 Mapper */
@Mapper
public interface LandlordMapper {

    Landlord selectByPhone(@Param("phone") String phone);

    Landlord selectById(@Param("id") Long id);

    List<Landlord> selectByIds(@Param("ids") Collection<Long> ids);

    int insert(Landlord landlord);

    int updatePassword(@Param("id") Long id, @Param("password") String password);
}
