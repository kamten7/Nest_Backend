package com.nest.service;

import com.nest.common.PageResult;
import com.nest.dto.AppointmentCreateDTO;
import com.nest.vo.AppointmentVO;

/** 预约服务接口 */
public interface AppointmentService {

    /** 创建预约（租客） */
    Long create(AppointmentCreateDTO dto);

    /** 我的预约列表（租客） */
    PageResult<AppointmentVO> myList(Integer page, Integer pageSize);

    /** 取消预约（租客，只能取消自己的） */
    void cancelByTenant(Long appointmentId, String reason);

    /** 房东收到的预约列表（可按状态筛选） */
    PageResult<AppointmentVO> landlordList(Integer status, Integer page, Integer pageSize);

    /** 确认预约（房东） */
    void confirm(Long appointmentId);

    /** 完成看房（房东） */
    void complete(Long appointmentId);

    /** 取消预约（房东） */
    void cancelByLandlord(Long appointmentId, String reason);
}
