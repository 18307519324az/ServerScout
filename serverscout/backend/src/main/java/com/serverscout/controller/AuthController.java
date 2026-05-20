package com.serverscout.controller;

import com.serverscout.dto.ApiResponse;
import com.serverscout.util.JwtTokenUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final JwtTokenUtil jwtTokenUtil;

    @PostMapping("/login")
    public ApiResponse<Map<String, String>> login(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String password = body.get("password");

        // 简化的登录逻辑，后续可接入数据库用户表
        if ("admin".equals(username) && "admin123".equals(password)) {
            String token = jwtTokenUtil.generateToken(username);
            return ApiResponse.success(Map.of(
                "token", token,
                "username", username
            ));
        }
        return ApiResponse.error(4001, "用户名或密码错误");
    }
}
