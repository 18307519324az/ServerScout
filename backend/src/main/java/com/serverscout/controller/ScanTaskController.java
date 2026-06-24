package com.serverscout.controller;

import com.serverscout.dto.ApiResponse;
import com.serverscout.dto.CreateScanTaskRequest;
import com.serverscout.dto.ScanTaskResponse;
import com.serverscout.entity.ScanTaskStage;
import com.serverscout.exception.BadRequestException;
import com.serverscout.exception.ForbiddenException;
import com.serverscout.service.ProgressEmitter;
import com.serverscout.service.ScanService;
import com.serverscout.service.ScanTaskStageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/scan-tasks")
@RequiredArgsConstructor
public class ScanTaskController {

    @Value("${app.scan.demo-mode:true}")
    private boolean demoMode;

    private final ScanService scanService;
    private final ProgressEmitter progressEmitter;
    private final ScanTaskStageService scanTaskStageService;

    private String getUsername(Authentication auth) {
        return auth != null ? auth.getName() : null;
    }

    private boolean isAdmin(Authentication auth) {
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }

    @PostMapping
    public ApiResponse<ScanTaskResponse> createTask(@Valid @RequestBody CreateScanTaskRequest req,
                                                     Authentication auth) {
        // Real Mode requires explicit authorization confirmation
        if (!demoMode && !Boolean.TRUE.equals(req.getAuthorized())) {
            throw new BadRequestException("真实扫描模式下，请确认你对目标资产拥有合法授权。");
        }
        return ApiResponse.success(scanService.createTask(req, getUsername(auth), demoMode));
    }

    @GetMapping
    public ApiResponse<?> listTasks(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status,
            Authentication auth) {
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        String username = getUsername(auth);
        boolean admin = isAdmin(auth);
        return ApiResponse.success(scanService.listTasks(status, pageable, username, admin));
    }

    @GetMapping("/{id}")
    public ApiResponse<ScanTaskResponse> getTask(@PathVariable Long id) {
        return ApiResponse.success(scanService.getTaskDetail(id));
    }

    @PostMapping("/{id}/cancel")
    public ApiResponse<Void> cancelTask(@PathVariable Long id) {
        scanService.cancelTask(id);
        return ApiResponse.success(null);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteTask(@PathVariable Long id) {
        scanService.deleteTask(id);
        return ApiResponse.success(null);
    }

    @GetMapping("/{id}/stages")
    public ApiResponse<List<ScanTaskStage>> getTaskStages(@PathVariable Long id) {
        ScanTaskResponse task = scanService.getTaskDetail(id);
        // Auto-repair: if task is in a terminal state, finalize any lingering PENDING/RUNNING stages
        scanTaskStageService.ensureNoPendingStagesForTerminalTask(id, task.getStatus());
        return ApiResponse.success(scanTaskStageService.listByTaskId(id));
    }

    /** Admin-only: repair stage status for all terminal-state tasks. */
    @PostMapping("/admin/repair-stage-status")
    public ApiResponse<Map<String, Integer>> repairStageStatus(Authentication auth) {
        if (!isAdmin(auth)) {
            throw new ForbiddenException("仅管理员可执行此操作");
        }
        int repaired = scanTaskStageService.repairCompletedTaskPendingStages();
        return ApiResponse.success(Map.of("repairedTaskCount", repaired));
    }

    @GetMapping("/{id}/progress")
    public SseEmitter streamProgress(@PathVariable Long id) {
        return progressEmitter.createEmitter(id);
    }
}
