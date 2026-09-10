package com.nest.controller.admin;

import com.nest.common.PageResult;
import com.nest.common.Result;
import com.nest.service.AppointmentService;
import com.nest.vo.AppointmentVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** 房东端预约管理接口。 */
@Slf4j
@RestController
@RequestMapping("/admin/appointment")
@RequiredArgsConstructor
@Tag(name = "房东端-预约管理", description = "查看收到的预约/确认/完成看房/取消")
public class AppointmentAdminController {

    private final AppointmentService appointmentService;

    /** 收到的预约列表（可按状态筛选）。 */
    @GetMapping
    @Operation(summary = "收到的预约")
    public Result<PageResult<AppointmentVO>> list(
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize
    ) {
        log.info("房东查询预约: status={}, page={}", status, page);
        PageResult<AppointmentVO> result = appointmentService.landlordList(status, page, pageSize);
        return Result.success(result);
    }

    /** 确认预约。 */
    @PutMapping("/{id}/confirm")
    @Operation(summary = "确认预约")
    public Result<Void> confirm(@PathVariable Long id) {
        log.info("房东确认预约: id={}", id);
        appointmentService.confirm(id);
        return Result.successMsg("已确认预约");
    }

    /** 完成看房。 */
    @PutMapping("/{id}/complete")
    @Operation(summary = "完成看房")
    public Result<Void> complete(@PathVariable Long id) {
        log.info("房东完成看房: id={}", id);
        appointmentService.complete(id);
        return Result.successMsg("已完成看房");
    }

    /** 取消预约。 */
    @PutMapping("/{id}/cancel")
    @Operation(summary = "取消预约")
    public Result<Void> cancel(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body
    ) {
        String reason = body != null ? body.get("reason") : null;
        log.info("房东取消预约: id={}, reason={}", id, reason);
        appointmentService.cancelByLandlord(id, reason);
        return Result.successMsg("已取消预约");
    }
}
