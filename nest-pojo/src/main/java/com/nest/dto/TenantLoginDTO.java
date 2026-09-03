package com.nest.dto;

import lombok.Data;

/**
 * 租客登录请求体（微信小程序 code 登录 或 手机号登录）。
 */
@Data
public class TenantLoginDTO {

    /** 微信登录 code（wx.login 获取） */
    private String code;
    /** 手机号（手机号登录用） */
    private String phone;
    /** 昵称 */
    private String nickname;
    /** 头像 URL */
    private String avatar;
}
