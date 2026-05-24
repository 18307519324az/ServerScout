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

    @GetMapping("/detect-tool/{toolName}")
    public ApiResponse<Map<String, String>> detectSingleTool(@PathVariable String toolName) {
        Map<String, String> result = new HashMap<>();
        String path = detectTool(toolName);
        result.put(toolName + "-path", path.isEmpty() ? "" : path);
        log.info("Single tool detection for {}: {}", toolName, path.isEmpty() ? "not found" : path);
        return ApiResponse.success(result);
    }

    private String detectTool(String toolName) {
        // First check if already configured in DB
        String configured = configService.getConfig(toolName + "-path", "");
        if (!configured.isEmpty() && !configured.equals(toolName)) {
            // Verify the configured path still exists
            if (java.nio.file.Files.exists(java.nio.file.Path.of(configured))) {
                return configured;
            }
        }

        boolean isWin = System.getProperty("os.name", "").toLowerCase().contains("win");

        // 1. Try detecting from system PATH using which/where
        String pathResult = detectFromPath(toolName, isWin);
        if (!pathResult.isEmpty()) {
            return pathResult;
        }

        // 2. Search common installation directories
        String commonPath = detectFromCommonPaths(toolName, isWin);
        if (!commonPath.isEmpty()) {
            return commonPath;
        }

        return ""; // not found
    }

    private String detectFromPath(String toolName, boolean isWin) {
        String[] commands = isWin ? new String[]{"where", toolName} : new String[]{"which", toolName};
        try {
            ProcessBuilder pb = new ProcessBuilder(commands);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line = reader.readLine();
                if (line != null && !line.isEmpty()) {
                    String path = line.trim();
                    if (path.contains("无法找到") || path.contains("找不到")
                            || path.contains("could not find") || path.contains("not found")
                            || path.startsWith("信息:") || path.startsWith("INFO:")) {
                        return "";
                    }
                    // On Windows, where returns the full path to the exe
                    if (isWin && path.toLowerCase().endsWith(".exe")) {
                        log.info("Auto-detected {} at: {}", toolName, path);
                        return path;
                    }
                    // On Unix, which returns the full path
                    if (!isWin && path.startsWith("/")) {
                        log.info("Auto-detected {} at: {}", toolName, path);
                        return path;
                    }
                    // If just the tool name is returned, it's on PATH
                    if (path.equalsIgnoreCase(toolName) || path.equalsIgnoreCase(toolName + ".exe")) {
                        log.info("{} found in PATH as: {}", toolName, path);
                        return path;
                    }
                }
            }
            process.waitFor();
        } catch (Exception e) {
            log.debug("PATH detection failed for {}: {}", toolName, e.getMessage());
        }
        return "";
    }

    private String detectFromCommonPaths(String toolName, boolean isWin) {
        String exeName = isWin ? toolName + ".exe" : toolName;
        String userHome = System.getProperty("user.home", "");
        String programFiles = System.getenv("ProgramFiles");
        String programFilesX86 = System.getenv("ProgramFiles(x86)");
        String localAppData = System.getenv("LOCALAPPDATA");
        String userName = System.getProperty("user.name", "");

        String[][] commonPaths = isWin ? new String[][]{
            // Nmap common paths
            {"nmap", "C:\\Program Files (x86)\\Nmap\\" + exeName},
            {"nmap", "C:\\Program Files\\Nmap\\" + exeName},
            {"nmap", "D:\\web\\Nmap\\" + exeName},
            {"nmap", "D:\\tools\\Nmap\\" + exeName},
            {"nmap", programFiles != null ? programFiles + "\\Nmap\\" + exeName : null},
            {"nmap", programFilesX86 != null ? programFilesX86 + "\\Nmap\\" + exeName : null},
            {"nmap", "C:\\ProgramData\\chocolatey\\bin\\" + exeName},
            {"nmap", userHome + "\\scoop\\shims\\" + exeName},
            // Nuclei common paths
            {"nuclei", userHome + "\\go\\bin\\" + exeName},
            {"nuclei", "C:\\Users\\" + userName + "\\go\\bin\\" + exeName},
            {"nuclei", userHome + "\\scoop\\shims\\" + exeName},
            {"nuclei", "C:\\ProgramData\\chocolatey\\bin\\" + exeName},
            {"nuclei", localAppData != null ? localAppData + "\\Programs\\nuclei\\" + exeName : null},
        } : new String[][]{
            {"nmap", "/usr/bin/" + exeName},
            {"nmap", "/usr/local/bin/" + exeName},
            {"nmap", "/opt/homebrew/bin/" + exeName},
            {"nmap", "/snap/bin/" + exeName},
            {"nuclei", userHome + "/go/bin/" + exeName},
            {"nuclei", "/usr/local/bin/" + exeName},
            {"nuclei", "/opt/homebrew/bin/" + exeName},
            {"nuclei", "/snap/bin/" + exeName},
        };

        for (String[] entry : commonPaths) {
            if (entry[0].equals(toolName) && entry[1] != null) {
                java.nio.file.Path p = java.nio.file.Path.of(entry[1]);
                if (java.nio.file.Files.exists(p) && java.nio.file.Files.isExecutable(p)) {
                    log.info("Found {} at common path: {}", toolName, entry[1]);
                    return entry[1];
                }
            }
        }
        return "";
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
