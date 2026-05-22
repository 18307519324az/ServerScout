package com.serverscout.controller;

import com.serverscout.dto.ApiResponse;
import com.serverscout.service.CaptchaService;
import com.serverscout.service.UserService;
import com.serverscout.util.JwtTokenUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final JwtTokenUtil jwtTokenUtil;
    private final UserService userService;
    private final CaptchaService captchaService;

    @PostMapping("/login")
    public ApiResponse<Map<String, String>> login(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String password = body.get("password");
        String captchaId = body.get("captchaId");
        String captchaAnswer = body.get("captchaAnswer");

        // 验证码校验
        if (captchaId == null || captchaAnswer == null
                || !captchaService.validate(captchaId, captchaAnswer)) {
            return ApiResponse.error(4002, "验证码错误");
        }

        if (userService.authenticate(username, password)) {
            String role = userService.getUserByUsername(username).getRole();
            String token = jwtTokenUtil.generateToken(username, role);
            return ApiResponse.success(Map.of(
                "token", token,
                "username", username,
                "role", role
            ));
        }
        return ApiResponse.error(4001, "用户名或密码错误");
    }

    @PostMapping("/register")
    public ApiResponse<Map<String, String>> register(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String password = body.get("password");
        String captchaId = body.get("captchaId");
        String captchaAnswer = body.get("captchaAnswer");

        if (username == null || username.trim().isEmpty() || username.length() < 3) {
            return ApiResponse.error(4003, "用户名至少3个字符");
        }
        if (password == null || password.length() < 6) {
            return ApiResponse.error(4004, "密码至少6个字符");
        }
        if (captchaId == null || captchaAnswer == null
                || !captchaService.validate(captchaId, captchaAnswer)) {
            return ApiResponse.error(4002, "验证码错误");
        }

        try {
            userService.createUser(username.trim(), password, "USER", null);
            String token = jwtTokenUtil.generateToken(username.trim(), "USER");
            return ApiResponse.success(Map.of(
                "token", token,
                "username", username.trim(),
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
