package com.nest.vo;

import lombok.Builder;
import lombok.Data;

/** 租客登录响应 */
@Data
@Builder
public class TenantLoginVO {

    private Long id;
    private String nickname;
    private String avatar;
    private String phone;
    private Integer gender;
    private String token;
}
