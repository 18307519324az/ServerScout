package com.serverscout.dto;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data @Builder
public class ScanSummary {
    private int assetCount;
    private int portCount;
    private int webServiceCount;
    private int criticalVulnCount;
    private int highVulnCount;
    private int mediumVulnCount;
    private int newAssetCount;
    private int updatedAssetCount;
    private List<PortStat> topPorts;

    @Data @Builder
    public static class PortStat {
        private int port;
        private long count;
    }
}
