package com.nest.chat.core;

/** 账号可用性校验：JWT 签名有效不代表账号仍可用（封禁/注销），握手时由业务侧实现兜底。 */
public interface ChatAccountChecker {

    boolean isActive(String userType, Long userId);
}
