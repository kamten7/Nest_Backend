package com.nest.service.impl;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.nest.chat.push.PushService;
import com.nest.common.BaseContext;
import com.nest.common.PageParam;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 预约服务实现 —— 状态机 + 权限校验 + 通知推送。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AppointmentServiceImpl implements AppointmentService {

    private final AppointmentMapper appointmentMapper;
    private final HouseMapper houseMapper;
    private final HouseImageMapper houseImageMapper;
    private final LandlordMapper landlordMapper;
    private final TenantMapper tenantMapper;
    private final PushService pushService;

    /** 创建预约看房请求。 */
    @Override
    @Transactional
    public Long create(AppointmentCreateDTO dto) {
        Long tenantId = BaseContext.getCurrentId();

        House house = houseMapper.selectById(dto.getHouseId());
        if (house == null) {
            throw new BusinessException(MessageConstant.HOUSE_NOT_FOUND);
        }
        // 非上架状态(下架/在租中)不可预约
        if (house.getStatus() != 1) {
            throw new BusinessException(MessageConstant.HOUSE_NOT_RENTABLE);
        }

        Appointment active = appointmentMapper.selectActiveByTenantAndHouse(tenantId, dto.getHouseId());
        if (active != null) {
            throw new BusinessException(MessageConstant.APPOINTMENT_DUPLICATE);
        }

        Appointment appointment = Appointment.builder()
                .tenantId(tenantId)
                .houseId(dto.getHouseId())
                .landlordId(house.getLandlordId())
                .contactPhone(dto.getContactPhone())
                .appointmentTime(dto.getAppointmentTime())
                .remark(dto.getRemark())
                .status(AppointmentStatus.PENDING)
                .build();
        appointmentMapper.insert(appointment);
        log.info("创建预约: id={}, tenantId={}, houseId={}", appointment.getId(), tenantId, dto.getHouseId());

        pushNotification("landlord", house.getLandlordId(),
                "appointment", "有新的预约看房请求",
                "租客预约了「" + house.getTitle() + "」，点击查看");
        return appointment.getId();
    }

    /** 查询租客自己的预约列表（分页）。 */
    @Override
    public PageResult<AppointmentVO> myList(Integer page, Integer pageSize) {
        Long tenantId = BaseContext.getCurrentId();
        PageHelper.startPage(PageParam.pageOf(page), PageParam.pageSizeOf(pageSize));
        List<Appointment> list = appointmentMapper.selectByTenant(tenantId);
        PageInfo<Appointment> pageInfo = new PageInfo<>(list);
        return PageResult.of(pageInfo.getTotal(), buildVOs(list));
    }

    /** 租客取消预约。 */
    @Override
    @Transactional
    public void cancelByTenant(Long appointmentId, String reason) {
        Long tenantId = BaseContext.getCurrentId();
        Appointment appointment = getAndCheck(appointmentId);

        if (!appointment.getTenantId().equals(tenantId)) {
            throw new BusinessException(MessageConstant.APPOINTMENT_NOT_OWNER);
        }
        if (appointment.getStatus() != AppointmentStatus.PENDING
                && appointment.getStatus() != AppointmentStatus.CONFIRMED) {
            throw new BusinessException(MessageConstant.APPOINTMENT_STATUS_INVALID);
        }

        // 期望状态取「刚读到的值」：并发下若已被房东流转（确认/完成），影响行数为 0 ⇒ 拒绝本次取消。
        int rows = appointmentMapper.updateStatus(appointmentId, appointment.getStatus(),
                AppointmentStatus.CANCELLED, reason);
        if (rows == 0) {
            throw new BusinessException(MessageConstant.APPOINTMENT_STATUS_INVALID);
        }
        log.info("租客取消预约: id={}, tenantId={}", appointmentId, tenantId);

        pushNotification("landlord", appointment.getLandlordId(),
                "appointment", "预约已被取消", "租客取消了看房预约");
    }

    /** 房东查看预约列表（可按状态筛选，分页）。 */
    @Override
    public PageResult<AppointmentVO> landlordList(Integer status, Integer page, Integer pageSize) {
        Long landlordId = BaseContext.getCurrentId();
        PageHelper.startPage(PageParam.pageOf(page), PageParam.pageSizeOf(pageSize));
        List<Appointment> list = appointmentMapper.selectByLandlord(landlordId, status);
        PageInfo<Appointment> pageInfo = new PageInfo<>(list);
        return PageResult.of(pageInfo.getTotal(), buildVOs(list));
    }

    /** 房东确认预约（待确认→已确认）。 */
    @Override
    @Transactional
    public void confirm(Long appointmentId) {
        Long landlordId = BaseContext.getCurrentId();
        Appointment appointment = getAndCheck(appointmentId);
        checkLandlordOwns(appointment, landlordId);

        if (appointment.getStatus() != AppointmentStatus.PENDING) {
            throw new BusinessException(MessageConstant.APPOINTMENT_STATUS_INVALID);
        }
        // 期望状态钉死 PENDING：并发下租客可能已取消，影响行数为 0 ⇒ 拒绝，避免把「已取消」复活成「已确认」。
        int rows = appointmentMapper.updateStatus(appointmentId, AppointmentStatus.PENDING,
                AppointmentStatus.CONFIRMED, null);
        if (rows == 0) {
            throw new BusinessException(MessageConstant.APPOINTMENT_STATUS_INVALID);
        }
        log.info("房东确认预约: id={}, landlordId={}", appointmentId, landlordId);

        pushNotification("tenant", appointment.getTenantId(),
                "appointment", "预约已确认", "房东已确认您的看房预约");
    }

    /** 房东标记看房完成（已确认→已看房）。 */
    @Override
    @Transactional
    public void complete(Long appointmentId) {
        Long landlordId = BaseContext.getCurrentId();
        Appointment appointment = getAndCheck(appointmentId);
        checkLandlordOwns(appointment, landlordId);

        if (appointment.getStatus() != AppointmentStatus.CONFIRMED) {
            throw new BusinessException(MessageConstant.APPOINTMENT_STATUS_INVALID);
        }
        // 期望状态钉死 CONFIRMED：并发下租客可能已取消，影响行数为 0 ⇒ 拒绝。
        int rows = appointmentMapper.updateStatus(appointmentId, AppointmentStatus.CONFIRMED,
                AppointmentStatus.VISITED, null);
        if (rows == 0) {
            throw new BusinessException(MessageConstant.APPOINTMENT_STATUS_INVALID);
        }
        log.info("房东完成看房: id={}, landlordId={}", appointmentId, landlordId);
    }

    /** 房东取消预约（待确认/已确认→已取消）。 */
    @Override
    @Transactional
    public void cancelByLandlord(Long appointmentId, String reason) {
        Long landlordId = BaseContext.getCurrentId();
        Appointment appointment = getAndCheck(appointmentId);
        checkLandlordOwns(appointment, landlordId);

        if (appointment.getStatus() != AppointmentStatus.PENDING
                && appointment.getStatus() != AppointmentStatus.CONFIRMED) {
            throw new BusinessException(MessageConstant.APPOINTMENT_STATUS_INVALID);
        }
        // 期望状态取「刚读到的值」：并发下可能已被租客取消或已看房，影响行数为 0 ⇒ 拒绝。
        int rows = appointmentMapper.updateStatus(appointmentId, appointment.getStatus(),
                AppointmentStatus.CANCELLED, reason);
        if (rows == 0) {
            throw new BusinessException(MessageConstant.APPOINTMENT_STATUS_INVALID);
        }
        log.info("房东取消预约: id={}, landlordId={}", appointmentId, landlordId);

        pushNotification("tenant", appointment.getTenantId(),
                "appointment", "预约已被取消", "房东取消了看房预约");
    }

    private Appointment getAndCheck(Long id) {
        Appointment appointment = appointmentMapper.selectById(id);
        if (appointment == null) {
            throw new BusinessException(MessageConstant.APPOINTMENT_NOT_FOUND);
        }
        return appointment;
    }

    private void checkLandlordOwns(Appointment appointment, Long landlordId) {
        if (!appointment.getLandlordId().equals(landlordId)) {
            throw new BusinessException(MessageConstant.APPOINTMENT_NOT_OWNER);
        }
    }

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

            House house = houseMapper.selectById(a.getHouseId());
            if (house != null) {
                vo.setHouseTitle(house.getTitle());
                List<HouseImage> images = houseImageMapper.selectByHouseId(house.getId());
                if (images != null && !images.isEmpty()) {
                    vo.setHouseCover(images.get(0).getUrl());
                }
            }
            Landlord landlord = landlordMapper.selectById(a.getLandlordId());
            if (landlord != null) {
                vo.setLandlordName(landlord.getName());
            }
            Tenant tenant = tenantMapper.selectById(a.getTenantId());
            if (tenant != null) {
                vo.setTenantName(tenant.getNickname() != null ? tenant.getNickname() : "租客" + tenant.getId());
            }
            vos.add(vo);
        }
        return vos;
    }

    private void pushNotification(String userType, Long userId, String type, String title, String content) {
        pushService.pushNotice(userType, userId, type, title, content);
    }
}
