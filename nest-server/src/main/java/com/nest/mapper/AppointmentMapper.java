package com.nest.mapper;

import com.nest.entity.Appointment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 预约 Mapper。
 */
@Mapper
public interface AppointmentMapper {

    /** 创建预约，回填 ID */
    int insert(Appointment appointment);

    /** 更新状态（附取消原因） */
    int updateStatus(@Param("id") Long id,
                     @Param("status") Integer status,
                     @Param("cancelReason") String cancelReason);

    /** 按 ID 查询 */
    Appointment selectById(@Param("id") Long id);

    /** 查询某租客的预约（分页由 PageHelper 处理） */
    List<Appointment> selectByTenant(@Param("tenantId") Long tenantId);

    /** 查询某房东收到的预约，可选按状态过滤 */
    List<Appointment> selectByLandlord(@Param("landlordId") Long landlordId,
                                       @Param("status") Integer status);

    /** 查询某租客对某房源的有效预约（状态非取消），用于防重复 */
    Appointment selectActiveByTenantAndHouse(@Param("tenantId") Long tenantId,
                                             @Param("houseId") Long houseId);
}
