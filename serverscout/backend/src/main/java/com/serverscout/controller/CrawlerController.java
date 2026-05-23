package com.serverscout.controller;

import com.serverscout.dto.ApiResponse;
import com.serverscout.entity.CrawledUrl;
import com.serverscout.repository.CrawledUrlRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/crawler")
@RequiredArgsConstructor
public class CrawlerController {

    private final CrawledUrlRepository crawledUrlRepository;

    @GetMapping("/asset/{assetId}")
    public ApiResponse<List<CrawledUrl>> byAsset(@PathVariable Long assetId) {
        return ApiResponse.success(crawledUrlRepository.findByAssetIdOrderByCrawlDepthAsc(assetId));
    }

    @GetMapping("/port/{portId}")
    public ApiResponse<List<CrawledUrl>> byPort(@PathVariable Long portId) {
        return ApiResponse.success(crawledUrlRepository.findByPortIdOrderByCrawlDepthAsc(portId));
    }

    @GetMapping("/task/{taskId}")
    public ApiResponse<List<CrawledUrl>> byTask(@PathVariable Long taskId) {
        return ApiResponse.success(crawledUrlRepository.findByTaskIdOrderByCrawlDepthAsc(taskId));
    }

    @GetMapping("/task/{taskId}/screenshots")
    public ApiResponse<List<CrawledUrl>> screenshots(@PathVariable Long taskId) {
        return ApiResponse.success(crawledUrlRepository.findWithScreenshotsByTaskId(taskId));
    }
}
