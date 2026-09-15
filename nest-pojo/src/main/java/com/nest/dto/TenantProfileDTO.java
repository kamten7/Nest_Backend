package com.nest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 租客完善个人信息请求体（微信登录后填写/更新手机号等）。
 */
@Data
public class TenantProfileDTO {

    /** 手机号（必填） */
    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;
    /** 昵称（可选） */
    @Size(max = 50, message = "昵称长度不能超过 50 个字符")
    private String nickname;
    /** 头像 URL（可选） */
    @Size(max = 255, message = "头像地址过长")
    private String avatar;
}
