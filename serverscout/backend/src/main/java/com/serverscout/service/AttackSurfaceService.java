package com.serverscout.service;

import com.serverscout.entity.*;
import com.serverscout.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Builds hierarchical attack surface data (Shannon-style reconnaissance map).
 */
@Service
@RequiredArgsConstructor
public class AttackSurfaceService {

    private final AssetRepository assetRepository;
    private final PortRepository portRepository;
    private final WebFingerprintRepository webFingerprintRepository;
    private final AssetVulnerabilityRepository avRepository;

    /**
     * Build a hierarchical attack surface tree for visualization.
     * Structure: Target → Subnets → IPs → Ports → Services → Tech → Vulns
     */
    @Transactional(readOnly = true)
    public Map<String, Object> buildAttackSurfaceMap(String username, boolean isAdmin) {
        List<Asset> assets = isAdmin
                ? assetRepository.findAll()
                : assetRepository.searchByCreatedBy(null, null, username,
                        org.springframework.data.domain.Pageable.unpaged()).getContent();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("name", "Attack Surface");
        result.put("type", "root");

        // Group assets by subnet or first-seen time
        List<Map<String, Object>> children = new ArrayList<>();

        // IP Group: group by first two octets (subnet)
        Map<String, List<Asset>> grouped = assets.stream()
                .collect(Collectors.groupingBy(a -> {
                    String ip = a.getIpAddress();
                    if (ip == null) return "unknown";
                    int idx = ip.lastIndexOf('.');
                    return idx > 0 ? ip.substring(0, idx) + ".0/24" : ip;
                }));

        for (var entry : grouped.entrySet()) {
            Map<String, Object> subnetNode = new LinkedHashMap<>();
            subnetNode.put("name", entry.getKey());
            subnetNode.put("type", "subnet");

            List<Map<String, Object>> assetNodes = new ArrayList<>();
            for (Asset asset : entry.getValue()) {
                Map<String, Object> assetNode = buildAssetNode(asset);
                assetNodes.add(assetNode);
            }
            subnetNode.put("children", assetNodes);
            children.add(subnetNode);
        }

        result.put("children", children);
        return result;
    }

    private Map<String, Object> buildAssetNode(Asset asset) {
        Map<String, Object> node = new LinkedHashMap<>();
        String label = asset.getIpAddress();
        if (asset.getHostname() != null) {
            label += " (" + asset.getHostname() + ")";
        }
        // Append hostname aliases if any
        if (asset.getHostnameAliases() != null && !asset.getHostnameAliases().isBlank()) {
            try {
                String[] parts = asset.getHostnameAliases().replace("[","").replace("]","").replace("\"","").split(",");
                if (parts.length > 0 && !parts[0].trim().isEmpty()) {
                    label += " [aka: " + String.join(", ", parts).replace("\"", "") + "]";
                }
            } catch (Exception ignored) {}
        }
        node.put("name", label);
        node.put("type", "asset");
        node.put("id", asset.getId());

        List<Port> ports = portRepository.findByAssetId(asset.getId());
        List<Map<String, Object>> portNodes = new ArrayList<>();

        for (Port port : ports) {
            Map<String, Object> portNode = new LinkedHashMap<>();
            String portLabel = ":" + port.getPortNumber() + " " +
                    (port.getServiceName() != null ? port.getServiceName() : "unknown");
            portNode.put("name", portLabel);
            portNode.put("type", Boolean.TRUE.equals(port.getIsWebService()) ? "web-port" : "port");

            List<Map<String, Object>> techNodes = new ArrayList<>();

            // Add service info
            if (port.getServiceProduct() != null) {
                Map<String, Object> svcNode = new LinkedHashMap<>();
                svcNode.put("name", port.getServiceProduct()
                        + (port.getServiceVersion() != null ? " " + port.getServiceVersion() : ""));
                svcNode.put("type", "service");
                techNodes.add(svcNode);
            }

            // Add web fingerprint details
            webFingerprintRepository.findByPortId(port.getId()).ifPresent(wf -> {
                if (wf.getFrameworkName() != null) {
                    Map<String, Object> fw = new LinkedHashMap<>();
                    fw.put("name", wf.getFrameworkName()
                            + (wf.getFrameworkVersion() != null ? " " + wf.getFrameworkVersion() : ""));
                    fw.put("type", "framework");
                    techNodes.add(fw);
                }
                if (wf.getCmsName() != null) {
                    Map<String, Object> cms = new LinkedHashMap<>();
                    cms.put("name", wf.getCmsName()
                            + (wf.getCmsVersion() != null ? " " + wf.getCmsVersion() : ""));
                    cms.put("type", "cms");
                    techNodes.add(cms);
                }
                if (wf.getWafName() != null) {
                    Map<String, Object> waf = new LinkedHashMap<>();
                    waf.put("name", wf.getWafName());
                    waf.put("type", "waf");
                    techNodes.add(waf);
                }
            });

            // Add vulnerabilities for this port
            List<AssetVulnerability> avs = avRepository.findByAssetIdWithCve(asset.getId());
            for (AssetVulnerability av : avs) {
                if (av.getCveDatabase() != null) {
                    Map<String, Object> vuln = new LinkedHashMap<>();
                    vuln.put("name", av.getCveDatabase().getCveId()
                            + " [" + (av.getCveDatabase().getSeverity() != null
                            ? av.getCveDatabase().getSeverity().toUpperCase() : "N/A") + "]");
                    vuln.put("type", "vulnerability");
                    vuln.put("severity", av.getCveDatabase().getSeverity());
                    techNodes.add(vuln);
                }
            }

            if (!techNodes.isEmpty()) {
                portNode.put("children", techNodes);
            }
            portNodes.add(portNode);
        }

        if (!portNodes.isEmpty()) {
            node.put("children", portNodes);
        }
        return node;
    }

    /**
     * Build aggregated technology stack statistics.
     */
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
            if (wf.getFrameworkName() != null && !wf.getFrameworkName().isEmpty()) {
                frameworks.merge(wf.getFrameworkName(), 1L, Long::sum);
            }
            if (wf.getCmsName() != null && !wf.getCmsName().isEmpty()) {
                cms.merge(wf.getCmsName(), 1L, Long::sum);
            }
            if (wf.getServerHeader() != null && !wf.getServerHeader().isEmpty()) {
                // Truncate server header to first word
                String srv = wf.getServerHeader().split("/")[0].split(" ")[0];
                servers.merge(srv, 1L, Long::sum);
            }
            if (wf.getWafName() != null && !wf.getWafName().isEmpty()) {
                wafs.merge(wf.getWafName(), 1L, Long::sum);
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
                .limit(10)
                .map(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("name", e.getKey());
                    m.put("count", e.getValue());
                    return m;
                })
                .collect(Collectors.toList());
    }
}
