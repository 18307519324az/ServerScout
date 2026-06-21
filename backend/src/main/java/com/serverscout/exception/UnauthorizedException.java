package com.serverscout.exception;

import com.serverscout.common.ErrorCode;
import org.springframework.http.HttpStatus;

/**
 * Thrown when the user is not authenticated (missing or invalid token).
 * Maps to HTTP 401.
 */
public class UnauthorizedException extends BusinessException {

    public UnauthorizedException() {
        super(ErrorCode.UNAUTHORIZED, HttpStatus.UNAUTHORIZED);
    }

    public UnauthorizedException(String message) {
        super(ErrorCode.UNAUTHORIZED, HttpStatus.UNAUTHORIZED, message);
    }
}
