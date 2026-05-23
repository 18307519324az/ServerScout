package com.serverscout.service;

import com.serverscout.entity.ScanTask;
import com.serverscout.repository.ScanTaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Scheduled auto-scan service.
 * Supports daily patrol scans and weekly comprehensive scans via cron expressions.
 * Configured via system_config table: scheduled-scan-enabled, scheduled-scan-target,
 * scheduled-scan-cron (default: 0 0 2 * * ? = 2 AM daily).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduledScanService {

    private final ScanTaskRepository scanTaskRepository;
    private final ScanExecutionService scanExecutionService;
    private final SystemConfigService configService;

    /**
     * Daily quick patrol scan — runs at 2 AM by default.
     */
    @Scheduled(cron = "${app.scheduled.daily-cron:0 0 2 * * ?}")
    public void dailyPatrolScan() {
        if (!isEnabled("daily-scan-enabled", "true")) return;

        String target = configService.getConfig("daily-scan-target", "192.168.1.0/24");
        log.info("Starting daily patrol scan for {}", target);
        executeScheduledScan("每日自动巡检", target, "quick", "1-1000", true, false);
    }

    /**
     * Weekly comprehensive scan — runs Sunday at 3 AM by default.
     */
    @Scheduled(cron = "${app.scheduled.weekly-cron:0 0 3 * * SUN}")
    public void weeklyFullScan() {
        if (!isEnabled("weekly-scan-enabled", "false")) return;

        String target = configService.getConfig("weekly-scan-target", "192.168.1.0/24");
        log.info("Starting weekly full scan for {}", target);
        executeScheduledScan("每周全面巡检", target, "full", "1-65535", true, true);
    }

    private boolean isEnabled(String configKey, String defaultVal) {
        return "true".equalsIgnoreCase(configService.getConfig(configKey, defaultVal));
    }

    private void executeScheduledScan(String name, String target, String scanType,
                                       String portRange, boolean fingerprint, boolean vulnScan) {
        try {
            ScanTask task = ScanTask.builder()
                    .name(name + " - " + java.time.LocalDate.now())
                    .targetRange(target)
                    .scanType(scanType)
                    .portRange(portRange)
                    .enableFingerprint(fingerprint)
                    .enableVulnScan(vulnScan)
                    .status("pending")
                    .progress(0)
                    .totalAssets(0)
                    .totalPorts(0)
                    .createdAt(Instant.now())
                    .createdBy("system")
                    .build();
            task = scanTaskRepository.save(task);
            scanExecutionService.executeScan(task.getId());
            log.info("Scheduled scan created: taskId={}, type={}", task.getId(), scanType);
        } catch (Exception e) {
            log.error("Failed to create scheduled scan", e);
        }
    }
}
