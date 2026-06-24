package com.serverscout.service.scan;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data @Builder
public class ScanResult {
    private List<AssetEntry> assets;
    private List<VulnEntry> vulnerabilities;

    @Data @Builder
    public static class AssetEntry {
        private String ipAddress;
        private String hostname;
        private String macAddress;
        private String osFingerprint;
        private String osVersion;
        private List<PortEntry> ports;
    }

    @Data @Builder
    public static class PortEntry {
        private int portNumber;
        private String protocol;
        private String serviceName;
        private String serviceVersion;
        private String serviceProduct;
        private String state;
        private String banner;
    }

    @Data @Builder
    public static class VulnEntry {
        private String severity;
        private String template;
        private String url;
        private String name;
        private String matched;
    }
}
