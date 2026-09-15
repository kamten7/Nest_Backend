package com.nest.dto;

import lombok.Data;

/** 微信 jscode2session 接口响应体 */
@Data
public class WxSessionResponse {

    private String openid;

    private Integer errcode;

    private String errmsg;
}
