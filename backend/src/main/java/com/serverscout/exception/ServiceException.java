package com.serverscout.exception;

import com.serverscout.common.ErrorCode;
import com.serverscout.common.ResultCode;
import org.springframework.http.HttpStatus;

/**
 * Generic service-layer exception.
 * Use for unexpected service errors not covered by more specific exceptions.
 * Maps to HTTP 500 by default.
 */
public class ServiceException extends BusinessException {

    public ServiceException(String message) {
        super(ErrorCode.INTERNAL_ERROR, HttpStatus.INTERNAL_SERVER_ERROR, message);
    }

    public ServiceException(ResultCode resultCode, String message) {
        super(resultCode, HttpStatus.INTERNAL_SERVER_ERROR, message);
    }

    public ServiceException(String message, Throwable cause) {
        super(ErrorCode.INTERNAL_ERROR, cause);
    }
}
