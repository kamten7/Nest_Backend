package com.nest.exception;

/** 无权限异常。 */
public class NoPermissionException extends RuntimeException {

    public NoPermissionException(String message) {
        super(message);
    }
}
