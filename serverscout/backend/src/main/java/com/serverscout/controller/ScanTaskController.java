package com.serverscout.controller;

import com.serverscout.dto.ApiResponse;
import com.serverscout.dto.CreateScanTaskRequest;
import com.serverscout.dto.ScanTaskResponse;
import com.serverscout.service.ProgressEmitter;
import com.serverscout.service.ScanService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1/scan-tasks")
@RequiredArgsConstructor
public class ScanTaskController {

    private final ScanService scanService;
    private final ProgressEmitter progressEmitter;

    @PostMapping
    public ApiResponse<ScanTaskResponse> createTask(@Valid @RequestBody CreateScanTaskRequest req) {
        return ApiResponse.success(scanService.createTask(req));
    }

    @GetMapping
    public ApiResponse<?> listTasks(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status) {
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return ApiResponse.success(scanService.listTasks(status, pageable));
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

    @GetMapping("/{id}/progress")
    public SseEmitter streamProgress(@PathVariable Long id) {
        return progressEmitter.createEmitter(id);
    }
}
