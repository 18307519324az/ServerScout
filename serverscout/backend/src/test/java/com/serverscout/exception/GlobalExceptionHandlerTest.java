package com.serverscout.exception;

import com.serverscout.common.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Comprehensive tests for the unified exception handling layer.
 *
 * Scenarios covered:
 *  1. Asset not found          → HTTP 404, code 40401
 *  2. Scan task not found      → HTTP 404, code 40402
 *  3. Vulnerability not found  → HTTP 404, code 40403
 *  4. User not found           → HTTP 404, code 40404
 *  5. Forbidden (no role)      → HTTP 403, code 40300
 *  6. Unauthorized (no token)  → HTTP 401, code 40100
 *  7. Bad request (validation) → HTTP 400, code 40000
 *  8. Scan tool failure        → HTTP 500, code 50010
 */
@DisplayName("Global Exception Handler")
class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @RestController
    @RequestMapping("/test")
    static class TestController {

        // ─── 404 scenarios ───

        @GetMapping("/asset-not-found")
        public String assetNotFound() {
            throw new ResourceNotFoundException(ErrorCode.ASSET_NOT_FOUND, "Asset", 9999L);
        }

        @GetMapping("/scan-task-not-found")
        public String scanTaskNotFound() {
            throw new ResourceNotFoundException(ErrorCode.SCAN_TASK_NOT_FOUND, "ScanTask", 8888L);
        }

        @GetMapping("/vuln-not-found")
        public String vulnNotFound() {
            throw new ResourceNotFoundException(ErrorCode.VULNERABILITY_NOT_FOUND, "Vulnerability", 7777L);
        }

        @GetMapping("/user-not-found")
        public String userNotFound() {
            throw new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND, "User", "nonexistent");
        }

        // ─── 403 scenario ───

        @GetMapping("/forbidden")
        public String forbidden() {
            throw new ForbiddenException("您没有权限访问此资产");
        }

        @GetMapping("/spring-access-denied")
        public String springAccessDenied() {
            throw new AccessDeniedException("Access Denied");
        }

        // ─── 401 scenarios ───

        @GetMapping("/unauthorized")
        public String unauthorized() {
            throw new UnauthorizedException("Token已过期，请重新登录");
        }

        @GetMapping("/login-failed")
        public String loginFailed() {
            throw new UnauthorizedException("用户名或密码错误");
        }

        @GetMapping("/spring-auth-exception")
        public String springAuthException() {
            throw new AuthenticationException("Bad credentials") {};
        }

        // ─── 400 scenarios ───

        @GetMapping("/bad-request")
        public String badRequest() {
            throw new BadRequestException("扫描目标不能为空");
        }

        @GetMapping("/illegal-argument")
        public String illegalArgument() {
            throw new IllegalArgumentException("Invalid parameter value");
        }

        @GetMapping("/conflict")
        public String conflict() {
            throw new ConflictException("用户名已存在: admin");
        }

        // ─── 500 scenarios ───

        @GetMapping("/scan-fail")
        public String scanFail() {
            throw new ScanException("Nmap execution failed (exit code 1)");
        }

        @GetMapping("/service-error")
        public String serviceError() {
            throw new ServiceException("PDF报告生成失败: 字体文件缺失");
        }

        @GetMapping("/generic-error")
        public String genericError() {
            throw new RuntimeException("Something unexpected happened");
        }

        @GetMapping("/business-error")
        public String businessError() {
            throw new BusinessException(ErrorCode.AI_SERVICE_ERROR, HttpStatus.BAD_GATEWAY,
                    "AI模型调用超时", Map.of("model", "gpt-4"));
        }
    }

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new TestController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    // ═══════════════════════════════════════════════════════════════
    // 场景 1–4: 资源不存在 → 404 + 对应业务码
    // ═══════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("资源不存在 → HTTP 404")
    class ResourceNotFound {

        @Test
        @DisplayName("1. 资产不存在 → 404 + code 40401")
        void assetNotFound() throws Exception {
            mockMvc.perform(get("/test/asset-not-found"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value(ErrorCode.ASSET_NOT_FOUND.getCode()))
                    .andExpect(jsonPath("$.message").value("Asset 不存在: 9999"));
        }

        @Test
        @DisplayName("2. 扫描任务不存在 → 404 + code 40402")
        void scanTaskNotFound() throws Exception {
            mockMvc.perform(get("/test/scan-task-not-found"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value(ErrorCode.SCAN_TASK_NOT_FOUND.getCode()))
                    .andExpect(jsonPath("$.message").value("ScanTask 不存在: 8888"));
        }

        @Test
        @DisplayName("3. 漏洞不存在 → 404 + code 40403")
        void vulnerabilityNotFound() throws Exception {
            mockMvc.perform(get("/test/vuln-not-found"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value(ErrorCode.VULNERABILITY_NOT_FOUND.getCode()))
                    .andExpect(jsonPath("$.message").value("Vulnerability 不存在: 7777"));
        }

        @Test
        @DisplayName("4. 用户不存在 → 404 + code 40404")
        void userNotFound() throws Exception {
            mockMvc.perform(get("/test/user-not-found"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value(ErrorCode.USER_NOT_FOUND.getCode()))
                    .andExpect(jsonPath("$.message").value("User 不存在: nonexistent"));
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // 场景 5: 无权限 → 403
    // ═══════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("无权限 → HTTP 403")
    class Forbidden {

        @Test
        @DisplayName("5a. ForbiddenException → 403 + code 40300")
        void forbiddenException() throws Exception {
            mockMvc.perform(get("/test/forbidden"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value(ErrorCode.FORBIDDEN.getCode()))
                    .andExpect(jsonPath("$.message").value("您没有权限访问此资产"));
        }

        @Test
        @DisplayName("5b. Spring AccessDeniedException → 403 + code 40300")
        void springAccessDenied() throws Exception {
            mockMvc.perform(get("/test/spring-access-denied"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value(ErrorCode.FORBIDDEN.getCode()))
                    .andExpect(jsonPath("$.message").value("权限不足"));
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // 场景 6: 未认证 → 401
    // ═══════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("未认证 / Token 过期 → HTTP 401")
    class Unauthorized {

        @Test
        @DisplayName("6a. UnauthorizedException → 401 + code 40100")
        void unauthorizedException() throws Exception {
            mockMvc.perform(get("/test/unauthorized"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value(ErrorCode.UNAUTHORIZED.getCode()))
                    .andExpect(jsonPath("$.message").value("Token已过期，请重新登录"));
        }

        @Test
        @DisplayName("6b. 登录失败 → 401 + code 40100 (统一提示，不区分用户/密码)")
        void loginFailed() throws Exception {
            mockMvc.perform(get("/test/login-failed"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value(ErrorCode.UNAUTHORIZED.getCode()))
                    .andExpect(jsonPath("$.message").value("用户名或密码错误"));
        }

        @Test
        @DisplayName("6c. Spring AuthenticationException → 401 + code 40100")
        void springAuthException() throws Exception {
            mockMvc.perform(get("/test/spring-auth-exception"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value(ErrorCode.UNAUTHORIZED.getCode()));
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // 场景 7: 参数错误 → 400
    // ═══════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("参数错误 → HTTP 400")
    class BadRequest {

        @Test
        @DisplayName("7a. BadRequestException → 400 + code 40000")
        void badRequestException() throws Exception {
            mockMvc.perform(get("/test/bad-request"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(ErrorCode.BAD_REQUEST.getCode()))
                    .andExpect(jsonPath("$.message").value("扫描目标不能为空"));
        }

        @Test
        @DisplayName("7b. IllegalArgumentException → 400 + code 40000")
        void illegalArgument() throws Exception {
            mockMvc.perform(get("/test/illegal-argument"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(ErrorCode.BAD_REQUEST.getCode()));
        }

        @Test
        @DisplayName("7c. ConflictException → 409 + code 40900")
        void conflict() throws Exception {
            mockMvc.perform(get("/test/conflict"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value(ErrorCode.CONFLICT.getCode()))
                    .andExpect(jsonPath("$.message").value("用户名已存在: admin"));
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // 场景 8: 扫描工具失败 → 500
    // ═══════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("扫描工具失败 / 服务异常 → HTTP 500")
    class ServerErrors {

        @Test
        @DisplayName("8a. ScanException (Nmap 失败) → 500 + code 50010")
        void scanFailed() throws Exception {
            mockMvc.perform(get("/test/scan-fail"))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.code").value(ErrorCode.SCAN_FAILED.getCode()))
                    .andExpect(jsonPath("$.message").value("Nmap execution failed (exit code 1)"));
        }

        @Test
        @DisplayName("8b. ServiceException → 500 + code 50000")
        void serviceError() throws Exception {
            mockMvc.perform(get("/test/service-error"))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.code").value(ErrorCode.INTERNAL_ERROR.getCode()))
                    .andExpect(jsonPath("$.message").value("PDF报告生成失败: 字体文件缺失"));
        }

        @Test
        @DisplayName("8c. RuntimeException → 500 + code 50000 (不泄露内部细节)")
        void genericErrorDoesNotLeakInternals() throws Exception {
            mockMvc.perform(get("/test/generic-error"))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.code").value(ErrorCode.INTERNAL_ERROR.getCode()))
                    // 泛化后的消息不应暴露原始异常细节
                    .andExpect(jsonPath("$.message").value(ErrorCode.INTERNAL_ERROR.getMessage()));
        }

        @Test
        @DisplayName("8d. BusinessException with payload → 对应状态码 + payload")
        void businessErrorWithPayload() throws Exception {
            mockMvc.perform(get("/test/business-error"))
                    .andExpect(status().isBadGateway())
                    .andExpect(jsonPath("$.code").value(ErrorCode.AI_SERVICE_ERROR.getCode()))
                    .andExpect(jsonPath("$.message").value("AI模型调用超时"))
                    .andExpect(jsonPath("$.data.model").value("gpt-4"));
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // 统一响应格式验证
    // ═══════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("统一响应格式")
    class ResponseFormat {

        @Test
        @DisplayName("所有错误响应包含 code, message, timestamp")
        void responseHasStandardFields() throws Exception {
            mockMvc.perform(get("/test/asset-not-found"))
                    .andExpect(jsonPath("$.code").isNumber())
                    .andExpect(jsonPath("$.message").isString())
                    .andExpect(jsonPath("$.timestamp").isString());
        }

        @Test
        @DisplayName("Content-Type 为 application/json")
        void contentTypeIsJson() throws Exception {
            mockMvc.perform(get("/test/asset-not-found"))
                    .andExpect(header().string("Content-Type",
                            containsString(MediaType.APPLICATION_JSON_VALUE)));
        }

        @Test
        @DisplayName("成功时 data 字段为 null 则不出现在 JSON 中")
        void nullDataIsOmitted() throws Exception {
            mockMvc.perform(get("/test/asset-not-found"))
                    .andExpect(jsonPath("$.data").doesNotExist());
        }
    }
}
