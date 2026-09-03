package com.nest.dto;

import lombok.Data;

/**
 * 房东登录请求体。
 */
@Data
public class LandlordLoginDTO {

    /** 手机号 */
    private String phone;
    /** 密码（明文，后端 MD5 后比对） */
    private String password;
}
