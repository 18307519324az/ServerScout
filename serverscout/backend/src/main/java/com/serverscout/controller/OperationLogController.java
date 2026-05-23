package com.serverscout.controller;

import com.serverscout.dto.ApiResponse;
import com.serverscout.entity.OperationLog;
import com.serverscout.service.OperationLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/operation-logs")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class OperationLogController {

    private final OperationLogService logService;

    @GetMapping
    public ApiResponse<Page<OperationLog>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endTime) {

        PageRequest pageable = PageRequest.of(page, Math.min(size, 100),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<OperationLog> result = logService.search(username, type, startTime, endTime, pageable);
        return ApiResponse.success(result);
    }

    @GetMapping("/user/{username}")
    public ApiResponse<Page<OperationLog>> byUser(
            @PathVariable String username,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, Math.min(size, 100),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        return ApiResponse.success(logService.findByUser(username, pageable));
    }

    @GetMapping("/stats")
    public ApiResponse<Map<String, Object>> stats() {
        return ApiResponse.success(Map.of(
                "total", logService.search(null, null, null, null,
                        PageRequest.of(0, 1)).getTotalElements(),
                "types", Map.of(
                        "LOGIN_SUCCESS", logService.search(null, "LOGIN_SUCCESS", null, null,
                                PageRequest.of(0, 1)).getTotalElements(),
                        "LOGIN_FAILED", logService.search(null, "LOGIN_FAILED", null, null,
                                PageRequest.of(0, 1)).getTotalElements(),
                        "API_CALL", logService.search(null, "API_CALL", null, null,
                                PageRequest.of(0, 1)).getTotalElements()
                )
        ));
    }
}
