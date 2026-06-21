package com.serverscout.exception;

import com.serverscout.common.ErrorCode;
import org.springframework.http.HttpStatus;

/**
 * Thrown when a scan tool fails (nmap, nuclei, custom plugin).
 * Maps to HTTP 500.
 */
public class ScanException extends BusinessException {

    public ScanException(String message) {
        super(ErrorCode.SCAN_FAILED, HttpStatus.INTERNAL_SERVER_ERROR, message);
    }

    public ScanException(String message, Throwable cause) {
        super(ErrorCode.SCAN_FAILED, HttpStatus.INTERNAL_SERVER_ERROR, message);
        initCause(cause);
    }
}
