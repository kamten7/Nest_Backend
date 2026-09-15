package com.nest.controller.user;

import com.nest.common.BaseContext;
import com.nest.common.Result;
import com.nest.dto.TenantLoginDTO;
import com.nest.dto.TenantProfileDTO;
import com.nest.dto.TenantRegisterDTO;
import com.nest.service.TenantService;
import com.nest.vo.TenantLoginVO;
import com.nest.vo.TenantProfileVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * 租客端认证与个人信息接口。
 */
@Slf4j
@RestController
@RequestMapping("/user/tenant")
@RequiredArgsConstructor
@Tag(name = "租客端-认证", description = "租客登录/注册/个人信息接口")
public class TenantController {

    private final TenantService tenantService;

    /**
     * 租客登录（微信 code 或手机号）。
     */
    @PostMapping("/login")
    @Operation(summary = "租客登录", description = "微信 code 登录或手机号登录")
    public Result<TenantLoginVO> login(@Valid @RequestBody TenantLoginDTO dto) {
        log.info("租客登录请求: code={}, phone={}", dto.getCode(), dto.getPhone());
        TenantLoginVO vo = tenantService.login(dto);
        return Result.success("登录成功", vo);
    }

    /**
     * 租客注册。
     */
    @PostMapping("/register")
    @Operation(summary = "租客注册", description = "手机号注册")
    public Result<TenantLoginVO> register(@Valid @RequestBody TenantRegisterDTO dto) {
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
     * 查询个人信息（昵称 / 头像 / 手机号 / 性别）。
     */
    @GetMapping("/profile")
    @Operation(summary = "查询个人信息", description = "手机号是「确认租房」的前置条件，未绑定需先绑定")
    public Result<TenantProfileVO> getProfile() {
        return Result.success(tenantService.getProfile());
    }

    /**
     * 更新个人信息（所有字段可选，只更新传了的字段）。
     */
    @PutMapping("/profile")
    @Operation(summary = "更新个人信息",
            description = "昵称/手机号/头像/性别均可选；手机号当前只校验格式长度，短信验证码校验为后续上线项")
    public Result<Void> updateProfile(@Valid @RequestBody TenantProfileDTO dto) {
        log.info("租客更新个人信息: phone={}, nickname={}, gender={}", dto.getPhone(), dto.getNickname(), dto.getGender());
        tenantService.updateProfile(dto);
        return Result.successMsg("个人信息更新成功");
    }

    /**
     * 上传头像到「用户头像专用 bucket」，并直接写回租客资料。
     */
    @PostMapping("/avatar")
    @Operation(summary = "上传头像", description = "上传到头像专用 bucket，成功后直接更新资料，返回头像 URL")
    public Result<String> uploadAvatar(@RequestParam("file") MultipartFile file) {
        log.info("租客头像上传: name={}, size={}", file.getOriginalFilename(), file.getSize());
        String url = tenantService.uploadAvatar(file);
        return Result.success("上传成功", url);
    }
}
