package com.nest.vo;

import lombok.Builder;
import lombok.Data;

/** 租客个人信息 */
@Data
@Builder
public class TenantProfileVO {

    private Long id;
    private String nickname;
    private String avatar;
    private String phone;
    private Integer gender;
    private Boolean phoneBound;
}
