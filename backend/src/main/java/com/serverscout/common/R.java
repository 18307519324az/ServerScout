package com.serverscout.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;

import java.time.Instant;

/**
 * Unified API response wrapper.
 *
 * Usage:
 * <pre>{@code
 *   R.ok(data);                          // success with data
 *   R.fail(ErrorCode.NOT_FOUND);          // fail with predefined error
 *   R.fail(ErrorCode.BAD_REQUEST, "具体原因"); // fail with custom message
 * }</pre>
 *
 * @param <T> payload type
 */
@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class R<T> {

    private final int code;
    private final String message;
    private final T data;
    private final String timestamp;

    private R(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
        this.timestamp = Instant.now().toString();
    }

    // ──────────────── factory methods ────────────────

    /** 200 – success with data */
    public static <T> R<T> ok(T data) {
        return new R<>(ErrorCode.SUCCESS.getCode(), ErrorCode.SUCCESS.getMessage(), data);
    }

    /** 200 – success with custom message */
    public static <T> R<T> ok(String message, T data) {
        return new R<>(ErrorCode.SUCCESS.getCode(), message, data);
    }

    /** Fail with a ResultCode */
    public static <T> R<T> fail(ResultCode resultCode) {
        return new R<>(resultCode.getCode(), resultCode.getMessage(), null);
    }

    /** Fail with a ResultCode, overriding the message */
    public static <T> R<T> fail(ResultCode resultCode, String message) {
        return new R<>(resultCode.getCode(), message, null);
    }

    /** Fail with explicit code and message */
    public static <T> R<T> fail(int code, String message) {
        return new R<>(code, message, null);
    }

    /** Fail with code, message, and data */
    public static <T> R<T> fail(int code, String message, T data) {
        return new R<>(code, message, data);
    }
}
