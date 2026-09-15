package com.nest.mapper;

import com.nest.entity.Appointment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 预约 Mapper */
@Mapper
public interface AppointmentMapper {

    int insert(Appointment appointment);

    int updateStatus(@Param("id") Long id,
                     @Param("status") Integer status,
                     @Param("cancelReason") String cancelReason);

    Appointment selectById(@Param("id") Long id);

    List<Appointment> selectByTenant(@Param("tenantId") Long tenantId);

    List<Appointment> selectByLandlord(@Param("landlordId") Long landlordId,
                                       @Param("status") Integer status);

    Appointment selectActiveByTenantAndHouse(@Param("tenantId") Long tenantId,
                                             @Param("houseId") Long houseId);
}
