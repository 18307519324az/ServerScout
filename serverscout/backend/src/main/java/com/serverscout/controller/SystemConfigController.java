package com.serverscout.controller;

import com.serverscout.dto.ApiResponse;
import com.serverscout.service.ScheduledScanService;
import com.serverscout.service.SystemConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/config")
@RequiredArgsConstructor
public class SystemConfigController {

    private final SystemConfigService configService;
    private final ScheduledScanService scheduledScanService;

    @GetMapping
    public ApiResponse<Map<String, String>> getAllConfigs() {
        Map<String, String> configs = new HashMap<>(configService.getAllConfigs());
        // Fill defaults for missing keys so UI always shows values
        configs.putIfAbsent("nmap-path", configService.getConfig("nmap-path", "nmap"));
        configs.putIfAbsent("nuclei-path", configService.getConfig("nuclei-path", "nuclei"));
        return ApiResponse.success(configs);
    }

    @GetMapping("/detect-tools")
    public ApiResponse<Map<String, String>> detectTools() {
        Map<String, String> result = new HashMap<>();
        result.put("nmap-path", detectTool("nmap"));
        result.put("nuclei-path", detectTool("nuclei"));
        return ApiResponse.success(result);
    }

    private String detectTool(String toolName) {
        // First check if already configured in DB
        String configured = configService.getConfig(toolName + "-path", "");
        if (!configured.isEmpty() && !configured.equals(toolName)) {
            return configured;
        }

        // Try detecting from system PATH
        boolean isWin = System.getProperty("os.name", "").toLowerCase().contains("win");
        String[] commands = isWin ? new String[]{"where", toolName} : new String[]{"which", toolName};

        try {
            ProcessBuilder pb = new ProcessBuilder(commands);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line = reader.readLine();
                if (line != null && !line.isEmpty()) {
                    String path = line.trim();
                    // Filter out error messages from Windows "where" command (Chinese and English)
                    if (path.contains("无法找到") || path.contains("找不到")
                            || path.contains("could not find") || path.contains("not found")
                            || path.startsWith("信息:") || path.startsWith("INFO:")) {
                        log.debug("{} not found in PATH", toolName);
                        return "";
                    }
                    // Windows: check if path is a valid executable path
                    if (isWin && (path.endsWith(".exe") || path.contains("\\") || path.contains("/"))) {
                        log.info("Auto-detected {} at: {}", toolName, path);
                        return path;
                    }
                    // Unix: valid which output
                    if (!isWin && (path.startsWith("/") || path.startsWith("~/"))) {
                        log.info("Auto-detected {} at: {}", toolName, path);
                        return path;
                    }
                    // If where returns just the tool name (meaning it's on PATH but no full path)
                    if (path.equalsIgnoreCase(toolName) || path.equalsIgnoreCase(toolName + ".exe")) {
                        log.info("{} found in PATH as: {}", toolName, path);
                        return path;
                    }
                    return "";
                }
            }
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                log.debug("{} not found (exit code {})", toolName, exitCode);
            }
        } catch (Exception e) {
            log.debug("Could not detect {}: {}", toolName, e.getMessage());
        }
        return ""; // not found
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping
    public ApiResponse<Void> updateConfigs(@RequestBody Map<String, String> configs) {
        configService.setConfigs(configs);
        // Reschedule cron jobs if scan schedule configs changed
        if (configs.keySet().stream().anyMatch(k -> k.startsWith("daily-scan-") || k.startsWith("weekly-scan-"))) {
            scheduledScanService.reschedule();
        }
        return ApiResponse.success(null);
    }
}
