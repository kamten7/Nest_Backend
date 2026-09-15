package com.nest.controller.admin;

import com.nest.common.BaseContext;
import com.nest.common.Result;
import com.nest.dto.LandlordLoginDTO;
import com.nest.entity.Landlord;
import com.nest.mapper.LandlordMapper;
import com.nest.service.LandlordService;
import com.nest.vo.LandlordLoginVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 房东端认证接口。
 * 包含登录、获取当前登录房东信息等操作。
 */
@Slf4j
@RestController
@RequestMapping("/admin/landlord")
@RequiredArgsConstructor
@Tag(name = "房东端-认证", description = "房东登录相关接口")
public class LandlordController {

    private final LandlordService landlordService;
    private final LandlordMapper landlordMapper;

    /**
     * 房东登录。
     */
    @PostMapping("/login")
    @Operation(summary = "房东登录", description = "用手机号 + 密码登录，返回 JWT Token")
    public Result<LandlordLoginVO> login(@Valid @RequestBody LandlordLoginDTO dto) {
        log.info("房东登录请求: phone={}", dto.getPhone());
        LandlordLoginVO vo = landlordService.login(dto);
        return Result.success("登录成功", vo);
    }

    /**
     * 获取当前登录房东信息（个人中心用）。
     */
    @GetMapping("/me")
    @Operation(summary = "获取当前房东信息", description = "返回房东完整信息（姓名/手机号/头像等）")
    public Result<Landlord> me() {
        Long id = BaseContext.getCurrentId();
        Landlord landlord = landlordMapper.selectById(id);
        if (landlord == null) {
            return Result.error("房东不存在");
        }
        // 脱敏：不返回密码
        landlord.setPassword(null);
        return Result.success(landlord);
    }
}
