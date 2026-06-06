package com.serverscout.service;

import com.serverscout.dto.CreateScanTaskRequest;
import com.serverscout.dto.ScanSummary;
import com.serverscout.dto.ScanTaskResponse;
import com.serverscout.entity.Asset;
import com.serverscout.entity.Port;
import com.serverscout.entity.ScanAssetMapping;
import com.serverscout.entity.ScanTask;
import com.serverscout.repository.*;
import com.serverscout.service.scan.TargetConcurrencyLimiter;
import com.serverscout.util.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScanService {
    private static final Pattern HOST_OR_HOST_PORT = Pattern.compile("^[\\w.-]+(?::\\d{1,5})?$");
    private static final Pattern PORT_TOKEN = Pattern.compile("^\\d{1,5}(?:-\\d{1,5})?$");

    private final ScanTaskRepository scanTaskRepository;
    private final AssetRepository assetRepository;
    private final PortRepository portRepository;
    private final ScanAssetMappingRepository scanAssetMappingRepository;
    private final WebFingerprintRepository webFingerprintRepository;
    private final CrawledUrlRepository crawledUrlRepository;
    private final ScanExecutionService scanExecutionService;
    private final ProgressEmitter progressEmitter;
    private final TargetConcurrencyLimiter targetConcurrencyLimiter;
    @Transactional
    public ScanTaskResponse createTask(CreateScanTaskRequest req, String createdBy) {
        validateTargetRange(req.getTargetRange());
        validatePortRange(req.getPortRange());

        ScanTask task = ScanTask.builder()
                .name(req.getName())
                .targetRange(req.getTargetRange())
                .scanType(req.getScanType())
                .portRange(req.getPortRange())
                .enableFingerprint(req.getEnableFingerprint())
                .enableVulnScan(req.getEnableVulnScan())
                .enableCrawler(req.getEnableCrawler() != null ? req.getEnableCrawler() : true)
                .status("pending")
                .progress(0)
                .totalAssets(0)
                .totalPorts(0)
                .createdBy(createdBy)
                .build();
        task = scanTaskRepository.save(task);
        final Long taskId = task.getId();
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    scanExecutionService.executeScan(taskId);
                }
            });
        } else {
            scanExecutionService.executeScan(taskId);
        }

        return toResponse(task);
    }

    @Transactional(readOnly = true)
    public Page<ScanTaskResponse> listTasks(String status, Pageable pageable, String username, boolean isAdmin) {
        Page<ScanTask> tasks;
        if (isAdmin) {
            if (status != null) tasks = scanTaskRepository.findByStatus(status, pageable);
            else tasks = scanTaskRepository.findAll(pageable);
        } else {
            if (status != null) tasks = scanTaskRepository.findByCreatedByAndStatus(username, status, pageable);
            else tasks = scanTaskRepository.findByCreatedBy(username, pageable);
        }
        return tasks.map(t -> {
            List<ScanAssetMapping> mappings = scanAssetMappingRepository.findByScanTaskIdWithAsset(t.getId());
            long totalAssets = mappings.stream().map(ScanAssetMapping::getAsset).distinct().count();
            ScanTaskResponse r = toResponse(t);
            r.setTotalAssets((int) totalAssets);
            return r;
        });
    }

    @Transactional(readOnly = true)
    public ScanTaskResponse getTaskDetail(Long id) {
        ScanTask task = scanTaskRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ScanTask", id));
        return toFullResponse(task);
    }

    @Transactional
    public void deleteTask(Long id) {
        ScanTask task = scanTaskRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ScanTask", id));

        boolean wasRunning = "running".equals(task.getStatus());
        if ("running".equals(task.getStatus()) || "pending".equals(task.getStatus())) {
            scanExecutionService.forceCancel(id);
            task.setStatus("cancelled");
            scanTaskRepository.save(task);
        }
        if (wasRunning) {
            targetConcurrencyLimiter.forceReleaseByTarget(task.getTargetRange());
        }
        progressEmitter.cleanup(id);

        // Delete child rows first to avoid FK violations.
        crawledUrlRepository.deleteByTaskId(id);

        List<ScanAssetMapping> mappings = scanAssetMappingRepository.findByScanTaskId(id);
        for (ScanAssetMapping m : mappings) {
            Asset asset = m.getAsset();
            if (asset.getTask() != null && asset.getTask().getId().equals(id)) {
                asset.setTask(null);
                assetRepository.save(asset);
            }
        }
        if (!mappings.isEmpty()) {
            scanAssetMappingRepository.deleteAll(mappings);
        }

        scanTaskRepository.delete(task);
    }

    public void cancelTask(Long id) {
        ScanTask task = scanTaskRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ScanTask", id));

        if ("completed".equals(task.getStatus()) || "failed".equals(task.getStatus()) || "cancelled".equals(task.getStatus())) {
            throw new IllegalStateException("Task already finished");
        }

        boolean wasRunning = "running".equals(task.getStatus());
        scanExecutionService.forceCancel(id);
        task.setStatus("cancelled");
        scanTaskRepository.save(task);
        if (wasRunning) {
            targetConcurrencyLimiter.forceReleaseByTarget(task.getTargetRange());
        }
        progressEmitter.cleanup(id);
    }

    private void validateTargetRange(String targetRange) {
        if (targetRange == null || targetRange.isBlank()) {
            throw new IllegalArgumentException("Scan target cannot be empty");
        }
        String[] targets = targetRange.split(",");
        boolean hasAny = false;
        for (String raw : targets) {
            String target = raw == null ? "" : raw.trim();
            if (target.isEmpty()) {
                continue;
            }
            hasAny = true;
            if (isIpv4(target) || isCidr(target) || isHostOrHostPort(target)) {
                continue;
            }
            throw new IllegalArgumentException("Invalid target format: " + target);
        }
        if (!hasAny) {
            throw new IllegalArgumentException("Scan target cannot be empty");
        }
    }

    private void validatePortRange(String portRange) {
        if (portRange == null || portRange.isBlank()) {
            throw new IllegalArgumentException("Port range cannot be empty");
        }
        for (String raw : portRange.split(",")) {
            String token = raw.trim();
            if (!PORT_TOKEN.matcher(token).matches()) {
                throw new IllegalArgumentException("Invalid port range: " + token);
            }
            String[] bounds = token.split("-");
            int start = Integer.parseInt(bounds[0]);
            int end = bounds.length == 2 ? Integer.parseInt(bounds[1]) : start;
            if (start < 1 || end > 65535 || start > end) {
                throw new IllegalArgumentException("Invalid port range: " + token);
            }
        }
    }

    private boolean isHostOrHostPort(String value) {
        if (!HOST_OR_HOST_PORT.matcher(value).matches()) {
            return false;
        }
        int colonIdx = value.lastIndexOf(':');
        if (colonIdx > 0 && colonIdx < value.length() - 1) {
            try {
                int port = Integer.parseInt(value.substring(colonIdx + 1));
                return port >= 1 && port <= 65535;
            } catch (NumberFormatException ex) {
                return false;
            }
        }
        return true;
    }

    private boolean isCidr(String value) {
        int slash = value.indexOf('/');
        if (slash <= 0 || slash >= value.length() - 1) {
            return false;
        }
        String ip = value.substring(0, slash);
        String mask = value.substring(slash + 1);
        if (!isIpv4(ip)) {
            return false;
        }
        try {
            int m = Integer.parseInt(mask);
            return m >= 0 && m <= 32;
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    private boolean isIpv4(String value) {
        String[] parts = value.split("\\.");
        if (parts.length != 4) {
            return false;
        }
        for (String part : parts) {
            if (part.isEmpty() || part.length() > 3) {
                return false;
            }
            for (int i = 0; i < part.length(); i++) {
                if (!Character.isDigit(part.charAt(i))) {
                    return false;
                }
            }
            int n = Integer.parseInt(part);
            if (n < 0 || n > 255) {
                return false;
            }
        }
        return true;
    }

    private ScanTaskResponse toResponse(ScanTask task) {
        return ScanTaskResponse.builder()
                .id(task.getId()).name(task.getName())
                .targetRange(task.getTargetRange()).scanType(task.getScanType())
                .portRange(task.getPortRange())
                .status(task.getStatus()).progress(task.getProgress())
                .totalAssets(task.getTotalAssets()).totalPorts(task.getTotalPorts())
                .enableFingerprint(task.getEnableFingerprint())
                .enableVulnScan(task.getEnableVulnScan())
                .enableCrawler(task.getEnableCrawler())
                .startedAt(task.getStartedAt()).completedAt(task.getCompletedAt())
                .createdAt(task.getCreatedAt()).build();
    }

    private ScanTaskResponse toFullResponse(ScanTask task) {
        List<ScanAssetMapping> mappings = scanAssetMappingRepository.findByScanTaskId(task.getId());
        List<Asset> assets = mappings.stream().map(ScanAssetMapping::getAsset)
                .distinct().collect(Collectors.toList());

        int taskPortCount = 0;
        int webCount = 0;
        Map<Integer, Long> portDist = new HashMap<>();
        for (Asset a : assets) {
            List<Port> ports = portRepository.findByAssetId(a.getId());
            taskPortCount += ports.size();
            webCount += ports.stream().filter(Port::getIsWebService).count();
            for (Port p : ports) {
                portDist.merge(p.getPortNumber(), 1L, Long::sum);
            }
        }
        List<ScanSummary.PortStat> topPorts = portDist.entrySet().stream()
                .sorted(Map.Entry.<Integer, Long>comparingByValue().reversed())
                .limit(10).map(e -> ScanSummary.PortStat.builder()
                        .port(e.getKey()).count(e.getValue()).build())
                .collect(Collectors.toList());

        long crit = assets.stream().mapToLong(a ->
                a.getCriticalVulnCount() != null ? a.getCriticalVulnCount() : 0).sum();

        int newAssets = (int) mappings.stream().filter(m ->
                Boolean.TRUE.equals(m.getIsNew())).count();
        int updatedAssets = mappings.size() - newAssets;

        ScanSummary summary = ScanSummary.builder()
                .assetCount(assets.size()).portCount(taskPortCount)
                .webServiceCount(webCount).criticalVulnCount((int) crit)
                .newAssetCount(newAssets).updatedAssetCount(updatedAssets)
                .topPorts(topPorts).build();

        return ScanTaskResponse.builder()
                .id(task.getId()).name(task.getName())
                .targetRange(task.getTargetRange()).scanType(task.getScanType())
                .portRange(task.getPortRange())
                .status(task.getStatus()).progress(task.getProgress())
                .totalAssets(task.getTotalAssets()).totalPorts(task.getTotalPorts())
                .enableFingerprint(task.getEnableFingerprint())
                .enableVulnScan(task.getEnableVulnScan())
                .enableCrawler(task.getEnableCrawler())
                .startedAt(task.getStartedAt()).completedAt(task.getCompletedAt())
                .createdAt(task.getCreatedAt()).errorMessage(task.getErrorMessage())
                .summary(summary).build();
    }
}

