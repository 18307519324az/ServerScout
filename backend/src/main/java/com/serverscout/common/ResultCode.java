package com.serverscout.common;

/**
 * Unified result code interface.
 * All error codes and success codes implement this interface.
 */
public interface ResultCode {

    /**
     * @return business status code
     */
    int getCode();

    /**
     * @return human-readable message
     */
    String getMessage();
}
