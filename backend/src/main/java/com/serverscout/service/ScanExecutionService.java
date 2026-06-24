package com.serverscout.service;

import com.serverscout.entity.*;
import com.serverscout.entity.enums.ScanStageCode;
import com.serverscout.repository.*;
import com.serverscout.service.scan.*;
import com.serverscout.exception.ScanException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScanExecutionService {

    private final ScanTaskRepository scanTaskRepository;
    private final AssetRepository assetRepository;
    private final PortRepository portRepository;
    private final ScanAssetMappingRepository scanAssetMappingRepository;
    private final List<ScannerStrategy> scannerStrategies;
    private final ProgressEmitter progressEmitter;
    private final HttpProbeService httpProbeService;
    private final SslCertService sslCertService;
    private final SubdomainService subdomainService;
    private final CveDatabaseRepository cveDatabaseRepository;
    private final AssetVulnerabilityRepository assetVulnerabilityRepository;
    private final CertTransparencyService certTransparencyService;
    private final WebFingerprintRepository webFingerprintRepository;
    private final WebVulnDetectorService webVulnDetectorService;
    private final WebhookNotificationService webhookService;
    private final CrawlerService crawlerService;
    private final ScreenshotService screenshotService;
    private final HoneypotDetectionService honeypotDetectionService;
    private final ProcessRegistry processRegistry;
    private final TargetConcurrencyLimiter targetConcurrencyLimiter;
    private final ScanTaskStageService scanTaskStageService;
    private final ScanStagePlanService scanStagePlanService;
    private final RiskScoreService riskScoreService;

    @Value("${app.scan.http-probe-workers:8}")
    private int httpProbeWorkers;

    @Value("${app.scan.ssl-collect-workers:6}")
    private int sslCollectWorkers;

    @Value("${app.scan.auto-screenshot-limit:6}")
    private int autoScreenshotLimit;

    @Value("${app.scan.target-concurrency.max-wait-seconds:300}")
    private long targetAcquireMaxWaitSeconds;

    @Value("${app.scan.demo-mode:false}")
    private boolean demoMode;

    @Async("scanExecutor")
    public void executeScan(Long taskId) {
        ScanTask task = scanTaskRepository.findById(taskId).orElse(null);
        if (task == null) { log.error("Scan task {} not found", taskId); return; }
        TargetConcurrencyLimiter.Lease targetLease = null;
        long targetWaitStartedAt = System.nanoTime();

        try {
            while (targetLease == null) {
                if ("cancelled".equals(task.getStatus())) {
                    log.info("Scan task {} is already cancelled before execution", taskId);
                    return;
                }
                targetLease = targetConcurrencyLimiter.tryAcquire(task.getTargetRange());
                if (targetLease != null) {
                    break;
                }
                if (TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - targetWaitStartedAt)
                        >= Math.max(1L, targetAcquireMaxWaitSeconds)) {
                    throw new ScanException("Timed out waiting for another scan of the same target to finish");
                }

                if (!"pending".equals(task.getStatus())
                        || task.getProgress() == null
                        || task.getProgress() < 1) {
                    task.setStatus("pending");
                    task.setProgress(1);
                    scanTaskRepository.save(task);
                }
                progressEmitter.sendProgress(taskId,
                        task.getProgress() != null ? task.getProgress() : 1,
                        "Waiting for target scan slot...",
                        task.getTotalAssets() != null ? task.getTotalAssets() : 0);

                try {
                    Thread.sleep(targetConcurrencyLimiter.getAcquireWaitMs());
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }

                task = scanTaskRepository.findById(taskId).orElse(null);
                if (task == null) {
                    log.info("Scan task {} removed while waiting for target slot", taskId);
                    return;
                }
            }

            task.setStatus("running");
            task.setStartedAt(Instant.now());
            task.setProgress(5);
            scanTaskRepository.save(task);
            progressEmitter.sendProgress(taskId, 5, "Starting port scan...", 0);

            scanTaskStageService.initStages(taskId);
            scanTaskStageService.markSuccess(taskId, ScanStageCode.TARGET_VALIDATION, "目标校验通过");
            // Apply stage plan based on scan profile (marks non-applicable stages as SKIPPED)
            scanStagePlanService.applyInitialStagePlan(taskId, task);
            scanTaskStageService.markRunning(taskId, ScanStageCode.PORT_SCAN, "正在执行端口扫描...");

            final String scanType = task.getScanType();
            ScannerStrategy strategy = scannerStrategies.stream()
                    .filter(s -> s.supports(scanType))
                    .findFirst()
                    .orElseThrow(() -> new ScanException("No scanner for type: " + scanType));

            ScanResult result = strategy.execute(task);

            // For custom plugin scanners that only produce vulnerabilities (no assets),
            // run a quick Nmap scan first to discover hosts/ports
            if (result.getAssets().isEmpty() && strategy instanceof CustomCommandScanner) {
                log.info("Custom plugin scanner detected, running quick Nmap for asset discovery");
                ScannerStrategy quickScanner = scannerStrategies.stream()
                        .filter(s -> s.supports("quick"))
                        .findFirst().orElse(null);
                if (quickScanner != null) {
                    ScanTask quickTask = ScanTask.builder()
                            .targetRange(task.getTargetRange())
                            .scanType("quick")
                            .portRange(task.getPortRange() != null ? task.getPortRange() : "1-1000")
                            .build();
                    ScanResult quickResult = quickScanner.execute(quickTask);
                    result = ScanResult.builder()
                            .assets(quickResult.getAssets())
                            .vulnerabilities(result.getVulnerabilities())
                            .build();
                }
            }

            if (isCancelled(taskId)) return;
            int hostCount = result.getAssets().size();
            if (hostCount == 0) {
                progressEmitter.sendProgress(taskId, 30,
                        "未发现开放端口。可能原因：目标安全组未开放对应端口、防火墙拦截扫描、端口范围未包含实际开放端口、或目标不允许公网访问。请尝试指定端口或检查安全组设置。",
                        0);
            } else {
                progressEmitter.sendProgress(taskId, 30,
                        "Port scan completed, found " + hostCount + " hosts",
                        hostCount);
            }
            scanTaskStageService.markSuccess(taskId, ScanStageCode.PORT_SCAN,
                    "端口扫描完成，发现 " + hostCount + " 个主机");

            // Phase 1: Save/merge assets and ports with real-time discovery events
            if (Boolean.TRUE.equals(task.getEnableFingerprint())) {
                scanTaskStageService.markRunning(taskId, ScanStageCode.SERVICE_FINGERPRINT, "正在识别服务...");
            }
            scanTaskStageService.markRunning(taskId, ScanStageCode.RESULT_SAVE, "正在保存扫描结果...");
            int assetCount = saveScanResults(task, result);
            if (isCancelled(taskId)) return;

            task.setProgress(40);
            scanTaskRepository.save(task);
            progressEmitter.sendProgress(taskId, 40,
                    "Asset data saved, " + assetCount + " new hosts", assetCount);
            if (Boolean.TRUE.equals(task.getEnableFingerprint())) {
                scanTaskStageService.markSuccess(taskId, ScanStageCode.SERVICE_FINGERPRINT, "服务识别完成，共 " + assetCount + " 个资产");
            } else {
                scanTaskStageService.markSkipped(taskId, ScanStageCode.SERVICE_FINGERPRINT, "未启用指纹识别");
            }
            scanTaskStageService.markSuccess(taskId, ScanStageCode.RESULT_SAVE, "结果保存完成");

            // Demo mode: skip network-dependent phases, complete with mock vulnerabilities
            if (demoMode) {
                completeDemoScanTask(task, taskId, assetCount);
                return;
            }

            // Phase 1.5: Subdomain enumeration (if target is a domain)
            if (isDomainName(task.getTargetRange())) {
                try {
                    progressEmitter.sendProgress(taskId, 42, "Enumerating subdomains...", assetCount);
                    SubdomainService.SubdomainResult subResult = subdomainService.enumerate(task.getTargetRange());
                    if (isCancelled(taskId)) return;
                    progressEmitter.sendProgress(taskId, 44,
                            "Subdomain enumeration completed, found " + subResult.total() + " subdomains",
                            assetCount);
                    log.info("Task {}: subdomain enum found {} subdomains", taskId, subResult.total());
                } catch (Exception e) {
                    log.warn("Subdomain enumeration failed: {}", e.getMessage());
                }
            }

            // Phase 2: Web probe + crawler (controlled by enableCrawler)
            if (Boolean.TRUE.equals(task.getEnableCrawler())) {
                progressEmitter.sendProgress(taskId, 45, "Probing web services and crawling...", assetCount);
                boolean hasWebPorts = hasWebServicePorts(taskId);
                if (hasWebPorts) {
                    scanTaskStageService.markRunning(taskId, ScanStageCode.WEB_PROBE, "正在 Web 探测...");
                }
                probeWebServices(task);
                if (isCancelled(taskId)) return;

                List<ScanAssetMapping> crawlMappings = scanAssetMappingRepository
                        .findByScanTaskIdWithAsset(task.getId());
                List<Asset> webAssets = crawlMappings.stream()
                        .map(ScanAssetMapping::getAsset).distinct().collect(Collectors.toList());
                try {
                    int crawled = crawlerService.crawl(task, webAssets);
                    if (isCancelled(taskId)) return;
                    log.info("Task {}: crawler found {} pages", taskId, crawled);
                } catch (Exception e) {
                    log.warn("Crawler failed: {}", e.getMessage());
                }

                if (hasWebPorts) {
                    scanTaskStageService.markSuccess(taskId, ScanStageCode.WEB_PROBE, "Web 探测与爬虫完成");
                } else {
                    scanTaskStageService.markSkipped(taskId, ScanStageCode.WEB_PROBE, "未发现 Web 服务端口，跳过 Web 探测");
                }
                task.setProgress(55);
                scanTaskRepository.save(task);
                progressEmitter.sendProgress(taskId, 55, "Web probing and crawling completed", assetCount);
            } else {
                scanTaskStageService.markSkipped(taskId, ScanStageCode.WEB_PROBE, "未启用爬虫发现，跳过 Web 探测");
                task.setProgress(55);
            }

            // Phase 2.5: Vulnerability scanning (Nuclei + CVE matching)
            if (Boolean.TRUE.equals(task.getEnableVulnScan())) {
                progressEmitter.sendProgress(taskId, 57, "Running vulnerability scan...", assetCount);

                // Check if vuln scan can actually run
                ScannerStrategy vulnStrategy = scannerStrategies.stream()
                        .filter(s -> s.supports("nuclei"))
                        .findFirst().orElse(null);
                List<ScanAssetMapping> vulnMappings = scanAssetMappingRepository.findByScanTaskIdWithAsset(task.getId());
                boolean hasVulnTargets = !vulnMappings.isEmpty();

                if (vulnStrategy == null) {
                    scanTaskStageService.markSkipped(taskId, ScanStageCode.VULNERABILITY_SCAN, "Nuclei 未安装或不可用");
                } else if (!hasVulnTargets) {
                    scanTaskStageService.markSkipped(taskId, ScanStageCode.VULNERABILITY_SCAN, "无可检测目标");
                } else {
                    scanTaskStageService.markRunning(taskId, ScanStageCode.VULNERABILITY_SCAN, "正在执行漏洞检测...");
                    runVulnScan(task);
                    scanTaskStageService.markSuccess(taskId, ScanStageCode.VULNERABILITY_SCAN, "漏洞检测完成");
                }

                if (isCancelled(taskId)) return;
                task.setProgress(65);
                scanTaskRepository.save(task);
                progressEmitter.sendProgress(taskId, 65, "Vulnerability scan completed", assetCount);

                // CVE matching
                progressEmitter.sendProgress(taskId, 67, "Matching CVEs...", assetCount);
                boolean hasFingerprints = hasWebServicePorts(taskId);
                int matched = 0;
                if (!hasFingerprints) {
                    scanTaskStageService.markSkipped(taskId, ScanStageCode.CVE_MATCH, "未发现服务指纹，跳过 CVE 匹配");
                } else {
                    scanTaskStageService.markRunning(taskId, ScanStageCode.CVE_MATCH, "正在匹配 CVE...");
                    matched = matchCves(task);
                    if (isCancelled(taskId)) return;
                    scanTaskStageService.markSuccess(taskId, ScanStageCode.CVE_MATCH, "CVE 匹配完成，发现 " + matched + " 个漏洞");
                }

                task.setProgress(70);
                scanTaskRepository.save(task);
                progressEmitter.sendProgress(taskId, 70,
                        "CVE matching completed, found " + matched + " vulnerabilities", assetCount);
            } else {
                scanTaskStageService.markSkipped(taskId, ScanStageCode.VULNERABILITY_SCAN, "未启用漏洞检测");
                scanTaskStageService.markSkipped(taskId, ScanStageCode.CVE_MATCH, "未启用漏洞检测，跳过 CVE 匹配");
                task.setProgress(70);
            }

            // Phase 2.7: Web vulnerability detection (SQLi/XSS/CSRF)
            if (Boolean.TRUE.equals(task.getEnableVulnScan())) {
                progressEmitter.sendProgress(taskId, 72, "Running web vulnerability checks...", assetCount);
                List<ScanAssetMapping> allMappings = scanAssetMappingRepository
                        .findByScanTaskIdWithAsset(task.getId());
                List<Asset> detectedAssets = allMappings.stream()
                        .map(ScanAssetMapping::getAsset).distinct().collect(Collectors.toList());
                int webVulnCount = webVulnDetectorService.detect(task, detectedAssets,
                        portRepository, webFingerprintRepository);
                if (isCancelled(taskId)) return;
                task.setProgress(75);
                scanTaskRepository.save(task);
                progressEmitter.sendProgress(taskId, 75,
                        "Web vulnerability detection completed, found " + webVulnCount + " findings", assetCount);
            }

            // Phase 3: SSL cert collection
            progressEmitter.sendProgress(taskId, 78, "Collecting SSL certificates...", assetCount);
            collectSslCerts(task);
            if (isCancelled(taskId)) return;
            task.setProgress(85);
            scanTaskRepository.save(task);
            progressEmitter.sendProgress(taskId, 85, "SSL certificate collection completed", task.getTotalPorts());

            // Phase 3.5: Certificate transparency analysis
            try {
                progressEmitter.sendProgress(taskId, 87, "Analyzing certificate transparency...", assetCount);
                var ctResult = certTransparencyService.analyzeCertificates(task);
                if (isCancelled(taskId)) return;
                progressEmitter.sendProgress(taskId, 88,
                        "Certificate transparency completed, found " + ctResult.get("uniqueDomains") + " unique domains",
                        assetCount);
                log.info("Task {}: CT analysis found {} domains", taskId, ctResult.get("uniqueDomains"));
            } catch (Exception e) {
                log.warn("Certificate transparency analysis failed: {}", e.getMessage());
            }

            // Phase 3.7: Honeypot detection (fingerprint-based, offline)
            try {
                progressEmitter.sendProgress(taskId, 89, "Running honeypot detection...", assetCount);
                List<ScanAssetMapping> hpMappings = scanAssetMappingRepository
                        .findByScanTaskIdWithAsset(task.getId());
                List<Asset> hpAssets = hpMappings.stream()
                        .map(ScanAssetMapping::getAsset).distinct().collect(Collectors.toList());
                int honeypotCount = 0;
                for (Asset a : hpAssets) {
                    var detections = honeypotDetectionService.detectByFingerprint(a);
                    if (!detections.isEmpty()) honeypotCount++;
                }
                progressEmitter.sendProgress(taskId, 90,
                        "Honeypot detection completed, found " + honeypotCount + " suspicious assets", assetCount);
                log.info("Task {}: honeypot detection found {} suspicious assets", taskId, honeypotCount);
            } catch (Exception e) {
                log.warn("Honeypot detection failed: {}", e.getMessage());
            }

            // Risk analysis phase
            scanTaskStageService.markRunning(taskId, ScanStageCode.RISK_ANALYSIS, "正在执行风险分析...");
            try {
                riskScoreService.calculateForTask(taskId);
                scanTaskStageService.markSuccess(taskId, ScanStageCode.RISK_ANALYSIS, "风险分析完成");
            } catch (Exception e) {
                log.warn("Risk analysis failed for task {}: {}", taskId, e.getMessage());
                scanTaskStageService.markFailed(taskId, ScanStageCode.RISK_ANALYSIS, "风险分析失败: " + e.getMessage());
            }

            task.setProgress(90);
            task.setStatus("completed");
            task.setProgress(100);
            task.setCompletedAt(Instant.now());
            scanTaskRepository.save(task);
            log.info("Scan task {} completed successfully", taskId);

            // NOTIFICATION stage handling (after task is saved as completed)
            if (webhookService.isConfigured()) {
                scanTaskStageService.markRunning(taskId, ScanStageCode.NOTIFICATION, "正在发送通知...");
                try {
                    webhookService.sendScanCompletedNotification(taskId);
                    scanTaskStageService.markSuccess(taskId, ScanStageCode.NOTIFICATION, "通知发送完成");
                } catch (Exception e) {
                    log.warn("Webhook notification failed for task {}: {}", taskId, e.getMessage());
                    scanTaskStageService.markFailed(taskId, ScanStageCode.NOTIFICATION, "通知发送失败: " + e.getMessage());
                }
            } else {
                scanTaskStageService.markSkipped(taskId, ScanStageCode.NOTIFICATION, "未配置通知回调地址，跳过通知");
            }

            // Safety net: ensure absolutely no PENDING/RUNNING stages remain
            scanTaskStageService.finalizeUnfinishedStages(taskId, "任务已完成，未执行的可选阶段已跳过");

            progressEmitter.sendCompleted(taskId);

        } catch (Exception e) {
            if (isCancelled(taskId)) {
                scanTaskStageService.finalizeUnfinishedStages(taskId, "任务已取消");
                processRegistry.cleanup(taskId);
                return;
            }

            ScanTask latest = scanTaskRepository.findById(taskId).orElse(null);
            if (latest == null) {
                log.info("Scan task {} was deleted after failure, stop retry", taskId);
                return;
            }
            task = latest;

            int currentRetry = task.getRetryCount() != null ? task.getRetryCount() : 0;
            int maxRetries = task.getMaxRetries() != null ? task.getMaxRetries() : 3;
            log.error("Scan failed for task {} (attempt {}/{})", taskId,
                    currentRetry + 1, maxRetries + 1, e);

            if (currentRetry < maxRetries) {
                task.setRetryCount(currentRetry + 1);
                task.setErrorMessage("Retry " + task.getRetryCount() + "/" + maxRetries
                        + ": " + (e.getMessage() != null ? e.getMessage() : "scan execution error"));
                task.setStatus("pending");
                task.setProgress(1);
                scanTaskRepository.save(task);
                progressEmitter.sendProgress(taskId, 1,
                        "Scan failed, retrying (" + task.getRetryCount() + "/" + maxRetries + ")...", 0);

                // Release slot before retry to avoid self-deadlock on the same target.
                targetConcurrencyLimiter.release(targetLease);
                targetLease = null;

                long backoffMs = Math.min(60000L, 5000L * (long) Math.pow(2, currentRetry));
                try { Thread.sleep(backoffMs); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); return; }

                executeScan(taskId);
                return;
            }

            scanTaskStageService.finalizeUnfinishedStages(taskId, "扫描执行失败");

            task.setStatus("failed");
            task.setErrorMessage(e.getMessage() != null ? e.getMessage() : "scan execution error");
            try { scanTaskRepository.save(task); } catch (Exception ex) { log.error("Failed to save task", ex); }
            progressEmitter.sendError(taskId, task.getErrorMessage());
            if (webhookService.isConfigured()) {
                try {
                    webhookService.sendScanCompletedNotification(taskId);
                } catch (Exception ignored) {
                    // notification errors are non-fatal here
                }
            }
        } finally {
            targetConcurrencyLimiter.release(targetLease);
            processRegistry.cleanup(taskId);
        }
    }

    public void forceCancel(Long taskId) {
        processRegistry.destroyAll(taskId);
        log.info("Force-cancelled all running processes for task {}", taskId);
    }

    /**
     * Demo mode completion: runs mock vulnerability scan, skips all network-dependent phases.
     * Only called when app.scan.demo-mode=true.
     */
    private void completeDemoScanTask(ScanTask task, Long taskId, int assetCount) {
        try {
            // Web Probe is skipped in demo mode
            scanTaskStageService.markSkipped(taskId, ScanStageCode.WEB_PROBE, "Demo 模式跳过 Web 探测");

            // For full scans, ensure vulnerabilities are generated even if frontend omitted the flag
            if (!Boolean.TRUE.equals(task.getEnableVulnScan()) && "FULL".equalsIgnoreCase(task.getScanType())) {
                task.setEnableVulnScan(true);
            }

            // Run vulnerability scan (DemoScannerStrategy handles mock vulns)
            if (Boolean.TRUE.equals(task.getEnableVulnScan())) {
                scanTaskStageService.markRunning(taskId, ScanStageCode.VULNERABILITY_SCAN,
                        "正在执行漏洞检测...");
                progressEmitter.sendProgress(taskId, 57, "Running vulnerability scan...", assetCount);
                runVulnScan(task);
                if (isCancelled(taskId)) return;
                scanTaskStageService.markSuccess(taskId, ScanStageCode.VULNERABILITY_SCAN,
                        "漏洞检测完成");
                // CVE matching — DemoScannerStrategy generates CVE-tagged vulns directly
                scanTaskStageService.markSuccess(taskId, ScanStageCode.CVE_MATCH, "CVE 匹配完成（Demo 模式使用预设数据）");
            } else {
                scanTaskStageService.markSkipped(taskId, ScanStageCode.VULNERABILITY_SCAN,
                        "未启用漏洞扫描");
                scanTaskStageService.markSkipped(taskId, ScanStageCode.CVE_MATCH,
                        "未启用漏洞扫描，跳过 CVE 匹配");
            }

            // Risk analysis — calculate risk scores for all assets
            scanTaskStageService.markRunning(taskId, ScanStageCode.RISK_ANALYSIS, "正在执行风险分析...");
            try {
                riskScoreService.calculateForTask(taskId);
                scanTaskStageService.markSuccess(taskId, ScanStageCode.RISK_ANALYSIS, "风险分析完成");
            } catch (Exception e) {
                log.warn("Risk analysis failed for task {}: {}", taskId, e.getMessage());
                scanTaskStageService.markFailed(taskId, ScanStageCode.RISK_ANALYSIS, "风险分析失败: " + e.getMessage());
            }
            task.setProgress(90);
            scanTaskRepository.save(task);

            task.setStatus("completed");
            task.setProgress(100);
            task.setCompletedAt(Instant.now());
            scanTaskRepository.save(task);
            // Safety net: ensure absolutely no PENDING/RUNNING stages remain
            scanTaskStageService.finalizeUnfinishedStages(taskId, "任务已完成，未执行的可选阶段已跳过");
            progressEmitter.sendCompleted(taskId);
            log.info("Scan task {} completed (demo mode)", taskId);
        } catch (Exception e) {
            log.warn("Demo completion error for task {}: {}", taskId, e.getMessage());
            scanTaskStageService.markFailed(taskId, ScanStageCode.RISK_ANALYSIS,
                    "Demo 完成阶段出错: " + e.getMessage());
            task.setStatus("completed");
            task.setProgress(100);
            task.setCompletedAt(Instant.now());
            try { scanTaskRepository.save(task); } catch (Exception ignored) {}
            // Safety net even on error
            scanTaskStageService.finalizeUnfinishedStages(taskId, "任务已完成，未执行的可选阶段已跳过");
            progressEmitter.sendCompleted(taskId);
        }

        // Webhook notification — mark stage regardless of outcome
        try {
            scanTaskStageService.markRunning(taskId, ScanStageCode.NOTIFICATION, "正在发送通知...");
            webhookService.sendScanCompletedNotification(taskId);
            scanTaskStageService.markSuccess(taskId, ScanStageCode.NOTIFICATION, "通知发送完成");
        } catch (Exception e) {
            log.warn("Webhook notification failed for demo task {}: {}", taskId, e.getMessage());
            scanTaskStageService.markFailed(taskId, ScanStageCode.NOTIFICATION,
                    "通知发送失败: " + e.getMessage());
        }
        // Ultimate safety net: finalize any stragglers
        scanTaskStageService.finalizeUnfinishedStages(taskId, "任务已完成，未执行的可选阶段已跳过");
    }

    private boolean isCancelled(Long taskId) {
        ScanTask task = scanTaskRepository.findById(taskId).orElse(null);
        if (task == null) {
            log.info("Scan task {} no longer exists, stopping execution", taskId);
            return true;
        }
        if ("cancelled".equals(task.getStatus())) {
            log.info("Scan task {} was cancelled, stopping execution", taskId);
            progressEmitter.sendProgress(taskId,
                    task.getProgress() != null ? task.getProgress() : 0,
                    "Scan has been cancelled",
                    task.getTotalAssets() != null ? task.getTotalAssets() : 0);
            progressEmitter.sendError(taskId, "Scan cancelled by user");
            return true;
        }
        return false;
    }

    /** Check if the task has any ports marked as web services. */
    private boolean hasWebServicePorts(Long taskId) {
        List<ScanAssetMapping> mappings = scanAssetMappingRepository.findByScanTaskIdWithAsset(taskId);
        if (mappings.isEmpty()) return false;
        List<Long> assetIds = mappings.stream()
                .map(m -> m.getAsset().getId())
                .distinct()
                .toList();
        return portRepository.findByAssetIdIn(assetIds).stream()
                .anyMatch(p -> Boolean.TRUE.equals(p.getIsWebService()));
    }

    @Transactional
    protected int saveScanResults(ScanTask task, ScanResult result) {
        int newAssets = 0;
        int totalPorts = 0;

        List<String> ips = result.getAssets().stream()
                .map(ScanResult.AssetEntry::getIpAddress)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<String, Asset> existingByIp = assetRepository.findByIpAddressIn(ips).stream()
                .collect(Collectors.toMap(Asset::getIpAddress, a -> a, (a, b) -> a));

        for (ScanResult.AssetEntry ae : result.getAssets()) {
            Asset asset = existingByIp.get(ae.getIpAddress());
            boolean isNew = false;

            if (asset != null) {
                if (ae.getHostname() != null) {
                    appendHostname(asset, ae.getHostname());
                }
                if (ae.getOsFingerprint() != null) asset.setOsFingerprint(ae.getOsFingerprint());
                if (ae.getOsVersion() != null) asset.setOsVersion(ae.getOsVersion());
                if (ae.getMacAddress() != null) asset.setMacAddress(ae.getMacAddress());
                asset.setStatus("alive");
                asset.setLastScanTime(Instant.now());
            } else {
                asset = Asset.builder()
                        .task(task).ipAddress(ae.getIpAddress())
                        .hostname(ae.getHostname()).macAddress(ae.getMacAddress())
                        .osFingerprint(ae.getOsFingerprint()).osVersion(ae.getOsVersion())
                        .status("alive").openPortCount(0).criticalVulnCount(0).tags("[]")
                        .build();
                isNew = true;
                newAssets++;
            }

            asset = assetRepository.save(asset);
            existingByIp.put(asset.getIpAddress(), asset);

            PortSyncSummary syncSummary = syncPorts(asset, ae.getPorts());
            asset.setOpenPortCount(syncSummary.openPortCount());
            assetRepository.save(asset);

            ScanAssetMapping mapping = scanAssetMappingRepository
                    .findByScanTaskIdAndAssetId(task.getId(), asset.getId())
                    .orElse(ScanAssetMapping.builder().scanTask(task).asset(asset).build());
            mapping.setScanTime(Instant.now());
            mapping.setIsNew(isNew);
            mapping.setPortsFound(syncSummary.detectedCount());
            scanAssetMappingRepository.save(mapping);

            List<Map<String, Object>> portInfoList = ae.getPorts().stream()
                    .map(p -> {
                        Map<String, Object> pi = new HashMap<>();
                        pi.put("port", p.getPortNumber());
                        pi.put("protocol", p.getProtocol());
                        pi.put("service", p.getServiceName() != null ? p.getServiceName() : "");
                        pi.put("version", p.getServiceVersion() != null ? p.getServiceVersion() : "");
                        pi.put("product", p.getServiceProduct() != null ? p.getServiceProduct() : "");
                        return pi;
                    })
                    .collect(Collectors.toList());

            progressEmitter.sendDiscoveredAsset(task.getId(), ae.getIpAddress(),
                    ae.getHostname(), ae.getOsFingerprint(), syncSummary.detectedCount(), portInfoList);
            progressEmitter.sendProgress(task.getId(),
                    30 + (int) ((double) (newAssets + totalPorts) / Math.max(result.getAssets().size(), 1) * 10),
                    "Discovered host: " + ae.getIpAddress() + " (" + syncSummary.detectedCount() + " ports)",
                    newAssets + totalPorts);

            totalPorts += syncSummary.detectedCount();
        }

        task.setTotalAssets(result.getAssets().size());
        task.setTotalPorts(totalPorts);
        log.info("Scan {} saved: {} new, {} touched, {} ports",
                task.getId(), newAssets, result.getAssets().size(), totalPorts);
        return newAssets;
    }

    private record PortSyncSummary(int detectedCount, int openPortCount) {}

    private PortSyncSummary syncPorts(Asset asset, List<ScanResult.PortEntry> portEntries) {
        List<Port> existingPorts = portRepository.findByAssetId(asset.getId());
        Map<String, Port> existingByKey = new HashMap<>();
        for (Port existing : existingPorts) {
            String key = existing.getPortNumber() + "/" + (existing.getProtocol() != null ? existing.getProtocol() : "tcp");
            existingByKey.put(key, existing);
        }

        List<Port> toSave = new ArrayList<>();
        int detectedCount = 0;

        for (ScanResult.PortEntry pe : portEntries) {
            String protocol = pe.getProtocol() != null ? pe.getProtocol() : "tcp";
            String key = pe.getPortNumber() + "/" + protocol;
            Port port = existingByKey.get(key);

            if (port == null) {
                port = Port.builder()
                        .asset(asset)
                        .portNumber(pe.getPortNumber())
                        .protocol(protocol)
                        .build();
                existingByKey.put(key, port);
            }

            if (pe.getServiceName() != null) port.setServiceName(pe.getServiceName());
            if (pe.getServiceVersion() != null) port.setServiceVersion(pe.getServiceVersion());
            if (pe.getServiceProduct() != null) port.setServiceProduct(pe.getServiceProduct());
            if (pe.getState() != null) port.setState(pe.getState());
            if (pe.getBanner() != null) port.setBanner(pe.getBanner());
            if (port.getState() == null) port.setState("open");
            port.setIsWebService(isWebPort(pe.getPortNumber(), pe.getServiceName()));

            toSave.add(port);
            detectedCount++;
        }

        if (!toSave.isEmpty()) {
            portRepository.saveAll(toSave);
        }

        return new PortSyncSummary(detectedCount, existingByKey.size());
    }

    private void probeWebServices(ScanTask task) {
        List<ScanAssetMapping> mappings = scanAssetMappingRepository.findByScanTaskIdWithAsset(task.getId());
        List<Asset> assets = mappings.stream().map(ScanAssetMapping::getAsset).distinct().collect(Collectors.toList());
        if (assets.isEmpty()) {
            log.info("Task {}: no assets for HTTP probing", task.getId());
            return;
        }

        List<Long> assetIds = assets.stream().map(Asset::getId).toList();
        Map<Long, List<Port>> portsByAsset = portRepository.findByAssetIdIn(assetIds).stream()
                .collect(Collectors.groupingBy(p -> p.getAsset().getId()));

        record WebProbeTarget(String ip, Port port) {}
        List<WebProbeTarget> targets = new ArrayList<>();
        for (Asset asset : assets) {
            for (Port port : portsByAsset.getOrDefault(asset.getId(), Collections.emptyList())) {
                if (Boolean.TRUE.equals(port.getIsWebService())) {
                    targets.add(new WebProbeTarget(asset.getIpAddress(), port));
                }
            }
        }

        if (targets.isEmpty()) {
            log.info("Task {}: no web-service ports for HTTP probing", task.getId());
            return;
        }

        int workers = Math.min(Math.max(2, httpProbeWorkers), Math.max(2, targets.size()));
        ExecutorService executor = Executors.newFixedThreadPool(workers);
        List<Future<?>> futures = new ArrayList<>();
        AtomicInteger probed = new AtomicInteger(0);
        AtomicInteger screenshots = new AtomicInteger(0);

        for (WebProbeTarget target : targets) {
            futures.add(executor.submit(() -> {
                String ip = target.ip();
                Port port = target.port();
                try {
                    HttpProbeService.ProbeResult pr = httpProbeService.probePort(ip, port);
                    if (pr == null) return;

                    httpProbeService.saveProbeResult(port, pr);
                    int done = probed.incrementAndGet();

                    progressEmitter.sendDiscoveredFingerprint(task.getId(), ip,
                            port.getPortNumber(), pr.serverHeader(), pr.frameworkName(),
                            pr.cmsName(), pr.title());

                    progressEmitter.sendProgress(task.getId(),
                            45 + (int) ((double) done / Math.max(targets.size(), 1) * 10),
                            "Discovered web service: " + ip + ":" + port.getPortNumber()
                                    + (pr.cmsName() != null ? " [" + pr.cmsName() + "]" : ""),
                            task.getTotalAssets());

                    if (screenshots.incrementAndGet() <= Math.max(0, autoScreenshotLimit)) {
                        try {
                            String scheme = port.getPortNumber() == 443 || port.getPortNumber() == 8443 ? "https" : "http";
                            screenshotService.captureAndSave(scheme + "://" + ip + ":" + port.getPortNumber(), 1280, 800);
                        } catch (Exception e) {
                            log.debug("Auto-screenshot failed for {}:{}: {}", ip, port.getPortNumber(), e.getMessage());
                        }
                    }
                } catch (Exception e) {
                    log.debug("HTTP probe error on {}:{} - {}", ip, port.getPortNumber(), e.getMessage());
                }
            }));
        }

        for (Future<?> future : futures) {
            try {
                future.get(45, TimeUnit.SECONDS);
            } catch (Exception e) {
                log.debug("HTTP probe worker failed: {}", e.getMessage());
            }
        }
        executor.shutdownNow();

        log.info("Task {}: HTTP probed {} services out of {} web targets",
                task.getId(), probed.get(), targets.size());
    }

    private void collectSslCerts(ScanTask task) {
        List<ScanAssetMapping> mappings = scanAssetMappingRepository.findByScanTaskIdWithAsset(task.getId());
        List<Asset> assets = mappings.stream().map(ScanAssetMapping::getAsset).distinct().collect(Collectors.toList());
        if (assets.isEmpty()) return;

        List<Long> assetIds = assets.stream().map(Asset::getId).toList();
        Map<Long, List<Port>> portsByAsset = portRepository.findByAssetIdIn(assetIds).stream()
                .collect(Collectors.groupingBy(p -> p.getAsset().getId()));

        record SslTarget(String ip, Port port) {}
        List<SslTarget> targets = new ArrayList<>();
        for (Asset asset : assets) {
            for (Port port : portsByAsset.getOrDefault(asset.getId(), Collections.emptyList())) {
                if (port.getPortNumber() == 443 || port.getPortNumber() == 8443 ||
                        port.getPortNumber() == 636 || port.getPortNumber() == 993 ||
                        port.getPortNumber() == 995 ||
                        (port.getServiceName() != null && port.getServiceName().toLowerCase().contains("https"))) {
                    targets.add(new SslTarget(asset.getIpAddress(), port));
                }
            }
        }

        if (targets.isEmpty()) return;

        int workers = Math.min(Math.max(2, sslCollectWorkers), Math.max(2, targets.size()));
        ExecutorService executor = Executors.newFixedThreadPool(workers);
        AtomicInteger certs = new AtomicInteger(0);
        List<Future<?>> futures = new ArrayList<>();

        for (SslTarget target : targets) {
            futures.add(executor.submit(() -> {
                try {
                    SslCertService.SslCertResult cr = sslCertService.fetchCertificate(target.ip(), target.port());
                    if (cr != null) {
                        sslCertService.saveCertResult(target.port(), cr);
                        certs.incrementAndGet();
                    }
                } catch (Exception e) {
                    log.debug("SSL cert error on {}:{} - {}",
                            target.ip(), target.port().getPortNumber(), e.getMessage());
                }
            }));
        }

        for (Future<?> future : futures) {
            try {
                future.get(45, TimeUnit.SECONDS);
            } catch (Exception e) {
                log.debug("SSL worker failed: {}", e.getMessage());
            }
        }
        executor.shutdownNow();

        log.info("Task {}: collected {} SSL certificates", task.getId(), certs.get());
    }

    private void runVulnScan(ScanTask task) {
        try {
            ScannerStrategy vulnStrategy = scannerStrategies.stream()
                    .filter(s -> s.supports("nuclei"))
                    .findFirst()
                    .orElse(null);
            if (vulnStrategy == null) {
                log.warn("No nuclei scanner found");
                return;
            }

            ScanResult vulnResult = vulnStrategy.execute(task);
            List<ScanResult.VulnEntry> findings = vulnResult.getVulnerabilities();
            if (findings == null || findings.isEmpty()) {
                log.info("Task {}: nuclei found 0 vulns", task.getId());
                return;
            }

            List<ScanAssetMapping> mappings = scanAssetMappingRepository.findByScanTaskIdWithAsset(task.getId());
            List<Asset> assets = mappings.stream().map(ScanAssetMapping::getAsset).distinct().collect(Collectors.toList());
            if (assets.isEmpty()) {
                log.info("Task {}: nuclei found {} vulns but no assets mapped", task.getId(), findings.size());
                return;
            }

            Map<String, Asset> assetByIp = assets.stream()
                    .collect(Collectors.toMap(Asset::getIpAddress, a -> a, (a, b) -> a));
            Map<Long, Set<String>> existingCvesByAsset = loadExistingCveIdsByAsset(
                    assets.stream().map(Asset::getId).toList());

            for (ScanResult.VulnEntry vuln : findings) {
                CveDatabase cve = cveDatabaseRepository.findByCveId(vuln.getTemplate()).orElse(null);
                if (cve == null && vuln.getTemplate() != null && vuln.getTemplate().startsWith("CVE-")) {
                    cve = CveDatabase.builder()
                            .cveId(vuln.getTemplate())
                            .description(vuln.getName())
                            .severity(vuln.getSeverity())
                            .affectedSoftware(vuln.getMatched())
                            .build();
                    try {
                        cve = cveDatabaseRepository.save(cve);
                    } catch (DataIntegrityViolationException duplicate) {
                        cve = cveDatabaseRepository.findByCveId(vuln.getTemplate()).orElse(cve);
                    }
                }

                progressEmitter.sendDiscoveredVuln(task.getId(), vuln.getSeverity(),
                        vuln.getTemplate(), vuln.getName(), vuln.getUrl(), vuln.getMatched());

                if (cve == null || vuln.getTemplate() == null) continue;

                Set<Asset> matchedAssets = new LinkedHashSet<>();
                String host = extractHostFromUrl(vuln.getUrl());
                if (host != null) {
                    Asset hostAsset = assetByIp.get(host);
                    if (hostAsset != null) matchedAssets.add(hostAsset);
                }
                if (matchedAssets.isEmpty() && vuln.getUrl() != null) {
                    for (Map.Entry<String, Asset> entry : assetByIp.entrySet()) {
                        if (vuln.getUrl().contains(entry.getKey())) {
                            matchedAssets.add(entry.getValue());
                        }
                    }
                }

                for (Asset asset : matchedAssets) {
                    Set<String> existing = existingCvesByAsset.computeIfAbsent(asset.getId(), k -> new HashSet<>());
                    if (existing.contains(vuln.getTemplate())) continue;

                    AssetVulnerability av = AssetVulnerability.builder()
                            .asset(asset)
                            .cveDatabase(cve)
                            .status("open")
                            .build();
                    try {
                        assetVulnerabilityRepository.save(av);
                        existing.add(vuln.getTemplate());
                    } catch (DataIntegrityViolationException duplicate) {
                        existing.add(vuln.getTemplate());
                    }
                }
            }

            log.info("Task {}: nuclei found {} vulns", task.getId(), findings.size());
        } catch (Exception e) {
            log.warn("Vulnerability scan failed: {}", e.getMessage());
        }
    }

    // Only match CVEs against web middleware, frameworks, CMS, and server software
    private static final Set<String> GENERIC_SERVICES = Set.of(
            "http", "https", "http-proxy", "tcpwrapped", "ssh", "ftp", "smtp",
            "dns", "dhcp", "snmp", "telnet", "pop3", "imap", "nfs", "ntp",
            "netbios-ssn", "microsoft-ds", "msrpc", "mysql", "postgresql",
            "rdp", "vnc", "sip", "rtsp", "ipp", "cups", "upnp");

    // OS-level and generic software CVEs to skip entirely
    private static final Set<String> SKIP_CVE_AFFECTED = Set.of(
            "microsoft windows", "microsoft windows server", "microsoft windows 10",
            "microsoft windows 7", "microsoft windows 8.1", "microsoft windows 11",
            "microsoft windows rdp", "microsoft http.sys", "microsoft windows print spooler",
            "linux kernel", "sudo", "polkit", "glibc", "gnu c library",
            "runc", "arm mali", "arm mali gpu driver", "libcurl",
            "keepass", "pymatgen", "langchain", "ollama", "xz utils",
            "microsoft exchange server", "microsoft sharepoint",
            "microsoft office", "microsoft netlogon", "microsoft smbv3",
            "solarwinds", "solarwinds orion", "cobalt strike", "exiftool",
            "pear archive_tar", "phpunit", "systeminformation",
            "systeminformation (npm)", "anscale ray", "goanywhere mft",
            "http/2", "vbulletin", "sap netweaver");

    // Domain keywords that indicate web-app level software
    private static final Set<String> WEB_APP_KEYWORDS = Set.of(
            "apache", "nginx", "tomcat", "spring", "wordpress", "drupal", "joomla",
            "jenkins", "laravel", "rails", "django", "flask", "express", "node",
            "react", "vue", "angular", "jquery", "bootstrap", "php", "asp.net",
            "struts", "weblogic", "websphere", "jboss", "wildfly", "jetty",
            "iis", "caddy", "haproxy", "traefik", "envoy", "kong",
            "grafana", "kibana", "elasticsearch", "logstash", "solr", "lucene",
            "redis", "memcached", "rabbitmq", "activemq", "kafka", "rocketmq",
            "zookeeper", "consul", "etcd", "vault", "keycloak",
            "gitlab", "github", "bitbucket", "confluence", "jira",
            "nextcloud", "owncloud", "moodle", "magento", "shopify",
            "f5", "citrix", "fortinet", "pulse", "sonicwall", "cisco",
            "vmware", "vcenter", "esxi", "oracle", "mysql", "postgresql",
            "mariadb", "mongodb", "cassandra", "couchdb", "couchbase",
            "elastic", "splunk", "datadog", "new relic", "dynatrace");

    private int matchCves(ScanTask task) {
        int matched = 0;
        try {
            List<ScanAssetMapping> mappings = scanAssetMappingRepository.findByScanTaskIdWithAsset(task.getId());
            List<Asset> assets = mappings.stream().map(ScanAssetMapping::getAsset).distinct().collect(Collectors.toList());
            if (assets.isEmpty()) return 0;

            List<Long> assetIds = assets.stream().map(Asset::getId).toList();
            List<Port> allPorts = portRepository.findByAssetIdIn(assetIds);
            Map<Long, List<Port>> portsByAsset = allPorts.stream()
                    .collect(Collectors.groupingBy(p -> p.getAsset().getId()));

            List<Long> portIds = allPorts.stream().map(Port::getId).toList();
            Map<Long, WebFingerprint> wfByPortId = portIds.isEmpty()
                    ? Collections.emptyMap()
                    : webFingerprintRepository.findByPortIdIn(portIds).stream()
                        .collect(Collectors.toMap(wf -> wf.getPort().getId(), wf -> wf, (a, b) -> a));

            Map<Long, Set<Long>> existingCveDbIdsByAsset = loadExistingCveDbIdsByAsset(assetIds);
            Map<String, List<CveDatabase>> cveCache = new HashMap<>();

            for (Asset asset : assets) {
                List<Port> ports = portsByAsset.getOrDefault(asset.getId(), Collections.emptyList());
                Set<String> matchedProducts = new HashSet<>();

                for (Port port : ports) {
                    WebFingerprint wf = wfByPortId.get(port.getId());
                    if (wf != null) {
                        if (wf.getCmsName() != null && wf.getCmsName().length() >= 3) {
                            matchedProducts.add(wf.getCmsName().toLowerCase());
                        }
                        if (wf.getFrameworkName() != null && wf.getFrameworkName().length() >= 3) {
                            matchedProducts.add(wf.getFrameworkName().toLowerCase());
                        }
                        if (wf.getWafName() != null && wf.getWafName().length() >= 3) {
                            matchedProducts.add(wf.getWafName().toLowerCase());
                        }
                        if (wf.getServerHeader() != null && wf.getServerHeader().length() >= 3) {
                            matchedProducts.add(wf.getServerHeader().toLowerCase());
                        }
                    }

                    if (Boolean.TRUE.equals(port.getIsWebService())) {
                        String product = port.getServiceProduct() != null ? port.getServiceProduct().toLowerCase().trim() : "";
                        String svcName = port.getServiceName() != null ? port.getServiceName().toLowerCase().trim() : "";
                        if (product.length() >= 3 && !GENERIC_SERVICES.contains(product) && isWebAppProduct(product)) {
                            matchedProducts.add(product);
                        }
                        if (!svcName.isEmpty() && !GENERIC_SERVICES.contains(svcName) && isWebAppProduct(svcName)) {
                            matchedProducts.add(svcName);
                        }
                    }
                }

                Set<Long> existing = existingCveDbIdsByAsset.computeIfAbsent(asset.getId(), k -> new HashSet<>());
                int criticalAdded = 0;

                for (String product : matchedProducts) {
                    List<CveDatabase> cves = cveCache.computeIfAbsent(product,
                            p -> cveDatabaseRepository.findByAffectedSoftwareContaining(p));

                    for (CveDatabase cve : cves) {
                        if (cve.getAffectedSoftware() != null
                                && SKIP_CVE_AFFECTED.contains(cve.getAffectedSoftware().toLowerCase())) {
                            continue;
                        }
                        if (existing.contains(cve.getId())) continue;

                        AssetVulnerability av = AssetVulnerability.builder()
                                .asset(asset).cveDatabase(cve)
                                .status("open").build();
                        try {
                            assetVulnerabilityRepository.save(av);
                            matched++;
                            existing.add(cve.getId());
                            if ("critical".equalsIgnoreCase(cve.getSeverity())) {
                                criticalAdded++;
                            }
                            progressEmitter.sendDiscoveredVuln(task.getId(),
                                    cve.getSeverity(), cve.getCveId(), cve.getDescription(),
                                    asset.getIpAddress(), cve.getAffectedSoftware());
                        } catch (DataIntegrityViolationException duplicate) {
                            existing.add(cve.getId());
                        }
                    }
                }

                if (criticalAdded > 0) {
                    asset.setCriticalVulnCount((asset.getCriticalVulnCount() == null ? 0 : asset.getCriticalVulnCount()) + criticalAdded);
                    assetRepository.save(asset);
                }
            }
        } catch (Exception e) {
            log.warn("CVE matching failed: {}", e.getMessage());
        }
        return matched;
    }

    /** Check if a product name is likely a web application, not an OS or system utility */
    private boolean isWebAppProduct(String product) {
        if (product == null || product.length() < 3) return false;
        // Must contain at least one web-app keyword
        for (String keyword : WEB_APP_KEYWORDS) {
            if (product.contains(keyword)) return true;
        }
        // Check if product looks like a web technology (has version, known patterns)
        return product.contains("web") || product.contains("http")
                || product.contains("cms") || product.contains("blog")
                || product.contains("shop") || product.contains("forum")
                || product.contains("wiki") || product.contains("portal");
    }

    private Map<Long, Set<String>> loadExistingCveIdsByAsset(List<Long> assetIds) {
        Map<Long, Set<String>> result = new HashMap<>();
        if (assetIds == null || assetIds.isEmpty()) return result;
        for (Object[] row : assetVulnerabilityRepository.findAssetCvePairsByAssetIds(assetIds)) {
            Long assetId = row[0] instanceof Long ? (Long) row[0] : null;
            String cveId = row[1] instanceof String ? (String) row[1] : null;
            if (assetId == null || cveId == null) continue;
            result.computeIfAbsent(assetId, k -> new HashSet<>()).add(cveId);
        }
        return result;
    }

    private Map<Long, Set<Long>> loadExistingCveDbIdsByAsset(List<Long> assetIds) {
        Map<Long, Set<Long>> result = new HashMap<>();
        if (assetIds == null || assetIds.isEmpty()) return result;
        for (Object[] row : assetVulnerabilityRepository.findAssetCveDbPairsByAssetIds(assetIds)) {
            Long assetId = row[0] instanceof Long ? (Long) row[0] : null;
            Long cveDbId = row[1] instanceof Long ? (Long) row[1] : null;
            if (assetId == null || cveDbId == null) continue;
            result.computeIfAbsent(assetId, k -> new HashSet<>()).add(cveDbId);
        }
        return result;
    }

    private String extractHostFromUrl(String url) {
        if (url == null || url.isBlank()) return null;
        try {
            String normalized = url.startsWith("http://") || url.startsWith("https://") ? url : "http://" + url;
            return java.net.URI.create(normalized).getHost();
        } catch (Exception e) {
            return null;
        }
    }

    private boolean isDomainName(String target) {
        if (target == null) return false;
        return !target.matches("^[\\d.]+(/\\d+)?$") && target.contains(".");
    }

    /**
     * Append a new hostname to the asset's hostname aliases, keeping existing hostname as primary.
     */
    private void appendHostname(Asset asset, String newHostname) {
        if (newHostname == null || newHostname.isBlank()) return;

        String current = asset.getHostname();
        // If no current hostname, set it directly
        if (current == null || current.isBlank()) {
            asset.setHostname(newHostname);
            return;
        }

        // If same as current, nothing to do
        if (current.equalsIgnoreCase(newHostname)) return;

        // Check existing aliases
        Set<String> allHostnames = getHostnameAliases(asset);
        if (allHostnames.contains(newHostname.toLowerCase())) return;

        // Add current as alias if not already there, then set new as primary
        allHostnames.add(current.toLowerCase());
        allHostnames.add(newHostname.toLowerCase());
        // Switch: new becomes primary, old goes to aliases
        asset.setHostnameAliases(toJsonArray(allHostnames));
    }

    private Set<String> getHostnameAliases(Asset asset) {
        Set<String> result = new LinkedHashSet<>();
        String aliases = asset.getHostnameAliases();
        if (aliases != null && !aliases.isBlank()) {
            try {
                String[] parts = aliases.replace("[", "").replace("]", "").replace("\"", "").split(",");
                for (String p : parts) {
                    String trimmed = p.trim();
                    if (!trimmed.isEmpty()) result.add(trimmed.toLowerCase());
                }
            } catch (Exception e) {
                // ignore parse errors
            }
        }
        return result;
    }

    private String toJsonArray(Collection<String> items) {
        return "[" + items.stream().map(s -> "\"" + s + "\"").collect(Collectors.joining(",")) + "]";
    }

    private boolean isWebPort(int port, String serviceName) {
        if (port == 80 || port == 443 || port == 8080 || port == 8443 ||
            port == 8000 || port == 8888 || port == 3000 || port == 8443) return true;
        if (serviceName != null) {
            String s = serviceName.toLowerCase();
            return s.contains("http") || s.contains("https") || s.contains("ssl/http")
                || s.contains("www") || s.contains("web");
        }
        return false;
    }
}





