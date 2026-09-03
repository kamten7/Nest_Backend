package com.nest.dto;

import lombok.Data;

/**
 * 租客注册请求体。
 */
@Data
public class TenantRegisterDTO {

    /** 昵称 */
    private String nickname;
    /** 手机号 */
    private String phone;
    /** 性别：1 男，2 女，0 未知 */
    private Integer gender;
    /** 头像 URL */
    private String avatar;
}
