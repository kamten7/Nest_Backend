package com.nest.vo;

import lombok.Builder;
import lombok.Data;

/**
 * 租客登录响应。
 */
@Data
@Builder
public class TenantLoginVO {

    /** 租客 ID */
    private Long id;
    /** 昵称 */
    private String nickname;
    /** 头像 */
    private String avatar;
    /** 手机号（未绑定为 null；「我的」页展示 + 租房前置条件） */
    private String phone;
    /** 性别：1 男，2 女，0 未知 */
    private Integer gender;
    /** JWT Token */
    private String token;
}
