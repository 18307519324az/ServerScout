package com.serverscout.controller;

import com.serverscout.dto.ApiResponse;
import com.serverscout.service.ScreenshotService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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

        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "http://" + url;
        }

        int width = parseInt(request.get("width"), 1280);
        int height = parseInt(request.get("height"), 800);

        log.info("Screenshot request: url={}, {}x{}", url, width, height);

        String base64 = screenshotService.captureScreenshot(url, width, height);
        if (base64 == null) {
            return ApiResponse.error(503, "Screenshot capture failed. Install Playwright, wkhtmltoimage or cutycapt.");
        }

        return ApiResponse.success(Map.of(
                "url", url,
                "width", String.valueOf(width),
                "height", String.valueOf(height),
                "data", "data:image/png;base64," + base64
        ));
    }

    /** Serve a saved screenshot file by filename */
    @GetMapping("/file/{filename}")
    public ResponseEntity<Resource> serveFile(@PathVariable String filename) {
        try {
            Path filePath = screenshotService.findScreenshot(filename);
            if (filePath == null || !Files.exists(filePath)) {
                return ResponseEntity.notFound().build();
            }
            Resource resource = new FileSystemResource(filePath);
            String contentType = Files.probeContentType(filePath);
            return ResponseEntity.ok()
                    .contentType(contentType != null ? MediaType.parseMediaType(contentType) : MediaType.IMAGE_PNG)
                    .header(HttpHeaders.CACHE_CONTROL, "max-age=3600")
                    .body(resource);
        } catch (IOException e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    private int parseInt(String val, int defaultVal) {
        try {
            return val != null ? Integer.parseInt(val) : defaultVal;
        } catch (NumberFormatException e) {
            return defaultVal;
        }
    }
}
