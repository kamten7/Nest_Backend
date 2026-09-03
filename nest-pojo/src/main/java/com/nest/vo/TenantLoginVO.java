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
    /** JWT Token */
    private String token;
}
