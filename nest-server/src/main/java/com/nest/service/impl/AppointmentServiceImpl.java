package com.nest.service.impl;

import com.alibaba.fastjson2.JSONObject;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.nest.common.BaseContext;
import com.nest.common.PageResult;
import com.nest.constant.AppointmentStatus;
import com.nest.constant.MessageConstant;
import com.nest.dto.AppointmentCreateDTO;
import com.nest.entity.Appointment;
import com.nest.entity.House;
import com.nest.entity.HouseImage;
import com.nest.entity.Landlord;
import com.nest.entity.Tenant;
import com.nest.exception.BusinessException;
import com.nest.mapper.AppointmentMapper;
import com.nest.mapper.HouseImageMapper;
import com.nest.mapper.HouseMapper;
import com.nest.mapper.LandlordMapper;
import com.nest.mapper.TenantMapper;
import com.nest.service.AppointmentService;
import com.nest.vo.AppointmentVO;
import com.nest.websocket.ChatWebSocketServer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 预约服务实现 —— 状态机 + 权限校验 + 通知推送。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AppointmentServiceImpl implements AppointmentService {

    private final AppointmentMapper appointmentMapper;
    private final HouseMapper houseMapper;
    private final HouseImageMapper houseImageMapper;
    private final LandlordMapper landlordMapper;
    private final TenantMapper tenantMapper;

    // ==================== 租客端 ====================

    /**
     * 创建预约看房请求
     * @param dto 预约创建参数
     * @return 预约 ID
     */
    @Override
    @Transactional
    public Long create(AppointmentCreateDTO dto) {
        Long tenantId = BaseContext.getCurrentId();

        // 1. 校验房源存在且上架（已下架的房源不允许预约）
        House house = houseMapper.selectById(dto.getHouseId());
        if (house == null || house.getStatus() == 0) {
            throw new BusinessException(MessageConstant.HOUSE_NOT_FOUND);
        }

        // 2. 防重复预约（appointment 表无唯一约束，靠应用层先查后插）
        //    查询"有效预约"：状态为 待确认/已确认/已看房/已成交（已取消不算，可重新约）
        Appointment active = appointmentMapper.selectActiveByTenantAndHouse(tenantId, dto.getHouseId());
        if (active != null) {
            throw new BusinessException(MessageConstant.APPOINTMENT_DUPLICATE);
        }

        // 3. 落库（初始状态 PENDING 待确认）
        Appointment appointment = Appointment.builder()
                .tenantId(tenantId)               // 发起预约的租客
                .houseId(dto.getHouseId())
                .landlordId(house.getLandlordId()) // 房东 ID 从房源带出，不信任前端
                .contactPhone(dto.getContactPhone())
                .appointmentTime(dto.getAppointmentTime())
                .remark(dto.getRemark())
                .status(AppointmentStatus.PENDING) // 预约创建后是"待确认"
                .build();
        appointmentMapper.insert(appointment);
        log.info("创建预约: id={}, tenantId={}, houseId={}", appointment.getId(), tenantId, dto.getHouseId());

        // 4. 推通知给房东（Web 端右上角弹窗）
        pushNotification("landlord", house.getLandlordId(),
                "appointment", "有新的预约看房请求",
                "租客预约了「" + house.getTitle() + "」，点击查看");
        return appointment.getId();   // 返回新预约 ID
    }

    /**
     * 查询租客自己的预约列表
     * @param page 页码，默认第1页
     * @param pageSize 每页数量，默认10条
     * @return 分页结果，包含总记录数和预约列表VO
     */
    @Override
    public PageResult<AppointmentVO> myList(Integer page, Integer pageSize) {
        Long tenantId = BaseContext.getCurrentId();
        PageHelper.startPage(page, pageSize);
        List<Appointment> list = appointmentMapper.selectByTenant(tenantId);
        PageInfo<Appointment> pageInfo = new PageInfo<>(list);
        return PageResult.of(pageInfo.getTotal(), buildVOs(list));
    }

    /**
     * 租客取消预约看房请求
     * @param appointmentId 预约 ID
     * @param reason 取消原因
     */
    @Override
    public void cancelByTenant(Long appointmentId, String reason) {
        Long tenantId = BaseContext.getCurrentId();
        Appointment appointment = getAndCheck(appointmentId);

        // 权限：只能取消自己的
        if (!appointment.getTenantId().equals(tenantId)) {
            throw new BusinessException(MessageConstant.APPOINTMENT_NOT_OWNER);
        }
        // 状态：待确认/已确认 才能取消
        if (appointment.getStatus() != AppointmentStatus.PENDING
                && appointment.getStatus() != AppointmentStatus.CONFIRMED) {
            throw new BusinessException(MessageConstant.APPOINTMENT_STATUS_INVALID);
        }

        appointmentMapper.updateStatus(appointmentId, AppointmentStatus.CANCELLED, reason);
        log.info("租客取消预约: id={}, tenantId={}", appointmentId, tenantId);

        // 通知房东
        pushNotification("landlord", appointment.getLandlordId(),
                "appointment", "预约已被取消",
                "租客取消了看房预约");
    }

    // ==================== 房东端 ====================

    @Override
    public PageResult<AppointmentVO> landlordList(Integer status, Integer page, Integer pageSize) {
        Long landlordId = BaseContext.getCurrentId();
        PageHelper.startPage(page, pageSize);
        List<Appointment> list = appointmentMapper.selectByLandlord(landlordId, status);
        PageInfo<Appointment> pageInfo = new PageInfo<>(list);
        return PageResult.of(pageInfo.getTotal(), buildVOs(list));
    }

    @Override
    public void confirm(Long appointmentId) {
        Long landlordId = BaseContext.getCurrentId();
        Appointment appointment = getAndCheck(appointmentId);
        checkLandlordOwns(appointment, landlordId);

        // 待确认 → 已确认（状态机：只有"待确认"状态才能被确认，防止已确认/已取消被重复操作）
        if (appointment.getStatus() != AppointmentStatus.PENDING) {
            throw new BusinessException(MessageConstant.APPOINTMENT_STATUS_INVALID);
        }
        appointmentMapper.updateStatus(appointmentId, AppointmentStatus.CONFIRMED, null);   // 状态流转为已确认
        log.info("房东确认预约: id={}, landlordId={}", appointmentId, landlordId);

        // 通知租客（预约被确认，实时推给租客端）
        pushNotification("tenant", appointment.getTenantId(),
                "appointment", "预约已确认",
                "房东已确认您的看房预约");
    }

    @Override
    public void complete(Long appointmentId) {
        Long landlordId = BaseContext.getCurrentId();
        Appointment appointment = getAndCheck(appointmentId);
        checkLandlordOwns(appointment, landlordId);

        // 已确认 → 已看房（状态机：只有"已确认"状态才能标记看房完成）
        if (appointment.getStatus() != AppointmentStatus.CONFIRMED) {
            throw new BusinessException(MessageConstant.APPOINTMENT_STATUS_INVALID);
        }
        appointmentMapper.updateStatus(appointmentId, AppointmentStatus.VISITED, null);   // 状态流转为已看房
        log.info("房东完成看房: id={}, landlordId={}", appointmentId, landlordId);
    }

    @Override
    public void cancelByLandlord(Long appointmentId, String reason) {
        Long landlordId = BaseContext.getCurrentId();
        Appointment appointment = getAndCheck(appointmentId);
        checkLandlordOwns(appointment, landlordId);

        // 状态机：只有"待确认/已确认"状态才能取消，已看房/已成交/已取消是终态不可再取消
        if (appointment.getStatus() != AppointmentStatus.PENDING
                && appointment.getStatus() != AppointmentStatus.CONFIRMED) {
            throw new BusinessException(MessageConstant.APPOINTMENT_STATUS_INVALID);
        }
        appointmentMapper.updateStatus(appointmentId, AppointmentStatus.CANCELLED, reason);   // 状态流转为已取消并记录原因
        log.info("房东取消预约: id={}, landlordId={}", appointmentId, landlordId);

        // 通知租客（预约被取消）
        pushNotification("tenant", appointment.getTenantId(),
                "appointment", "预约已被取消",
                "房东取消了看房预约");
    }

    // ==================== 内部方法 ====================

    /** 查询预约并校验存在 */
    private Appointment getAndCheck(Long id) {
        Appointment appointment = appointmentMapper.selectById(id);
        if (appointment == null) {
            throw new BusinessException(MessageConstant.APPOINTMENT_NOT_FOUND);
        }
        return appointment;
    }

    /** 校验预约属于当前房东 */
    private void checkLandlordOwns(Appointment appointment, Long landlordId) {
        if (!appointment.getLandlordId().equals(landlordId)) {
            throw new BusinessException(MessageConstant.APPOINTMENT_NOT_OWNER);
        }
    }

    /** 组装 VO 列表（循环查房源/房东/租客，数据量小可接受） */
    private List<AppointmentVO> buildVOs(List<Appointment> appointments) {
        if (appointments == null || appointments.isEmpty()) {
            return Collections.emptyList();
        }
        List<AppointmentVO> vos = new ArrayList<>();
        for (Appointment a : appointments) {
            AppointmentVO vo = new AppointmentVO();
            vo.setId(a.getId());
            vo.setHouseId(a.getHouseId());
            vo.setLandlordId(a.getLandlordId());
            vo.setTenantId(a.getTenantId());
            vo.setContactPhone(a.getContactPhone());
            vo.setAppointmentTime(a.getAppointmentTime());
            vo.setRemark(a.getRemark());
            vo.setStatus(a.getStatus());
            vo.setStatusText(AppointmentStatus.text(a.getStatus()));
            vo.setCancelReason(a.getCancelReason());
            vo.setCreateTime(a.getCreateTime());

            // 房源信息
            House house = houseMapper.selectById(a.getHouseId());
            if (house != null) {
                vo.setHouseTitle(house.getTitle());
                List<HouseImage> images = houseImageMapper.selectByHouseId(house.getId());
                if (images != null && !images.isEmpty()) {
                    vo.setHouseCover(images.get(0).getUrl());
                }
            }
            // 房东名
            Landlord landlord = landlordMapper.selectById(a.getLandlordId());
            if (landlord != null) {
                vo.setLandlordName(landlord.getName());
            }
            // 租客名
            Tenant tenant = tenantMapper.selectById(a.getTenantId());
            if (tenant != null) {
                vo.setTenantName(tenant.getNickname() != null ? tenant.getNickname() : "租客" + tenant.getId());
            }
            vos.add(vo);
        }
        return vos;
    }

    /** 通过 WebSocket 推通知（复用 NotificationController 的 JSON 结构，前端 NotificationHost 可解析） */
    private void pushNotification(
            String userType,
            Long userId,
            String type,
            String title,
            String content
    ) {
        JSONObject msg = new JSONObject();
        msg.put("type", type);
        msg.put("title", title);
        msg.put("content", content);
        ChatWebSocketServer.sendToUser(userType, userId, msg.toJSONString());
    }
}
