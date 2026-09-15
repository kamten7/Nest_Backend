package com.nest.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 租客登录请求体（微信小程序 code 登录 或 手机号登录）。
 */
@Data
public class TenantLoginDTO {

    /** 微信登录 code（wx.login 获取） */
    @Size(max = 128, message = "微信登录凭证长度不合法")
    private String code;
    /** 手机号（手机号登录用） */
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;
    /** 昵称 */
    @Size(max = 50, message = "昵称长度不能超过 50 个字符")
    private String nickname;
    /** 头像 URL */
    @Size(max = 255, message = "头像地址过长")
    private String avatar;

    /** 微信 code 与手机号至少提供一个（否则无法登录）。 */
    @AssertTrue(message = "请提供登录凭证（微信 code 或手机号）")
    public boolean isCredentialProvided() {
        return (code != null && !code.isBlank()) || (phone != null && !phone.isBlank());
    }
}
