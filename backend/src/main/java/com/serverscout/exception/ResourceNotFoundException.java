package com.serverscout.exception;

import com.serverscout.common.ErrorCode;
import org.springframework.http.HttpStatus;

/**
 * Thrown when a requested resource does not exist.
 * Maps to HTTP 404.
 *
 * Usage:
 * <pre>{@code
 *   // Generic — uses 40400
 *   throw new ResourceNotFoundException("Asset", 9999L);
 *
 *   // Specific error code
 *   throw new ResourceNotFoundException(ErrorCode.ASSET_NOT_FOUND, "Asset", 9999L);
 * }</pre>
 */
public class ResourceNotFoundException extends BusinessException {

    /** Generic not-found (40400) */
    public ResourceNotFoundException(String resource, Object id) {
        super(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, resource + " 不存在: " + id);
    }

    /** Specific error code (e.g. 40401 Asset, 40402 ScanTask) */
    public ResourceNotFoundException(ErrorCode code, String resource, Object id) {
        super(code, HttpStatus.NOT_FOUND, resource + " 不存在: " + id);
    }

    /** Generic not-found with custom message */
    public ResourceNotFoundException(String message) {
        super(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, message);
    }
}
