package com.serverscout.controller;

import com.serverscout.dto.ApiResponse;
import com.serverscout.dto.DashboardResponse;
import com.serverscout.service.AttackSurfaceService;
import com.serverscout.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;
    private final AttackSurfaceService attackSurfaceService;

    private String getUsername(Authentication auth) {
        return auth != null ? auth.getName() : null;
    }

    private boolean isAdmin(Authentication auth) {
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }

    @GetMapping("/stats")
    public ApiResponse<DashboardResponse> getStats(Authentication auth) {
        String username = getUsername(auth);
        boolean admin = isAdmin(auth);
        return ApiResponse.success(dashboardService.getStats(username, admin));
    }

    @GetMapping("/tech-stack")
    public ApiResponse<Map<String, Object>> getTechStack(Authentication auth) {
        String username = getUsername(auth);
        boolean admin = isAdmin(auth);
        return ApiResponse.success(attackSurfaceService.buildTechStackStats(username, admin));
    }
}
