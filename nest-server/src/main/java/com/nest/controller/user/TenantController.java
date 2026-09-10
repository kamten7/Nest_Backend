package com.nest.controller.user;

import com.nest.common.BaseContext;
import com.nest.common.Result;
import com.nest.dto.TenantLoginDTO;
import com.nest.dto.TenantProfileDTO;
import com.nest.dto.TenantRegisterDTO;
import com.nest.service.TenantService;
import com.nest.vo.TenantLoginVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 租客端认证接口。
 */
@Slf4j
@RestController
@RequestMapping("/user/tenant")
@RequiredArgsConstructor
@Tag(name = "租客端-认证", description = "租客登录/注册接口")
public class TenantController {

    private final TenantService tenantService;

    /**
     * 租客登录（微信 code 或手机号）。
     */
    @PostMapping("/login")
    @Operation(summary = "租客登录", description = "微信 code 登录或手机号登录")
    public Result<TenantLoginVO> login(@RequestBody TenantLoginDTO dto) {
        log.info("租客登录请求: code={}, phone={}", dto.getCode(), dto.getPhone());
        TenantLoginVO vo = tenantService.login(dto);
        return Result.success("登录成功", vo);
    }

    /**
     * 租客注册。
     */
    @PostMapping("/register")
    @Operation(summary = "租客注册", description = "手机号注册")
    public Result<TenantLoginVO> register(@RequestBody TenantRegisterDTO dto) {
        log.info("租客注册请求: phone={}", dto.getPhone());
        TenantLoginVO vo = tenantService.register(dto);
        return Result.success("注册成功", vo);
    }

    /**
     * 获取当前登录租客信息（验证 JWT 拦截器）。
     */
    @GetMapping("/me")
    @Operation(summary = "获取当前租客信息")
    public Result<String> me() {
        Long id = BaseContext.getCurrentId();
        String type = BaseContext.getCurrentType();
        return Result.success("当前登录: id=" + id + ", type=" + type);
    }

    /**
     * 完善个人信息（微信登录后填写/更新手机号等）。
     */
    @PutMapping("/profile")
    @Operation(summary = "完善个人信息", description = "微信登录后填写/更新手机号等个人信息")
    public Result<Void> updateProfile(@RequestBody TenantProfileDTO dto) {
        log.info("租客完善个人信息请求: phone={}", dto.getPhone());
        tenantService.updateProfile(dto);
        return Result.successMsg("个人信息更新成功");
    }
}
