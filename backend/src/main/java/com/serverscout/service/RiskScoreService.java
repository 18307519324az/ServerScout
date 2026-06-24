package com.serverscout.service;

import com.serverscout.entity.*;
import com.serverscout.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RiskScoreService {

    private final RiskScoreRepository riskScoreRepository;
    private final AssetRepository assetRepository;
    private final PortRepository portRepository;
    private final AssetVulnerabilityRepository assetVulnerabilityRepository;
    private final ScanTaskRepository scanTaskRepository;

    // High-risk ports and their categories
    private static final Set<Integer> HIGH_RISK_DB_PORTS = Set.of(3306, 5432, 6379, 9200, 27017, 1433, 1521);
    private static final Set<Integer> HIGH_RISK_REMOTE_PORTS = Set.of(22, 23, 3389, 5900, 5901);
    private static final Set<Integer> HIGH_RISK_ADMIN_PORTS = Set.of(8080, 8443, 9090, 10000);

    // Business importance keywords found in hostname/tags
    private static final Set<String> CORE_KEYWORDS = Set.of(
            "production", "核心", "主站", "api", "网关", "gateway",
            "database", "master", "primary", "关键", "线上",
            "payment", "pay", "交易", "prod");
    private static final Set<String> GENERAL_KEYWORDS = Set.of(
            "test", "dev", "staging", "开发", "测试",
            "sandbox", "internal", "内部");

    @Transactional
    public RiskScoreDetail calculateForAsset(Long taskId, Long assetId) {
        Asset asset = assetRepository.findById(assetId).orElse(null);
        if (asset == null) {
            log.warn("Asset {} not found, cannot calculate risk score", assetId);
            return null;
        }

        List<Port> ports = portRepository.findByAssetId(assetId);
        List<AssetVulnerability> vulns = assetVulnerabilityRepository.findByAssetIdWithCve(assetId);

        int assetExposureScore = calcAssetExposure(ports);
        int vulnerabilitySeverityScore = calcVulnerabilitySeverity(vulns);
        int serviceRiskScore = calcServiceRisk(ports);
        int exploitabilityScore = calcExploitability(vulns);
        int businessImportanceScore = calcBusinessImportance(asset, ports);

        int rawScore = (int) Math.round(
                assetExposureScore * 0.25
                + vulnerabilitySeverityScore * 0.30
                + serviceRiskScore * 0.15
                + exploitabilityScore * 0.15
                + businessImportanceScore * 0.15
        );

        int hasFixes = hasFixSuggestions(vulns) ? 10 : 0;
        int openVulnCount = (int) vulns.stream().filter(v -> "open".equals(v.getStatus())).count();
        int vulnDeduction = Math.min(openVulnCount, 5) * 2;

        int remediationDeduction = Math.min(hasFixes + vulnDeduction, 30);

        int finalScore = clamp(rawScore - remediationDeduction);
        String riskLevel = toRiskLevel(finalScore);

        String riskReason = buildRiskReason(asset, ports, vulns, finalScore, riskLevel);
        String repairSuggestion = buildRepairSuggestion(ports, vulns);

        RiskScoreDetail detail = riskScoreRepository.findByTaskIdAndAssetId(taskId, assetId)
                .orElse(RiskScoreDetail.builder()
                        .taskId(taskId)
                        .assetId(assetId)
                        .assetIp(asset.getIpAddress())
                        .assetName(asset.getHostname() != null ? asset.getHostname() : asset.getIpAddress())
                        .build());

        detail.setAssetExposureScore(assetExposureScore);
        detail.setVulnerabilitySeverityScore(vulnerabilitySeverityScore);
        detail.setServiceRiskScore(serviceRiskScore);
        detail.setExploitabilityScore(exploitabilityScore);
        detail.setBusinessImportanceScore(businessImportanceScore);
        detail.setRemediationDeduction(remediationDeduction);
        detail.setFinalRiskScore(finalScore);
        detail.setRiskLevel(riskLevel);
        detail.setRiskReason(riskReason);
        detail.setRepairSuggestion(repairSuggestion);

        return riskScoreRepository.save(detail);
    }

    @Transactional
    public List<RiskScoreDetail> calculateForTask(Long taskId) {
        ScanTask task = scanTaskRepository.findById(taskId).orElse(null);
        if (task == null) {
            log.warn("Task {} not found for risk calculation", taskId);
            return List.of();
        }

        List<Asset> assets = assetRepository.findByTaskId(taskId);
        if (assets.isEmpty()) {
            log.info("No assets found for task {}, risk calculation skipped", taskId);
            return List.of();
        }

        List<RiskScoreDetail> results = new ArrayList<>();
        for (Asset asset : assets) {
            RiskScoreDetail detail = calculateForAsset(taskId, asset.getId());
            if (detail != null) {
                results.add(detail);
            }
        }
        log.info("Risk scores calculated for task {}: {} assets scored", taskId, results.size());
        return results;
    }

    @Transactional(readOnly = true)
    public List<RiskScoreDetail> listByTaskId(Long taskId) {
        return riskScoreRepository.findByTaskId(taskId);
    }

    @Transactional(readOnly = true)
    public List<RiskScoreDetail> listByAssetId(Long assetId) {
        return riskScoreRepository.findByAssetId(assetId);
    }

    @Transactional(readOnly = true)
    public List<RiskScoreDetail> topRisks(int limit, String username, boolean admin) {
        if (admin) {
            return riskScoreRepository.findTopRisks(PageRequest.of(0, Math.min(limit, 50)));
        }
        return riskScoreRepository.findTopRisksByCreatedBy(username, PageRequest.of(0, Math.min(limit, 50)));
    }

    // ---- Sub-scores ----

    // 1. Asset exposure score (0-100): based on number of open ports
    private int calcAssetExposure(List<Port> ports) {
        if (ports == null || ports.isEmpty()) return 0;
        long openPorts = ports.stream().filter(p -> "open".equals(p.getState())).count();
        if (openPorts == 0) return 0;
        if (openPorts <= 2) return 15;
        if (openPorts <= 5) return 30;
        if (openPorts <= 10) return 50;
        if (openPorts <= 20) return 70;
        return 90;
    }

    // 2. Vulnerability severity score (0-100)
    private int calcVulnerabilitySeverity(List<AssetVulnerability> vulns) {
        if (vulns == null || vulns.isEmpty()) return 0;
        int score = 0;
        for (AssetVulnerability av : vulns) {
            if (!"open".equals(av.getStatus())) continue;
            CveDatabase cve = av.getCveDatabase();
            if (cve == null) continue;
            String severity = cve.getSeverity();
            if (severity == null) continue;
            switch (severity.toLowerCase()) {
                case "critical" -> score += 35;
                case "high" -> score += 20;
                case "medium" -> score += 10;
                case "low" -> score += 3;
            }
        }
        return Math.min(score, 100);
    }

    // 3. Service risk score (0-100)
    private int calcServiceRisk(List<Port> ports) {
        if (ports == null || ports.isEmpty()) return 0;
        int maxScore = 0;
        for (Port p : ports) {
            if (!"open".equals(p.getState())) continue;
            int port = p.getPortNumber();
            int s;
            if (HIGH_RISK_DB_PORTS.contains(port)) {
                s = 85; // Database services exposed
            } else if (HIGH_RISK_REMOTE_PORTS.contains(port)) {
                s = 75; // Remote access services
            } else if (HIGH_RISK_ADMIN_PORTS.contains(port)) {
                s = 70; // Admin panel ports
            } else if (port == 80 || port == 443 || port == 80) {
                s = 40; // Common web
            } else {
                s = 20; // Other
            }
            if (s > maxScore) maxScore = s;
        }
        return maxScore;
    }

    // 4. Exploitability score (0-100): based on CVE existence + severity
    private int calcExploitability(List<AssetVulnerability> vulns) {
        if (vulns == null || vulns.isEmpty()) return 0;
        boolean hasCve = vulns.stream().anyMatch(av -> {
            CveDatabase cve = av.getCveDatabase();
            return cve != null && cve.getCveId() != null && cve.getCveId().startsWith("CVE-");
        });
        boolean hasCritical = vulns.stream().anyMatch(av -> {
            CveDatabase cve = av.getCveDatabase();
            return cve != null && "critical".equalsIgnoreCase(cve.getSeverity());
        });
        boolean hasHigh = vulns.stream().anyMatch(av -> {
            CveDatabase cve = av.getCveDatabase();
            return cve != null && "high".equalsIgnoreCase(cve.getSeverity());
        });

        if (hasCritical) return 85;
        if (hasHigh) return 60;
        if (hasCve) return 35;
        return 10;
    }

    // 5. Business importance score (0-100)
    private int calcBusinessImportance(Asset asset, List<Port> ports) {
        int score = 20; // default base score
        String hostname = asset.getHostname();
        String tags = asset.getTags();

        String searchText = ((hostname != null ? hostname : "") + " " + (tags != null ? tags : "")).toLowerCase();
        boolean isCore = CORE_KEYWORDS.stream().anyMatch(k -> searchText.contains(k.toLowerCase()));
        boolean isGeneral = GENERAL_KEYWORDS.stream().anyMatch(k -> searchText.contains(k.toLowerCase()));

        if (isCore) score += 40;
        if (isGeneral) score -= 15;

        // More web services → higher business importance
        if (ports != null) {
            long webCount = ports.stream()
                    .filter(p -> Boolean.TRUE.equals(p.getIsWebService()))
                    .count();
            if (webCount >= 3) score += 15;
            else if (webCount >= 1) score += 5;
        }

        return clamp(score);
    }

    private boolean hasFixSuggestions(List<AssetVulnerability> vulns) {
        return vulns.stream().anyMatch(av -> {
            CveDatabase cve = av.getCveDatabase();
            return cve != null && cve.getFixSuggestion() != null && !cve.getFixSuggestion().isBlank();
        });
    }

    // ---- Helpers ----

    private String buildRiskReason(Asset asset, List<Port> ports, List<AssetVulnerability> vulns,
                                    int finalScore, String riskLevel) {
        List<String> reasons = new ArrayList<>();

        long openPortCount = ports.stream().filter(p -> "open".equals(p.getState())).count();
        if (openPortCount > 0) {
            reasons.add("资产开放 " + openPortCount + " 个端口");

            List<String> highRiskServices = ports.stream()
                    .filter(p -> "open".equals(p.getState()))
                    .filter(p -> HIGH_RISK_DB_PORTS.contains(p.getPortNumber())
                            || HIGH_RISK_REMOTE_PORTS.contains(p.getPortNumber()))
                    .map(p -> p.getServiceName() != null ? p.getServiceName() : String.valueOf(p.getPortNumber()))
                    .distinct()
                    .collect(Collectors.toList());
            if (!highRiskServices.isEmpty()) {
                reasons.add("其中高风险服务：" + String.join("、", highRiskServices));
            }
        }

        long critCount = vulns.stream()
                .filter(av -> av.getCveDatabase() != null && "critical".equalsIgnoreCase(av.getCveDatabase().getSeverity()))
                .count();
        long highCount = vulns.stream()
                .filter(av -> av.getCveDatabase() != null && "high".equalsIgnoreCase(av.getCveDatabase().getSeverity()))
                .count();
        if (critCount > 0) {
            reasons.add("扫描结果命中 " + critCount + " 个高危漏洞");
        }
        if (highCount > 0) {
            reasons.add("存在 " + highCount + " 个中高危漏洞");
        }

        int vulnCount = (int) vulns.stream().filter(v -> "open".equals(v.getStatus())).count();
        if (vulnCount > 0) {
            reasons.add("共发现 " + vulnCount + " 个待修复漏洞");
        }

        String hostname = asset.getHostname();
        if (hostname != null && CORE_KEYWORDS.stream().anyMatch(k -> hostname.toLowerCase().contains(k.toLowerCase()))) {
            reasons.add("资产被标记为核心业务资产");
        }

        reasons.add("综合风险评分为 " + finalScore + "，风险等级为 " + riskLevel);

        return "该资产风险" + (finalScore >= 60 ? "较高" : finalScore >= 40 ? "中等" : "较低") + "，原因包括："
                + String.join("；", reasons) + "。";
    }

    private String buildRepairSuggestion(List<Port> ports, List<AssetVulnerability> vulns) {
        List<String> suggestions = new ArrayList<>();

        Set<Integer> seenPorts = new HashSet<>();
        for (Port p : ports) {
            if (!"open".equals(p.getState())) continue;
            int port = p.getPortNumber();
            if (seenPorts.contains(port)) continue;
            seenPorts.add(port);

            if (port == 22) {
                suggestions.add("限制 SSH（22）登录来源，仅允许堡垒机或办公网访问。");
            } else if (port == 23) {
                suggestions.add("关闭 Telnet（23）服务，改用 SSH 进行远程管理。");
            } else if (port == 3306 || port == 5432 || port == 1433 || port == 1521) {
                suggestions.add("数据库端口（" + port + "）不应直接暴露在公网，应通过安全组或防火墙限制访问。");
            } else if (port == 3389) {
                suggestions.add("限制 RDP（3389）访问来源，开启网络级认证（NLA）。");
            } else if (port == 6379) {
                suggestions.add("Redis（6379）应开启认证并绑定内网地址。");
            } else if (port == 9200) {
                suggestions.add("Elasticsearch（9200）应开启认证并限制访问来源。");
            } else if (port == 27017) {
                suggestions.add("MongoDB（27017）应开启认证并绑定内网地址。");
            } else if (port == 8080 || port == 8443 || port == 9090 || port == 10000) {
                suggestions.add("管理后台端口（" + port + "）应开启强密码策略和多因素认证。");
            }
        }

        Set<String> seenCves = new HashSet<>();
        for (AssetVulnerability av : vulns) {
            if (!"open".equals(av.getStatus())) continue;
            CveDatabase cve = av.getCveDatabase();
            if (cve == null) continue;
            String cveId = cve.getCveId();
            if (cveId == null || seenCves.contains(cveId)) continue;
            seenCves.add(cveId);

            if (cve.getFixSuggestion() != null && !cve.getFixSuggestion().isBlank()) {
                suggestions.add(cve.getCveId() + "：" + cve.getFixSuggestion());
            } else if (cve.getAffectedSoftware() != null) {
                suggestions.add("升级 " + cve.getAffectedSoftware() + " 至最新版本以修复 " + cveId + "。");
            }
        }

        if (suggestions.isEmpty()) {
            suggestions.add("暂未发现明显安全风险，建议定期进行安全扫描。");
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < suggestions.size(); i++) {
            sb.append(i + 1).append(". ").append(suggestions.get(i)).append("\n");
        }
        return sb.toString();
    }

    private String toRiskLevel(int score) {
        if (score >= 81) return "CRITICAL";
        if (score >= 61) return "HIGH";
        if (score >= 41) return "MEDIUM";
        if (score >= 21) return "LOW";
        return "INFO";
    }

    private int clamp(int value) {
        return Math.max(0, Math.min(100, value));
    }

    /**
     * Atomically delete existing risk scores for a task and recalculate them.
     * Both operations run in a single transaction — if recalculation fails,
     * the deletion is rolled back, ensuring no data loss.
     */
    @Transactional
    public List<RiskScoreDetail> recalculateForTask(Long taskId) {
        riskScoreRepository.deleteByTaskId(taskId);
        return calculateForTask(taskId);
    }

    @Transactional
    public void deleteByTaskId(Long taskId) {
        riskScoreRepository.deleteByTaskId(taskId);
    }
}
