package com.serverscout.service;

import com.serverscout.entity.Asset;
import com.serverscout.entity.AssetVulnerability;
import com.serverscout.entity.Port;
import com.serverscout.entity.WebFingerprint;
import com.serverscout.repository.AssetRepository;
import com.serverscout.repository.AssetVulnerabilityRepository;
import com.serverscout.repository.PortRepository;
import com.serverscout.repository.WebFingerprintRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Builds hierarchical attack-surface data for the reconnaissance tree and tech radar.
 */
@Service
@RequiredArgsConstructor
public class AttackSurfaceService {

    private static final int MAX_ASSETS_PER_SUBNET = 18;
    private static final int MAX_PORTS_PER_ASSET = 14;
    private static final int MAX_TECH_NODES_PER_PORT = 5;
    private static final int MAX_VULNS_PER_PORT = 3;
    private static final int MAX_TOP_ITEMS = 10;

    private final AssetRepository assetRepository;
    private final PortRepository portRepository;
    private final WebFingerprintRepository webFingerprintRepository;
    private final AssetVulnerabilityRepository avRepository;

    @Transactional(readOnly = true)
    public Map<String, Object> buildAttackSurfaceMap(String username, boolean isAdmin) {
        List<Asset> assets = isAdmin
                ? assetRepository.findAll()
                : assetRepository.searchByCreatedBy(null, null, username,
                org.springframework.data.domain.Pageable.unpaged()).getContent();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("name", "Attack Surface");
        result.put("type", "root");

        Map<String, List<Asset>> grouped = assets.stream()
                .collect(Collectors.groupingBy(asset -> resolveSubnet(asset.getIpAddress()), LinkedHashMap::new, Collectors.toList()));

        List<Map<String, Object>> children = new ArrayList<>();
        for (Map.Entry<String, List<Asset>> entry : grouped.entrySet()) {
            Map<String, Object> subnetNode = new LinkedHashMap<>();
            subnetNode.put("name", entry.getKey());
            subnetNode.put("type", "subnet");

            List<Asset> rankedAssets = entry.getValue().stream()
                    .sorted(Comparator
                            .comparingInt(this::assetRiskScore).reversed()
                            .thenComparing(Asset::getIpAddress, Comparator.nullsLast(String::compareTo)))
                    .collect(Collectors.toList());

            List<Map<String, Object>> assetNodes = rankedAssets.stream()
                    .limit(MAX_ASSETS_PER_SUBNET)
                    .map(this::buildAssetNode)
                    .collect(Collectors.toCollection(ArrayList::new));

            if (rankedAssets.size() > MAX_ASSETS_PER_SUBNET) {
                assetNodes.add(summaryNode(
                        "asset-summary",
                        rankedAssets.size() - MAX_ASSETS_PER_SUBNET,
                        sampleAssetIps(rankedAssets.subList(MAX_ASSETS_PER_SUBNET, rankedAssets.size()))
                ));
            }

            subnetNode.put("children", assetNodes);
            children.add(subnetNode);
        }

        result.put("children", children);
        return result;
    }

    private Map<String, Object> buildAssetNode(Asset asset) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("name", assetLabel(asset));
        node.put("type", "asset");
        node.put("id", asset.getId());

        List<Port> ports = portRepository.findByAssetId(asset.getId()).stream()
                .sorted(Comparator
                        .comparingInt((Port port) -> portImportance(port, asset)).reversed()
                        .thenComparingInt(Port::getPortNumber))
                .collect(Collectors.toList());

        Map<Long, WebFingerprint> fingerprintByPortId = new LinkedHashMap<>();
        for (Port port : ports) {
            fingerprintByPortId.put(port.getId(), webFingerprintRepository.findByPortId(port.getId()).orElse(null));
        }

        Map<Long, List<AssetVulnerability>> vulnsByPortId = avRepository.findByAssetIdWithCve(asset.getId()).stream()
                .filter(vuln -> vuln.getPort() != null && vuln.getPort().getId() != null)
                .collect(Collectors.groupingBy(vuln -> vuln.getPort().getId()));

        List<Map<String, Object>> portNodes = new ArrayList<>();
        for (Port port : ports.stream().limit(MAX_PORTS_PER_ASSET).collect(Collectors.toList())) {
            portNodes.add(buildPortNode(port, fingerprintByPortId.get(port.getId()), vulnsByPortId.get(port.getId())));
        }

        if (ports.size() > MAX_PORTS_PER_ASSET) {
            List<Port> overflow = ports.subList(MAX_PORTS_PER_ASSET, ports.size());
            List<Port> overflowWeb = overflow.stream().filter(port -> Boolean.TRUE.equals(port.getIsWebService())).collect(Collectors.toList());
            List<Port> overflowStandard = overflow.stream().filter(port -> !Boolean.TRUE.equals(port.getIsWebService())).collect(Collectors.toList());

            if (!overflowWeb.isEmpty()) {
                portNodes.add(summaryNode("port-summary", overflowWeb.size(), samplePorts(overflowWeb), "web"));
            }
            if (!overflowStandard.isEmpty()) {
                portNodes.add(summaryNode("port-summary", overflowStandard.size(), samplePorts(overflowStandard), "standard"));
            }
        }

