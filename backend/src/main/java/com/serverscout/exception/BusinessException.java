package com.serverscout.exception;

import com.serverscout.common.ErrorCode;
import com.serverscout.common.ResultCode;
import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Base business exception carrying a {@link ResultCode}.
 *
 * All business-layer exceptions should extend this so the
 * {@link GlobalExceptionHandler} can produce uniform error responses.
 */
@Getter
public class BusinessException extends RuntimeException {

    private final ResultCode resultCode;
    private final HttpStatus httpStatus;
    private final transient Object payload;

    public BusinessException(ResultCode resultCode) {
        super(resultCode.getMessage());
        this.resultCode = resultCode;
        this.httpStatus = HttpStatus.INTERNAL_SERVER_ERROR;
        this.payload = null;
    }

    public BusinessException(ResultCode resultCode, HttpStatus httpStatus) {
        super(resultCode.getMessage());
        this.resultCode = resultCode;
        this.httpStatus = httpStatus;
        this.payload = null;
    }

    public BusinessException(ResultCode resultCode, String customMessage) {
        super(customMessage);
        this.resultCode = resultCode;
        this.httpStatus = HttpStatus.INTERNAL_SERVER_ERROR;
        this.payload = null;
    }

    public BusinessException(ResultCode resultCode, HttpStatus httpStatus, String customMessage) {
        super(customMessage);
        this.resultCode = resultCode;
        this.httpStatus = httpStatus;
        this.payload = null;
    }

    public BusinessException(ResultCode resultCode, Throwable cause) {
        super(cause.getMessage() != null ? cause.getMessage() : resultCode.getMessage(), cause);
        this.resultCode = resultCode;
        this.httpStatus = HttpStatus.INTERNAL_SERVER_ERROR;
        this.payload = null;
    }

    public BusinessException(ResultCode resultCode, HttpStatus httpStatus, String customMessage, Object payload) {
        super(customMessage);
        this.resultCode = resultCode;
        this.httpStatus = httpStatus;
        this.payload = payload;
    }

    // ─── helpers for constructing from ErrorCode defaults ───

    public static BusinessException of(ErrorCode errorCode, HttpStatus httpStatus) {
        return new BusinessException(errorCode, httpStatus);
    }
}
