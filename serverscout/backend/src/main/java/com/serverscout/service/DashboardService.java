package com.serverscout.service;

import com.serverscout.dto.DashboardResponse;
import com.serverscout.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final AssetRepository assetRepository;
    private final PortRepository portRepository;
    private final AssetVulnerabilityRepository avRepository;
    private final ScanTaskRepository scanTaskRepository;
    private final HoneypotDetectionRepository honeypotDetectionRepository;

    public DashboardResponse getStats(String username, boolean isAdmin) {
        long totalAssets = isAdmin ? assetRepository.count() : assetRepository.countByCreatedBy(username);
        long totalPorts = isAdmin ? portRepository.count() : portRepository.countByCreatedBy(username);

        List<Object[]> severityData = isAdmin
                ? avRepository.countBySeverity()
                : avRepository.countBySeverityAndCreatedBy(username);
        Map<String, Long> sevMap = severityData.stream()
                .collect(Collectors.toMap(s -> (String) s[0], s -> (Long) s[1]));

        long crit = sevMap.getOrDefault("critical", 0L);
        long high = sevMap.getOrDefault("high", 0L);
        long medium = sevMap.getOrDefault("medium", 0L);
        long low = sevMap.getOrDefault("low", 0L);

        long totalVulns = crit + high + medium + low;
        List<String> activeStatuses = List.of("pending", "running");
        long activeTasks = isAdmin
                ? scanTaskRepository.countByStatusIn(activeStatuses)
                : scanTaskRepository.countByCreatedByAndStatusIn(username, activeStatuses);
        long recentScans = isAdmin
                ? scanTaskRepository.count()
                : scanTaskRepository.findIdsByCreatedBy(username).size();
        long riskAssetCount = isAdmin
                ? assetRepository.countByCriticalVulnCountGreaterThan(0)
                : assetRepository.countRiskByCreatedBy(username);
        long honeypotAssetCount = honeypotDetectionRepository.countDistinctAssets();

        List<Object[]> rawPorts = isAdmin
                ? portRepository.findPortDistribution()
                : portRepository.findPortDistributionByCreatedBy(username);
        List<DashboardResponse.PortStatItem> portDist = rawPorts.stream()
                .limit(10)
                .map(p -> DashboardResponse.PortStatItem.builder()
                        .port((Integer) p[0]).count((Long) p[1]).build())
                .collect(Collectors.toList());

        List<DashboardResponse.SeverityItem> sevDist = List.of(
            DashboardResponse.SeverityItem.builder().name("critical").value(crit).build(),
            DashboardResponse.SeverityItem.builder().name("high").value(high).build(),
            DashboardResponse.SeverityItem.builder().name("medium").value(medium).build(),
            DashboardResponse.SeverityItem.builder().name("low").value(low).build()
        );

        return DashboardResponse.builder()
                .overview(DashboardResponse.Overview.builder()
                        .totalAssets(totalAssets).totalPorts(totalPorts)
                        .totalVulnerabilities(totalVulns)
                        .criticalVulns(crit).highVulns(high)
                        .mediumVulns(medium).lowVulns(low)
                        .activeTasks(activeTasks).recentScanCount(recentScans)
                        .riskAssetCount(riskAssetCount)
                        .honeypotAssetCount(honeypotAssetCount)
                        .build())
                .portDistribution(portDist)
                .severityDistribution(sevDist)
                .trend(buildTrendData(isAdmin, username))
                .build();
    }

    private List<DashboardResponse.TrendItem> buildTrendData(boolean isAdmin, String username) {
        List<DashboardResponse.TrendItem> trend = new java.util.ArrayList<>();
        java.time.Instant now = java.time.Instant.now();
        for (int i = 6; i >= 0; i--) {
            java.time.Instant dayStart = now.minusSeconds(86400L * (i + 1));
            java.time.Instant dayEnd = now.minusSeconds(86400L * i);
            long assetsDiscovered = isAdmin
                    ? assetRepository.countScannedSince(dayStart) - assetRepository.countScannedSince(dayEnd)
                    : assetRepository.countScannedSinceByCreatedBy(dayStart, username)
                      - assetRepository.countScannedSinceByCreatedBy(dayEnd, username);
            long vulnsFound = isAdmin
                    ? avRepository.countByDiscoveredAtBetween(dayStart, dayEnd)
                    : avRepository.countByDiscoveredAtBetweenAndCreatedBy(dayStart, dayEnd, username);
            long vulnsFixed = isAdmin
                    ? avRepository.countByStatusAndFixedAtBetween("fixed", dayStart, dayEnd)
                    : avRepository.countByStatusAndFixedAtBetweenAndCreatedBy("fixed", dayStart, dayEnd, username);
            trend.add(DashboardResponse.TrendItem.builder()
                    .date(java.time.LocalDate.now().minusDays(i).toString())
                    .assetsDiscovered(assetsDiscovered)
                    .vulnsFound(vulnsFound)
                    .vulnsFixed(vulnsFixed)
                    .build());
        }
        return trend;
    }
}
