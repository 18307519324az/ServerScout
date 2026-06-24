package com.serverscout.service;

import com.serverscout.entity.*;
import com.serverscout.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class HoneypotDetectionService {

    private final HoneypotRuleRepository ruleRepository;
    private final HoneypotDetectionRepository detectionRepository;
    private final PortRepository portRepository;
    private final WebFingerprintRepository webFingerprintRepository;
    private final SslCertificateRepository sslCertificateRepository;
    private final AssetRepository assetRepository;

    /**
     * Run fingerprint-based (offline) honeypot detection against an asset.
     * Checks all port banners, service info, HTTP headers, and SSL certs
     * against known honeypot signatures.
     */
    @Transactional
    public List<HoneypotDetection> detectByFingerprint(Asset asset) {
        List<HoneypotRule> rules = ruleRepository.findByEnabledTrue();
        if (rules.isEmpty()) {
            log.debug("No honeypot rules configured, skipping detection for {}", asset.getIpAddress());
            return List.of();
        }

        List<HoneypotDetection> results = new ArrayList<>();
        List<Port> ports = portRepository.findByAssetId(asset.getId());

        for (Port port : ports) {
            for (HoneypotRule rule : rules) {
                String evidence = matchRule(rule, port, asset);
                if (evidence != null) {
                    // Check if this detection already exists
                    boolean exists = detectionRepository.findByAssetId(asset.getId()).stream()
                            .anyMatch(d -> d.getRule() != null && d.getRule().getId().equals(rule.getId())
                                    && Objects.equals(d.getMatchedPort(), port.getPortNumber()));
                    if (exists) continue;

                    HoneypotDetection detection = HoneypotDetection.builder()
                            .asset(asset)
                            .rule(rule)
                            .honeypotType(rule.getHoneypotType())
                            .honeypotCategory(rule.getHoneypotCategory())
                            .matchEvidence(evidence)
                            .confidence(rule.getConfidence())
                            .detectionMethod("FINGERPRINT")
                            .matchedPort(port.getPortNumber())
                            .build();
                    detection = detectionRepository.save(detection);
                    results.add(detection);
                    log.info("Honeypot detected: {} on {}:{} — {} (confidence: {})",
                            rule.getHoneypotType(), asset.getIpAddress(),
                            port.getPortNumber(), rule.getRuleName(), rule.getConfidence());
                }
            }
        }

        // Update asset honeypot status
        updateAssetHoneypotStatus(asset);

        return results;
    }

    /**
     * Match a single rule against a port and its associated data.
     * Returns the matched evidence string, or null if no match.
     */
    private String matchRule(HoneypotRule rule, Port port, Asset asset) {
        String matchType = rule.getMatchType();
        String pattern = rule.getMatchPattern();

        try {
            switch (matchType) {
                case "SERVICE_BANNER":
                    return matchAgainst(port.getBanner(), pattern);

                case "SERVICE_NAME":
                    String svc = (port.getServiceName() != null ? port.getServiceName() : "")
                            + " " + (port.getServiceProduct() != null ? port.getServiceProduct() : "");
                    return matchAgainst(svc, pattern);

                case "PORT_PATTERN":
                    String portInfo = port.getPortNumber() + "/" + port.getProtocol()
                            + " " + (port.getServiceName() != null ? port.getServiceName() : "");
                    return matchAgainst(portInfo, pattern);

                case "HTTP_HEADER":
                    Optional<WebFingerprint> wfOpt = webFingerprintRepository.findByPortId(port.getId());
                    if (wfOpt.isPresent()) {
                        WebFingerprint wf = wfOpt.get();
                        String headers = (wf.getServerHeader() != null ? wf.getServerHeader() : "")
                                + " " + (wf.getCmsName() != null ? wf.getCmsName() : "")
                                + " " + (wf.getFrameworkName() != null ? wf.getFrameworkName() : "")
                                + " " + (wf.getWafName() != null ? wf.getWafName() : "");
                        return matchAgainst(headers, pattern);
                    }
                    break;

                case "HTTP_BODY":
                    Optional<WebFingerprint> wfBodyOpt = webFingerprintRepository.findByPortId(port.getId());
                    if (wfBodyOpt.isPresent()) {
                        WebFingerprint wf = wfBodyOpt.get();
                        String body = (wf.getTitle() != null ? wf.getTitle() : "")
                                + " " + (wf.getTechStack() != null ? wf.getTechStack() : "");
                        return matchAgainst(body, pattern);
                    }
                    break;

                case "SSL_CERT":
                    Optional<SslCertificate> sslOpt = sslCertificateRepository.findByPortId(port.getId());
                    if (sslOpt.isPresent()) {
                        SslCertificate ssl = sslOpt.get();
                        String certInfo = (ssl.getSubject() != null ? ssl.getSubject() : "")
                                + " " + (ssl.getIssuer() != null ? ssl.getIssuer() : "")
                                + " " + (ssl.getSan() != null ? ssl.getSan() : "");
                        return matchAgainst(certInfo, pattern);
                    }
                    break;
            }
        } catch (Exception e) {
            log.debug("Rule matching error for {}: {}", rule.getRuleName(), e.getMessage());
        }
        return null;
    }

    private String matchAgainst(String haystack, String pattern) {
        if (haystack == null || haystack.isBlank()) return null;
        if (pattern == null || pattern.isBlank()) return null;

        try {
            Pattern regex = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
            java.util.regex.Matcher m = regex.matcher(haystack);
            if (m.find()) {
                // Return the matched portion as evidence (truncate to 500 chars)
                String evidence = haystack.length() > 500 ? haystack.substring(0, 500) + "..." : haystack;
                return evidence;
            }
        } catch (Exception e) {
            // If not a valid regex, try simple contains match
            if (haystack.toLowerCase().contains(pattern.toLowerCase())) {
                return haystack.length() > 500 ? haystack.substring(0, 500) + "..." : haystack;
            }
        }
        return null;
    }

    private void updateAssetHoneypotStatus(Asset asset) {
        List<HoneypotDetection> detections = detectionRepository.findByAssetId(asset.getId());
        if (!detections.isEmpty()) {
            asset.setIsHoneypot(true);
            // Use the highest-confidence detection for the asset-level fields
            HoneypotDetection best = detections.stream()
                    .max(Comparator.comparing(d -> confidenceWeight(d.getConfidence())))
                    .orElse(detections.get(0));
            asset.setHoneypotType(best.getHoneypotType());
            asset.setHoneypotConfidence(best.getConfidence());
        } else {
            asset.setIsHoneypot(false);
            asset.setHoneypotType(null);
            asset.setHoneypotConfidence(null);
        }
        assetRepository.save(asset);
    }

    private int confidenceWeight(String confidence) {
        if (confidence == null) return 0;
        switch (confidence.toUpperCase()) {
            case "HIGH": return 3;
            case "MEDIUM": return 2;
            case "LOW": return 1;
            default: return 0;
        }
    }

    /**
     * Get honeypot detection results for an asset.
     */
    public List<HoneypotDetection> getDetectionsForAsset(Long assetId) {
        return detectionRepository.findByAssetId(assetId);
    }

    /**
     * Get count of distinct assets flagged as honeypots.
     */
    public long getHoneypotAssetCount() {
        return detectionRepository.countDistinctAssets();
    }

    /**
     * Get honeypot type distribution for dashboard.
     */
    public List<Map<String, Object>> getHoneypotTypeDistribution() {
        List<Object[]> rows = detectionRepository.countByHoneypotType();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object[] row : rows) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("type", row[0]);
            item.put("count", row[1]);
            result.add(item);
        }
        return result;
    }

    /**
     * Clear all detections for an asset (used when re-scanning).
     */
    @Transactional
    public void clearDetectionsForAsset(Long assetId) {
        detectionRepository.deleteByAssetId(assetId);
        Asset asset = assetRepository.findById(assetId).orElse(null);
        if (asset != null) {
            asset.setIsHoneypot(false);
            asset.setHoneypotType(null);
            asset.setHoneypotConfidence(null);
            assetRepository.save(asset);
        }
    }
}
