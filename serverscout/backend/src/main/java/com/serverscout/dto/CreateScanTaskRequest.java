package com.serverscout.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateScanTaskRequest {
    @NotBlank(message = "任务名称不能为空")
    private String name;

    @NotBlank(message = "扫描目标不能为空")
    private String targetRange;

    private String scanType = "quick";
    private String portRange = "1-1000";
    private Boolean enableFingerprint = true;
    private Boolean enableVulnScan = false;
    private ScanConfig config;

    @Data
    public static class ScanConfig {
        private Integer timeout = 300;
        private Integer concurrency = 50;
        private Integer rateLimit = 1000;
    }
}
