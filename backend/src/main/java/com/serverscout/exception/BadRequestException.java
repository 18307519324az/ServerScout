package com.serverscout.exception;

import com.serverscout.common.ErrorCode;
import org.springframework.http.HttpStatus;

/**
 * Thrown when the client request is malformed or missing required parameters.
 * Maps to HTTP 400.
 */
public class BadRequestException extends BusinessException {

    public BadRequestException(String message) {
        super(ErrorCode.BAD_REQUEST, HttpStatus.BAD_REQUEST, message);
    }

    public BadRequestException(ErrorCode code, String message) {
        super(code, HttpStatus.BAD_REQUEST, message);
    }

    public BadRequestException(String message, Object payload) {
        super(ErrorCode.BAD_REQUEST, HttpStatus.BAD_REQUEST, message, payload);
    }
}
