package com.serverscout.controller;

import com.serverscout.dto.AiBriefingRequest;
import com.serverscout.dto.AiBriefingResponse;
import com.serverscout.dto.ApiResponse;
import com.serverscout.service.AiBriefingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ai-briefing")
@RequiredArgsConstructor
public class AiBriefingController {

    private final AiBriefingService aiBriefingService;

    @PostMapping("/generate")
    public ResponseEntity<ApiResponse<AiBriefingResponse>> generate(@Valid @RequestBody AiBriefingRequest request) {
        try {
            return ResponseEntity.ok(ApiResponse.success(aiBriefingService.generate(request)));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, ex.getMessage()));
        }
    }
}
