package com.nest.dto;

import lombok.Data;

/**
 * 房东登录请求体。
 */
@Data
public class LandlordLoginDTO {


    private String phone;

    private String password;
}
