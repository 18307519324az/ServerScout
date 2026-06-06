package com.serverscout.util;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<?> handleValidation(MethodArgumentNotValidException ex) {
        String field = ex.getBindingResult().getFieldErrors().isEmpty()
                ? "unknown" : ex.getBindingResult().getFieldErrors().get(0).getField();
        String error = ex.getBindingResult().getFieldErrors().isEmpty()
                ? ex.getMessage() : ex.getBindingResult().getFieldErrors().get(0).getDefaultMessage();
        return badRequest(Map.of("field", field, "error", error));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<?> handleNotReadable(HttpMessageNotReadableException ex) {
        Throwable root = ex.getMostSpecificCause();
        String detail = root != null && root.getMessage() != null ? root.getMessage() : ex.getMessage();
        if (detail == null || detail.isBlank()) {
            detail = "Invalid request body";
        }
        return badRequest(Map.of("error", detail));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<?> handleIllegalArgument(IllegalArgumentException ex) {
        return badRequest(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<?> handleIllegalState(IllegalStateException ex) {
        String msg = ex.getMessage() != null ? ex.getMessage() : "";
        if (msg.contains("AsyncContext") || msg.contains("A non-container (application) thread")) {
            log.debug("Ignored async state error: {}", msg);
            return ResponseEntity.noContent().build();
        }
        String message = msg.isBlank() ? "Request state conflict" : msg;
        return response(HttpStatus.CONFLICT, 4090, message, null);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<?> handleResponseStatus(ResponseStatusException ex) {
        HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
        if (status == null) status = HttpStatus.INTERNAL_SERVER_ERROR;
        return response(status, ex.getStatusCode().value(), ex.getReason(), null);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<?> handleNotFound(ResourceNotFoundException ex) {
        return response(HttpStatus.NOT_FOUND, 4001, ex.getMessage(), null);
    }

    @ExceptionHandler(ScanException.class)
    public ResponseEntity<?> handleScanError(ScanException ex) {
        return response(HttpStatus.INTERNAL_SERVER_ERROR, 5001, ex.getMessage(), null);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<?> handleDataIntegrity(DataIntegrityViolationException ex) {
        String msg = ex.getMostSpecificCause() != null ? ex.getMostSpecificCause().getMessage() : ex.getMessage();
        if (msg != null && msg.contains("Duplicate entry")) {
            return response(HttpStatus.CONFLICT, 4009, "Data conflict", Map.of("detail", msg));
        }
        return badRequest(Map.of("error", msg != null ? msg : "Data integrity violation"));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<?> handleNoResource(NoResourceFoundException ex) {
        log.debug("Resource not found: {}", ex.getMessage());
        return response(HttpStatus.NOT_FOUND, 4001, "Resource not found", null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleGeneral(Exception ex) {
        String msg = ex.getMessage() != null ? ex.getMessage() : "";
        if (msg.contains("Broken pipe")
                || msg.contains("AsyncRequestNotUsableException")
                || msg.contains("ServletOutputStream failed to flush")) {
            log.debug("Ignored client disconnect error: {}", msg);
            return ResponseEntity.noContent().build();
        }

        log.error("Unexpected error: {}", ex.getMessage(), ex);
        return response(HttpStatus.INTERNAL_SERVER_ERROR, 5000, "Internal server error: " + msg, null);
    }

    private ResponseEntity<?> badRequest(Object data) {
        return response(HttpStatus.BAD_REQUEST, 4000, "Parameter validation failed", data);
    }

    private ResponseEntity<?> response(HttpStatus status, int code, String message, Object data) {
        if (isSseRequest()) {
            return ResponseEntity.status(status)
                    .contentType(MediaType.TEXT_PLAIN)
                    .body(message != null ? message : "");
        }

        Map<String, Object> body = new HashMap<>();
        body.put("code", code);
        body.put("message", message);
        body.put("data", data);
        body.put("timestamp", Instant.now().toString());
        return ResponseEntity.status(status).body(body);
    }

    private boolean isSseRequest() {
        var attrs = RequestContextHolder.getRequestAttributes();
        if (!(attrs instanceof ServletRequestAttributes servletAttrs)) return false;

        HttpServletRequest request = servletAttrs.getRequest();
        String accept = request.getHeader("Accept");
        String contentType = request.getContentType();
        String uri = request.getRequestURI();

        return (accept != null && accept.contains(MediaType.TEXT_EVENT_STREAM_VALUE))
                || (contentType != null && contentType.contains(MediaType.TEXT_EVENT_STREAM_VALUE))
                || (uri != null && uri.endsWith("/progress"));
    }
}
