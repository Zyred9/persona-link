package com.personalink.server.exception;

import org.springframework.http.HttpStatus;

/**
 * 业务异常。
 */
public class BusinessException extends RuntimeException {

    private final HttpStatus httpStatus;
    private final int code;

    public BusinessException(int code, String message) {
        this(HttpStatus.BAD_REQUEST, code, message);
    }

    public BusinessException(HttpStatus httpStatus, int code, String message) {
        super(message);
        this.httpStatus = httpStatus;
        this.code = code;
    }

    public HttpStatus getHttpStatus() {
        return this.httpStatus;
    }

    public int getCode() {
        return this.code;
    }
}
