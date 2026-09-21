package com.nest.mapper;

import com.nest.entity.Appointment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 预约 Mapper */
@Mapper
public interface AppointmentMapper {

    int insert(Appointment appointment);

    /**
     * 条件更新预约状态（乐观锁）：仅当当前状态仍是 {@code expectedStatus} 时才改，
     * 影响行数为 0 表示已被并发修改，调用方据此拒绝本次流转。
     *
     * <p>没有这道守卫时，「租客取消」与「房东确认」并发会双双成功，后提交者把已取消覆盖成已确认。</p>
     */
    int updateStatus(@Param("id") Long id,
                     @Param("expectedStatus") Integer expectedStatus,
                     @Param("status") Integer status,
                     @Param("cancelReason") String cancelReason);

    Appointment selectById(@Param("id") Long id);

    List<Appointment> selectByTenant(@Param("tenantId") Long tenantId);

    List<Appointment> selectByLandlord(@Param("landlordId") Long landlordId,
                                       @Param("status") Integer status);

    Appointment selectActiveByTenantAndHouse(@Param("tenantId") Long tenantId,
                                             @Param("houseId") Long houseId);
}
