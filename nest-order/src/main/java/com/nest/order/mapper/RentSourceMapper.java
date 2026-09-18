package com.nest.order.mapper;

import com.nest.dto.HouseBriefDTO;
import com.nest.dto.RentSourceDTO;
import com.nest.dto.TenantBriefDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;

/** 订单源数据 Mapper —— 读 appointment / house / house_image。 */
@Mapper
public interface RentSourceMapper {

    /** 确认租房时一次性取「预约 + 房源 + 封面」 */
    RentSourceDTO selectSourceByAppointmentId(@Param("appointmentId") Long appointmentId);

    /** 批量取房源标题/封面/房东名（订单列表/详情用，避免逐条查询的 N+1） */
    List<HouseBriefDTO> selectHouseBriefByIds(@Param("houseIds") Collection<Long> houseIds);

    /** 批量取租客昵称（房东侧订单列表/详情用）。 */
    List<TenantBriefDTO> selectTenantBriefByIds(@Param("tenantIds") Collection<Long> tenantIds);

    /** 取租客绑定的手机号（确认租房的前置校验用）。 */
    String selectTenantPhone(@Param("tenantId") Long tenantId);
}
