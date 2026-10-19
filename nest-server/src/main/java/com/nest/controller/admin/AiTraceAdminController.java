package com.nest.controller.admin;

import com.nest.common.Result;
import com.nest.entity.AiTrace;
import com.nest.mapper.AiTraceMapper;
import com.nest.service.impl.PlatformAdminGuard;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin/ai")
@RequiredArgsConstructor
@Tag(name = "房东端-AI 运营", description = "AI 轨迹、token、延迟与工具调用统计")
public class AiTraceAdminController {

    private final AiTraceMapper aiTraceMapper;
    private final PlatformAdminGuard platformAdminGuard;

    @GetMapping("/traces")
    @Operation(summary = "最近 AI 轨迹")
    public Result<List<AiTrace>> recent(@RequestParam(defaultValue = "20") Integer limit) {
        platformAdminGuard.require();
        int size = limit == null ? 20 : Math.max(1, Math.min(limit, 100));
        return Result.success(aiTraceMapper.selectRecent(size));
    }

    @GetMapping("/stats")
    @Operation(summary = "AI 聚合统计", description = "默认统计最近 7 天：对话数/成功数/平均延迟/token")
    public Result<Map<String, Object>> stats(@RequestParam(defaultValue = "7") Integer days) {
        platformAdminGuard.require();
        int window = days == null ? 7 : Math.max(1, Math.min(days, 90));
        return Result.success(aiTraceMapper.statsSince(LocalDate.now().minusDays(window).atStartOfDay()));
    }
}
