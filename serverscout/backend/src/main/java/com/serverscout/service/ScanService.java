package com.serverscout.service;

import com.serverscout.dto.CreateScanTaskRequest;
import com.serverscout.dto.ScanSummary;
import com.serverscout.dto.ScanTaskResponse;
import com.serverscout.entity.*;
import com.serverscout.repository.*;
import com.serverscout.util.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;
import java.util.Map;
import java.util.HashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScanService {

    private final ScanTaskRepository scanTaskRepository;
    private final AssetRepository assetRepository;
    private final PortRepository portRepository;
    private final ScanAssetMappingRepository scanAssetMappingRepository;
    private final WebFingerprintRepository webFingerprintRepository;
    private final ScanExecutionService scanExecutionService;

    public ScanTaskResponse createTask(CreateScanTaskRequest req, String createdBy) {
        ScanTask task = ScanTask.builder()
                .name(req.getName())
                .targetRange(req.getTargetRange())
                .scanType(req.getScanType())
                .portRange(req.getPortRange())
                .enableFingerprint(req.getEnableFingerprint())
                .enableVulnScan(req.getEnableVulnScan())
                .status("pending")
                .progress(0)
                .totalAssets(0)
                .totalPorts(0)
                .createdBy(createdBy)
                .build();
        task = scanTaskRepository.save(task);

        scanExecutionService.executeScan(task.getId());

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
        // Cancel running tasks first so the async executor stops
        if ("running".equals(task.getStatus())) {
            task.setStatus("cancelled");
            scanTaskRepository.save(task);
        }
        // 解除资产与此任务的关联
        List<ScanAssetMapping> mappings = scanAssetMappingRepository.findByScanTaskId(id);
        for (ScanAssetMapping m : mappings) {
            Asset asset = m.getAsset();
            if (asset.getTask() != null && asset.getTask().getId().equals(id)) {
                asset.setTask(null);
                assetRepository.save(asset);
            }
            scanAssetMappingRepository.delete(m);
        }
        scanTaskRepository.delete(task);
    }

    public void cancelTask(Long id) {
        ScanTask task = scanTaskRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ScanTask", id));
        if (!"running".equals(task.getStatus())) {
            throw new IllegalStateException("Task is not running");
        }
        task.setStatus("cancelled");
        scanTaskRepository.save(task);
    }

    private ScanTaskResponse toResponse(ScanTask task) {
        return ScanTaskResponse.builder()
                .id(task.getId()).name(task.getName())
                .targetRange(task.getTargetRange()).scanType(task.getScanType())
                .portRange(task.getPortRange())
                .status(task.getStatus()).progress(task.getProgress())
                .totalAssets(task.getTotalAssets()).totalPorts(task.getTotalPorts())
                .startedAt(task.getStartedAt()).completedAt(task.getCompletedAt())
                .createdAt(task.getCreatedAt()).build();
    }

    private ScanTaskResponse toFullResponse(ScanTask task) {
        List<ScanAssetMapping> mappings = scanAssetMappingRepository.findByScanTaskId(task.getId());
        List<Asset> assets = mappings.stream().map(ScanAssetMapping::getAsset)
                .distinct().collect(Collectors.toList());

        // Task-scoped port count and web service count
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
                .startedAt(task.getStartedAt()).completedAt(task.getCompletedAt())
                .createdAt(task.getCreatedAt()).errorMessage(task.getErrorMessage())
                .summary(summary).build();
    }
}
