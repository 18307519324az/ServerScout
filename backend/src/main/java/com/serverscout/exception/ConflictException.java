package com.serverscout.exception;

import com.serverscout.common.ErrorCode;
import org.springframework.http.HttpStatus;

/**
 * Thrown on data conflicts (e.g. duplicate entry).
 * Maps to HTTP 409.
 */
public class ConflictException extends BusinessException {

    public ConflictException(String message) {
        super(ErrorCode.CONFLICT, HttpStatus.CONFLICT, message);
    }
}
