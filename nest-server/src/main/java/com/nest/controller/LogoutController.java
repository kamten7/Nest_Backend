package com.nest.controller;

import com.nest.common.Result;
import com.nest.constant.JwtConstant;
import com.nest.service.impl.TokenBlacklistService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(name = "认证", description = "登出 / JWT 吊销")
public class LogoutController {

    private final TokenBlacklistService tokenBlacklistService;

    @PostMapping("/user/auth/logout")
    @Operation(summary = "租客登出")
    public Result<Void> userLogout(
            @RequestHeader(value = JwtConstant.USER_TOKEN_NAME, required = false) String token) {
        tokenBlacklistService.revokeUser(token);
        return Result.successMsg("已退出登录");
    }

    @PostMapping("/admin/auth/logout")
    @Operation(summary = "房东登出")
    public Result<Void> adminLogout(
            @RequestHeader(value = JwtConstant.ADMIN_TOKEN_NAME, required = false) String token) {
        tokenBlacklistService.revokeAdmin(token);
        return Result.successMsg("已退出登录");
    }
}
