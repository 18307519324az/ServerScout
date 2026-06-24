package com.serverscout.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.time.Instant;

@Data @AllArgsConstructor
public class ApiResponse<T> {
    private int code;
    private String message;
    private T data;
    private String timestamp;

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(200, "success", data, Instant.now().toString());
    }

    public static <T> ApiResponse<T> error(int code, String message) {
        return new ApiResponse<>(code, message, null, Instant.now().toString());
    }

    public static <T> ApiResponse<T> error(int code, String message, T data) {
        return new ApiResponse<>(code, message, data, Instant.now().toString());
    }
}
