package com.serverscout.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(
            MethodArgumentNotValidException ex) {
        String field = ex.getBindingResult().getFieldErrors().get(0).getField();
        String error = ex.getBindingResult().getFieldErrors().get(0).getDefaultMessage();
        return badRequest(Map.of("field", field, "error", error));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex) {
        return badRequest(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(ResourceNotFoundException ex) {
        return response(HttpStatus.NOT_FOUND, 4001, ex.getMessage(), null);
    }

    @ExceptionHandler(ScanException.class)
    public ResponseEntity<Map<String, Object>> handleScanError(ScanException ex) {
        return response(HttpStatus.INTERNAL_SERVER_ERROR, 5001, ex.getMessage(), null);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNoResource(NoResourceFoundException ex) {
        log.debug("Resource not found: {}", ex.getMessage());
        return response(HttpStatus.NOT_FOUND, 4001, "资源不存在", null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneral(Exception ex) {
        log.error("Unexpected error", ex);
        return response(HttpStatus.INTERNAL_SERVER_ERROR, 5000, "服务器内部错误", null);
    }

    private ResponseEntity<Map<String, Object>> badRequest(Object data) {
        return response(HttpStatus.BAD_REQUEST, 4000, "参数校验失败", data);
    }

    private ResponseEntity<Map<String, Object>> response(
            HttpStatus status, int code, String message, Object data) {
        Map<String, Object> body = new HashMap<>();
        body.put("code", code);
        body.put("message", message);
        body.put("data", data);
        body.put("timestamp", Instant.now().toString());
        return ResponseEntity.status(status).body(body);
    }
}
