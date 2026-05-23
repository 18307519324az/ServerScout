package com.serverscout.dto;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data @Builder
public class DashboardResponse {
    private Overview overview;
    private List<PortStatItem> portDistribution;
    private List<SeverityItem> severityDistribution;
    private List<TrendItem> trend;

    @Data @Builder
    public static class Overview {
        private long totalAssets;
        private long totalPorts;
        private long totalVulnerabilities;
        private long criticalVulns;
        private long highVulns;
        private long mediumVulns;
        private long lowVulns;
        private long recentScanCount;
        private long activeTasks;
        private long riskAssetCount;
    }

    @Data @Builder
    public static class PortStatItem {
        private int port;
        private long count;
    }

    @Data @Builder
    public static class SeverityItem {
        private String name;
        private long value;
    }

    @Data @Builder
    public static class TrendItem {
        private String date;
        private long assetsDiscovered;
        private long vulnsFound;
        private long vulnsFixed;
    }
}
