package com.nest.service;

/** 微信登录凭证换取服务 */
public interface WxAuthService {

    /** 用小程序 code 调微信 jscode2session 换 openid */
    String code2Openid(String code);
}
