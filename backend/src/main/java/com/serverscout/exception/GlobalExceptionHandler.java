package com.serverscout.exception;

import com.serverscout.common.ErrorCode;
import com.serverscout.common.R;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * Unified global exception handler for the entire application.
 *
 * Covers:
 * 1. Custom business exceptions (BusinessException hierarchy)
 * 2. Spring validation exceptions
 * 3. Spring Security exceptions (AccessDenied, Authentication)
 * 4. Framework-level exceptions (DataIntegrity, ResponseStatus, etc.)
 * 5. Unknown / unhandled exceptions (catch-all)
 *
 * All responses follow the {@link R} envelope.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // ═══════════════════════════════════════════════════════════════
    // Custom Business Exceptions
    // ═══════════════════════════════════════════════════════════════

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<?> handleBusinessException(BusinessException ex) {
        if (ex.getHttpStatus().is4xxClientError()) {
            log.warn("Business client error [{}]: {}", ex.getResultCode().getCode(), ex.getMessage());
        } else {
            log.error("Business server error [{}]: {}", ex.getResultCode().getCode(), ex.getMessage(), ex);
        }
        R<?> body = (ex.getPayload() != null)
                ? R.fail(ex.getResultCode().getCode(), ex.getMessage(), ex.getPayload())
                : R.fail(ex.getResultCode().getCode(), ex.getMessage());
        return respond(ex.getHttpStatus(), body);
    }

    // ═══════════════════════════════════════════════════════════════
    // Spring Security
    // ═══════════════════════════════════════════════════════════════

    /** No JWT / expired JWT → 401 */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<?> handleAuthenticationException(AuthenticationException ex) {
        log.warn("Authentication failed: {}", ex.getMessage());
        return respond(HttpStatus.UNAUTHORIZED, R.fail(ErrorCode.UNAUTHORIZED, "认证失败: " + ex.getMessage()));
    }

    /** Has JWT but lacks required role → 403 */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<?> handleAccessDeniedException(AccessDeniedException ex) {
        log.warn("Access denied: {}", ex.getMessage());
        return respond(HttpStatus.FORBIDDEN, R.fail(ErrorCode.FORBIDDEN, "权限不足"));
    }

    // ═══════════════════════════════════════════════════════════════
    // Spring Validation & Binding
    // ═══════════════════════════════════════════════════════════════

    /** @Valid body validation failures */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<?> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> errors = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        fe -> fe.getDefaultMessage() != null ? fe.getDefaultMessage() : "invalid",
                        (a, b) -> a));
        String summary = errors.values().stream().findFirst().orElse("参数校验失败");
        log.debug("Validation failed: {}", errors);
        return respond(HttpStatus.BAD_REQUEST,
                R.fail(ErrorCode.VALIDATION_FAILED.getCode(), summary, errors));
    }

    /** Malformed JSON or unreadable body */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<?> handleNotReadable(HttpMessageNotReadableException ex) {
        Throwable root = ex.getMostSpecificCause();
        String detail = (root != null && root.getMessage() != null)
                ? root.getMessage()
                : (ex.getMessage() != null ? ex.getMessage() : "Invalid request body");
        log.debug("Message not readable: {}", detail);
        return respond(HttpStatus.BAD_REQUEST,
                R.fail(ErrorCode.BAD_REQUEST.getCode(), "请求体格式错误", Map.of("detail", detail)));
    }

    // ═══════════════════════════════════════════════════════════════
    // Standard Library Exceptions
    // ═══════════════════════════════════════════════════════════════

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<?> handleIllegalArgument(IllegalArgumentException ex) {
        log.debug("Illegal argument: {}", ex.getMessage());
        return respond(HttpStatus.BAD_REQUEST,
                R.fail(ErrorCode.BAD_REQUEST.getCode(), ex.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<?> handleIllegalState(IllegalStateException ex) {
        String msg = ex.getMessage() != null ? ex.getMessage() : "";
        // Suppress async / container thread errors caused by client disconnect
        if (msg.contains("AsyncContext") || msg.contains("A non-container (application) thread")) {
            log.debug("Ignored async state error: {}", msg);
            return ResponseEntity.noContent().build();
        }
        String message = msg.isBlank() ? "Request state conflict" : msg;
        log.warn("Illegal state: {}", message);
        return respond(HttpStatus.CONFLICT,
                R.fail(ErrorCode.CONFLICT.getCode(), message));
    }

    // ═══════════════════════════════════════════════════════════════
    // Spring Framework Exceptions
    // ═══════════════════════════════════════════════════════════════

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<?> handleResponseStatus(ResponseStatusException ex) {
        HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
        if (status == null) status = HttpStatus.INTERNAL_SERVER_ERROR;
        log.debug("ResponseStatus [{}]: {}", status.value(), ex.getReason());
        return respond(status,
                R.fail(status.value(), ex.getReason() != null ? ex.getReason() : status.getReasonPhrase()));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<?> handleNoResourceFound(NoResourceFoundException ex) {
        log.debug("No resource found: {}", ex.getMessage());
        return respond(HttpStatus.NOT_FOUND, R.fail(ErrorCode.NOT_FOUND, "接口不存在"));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<?> handleDataIntegrity(DataIntegrityViolationException ex) {
        String msg = ex.getMostSpecificCause() != null
                ? ex.getMostSpecificCause().getMessage()
                : ex.getMessage();
        if (msg != null && msg.contains("Duplicate entry")) {
            log.debug("Duplicate entry: {}", msg);
            return respond(HttpStatus.CONFLICT,
                    R.fail(ErrorCode.CONFLICT.getCode(), "数据冲突", Map.of("detail", msg)));
        }
        log.warn("Data integrity violation: {}", msg);
        return respond(HttpStatus.BAD_REQUEST,
                R.fail(ErrorCode.BAD_REQUEST.getCode(), msg != null ? msg : "数据完整性异常"));
    }

    // ═══════════════════════════════════════════════════════════════
    // Catch-all
    // ═══════════════════════════════════════════════════════════════

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleGeneral(Exception ex) {
        String msg = ex.getMessage() != null ? ex.getMessage() : "";

        // Suppress broken-pipe / client-disconnect noise
        if (msg.contains("Broken pipe")
                || msg.contains("AsyncRequestNotUsableException")
                || msg.contains("ServletOutputStream failed to flush")) {
            log.debug("Ignored client disconnect error: {}", msg);
            return ResponseEntity.noContent().build();
        }

        log.error("Unexpected error: {}", msg, ex);
        return respond(HttpStatus.INTERNAL_SERVER_ERROR,
                R.fail(ErrorCode.INTERNAL_ERROR.getCode(), ErrorCode.INTERNAL_ERROR.getMessage()));
    }

    // ═══════════════════════════════════════════════════════════════
    // Helper
    // ═══════════════════════════════════════════════════════════════

    private ResponseEntity<?> respond(HttpStatus status, R<?> body) {
        // SSE / progress endpoints: return plain-text so the stream doesn't break
        if (isSseRequest()) {
            return ResponseEntity.status(status)
                    .contentType(MediaType.TEXT_PLAIN)
                    .body(body.getMessage() != null ? body.getMessage() : "");
        }
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
