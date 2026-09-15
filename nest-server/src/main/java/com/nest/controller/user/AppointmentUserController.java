package com.nest.controller.user;

import com.nest.common.PageResult;
import com.nest.common.Result;
import com.nest.dto.AppointmentCreateDTO;
import com.nest.service.AppointmentService;
import com.nest.vo.AppointmentVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 租客端预约接口。
 */
@Slf4j
@RestController
@RequestMapping("/user/appointment")
@RequiredArgsConstructor
@Tag(name = "租客端-预约", description = "创建预约/我的预约/取消预约")
public class AppointmentUserController {

    private final AppointmentService appointmentService;

    /**
     * 创建预约。
     */
    @PostMapping
    @Operation(summary = "创建预约", description = "租客预约看房，成功后通知房东")
    public Result<Long> create(@Valid @RequestBody AppointmentCreateDTO dto) {
        log.info("创建预约: houseId={}, time={}", dto.getHouseId(), dto.getAppointmentTime());
        Long id = appointmentService.create(dto);
        return Result.success("预约成功，请等待房东确认", id);
    }

    /**
     * 我的预约列表。
     */
    @GetMapping("/my")
    @Operation(summary = "我的预约", description = "分页查询当前租客的预约列表")
    public Result<PageResult<AppointmentVO>> myList(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        PageResult<AppointmentVO> result = appointmentService.myList(page, pageSize);
        return Result.success(result);
    }

    /**
     * 取消预约。
     */
    @PutMapping("/{id}/cancel")
    @Operation(summary = "取消预约", description = "租客取消自己的预约（待确认/已确认状态可取消）")
    public Result<Void> cancel(@PathVariable Long id,
                               @RequestBody(required = false) Map<String, String> body) {
        String reason = body != null ? body.get("reason") : null;
        log.info("租客取消预约: id={}", id);
        appointmentService.cancelByTenant(id, reason);
        return Result.successMsg("已取消预约");
    }
}
