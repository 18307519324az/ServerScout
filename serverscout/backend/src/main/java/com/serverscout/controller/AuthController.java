package com.serverscout.controller;

import com.serverscout.config.RsaKeyProvider;
import com.serverscout.dto.ApiResponse;
import com.serverscout.service.CaptchaService;
import com.serverscout.service.OperationLogService;
import com.serverscout.service.UserService;
import com.serverscout.util.JwtTokenUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final JwtTokenUtil jwtTokenUtil;
    private final UserService userService;
    private final CaptchaService captchaService;
    private final OperationLogService logService;
    private final RsaKeyProvider rsaKeyProvider;
    private final HttpServletRequest request;

    @GetMapping("/public-key")
    public ApiResponse<Map<String, String>> getPublicKey() {
        return ApiResponse.success(Map.of("publicKey", rsaKeyProvider.getPublicKeyBase64()));
    }

    @PostMapping("/login")
    public ApiResponse<Map<String, String>> login(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String encryptedPassword = body.get("password");
        String captchaId = body.get("captchaId");
        String captchaAnswer = body.get("captchaAnswer");

        String password;
        try {
            password = rsaKeyProvider.decrypt(encryptedPassword);
        } catch (Exception e) {
            log.warn("RSA decrypt failed for user: {}", username);
            return ApiResponse.error(4000, "密码解密失败，请刷新页面重试");
        }

        // 验证码校验
        if (captchaId == null || captchaAnswer == null
                || !captchaService.validate(captchaId, captchaAnswer)) {
            return ApiResponse.error(4002, "验证码错误");
        }

        String ip = OperationLogService.getClientIp(request);
        String ua = request.getHeader("User-Agent");

        if (userService.authenticate(username, password)) {
            var user = userService.getUserByUsername(username);
            String role = user.getRole();
            String token = jwtTokenUtil.generateToken(username, role);
            logService.logLogin(user.getId(), username, ip, ua, true);
            return ApiResponse.success(Map.of(
                "token", token,
                "username", username,
                "role", role
            ));
        }
        logService.logLogin(null, username, ip, ua, false);
        return ApiResponse.error(4001, "用户名或密码错误");
    }

    @PostMapping("/register")
    public ApiResponse<Map<String, String>> register(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String encryptedPassword = body.get("password");
        String name = body.get("name");
        String gender = body.get("gender");
        String email = body.get("email");
        String captchaId = body.get("captchaId");
        String captchaAnswer = body.get("captchaAnswer");

        if (username == null || username.trim().isEmpty() || username.length() < 3) {
            return ApiResponse.error(4003, "用户名至少3个字符");
        }

        String password;
        try {
            password = rsaKeyProvider.decrypt(encryptedPassword);
        } catch (Exception e) {
            log.warn("RSA decrypt failed for register user: {}", username);
            return ApiResponse.error(4000, "密码解密失败，请刷新页面重试");
        }

        if (password == null || password.length() < 6) {
            return ApiResponse.error(4004, "密码至少6个字符");
        }
        if (name == null || name.trim().isEmpty()) {
            return ApiResponse.error(4006, "请输入姓名");
        }
        if (gender == null || gender.trim().isEmpty()
                || !java.util.List.of("MALE", "FEMALE", "OTHER").contains(gender.toUpperCase())) {
            return ApiResponse.error(4007, "请选择性别");
        }
        if (email == null || email.trim().isEmpty()
                || !email.matches("^[\\w.%+-]+@[\\w.-]+\\.[A-Za-z]{2,}$")) {
            return ApiResponse.error(4008, "请输入有效的邮箱地址");
        }
        if (captchaId == null || captchaAnswer == null
                || !captchaService.validate(captchaId, captchaAnswer)) {
            return ApiResponse.error(4002, "验证码错误");
        }

        try {
            userService.createUser(username.trim(), password, "USER", name.trim(), gender.toUpperCase(), email.trim());
            String token = jwtTokenUtil.generateToken(username.trim(), "USER");

            String ip = OperationLogService.getClientIp(request);
            String ua = request.getHeader("User-Agent");
            var newUser = userService.getUserByUsername(username.trim());
            logService.logLogin(newUser.getId(), username.trim(), ip, ua, true);

            return ApiResponse.success(Map.of(
                "token", token,
                "username", username.trim(),
                "name", name.trim(),
                "role", "USER"
            ));
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(4005, e.getMessage());
        }
    }

    @GetMapping("/captcha")
    public ApiResponse<Map<String, Object>> getCaptcha() {
        return ApiResponse.success(captchaService.generate());
    }
}
