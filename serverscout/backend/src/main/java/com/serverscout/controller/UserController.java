package com.serverscout.controller;

import com.serverscout.dto.ApiResponse;
import com.serverscout.entity.User;
import com.serverscout.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    public ApiResponse<User> getCurrentUser(
            @org.springframework.security.core.annotation.AuthenticationPrincipal String username) {
        return ApiResponse.success(userService.getUserByUsername(username));
    }

    @PutMapping("/me")
    public ApiResponse<User> updateCurrentUser(
            @org.springframework.security.core.annotation.AuthenticationPrincipal String username,
            @RequestBody Map<String, Object> body) {
        User user = userService.updateCurrentUser(
                username,
                (String) body.get("name"),
                (String) body.get("gender"),
                (String) body.get("email")
        );
        return ApiResponse.success(user);
    }

    @PutMapping("/me/password")
    public ApiResponse<Void> changeCurrentUserPassword(
            @org.springframework.security.core.annotation.AuthenticationPrincipal String username,
            @RequestBody Map<String, String> body) {
        userService.changeCurrentUserPassword(username,
                body.get("oldPassword"),
                body.get("newPassword"));
        return ApiResponse.success(null);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    public ApiResponse<java.util.List<User>> listUsers() {
        return ApiResponse.success(userService.listUsers());
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/{id}")
    public ApiResponse<User> getUser(@PathVariable Long id) {
        return ApiResponse.success(userService.getUserById(id));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ApiResponse<User> createUser(@RequestBody Map<String, String> body) {
        User user = userService.createUser(
                body.get("username"),
                body.get("password"),
                body.get("role"),
                body.get("name"),
                body.get("gender"),
                body.get("email")
        );
        return ApiResponse.success(user);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    public ApiResponse<User> updateUser(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        User user = userService.updateUser(
                id,
                (String) body.get("role"),
                (String) body.get("name"),
                (String) body.get("gender"),
                (String) body.get("email"),
                body.get("enabled") != null ? (Boolean) body.get("enabled") : null
        );
        return ApiResponse.success(user);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}/password")
    public ApiResponse<Void> resetPassword(@PathVariable Long id, @RequestBody Map<String, String> body) {
        userService.resetPassword(id, body.get("newPassword"));
        return ApiResponse.success(null);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteUser(@PathVariable Long id) {
        userService.deleteUser(id);
        return ApiResponse.success(null);
    }
}