        if (!portNodes.isEmpty()) {
            node.put("children", portNodes);
        }
        return node;
    }

    private Map<String, Object> buildPortNode(Port port, WebFingerprint fingerprint, List<AssetVulnerability> vulnerabilities) {
        Map<String, Object> portNode = new LinkedHashMap<>();
        String serviceName = normalizeServiceName(port);
        portNode.put("name", port.getPortNumber() + "/" + safeLower(port.getProtocol()) + " " + serviceName);
        portNode.put("type", Boolean.TRUE.equals(port.getIsWebService()) ? "web-port" : "port");

        List<Map<String, Object>> children = new ArrayList<>();

        if (fingerprint != null && hasText(fingerprint.getServerHeader())) {
            children.add(labelNode("server", fingerprint.getServerHeader()));
        } else if (hasText(port.getServiceProduct()) || hasText(port.getServiceVersion())) {
            String product = hasText(port.getServiceProduct()) ? port.getServiceProduct().trim() : serviceName;
            String version = hasText(port.getServiceVersion()) ? " " + port.getServiceVersion().trim() : "";
            children.add(labelNode("service", (product + version).trim()));
        }

        if (fingerprint != null) {
            addDistinct(children, labelNode("framework", joinNameVersion(fingerprint.getFrameworkName(), fingerprint.getFrameworkVersion())));
            addDistinct(children, labelNode("cms", joinNameVersion(fingerprint.getCmsName(), fingerprint.getCmsVersion())));
            addDistinct(children, labelNode("waf", fingerprint.getWafName()));
        }

        List<AssetVulnerability> portVulns = vulnerabilities == null ? List.of() : vulnerabilities.stream()
                .filter(vuln -> vuln.getCveDatabase() != null)
                .sorted(Comparator
                        .comparing((AssetVulnerability vuln) -> safeLower(vuln.getCveDatabase().getSeverity()), Comparator.reverseOrder())
                        .thenComparing(vuln -> vuln.getCveDatabase().getCveId(), Comparator.nullsLast(String::compareTo)))
                .collect(Collectors.toList());

        for (AssetVulnerability vuln : portVulns.stream().limit(MAX_VULNS_PER_PORT).collect(Collectors.toList())) {
            String severity = hasText(vuln.getCveDatabase().getSeverity())
                    ? vuln.getCveDatabase().getSeverity().trim().toUpperCase(Locale.ROOT)
                    : "N/A";
            Map<String, Object> vulnNode = labelNode("vulnerability", vuln.getCveDatabase().getCveId() + " [" + severity + "]");
            vulnNode.put("severity", vuln.getCveDatabase().getSeverity());
            children.add(vulnNode);
        }

        if (portVulns.size() > MAX_VULNS_PER_PORT) {
            children.add(summaryNode("vuln-summary", portVulns.size() - MAX_VULNS_PER_PORT, sampleVulns(portVulns.subList(MAX_VULNS_PER_PORT, portVulns.size()))));
        }

        if (children.size() > MAX_TECH_NODES_PER_PORT) {
            List<Map<String, Object>> visible = new ArrayList<>(children.subList(0, MAX_TECH_NODES_PER_PORT));
            List<Map<String, Object>> hidden = children.subList(MAX_TECH_NODES_PER_PORT, children.size());
            visible.add(summaryNode("tech-summary", hidden.size(), hidden.stream()
                    .map(item -> Objects.toString(item.get("name"), ""))
                    .filter(this::hasText)
                    .limit(3)
                    .collect(Collectors.joining(", "))));
            children = visible;
        }

        if (!children.isEmpty()) {
            portNode.put("children", children);
        }
        return portNode;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> buildTechStackStats(String username, boolean isAdmin) {
        Map<String, Long> frameworks = new LinkedHashMap<>();
        Map<String, Long> cms = new LinkedHashMap<>();
        Map<String, Long> servers = new LinkedHashMap<>();
        Map<String, Long> wafs = new LinkedHashMap<>();

        List<WebFingerprint> all = isAdmin
                ? webFingerprintRepository.findAll()
                : webFingerprintRepository.findAllByCreatedBy(username);
        for (WebFingerprint wf : all) {
            if (hasText(wf.getFrameworkName())) {
                frameworks.merge(wf.getFrameworkName().trim(), 1L, Long::sum);
            }
            if (hasText(wf.getCmsName())) {
                cms.merge(wf.getCmsName().trim(), 1L, Long::sum);
            }
            if (hasText(wf.getServerHeader())) {
                String server = wf.getServerHeader().trim().split("/")[0].split(" ")[0];
                servers.merge(server, 1L, Long::sum);
            }
            if (hasText(wf.getWafName())) {
                wafs.merge(wf.getWafName().trim(), 1L, Long::sum);
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("frameworks", sortMap(frameworks));
        result.put("cms", sortMap(cms));
        result.put("servers", sortMap(servers));
        result.put("wafs", sortMap(wafs));
        return result;
    }

    private List<Map<String, Object>> sortMap(Map<String, Long> map) {
        return map.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(MAX_TOP_ITEMS)
                .map(entry -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("name", entry.getKey());
                    item.put("count", entry.getValue());
                    return item;
                })
                .collect(Collectors.toList());
    }

    private int assetRiskScore(Asset asset) {
        int critical = asset.getCriticalVulnCount() != null ? asset.getCriticalVulnCount() : 0;
        int ports = asset.getOpenPortCount() != null ? asset.getOpenPortCount() : 0;
        return critical * 10 + ports;
    }

    private int portImportance(Port port, Asset asset) {
        int score = Boolean.TRUE.equals(port.getIsWebService()) ? 100 : 0;
        score += importantPortBonus(port.getPortNumber());
        score += hasText(port.getServiceProduct()) ? 12 : 0;
        score += hasText(port.getServiceName()) ? 6 : 0;
        score += Math.min(30, asset.getCriticalVulnCount() != null ? asset.getCriticalVulnCount() : 0);
        return score;
    }

    private int importantPortBonus(int portNumber) {
        return switch (portNumber) {
            case 80, 81, 88, 443, 445, 8080, 8081, 8443, 9000, 9200 -> 50;
            case 21, 22, 23, 25, 53, 110, 135, 139, 143, 389, 465, 587, 6379, 7001, 8000, 8888 -> 28;
            default -> 8;
        };
    }

    private String resolveSubnet(String ipAddress) {
        if (!hasText(ipAddress)) return "unknown";
        int idx = ipAddress.lastIndexOf('.');
        return idx > 0 ? ipAddress.substring(0, idx) + ".0/24" : ipAddress;
    }

    private String assetLabel(Asset asset) {
        StringBuilder label = new StringBuilder();
        label.append(hasText(asset.getIpAddress()) ? asset.getIpAddress().trim() : "unknown");
        if (hasText(asset.getHostname())) {
            label.append(" (").append(asset.getHostname().trim()).append(")");
        }

        if (hasText(asset.getHostnameAliases())) {
            String aliases = asset.getHostnameAliases()
                    .replace("[", "")
                    .replace("]", "")
                    .replace("\"", "")
                    .trim();
            if (!aliases.isBlank()) {
                label.append(" [aka: ").append(aliases).append("]");
            }
        }
        return label.toString();
    }

    private Map<String, Object> labelNode(String type, String name) {
        if (!hasText(name)) {
            return new LinkedHashMap<>();
        }
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("name", name.trim());
        node.put("type", type);
        return node;
    }

    private void addDistinct(List<Map<String, Object>> nodes, Map<String, Object> nodeData) {
        String name = Objects.toString(nodeData.get("name"), "").trim();
        if (name.isEmpty()) return;
        String type = Objects.toString(nodeData.get("type"), "");
        boolean exists = nodes.stream().anyMatch(node ->
                type.equals(Objects.toString(node.get("type"), "")) &&
                        name.equalsIgnoreCase(Objects.toString(node.get("name"), ""))
        );
        if (!exists) {
            nodes.add(nodeData);
        }
    }

    private Map<String, Object> summaryNode(String type, int count, String sample) {
        return summaryNode(type, count, sample, null);
    }

    private Map<String, Object> summaryNode(String type, int count, String sample, String category) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("name", Integer.toString(Math.max(count, 0)));
        node.put("type", type);

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("count", Math.max(count, 0));
        if (hasText(sample)) meta.put("sample", sample.trim());
        if (hasText(category)) meta.put("category", category);
        node.put("meta", meta);
        return node;
    }

    private String sampleAssetIps(List<Asset> assets) {
        return assets.stream()
                .map(Asset::getIpAddress)
                .filter(this::hasText)
                .limit(4)
                .collect(Collectors.joining(", "));
    }

    private String samplePorts(List<Port> ports) {
        return ports.stream()
                .map(port -> Integer.toString(port.getPortNumber()))
                .limit(6)
                .collect(Collectors.joining(", "));
    }

    private String sampleVulns(List<AssetVulnerability> vulns) {
        return vulns.stream()
                .map(vuln -> vuln.getCveDatabase())
                .filter(Objects::nonNull)
                .map(cve -> cve.getCveId())
                .filter(this::hasText)
                .limit(3)
                .collect(Collectors.joining(", "));
    }

    private String normalizeServiceName(Port port) {
        if (hasText(port.getServiceName())) {
            return port.getServiceName().trim();
        }
        if (hasText(port.getServiceProduct())) {
            return port.getServiceProduct().trim();
        }
        return "unknown";
    }

    private String joinNameVersion(String name, String version) {
        if (!hasText(name)) return "";
        if (!hasText(version)) return name.trim();
        return name.trim() + " " + version.trim();
    }

    private String safeLower(String value) {
        return hasText(value) ? value.trim().toLowerCase(Locale.ROOT) : "tcp";
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
