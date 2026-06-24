package com.serverscout.controller;

import com.serverscout.common.R;
import com.serverscout.config.RsaKeyProvider;
import com.serverscout.exception.BadRequestException;
import com.serverscout.exception.UnauthorizedException;
import com.serverscout.service.CaptchaService;
import com.serverscout.service.OperationLogService;
import com.serverscout.service.UserService;
import com.serverscout.util.JwtTokenUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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

    @Value("${app.scan.demo-mode:false}")
    private boolean demoMode;

    @GetMapping("/public-key")
    public R<Map<String, String>> getPublicKey() {
        // In demo mode, return empty key so the frontend sends plaintext password
        if (demoMode) {
            return R.ok(Map.of("publicKey", ""));
        }
        return R.ok(Map.of("publicKey", rsaKeyProvider.getPublicKeyBase64()));
    }

    @PostMapping("/login")
    public R<Map<String, String>> login(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String encryptedPassword = body.get("password");
        String captchaId = body.get("captchaId");
        String captchaAnswer = body.get("captchaAnswer");

        String password;
        try {
            password = rsaKeyProvider.decrypt(encryptedPassword);
        } catch (Exception e) {
            if (demoMode) {
                // In demo mode, accept plaintext password directly
                password = encryptedPassword;
            } else {
                log.warn("RSA decrypt failed for user: {}", username);
                throw new BadRequestException("密码解密失败，请刷新页面重试");
            }
        }

        if (!demoMode) {
            // 验证码校验 (only in non-demo mode; demo mode bypasses in CaptchaService)
            if (captchaId == null || captchaAnswer == null
                    || !captchaService.validate(captchaId, captchaAnswer)) {
                throw new BadRequestException("验证码错误");
            }
        }

        String ip = OperationLogService.getClientIp(request);
        String ua = request.getHeader("User-Agent");

        if (userService.authenticate(username, password)) {
            var user = userService.getUserByUsername(username);
            String role = user.getRole();
            String token = jwtTokenUtil.generateToken(username, role);
            logService.logLogin(user.getId(), username, ip, ua, true);
            return R.ok(Map.of(
                "token", token,
                "username", username,
                "role", role
            ));
        }
        logService.logLogin(null, username, ip, ua, false);
        throw new UnauthorizedException("用户名或密码错误");
    }

    @PostMapping("/register")
    public R<Map<String, String>> register(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String encryptedPassword = body.get("password");
        String name = body.get("name");
        String gender = body.get("gender");
        String email = body.get("email");
        String captchaId = body.get("captchaId");
        String captchaAnswer = body.get("captchaAnswer");

        if (username == null || username.trim().isEmpty() || username.length() < 3) {
            throw new BadRequestException("用户名至少3个字符");
        }

        String password;
        try {
            password = rsaKeyProvider.decrypt(encryptedPassword);
        } catch (Exception e) {
            log.warn("RSA decrypt failed for register user: {}", username);
            throw new BadRequestException("密码解密失败，请刷新页面重试");
        }

        if (password == null || password.length() < 6) {
            throw new BadRequestException("密码至少6个字符");
        }
        if (name == null || name.trim().isEmpty()) {
            throw new BadRequestException("请输入姓名");
        }
        if (gender == null || gender.trim().isEmpty()
                || !java.util.List.of("MALE", "FEMALE", "OTHER").contains(gender.toUpperCase())) {
            throw new BadRequestException("请选择性别");
        }
        if (email == null || email.trim().isEmpty()
                || !email.matches("^[\\w.%+-]+@[\\w.-]+\\.[A-Za-z]{2,}$")) {
            throw new BadRequestException("请输入有效的邮箱地址");
        }
        if (captchaId == null || captchaAnswer == null
                || !captchaService.validate(captchaId, captchaAnswer)) {
            throw new BadRequestException("验证码错误");
        }

        userService.createUser(username.trim(), password, "USER", name.trim(), gender.toUpperCase(), email.trim());
        String token = jwtTokenUtil.generateToken(username.trim(), "USER");

        String ip = OperationLogService.getClientIp(request);
        String ua = request.getHeader("User-Agent");
        var newUser = userService.getUserByUsername(username.trim());
        logService.logLogin(newUser.getId(), username.trim(), ip, ua, true);

        return R.ok(Map.of(
            "token", token,
            "username", username.trim(),
            "name", name.trim(),
            "role", "USER"
        ));
    }

    @GetMapping("/captcha")
    public R<Map<String, Object>> getCaptcha() {
        return R.ok(captchaService.generate());
    }

    /** Demo-mode-only login: accepts plaintext password, no captcha needed. */
    @PostMapping("/demo-login")
    public R<Map<String, String>> demoLogin(@RequestBody Map<String, String> body) {
        if (!demoMode) {
            throw new UnauthorizedException("演示登录仅限演示模式");
        }
        String username = body.get("username");
        String password = body.get("password");

        String ip = OperationLogService.getClientIp(request);
        String ua = request.getHeader("User-Agent");

        if (username != null && password != null && userService.authenticate(username, password)) {
            var user = userService.getUserByUsername(username);
            String role = user.getRole();
            String token = jwtTokenUtil.generateToken(username, role);
            logService.logLogin(user.getId(), username, ip, ua, true);
            return R.ok(Map.of(
                "token", token,
                "username", username,
                "role", role
            ));
        }
        logService.logLogin(null, username, ip, ua, false);
        throw new UnauthorizedException("用户名或密码错误");
    }
}
