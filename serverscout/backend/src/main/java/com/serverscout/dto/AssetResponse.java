package com.serverscout.dto;

import lombok.Builder;
import lombok.Data;
import java.time.Instant;
import java.util.List;

@Data @Builder
public class AssetResponse {
    private Long id;
    private String ipAddress;
    private String hostname;
    private List<String> hostnameAliases;
    private String osFingerprint;
    private String status;
    private int openPortCount;
    private int criticalVulnCount;
    private String macAddress;
    private Instant lastScanTime;
    private Instant firstSeenTime;
    private int scanCount;
    private List<String> tags;
    private Boolean isHoneypot;
    private String honeypotType;
    private String honeypotConfidence;
    private List<HoneypotDetectionInfo> honeypotDetections;
    private Instant discoveredAt;
    private Instant updatedAt;
    private List<PortDetail> ports;

    @Data @Builder
    public static class PortDetail {
        private Long id;
        private int portNumber;
        private String protocol;
        private String serviceName;
        private String serviceVersion;
        private String serviceProduct;
        private String state;
        private String banner;
        private boolean isWebService;
        private WebFingerprintDetail webFingerprint;
        private SslCertBrief sslCertificate;
        private List<VulnBrief> vulnerabilities;
    }

    @Data @Builder
    public static class WebFingerprintDetail {
        private Integer httpStatus;
        private String serverHeader;
        private String frameworkName;
        private String frameworkVersion;
        private String cmsName;
        private String cmsVersion;
        private String wafName;
        private String techStack;
        private String title;
        private String faviconHash;
        private String bodyHash;
    }

    @Data @Builder
    public static class SslCertBrief {
        private Long id;
        private String subject;
        private String issuer;
        private String fingerprintSha256;
        private Instant notBefore;
        private Instant notAfter;
        private String san;
        private String sigAlg;
        private Integer keySize;
        private Boolean isExpired;
    }

    @Data @Builder
    public static class VulnBrief {
        private String cveId;
        private String severity;
        private double cvssScore;
        private String status;
    }

    @Data @Builder
    public static class HoneypotDetectionInfo {
        private Long id;
        private String honeypotType;
        private String honeypotCategory;
        private String matchEvidence;
        private String confidence;
        private String detectionMethod;
        private Integer matchedPort;
        private String matchedAt;
        private String ruleName;
    }
}
