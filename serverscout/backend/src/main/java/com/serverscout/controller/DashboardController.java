package com.serverscout.controller;

import com.serverscout.dto.ApiResponse;
import com.serverscout.dto.DashboardResponse;
import com.serverscout.service.AttackSurfaceService;
import com.serverscout.service.DashboardService;
import lombok.RequiredArgsConstructor;
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

    @GetMapping("/stats")
    public ApiResponse<DashboardResponse> getStats() {
        return ApiResponse.success(dashboardService.getStats());
    }

    @GetMapping("/tech-stack")
    public ApiResponse<Map<String, Object>> getTechStack() {
        return ApiResponse.success(attackSurfaceService.buildTechStackStats());
    }
}
