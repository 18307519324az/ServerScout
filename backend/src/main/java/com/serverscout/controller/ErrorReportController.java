package com.serverscout.controller;

import com.serverscout.dto.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/error-report")
public class ErrorReportController {

    @PostMapping
    public ApiResponse<Void> reportError(@RequestBody Map<String, Object> body) {
        String message = (String) body.getOrDefault("message", "unknown");
        String stack = (String) body.getOrDefault("stack", "");
        String url = (String) body.getOrDefault("url", "");
        String userAgent = (String) body.getOrDefault("userAgent", "");

        log.error("[Frontend Error] URL={} | Message={} | Stack={} | UA={}",
                url, message,
                stack.length() > 500 ? stack.substring(0, 500) : stack,
                userAgent.length() > 200 ? userAgent.substring(0, 200) : userAgent);

        return ApiResponse.success(null);
    }
}
