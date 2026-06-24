package com.serverscout.controller;

import com.serverscout.dto.ApiResponse;
import com.serverscout.entity.RiskScoreDetail;
import com.serverscout.service.RiskScoreService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/risk-scores")
@RequiredArgsConstructor
public class RiskScoreController {

    private final RiskScoreService riskScoreService;

    private String getUsername(Authentication auth) {
        return auth != null ? auth.getName() : null;
    }

    private boolean isAdmin(Authentication auth) {
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }

    @GetMapping("/task/{taskId}")
    public ApiResponse<List<RiskScoreDetail>> getByTask(@PathVariable Long taskId) {
        return ApiResponse.success(riskScoreService.listByTaskId(taskId));
    }

    @GetMapping("/asset/{assetId}")
    public ApiResponse<List<RiskScoreDetail>> getByAsset(@PathVariable Long assetId) {
        return ApiResponse.success(riskScoreService.listByAssetId(assetId));
    }

    @GetMapping("/top")
    public ApiResponse<List<RiskScoreDetail>> getTopRisks(
            @RequestParam(defaultValue = "10") int limit,
            Authentication auth) {
        String username = getUsername(auth);
        boolean admin = isAdmin(auth);
        return ApiResponse.success(riskScoreService.topRisks(limit, username, admin));
    }

    @PostMapping("/task/{taskId}/recalculate")
    public ApiResponse<Map<String, Object>> recalculate(@PathVariable Long taskId) {
        List<RiskScoreDetail> results = riskScoreService.recalculateForTask(taskId);
        return ApiResponse.success(Map.of(
                "taskId", taskId,
                "scored", results.size()
        ));
    }
}
