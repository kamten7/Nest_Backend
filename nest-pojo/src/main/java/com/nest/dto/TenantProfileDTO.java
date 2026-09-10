package com.nest.dto;

import lombok.Data;

/**
 * 租客完善个人信息请求体（微信登录后填写/更新手机号等）。
 */
@Data
public class TenantProfileDTO {

    /** 手机号（必填） */
    private String phone;
    /** 昵称（可选） */
    private String nickname;
    /** 头像 URL（可选） */
    private String avatar;
}
