package com.serverscout.controller;

import com.serverscout.dto.ApiResponse;
import com.serverscout.service.HoneypotDetectionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/honeypot")
@RequiredArgsConstructor
public class HoneypotController {

    private final HoneypotDetectionService honeypotDetectionService;

    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getStats() {
        long count = honeypotDetectionService.getHoneypotAssetCount();
        List<Map<String, Object>> distribution = honeypotDetectionService.getHoneypotTypeDistribution();

        Map<String, Object> data = Map.of(
                "honeypotAssetCount", count,
                "typeDistribution", distribution
        );
        return ResponseEntity.ok(ApiResponse.success(data));
    }

    @GetMapping("/asset/{assetId}")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getAssetDetections(@PathVariable Long assetId) {
        var detections = honeypotDetectionService.getDetectionsForAsset(assetId);
        List<Map<String, Object>> result = detections.stream().map(d -> {
            Map<String, Object> item = new java.util.LinkedHashMap<>();
            item.put("id", d.getId());
            item.put("honeypotType", d.getHoneypotType());
            item.put("honeypotCategory", d.getHoneypotCategory());
            item.put("matchEvidence", d.getMatchEvidence());
            item.put("confidence", d.getConfidence());
            item.put("detectionMethod", d.getDetectionMethod());
            item.put("matchedPort", d.getMatchedPort());
            item.put("matchedAt", d.getMatchedAt() != null ? d.getMatchedAt().toString() : null);
            item.put("ruleName", d.getRule() != null ? d.getRule().getRuleName() : null);
            return item;
        }).toList();
        return ResponseEntity.ok(ApiResponse.success(result));
    }
}
