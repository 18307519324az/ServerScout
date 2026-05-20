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

    public DashboardResponse getStats() {
        long totalAssets = assetRepository.count();
        long totalPorts = portRepository.count();

        List<Object[]> severityData = avRepository.countBySeverity();
        Map<String, Long> sevMap = severityData.stream()
                .collect(Collectors.toMap(s -> (String) s[0], s -> (Long) s[1]));

        long crit = sevMap.getOrDefault("critical", 0L);
        long high = sevMap.getOrDefault("high", 0L);
        long medium = sevMap.getOrDefault("medium", 0L);
        long low = sevMap.getOrDefault("low", 0L);

        long totalVulns = crit + high + medium + low;
        long activeTasks = scanTaskRepository.countByStatus("running");
        long recentScans = scanTaskRepository.count(); // simplified

        // Port distribution
        List<Object[]> rawPorts = portRepository.findPortDistribution();
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
                        .build())
                .portDistribution(portDist)
                .severityDistribution(sevDist)
                .trend(buildTrendData())
                .build();
    }

    private List<DashboardResponse.TrendItem> buildTrendData() {
        // Generate recent 7-day trend from scan history
        List<DashboardResponse.TrendItem> trend = new java.util.ArrayList<>();
        java.time.Instant now = java.time.Instant.now();
        for (int i = 6; i >= 0; i--) {
            java.time.Instant dayStart = now.minusSeconds(86400L * (i + 1));
            java.time.Instant dayEnd = now.minusSeconds(86400L * i);
            trend.add(DashboardResponse.TrendItem.builder()
                    .date(java.time.LocalDate.now().minusDays(i).toString())
                    .assetsDiscovered(assetRepository.countScannedSince(dayStart) - assetRepository.countScannedSince(dayEnd))
                    .vulnsFound(avRepository.countByDiscoveredAtBetween(dayStart, dayEnd))
                    .vulnsFixed(avRepository.countByStatusAndFixedAtBetween("fixed", dayStart, dayEnd))
                    .build());
        }
        return trend;
    }
}
