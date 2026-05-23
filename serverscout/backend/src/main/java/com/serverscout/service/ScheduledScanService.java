package com.serverscout.service;

import com.serverscout.entity.ScanTask;
import com.serverscout.repository.ScanTaskRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneId;
import java.util.TimeZone;
import java.util.concurrent.ScheduledFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduledScanService {

    private final ScanTaskRepository scanTaskRepository;
    private final ScanExecutionService scanExecutionService;
    private final SystemConfigService configService;
    private final TaskScheduler taskScheduler;

    private ScheduledFuture<?> dailyFuture;
    private ScheduledFuture<?> weeklyFuture;

    private static final String DEFAULT_DAILY_CRON = "0 0 2 * * ?";
    private static final String DEFAULT_WEEKLY_CRON = "0 0 3 * * SUN";

    @PostConstruct
    public void init() {
        scheduleAll();
    }

    public synchronized void reschedule() {
        cancelAll();
        scheduleAll();
        log.info("Scheduled scans rescheduled");
    }

    private void scheduleAll() {
        String dailyCron = configService.getConfig("daily-scan-cron", DEFAULT_DAILY_CRON);
        String weeklyCron = configService.getConfig("weekly-scan-cron", DEFAULT_WEEKLY_CRON);

        if (isEnabled("daily-scan-enabled", "true")) {
            dailyFuture = taskScheduler.schedule(this::dailyPatrolScan,
                    new CronTrigger(dailyCron, TimeZone.getTimeZone(ZoneId.of("Asia/Shanghai"))));
            log.info("Daily patrol scan scheduled: cron={}", dailyCron);
        }

        if (isEnabled("weekly-scan-enabled", "false")) {
            weeklyFuture = taskScheduler.schedule(this::weeklyFullScan,
                    new CronTrigger(weeklyCron, TimeZone.getTimeZone(ZoneId.of("Asia/Shanghai"))));
            log.info("Weekly full scan scheduled: cron={}", weeklyCron);
        }
    }

    private void cancelAll() {
        if (dailyFuture != null) { dailyFuture.cancel(false); dailyFuture = null; }
        if (weeklyFuture != null) { weeklyFuture.cancel(false); weeklyFuture = null; }
    }

    public void dailyPatrolScan() {
        if (!isEnabled("daily-scan-enabled", "true")) return;
        String target = configService.getConfig("daily-scan-target", "192.168.1.0/24");
        log.info("Starting daily patrol scan for {}", target);
        executeScheduledScan("每日自动巡检", target, "quick", "1-1000", true, false);
    }

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
