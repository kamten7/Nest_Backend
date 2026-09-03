package com.nest.vo;

import lombok.Builder;
import lombok.Data;

/**
 * 房东登录响应。
 */
@Data
@Builder
public class LandlordLoginVO {

    /** 房东 ID */
    private Long id;
    /** 房东姓名 */
    private String name;
    /** 手机号 */
    private String phone;
    /** JWT Token */
    private String token;
}
