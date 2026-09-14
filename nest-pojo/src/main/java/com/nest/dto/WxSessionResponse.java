package com.nest.dto;

import lombok.Data;

/**
 * 微信 jscode2session 接口响应体。
 */
@Data
public class WxSessionResponse {

    /** 用户唯一标识（成功时返回） */
    private String openid;

    /** 错误码；成功响应可能缺省，必须判空 */
    private Integer errcode;

    /** 错误描述 */
    private String errmsg;
}
