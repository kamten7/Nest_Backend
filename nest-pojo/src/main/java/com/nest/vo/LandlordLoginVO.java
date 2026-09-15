package com.nest.vo;

import lombok.Builder;
import lombok.Data;

/** 房东登录响应 */
@Data
@Builder
public class LandlordLoginVO {

    private Long id;
    private String name;
    private String phone;
    private String token;
}
