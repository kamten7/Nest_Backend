package com.nest.exception;

/** 未登录异常（JWT 拦截器抛出）。 */
public class NotLoginException extends RuntimeException {

    public NotLoginException(String message) {
        super(message);
    }
}
