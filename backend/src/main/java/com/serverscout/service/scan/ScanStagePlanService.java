package com.serverscout.service.scan;

import com.serverscout.entity.ScanTask;
import com.serverscout.entity.enums.ScanProfile;
import com.serverscout.entity.enums.ScanStageCode;
import com.serverscout.service.ScanTaskStageService;
import com.serverscout.service.WebhookNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Determines the scan profile for a task and applies the initial stage plan:
 * stages that are not applicable to the profile are marked SKIPPED upfront,
 * so they never appear as PENDING in the UI.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScanStagePlanService {

    private final ScanTaskStageService scanTaskStageService;
    private final WebhookNotificationService webhookNotificationService;

    /**
     * Determine the scan profile based on the task's scan type and feature flags.
     */
    public ScanProfile determineProfile(ScanTask task) {
        String scanType = task.getScanType();
        boolean fingerprint = Boolean.TRUE.equals(task.getEnableFingerprint());
        boolean vuln = Boolean.TRUE.equals(task.getEnableVulnScan());
        boolean crawler = Boolean.TRUE.equals(task.getEnableCrawler());

        // "full" or "FULL" scan type always maps to FULL_SCAN
        if ("FULL".equalsIgnoreCase(scanType)) {
            return ScanProfile.FULL_SCAN;
        }

        // If neither fingerprint nor vuln nor crawler is enabled, treat as host discovery
        if (!fingerprint && !vuln && !crawler) {
            return ScanProfile.HOST_DISCOVERY;
        }

        // Default to quick scan for "quick" type and everything else
        return ScanProfile.QUICK_SCAN;
    }

    /**
     * Apply the initial stage plan for a task: stages that are definitively not
     * applicable for this profile are marked SKIPPED right after initStages.
     * <p>
     * Should be called after {@code initStages(taskId)} and before any execution begins.
     */
    public void applyInitialStagePlan(Long taskId, ScanTask task) {
        ScanProfile profile = determineProfile(task);
        log.info("Applying stage plan for task {}: scanType={}, profile={}",
                taskId, task.getScanType(), profile);

        switch (profile) {
            case HOST_DISCOVERY -> applyHostDiscoveryPlan(taskId, task);
            case QUICK_SCAN -> applyQuickScanPlan(taskId, task);
            case FULL_SCAN -> applyFullScanPlan(taskId, task);
        }

        // Common: skip NOTIFICATION upfront if no webhook is configured
        if (!webhookNotificationService.isConfigured()) {
            scanTaskStageService.skipIfPending(taskId, ScanStageCode.NOTIFICATION,
                    "未配置通知回调地址，跳过通知");
        }
    }

    private void applyHostDiscoveryPlan(Long taskId, ScanTask task) {
        scanTaskStageService.skipIfPending(taskId, ScanStageCode.SERVICE_FINGERPRINT,
                "主机发现模式不执行服务识别");
        if (!Boolean.TRUE.equals(task.getEnableCrawler())) {
            scanTaskStageService.skipIfPending(taskId, ScanStageCode.WEB_PROBE,
                    "未启用爬虫发现，跳过 Web 探测");
        }
        scanTaskStageService.skipIfPending(taskId, ScanStageCode.VULNERABILITY_SCAN,
                "主机发现模式不执行漏洞检测");
        scanTaskStageService.skipIfPending(taskId, ScanStageCode.CVE_MATCH,
                "主机发现模式不执行 CVE 匹配");
    }

    private void applyQuickScanPlan(Long taskId, ScanTask task) {
        // WEB_PROBE depends on enableCrawler
        if (!Boolean.TRUE.equals(task.getEnableCrawler())) {
            scanTaskStageService.skipIfPending(taskId, ScanStageCode.WEB_PROBE,
                    "未启用爬虫发现，跳过 Web 探测");
        }
        // VULNERABILITY_SCAN + CVE_MATCH depend on enableVulnScan
        if (!Boolean.TRUE.equals(task.getEnableVulnScan())) {
            scanTaskStageService.skipIfPending(taskId, ScanStageCode.VULNERABILITY_SCAN,
                    "未启用漏洞检测，跳过漏洞检测");
            scanTaskStageService.skipIfPending(taskId, ScanStageCode.CVE_MATCH,
                    "未启用漏洞检测，跳过 CVE 匹配");
        }
    }

    private void applyFullScanPlan(Long taskId, ScanTask task) {
        // WEB_PROBE depends on enableCrawler
        if (!Boolean.TRUE.equals(task.getEnableCrawler())) {
            scanTaskStageService.skipIfPending(taskId, ScanStageCode.WEB_PROBE,
                    "未启用爬虫发现，跳过 Web 探测");
        }
        if (!Boolean.TRUE.equals(task.getEnableVulnScan())) {
            scanTaskStageService.skipIfPending(taskId, ScanStageCode.VULNERABILITY_SCAN,
                    "未启用漏洞检测");
            scanTaskStageService.skipIfPending(taskId, ScanStageCode.CVE_MATCH,
                    "未启用漏洞检测，跳过 CVE 匹配");
        }
    }
}
