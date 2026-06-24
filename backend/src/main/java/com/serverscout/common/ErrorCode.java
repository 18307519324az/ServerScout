package com.serverscout.common;

import lombok.Getter;

/**
 * Standard error code enumeration.
 *
 * Naming convention:
 *   [HTTP status][sequence]  e.g. 40401 = HTTP 404, subtype 01
 *
 * Code groups:
 *   20000            success
 *   40000–40099      bad request / validation
 *   40100–40199      authentication
 *   40300–40399      authorization / forbidden
 *   40400–40499      resource not found
 *   40900–40999      data conflict
 *   42900–42999      rate limiting
 *   50000–50099      internal server error
 *   50010–50019      scan tool failures
 *   50020–50029      report generation failures
 *   50030–50039      AI / third-party service failures
 */
@Getter
public enum ErrorCode implements ResultCode {

    // ========================================================================
    // Success
    // ========================================================================
    SUCCESS(20000, "success"),

    // ========================================================================
    // 400xx — Bad Request
    // ========================================================================
    BAD_REQUEST(40000, "请求参数错误"),
    VALIDATION_FAILED(40001, "参数校验失败"),
    CAPTCHA_ERROR(40002, "验证码错误"),
    PASSWORD_DECRYPT_FAILED(40003, "密码解密失败"),

    // ========================================================================
    // 401xx — Unauthorized
    // ========================================================================
    UNAUTHORIZED(40100, "未登录或登录已过期"),
    LOGIN_FAILED(40101, "用户名或密码错误"),

    // ========================================================================
    // 403xx — Forbidden
    // ========================================================================
    FORBIDDEN(40300, "无权限访问"),

    // ========================================================================
    // 404xx — Not Found
    // ========================================================================
    NOT_FOUND(40400, "请求的资源不存在"),
    ASSET_NOT_FOUND(40401, "资产不存在"),
    SCAN_TASK_NOT_FOUND(40402, "扫描任务不存在"),
    VULNERABILITY_NOT_FOUND(40403, "漏洞不存在"),
    USER_NOT_FOUND(40404, "用户不存在"),
    SUBDOMAIN_NOT_FOUND(40405, "子域名不存在"),
    PORT_NOT_FOUND(40406, "端口不存在"),
    SCREENSHOT_NOT_FOUND(40407, "截图不存在"),

    // ========================================================================
    // 409xx — Conflict
    // ========================================================================
    CONFLICT(40900, "数据冲突"),
    USERNAME_EXISTS(40901, "用户名已存在"),
    EMAIL_EXISTS(40902, "邮箱已存在"),

    // ========================================================================
    // 429xx — Rate Limited
    // ========================================================================
    RATE_LIMITED(42900, "请求过于频繁，请稍后再试"),

    // ========================================================================
    // 5000x — Internal Server Error
    // ========================================================================
    INTERNAL_ERROR(50000, "服务器内部错误"),
    DATABASE_ERROR(50001, "数据库操作异常"),

    // ========================================================================
    // 5001x — Scan Tool Failures
    // ========================================================================
    SCAN_FAILED(50010, "扫描任务执行失败"),
    SCAN_TIMEOUT(50011, "扫描任务超时"),
    SCAN_PLUGIN_DISABLED(50012, "扫描插件已禁用"),

    // ========================================================================
    // 5002x — Report Generation Failures
    // ========================================================================
    PDF_GENERATION_FAILED(50020, "PDF报告生成失败"),
    EXCEL_GENERATION_FAILED(50021, "Excel报告生成失败"),

    // ========================================================================
    // 5003x — AI / Third-Party Service Failures
    // ========================================================================
    AI_SERVICE_ERROR(50030, "AI服务调用失败"),
    THIRD_PARTY_ERROR(50031, "第三方服务调用失败");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
