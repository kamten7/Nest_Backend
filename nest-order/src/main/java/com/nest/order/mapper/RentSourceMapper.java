package com.nest.order.mapper;

import com.nest.dto.HouseBriefDTO;
import com.nest.dto.RentSourceDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;

/**
 * 订单源数据 Mapper —— 读 appointment / house / house_image。
 *
 * <p>为什么放在本模块而不是复用 nest-server 的 AppointmentMapper / HouseMapper：
 * 那两个 Mapper 属于 nest-server，而 nest-server 依赖 nest-order，反向引用会形成循环依赖。
 * 这里只取订单真正需要的列（房东 ID、租金、押金、标题、封面），不做任何业务写操作。
 */
@Mapper
public interface RentSourceMapper {

    /** 确认租房时一次性取「预约 + 房源 + 封面」 */
    RentSourceDTO selectSourceByAppointmentId(@Param("appointmentId") Long appointmentId);

    /** 批量取房源标题与封面（订单列表/详情用，避免逐条查询的 N+1） */
    List<HouseBriefDTO> selectHouseBriefByIds(@Param("houseIds") Collection<Long> houseIds);
}
