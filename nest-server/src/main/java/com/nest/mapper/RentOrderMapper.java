package com.nest.mapper;

import com.nest.entity.RentOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 租房订单 Mapper。
 */
@Mapper
public interface RentOrderMapper {

    /** 新增订单，回填 ID */
    int insert(RentOrder order);

    /** 按 ID 查询订单 */
    RentOrder selectById(@Param("id") Long id);

    /** 某租客对某房源进行中的订单（status 1/2/3） */
    RentOrder selectActiveByTenantAndHouse(@Param("tenantId") Long tenantId,
                                           @Param("houseId") Long houseId);

    /** 查询租客订单（分页由 PageHelper 处理），可按状态过滤 */
    List<RentOrder> selectByTenant(@Param("tenantId") Long tenantId,
                                   @Param("status") Integer status);

    /** 查询房东名下订单（分页由 PageHelper 处理），可按状态过滤 */
    List<RentOrder> selectByLandlord(@Param("landlordId") Long landlordId,
                                     @Param("status") Integer status);

    /** 按状态查询（定时任务扫） */
    List<RentOrder> selectByStatus(@Param("status") Integer status);

    /** 更新订单状态 */
    int updateStatus(@Param("id") Long id, @Param("status") Integer status);

    /** 更新缴费进度：next_due_period 前移 + 已缴月数 */
    int advancePeriod(@Param("id") Long id,
                      @Param("nextDuePeriod") String nextDuePeriod,
                      @Param("paidMonths") Integer paidMonths);
}
