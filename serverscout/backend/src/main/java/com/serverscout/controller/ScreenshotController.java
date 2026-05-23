package com.serverscout.controller;

import com.serverscout.dto.ApiResponse;
import com.serverscout.service.ScreenshotService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/screenshot")
@RequiredArgsConstructor
public class ScreenshotController {

    private final ScreenshotService screenshotService;

    @PostMapping
    public ApiResponse<Map<String, String>> capture(@RequestBody Map<String, String> request) {
        String url = request.get("url");
        if (url == null || url.isBlank()) {
            return ApiResponse.error(400, "URL is required");
        }

        // Ensure URL has scheme
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "http://" + url;
        }

        int width = parseInt(request.get("width"), 1280);
        int height = parseInt(request.get("height"), 800);

        log.info("Screenshot request: url={}, {}x{}", url, width, height);

        String base64 = screenshotService.captureScreenshot(url, width, height);
        if (base64 == null) {
            return ApiResponse.error(503, "Screenshot capture failed. Install wkhtmltoimage or cutycapt.");
        }

        return ApiResponse.success(Map.of(
                "url", url,
                "width", String.valueOf(width),
                "height", String.valueOf(height),
                "data", "data:image/png;base64," + base64
        ));
    }

    private int parseInt(String val, int defaultVal) {
        try {
            return val != null ? Integer.parseInt(val) : defaultVal;
        } catch (NumberFormatException e) {
            return defaultVal;
        }
    }
}
