package com.serverscout.service;

import com.serverscout.dto.AssetResponse;
import com.serverscout.dto.TopologyResponse;
import com.serverscout.entity.*;
import com.serverscout.repository.*;
import com.serverscout.util.ResourceNotFoundException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AssetService {

    private final AssetRepository assetRepository;
    private final PortRepository portRepository;
    private final WebFingerprintRepository webFingerprintRepository;
    private final AssetVulnerabilityRepository assetVulnerabilityRepository;
    private final SslCertificateRepository sslCertificateRepository;
    private final ScanAssetMappingRepository scanAssetMappingRepository;
    private final SubdomainRepository subdomainRepository;
    private final ObjectMapper objectMapper;

    public Page<Asset> listAssets(String keyword, String status, Pageable pageable, String username, boolean isAdmin) {
        if (isAdmin) {
            if (keyword != null || status != null) {
                return assetRepository.search(keyword, status, pageable);
            }
            return assetRepository.findAll(pageable);
        }
        return assetRepository.searchByCreatedBy(keyword, status, username, pageable);
    }

    @Transactional(readOnly = true)
    public AssetResponse getAssetDetail(Long id) {
        Asset asset = assetRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Asset", id));

        List<Port> ports = portRepository.findByAssetId(id);
        List<AssetResponse.PortDetail> portDetails = ports.stream().map(this::toPortDetail).collect(Collectors.toList());

        List<String> tags;
        try { tags = objectMapper.readValue(asset.getTags(), new TypeReference<List<String>>() {}); }
        catch (Exception e) { tags = List.of(); }

        List<String> hostnameAliases;
        try {
            hostnameAliases = asset.getHostnameAliases() != null && !asset.getHostnameAliases().isBlank()
                ? objectMapper.readValue(asset.getHostnameAliases(), new TypeReference<List<String>>() {})
                : List.of();
        } catch (Exception e) { hostnameAliases = List.of(); }

        long scanCount = scanAssetMappingRepository.countByAssetId(id);

        return AssetResponse.builder()
                .id(asset.getId()).ipAddress(asset.getIpAddress())
                .hostname(asset.getHostname()).hostnameAliases(hostnameAliases)
                .osFingerprint(asset.getOsFingerprint())
                .status(asset.getStatus())
                .openPortCount(asset.getOpenPortCount() != null ? asset.getOpenPortCount() : 0)
                .criticalVulnCount(asset.getCriticalVulnCount() != null ? asset.getCriticalVulnCount() : 0)
                .macAddress(asset.getMacAddress())
                .lastScanTime(asset.getLastScanTime())
                .firstSeenTime(asset.getFirstSeenTime())
                .scanCount((int) scanCount)
                .tags(tags).discoveredAt(asset.getDiscoveredAt()).updatedAt(asset.getUpdatedAt())
                .ports(portDetails).build();
    }

    private AssetResponse.PortDetail toPortDetail(Port port) {
        List<AssetVulnerability> vulns = assetVulnerabilityRepository.findByAssetId(port.getAsset().getId());
        List<AssetResponse.VulnBrief> vulnBriefs = vulns.stream()
                .filter(v -> v.getPort() != null && v.getPort().getId().equals(port.getId()))
                .map(v -> AssetResponse.VulnBrief.builder()
                        .cveId(v.getCveDatabase().getCveId())
                        .severity(v.getCveDatabase().getSeverity())
                        .cvssScore(v.getCveDatabase().getCvssScore().doubleValue())
                        .status(v.getStatus()).build())
                .collect(Collectors.toList());

        WebFingerprint wf = webFingerprintRepository.findByPortId(port.getId()).orElse(null);
        AssetResponse.WebFingerprintDetail wfDetail = null;
        if (wf != null) {
            wfDetail = AssetResponse.WebFingerprintDetail.builder()
                    .httpStatus(wf.getHttpStatus()).serverHeader(wf.getServerHeader())
                    .frameworkName(wf.getFrameworkName()).frameworkVersion(wf.getFrameworkVersion())
                    .cmsName(wf.getCmsName()).cmsVersion(wf.getCmsVersion())
                    .wafName(wf.getWafName())
                    .techStack(wf.getTechStack())
                    .title(wf.getTitle())
                    .faviconHash(wf.getFaviconHash())
                    .bodyHash(wf.getBodyHash()).build();
        }

        SslCertificate ssl = sslCertificateRepository.findByPortId(port.getId()).orElse(null);
        AssetResponse.SslCertBrief sslBrief = null;
        if (ssl != null) {
            sslBrief = AssetResponse.SslCertBrief.builder()
                    .id(ssl.getId()).subject(ssl.getSubject()).issuer(ssl.getIssuer())
                    .fingerprintSha256(ssl.getFingerprintSha256())
                    .notBefore(ssl.getNotBefore()).notAfter(ssl.getNotAfter())
                    .san(ssl.getSan()).sigAlg(ssl.getSigAlg())
                    .keySize(ssl.getKeySize()).isExpired(ssl.getIsExpired()).build();
        }

        return AssetResponse.PortDetail.builder()
                .id(port.getId()).portNumber(port.getPortNumber())
                .protocol(port.getProtocol()).serviceName(port.getServiceName())
                .serviceVersion(port.getServiceVersion()).serviceProduct(port.getServiceProduct())
                .state(port.getState()).banner(port.getBanner()).isWebService(port.getIsWebService())
                .webFingerprint(wfDetail).sslCertificate(sslBrief)
                .vulnerabilities(vulnBriefs).build();
    }

    @Transactional
    public void updateTags(Long id, List<String> tags) {
        Asset asset = assetRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Asset", id));
        try { asset.setTags(objectMapper.writeValueAsString(tags)); }
        catch (Exception e) { asset.setTags("[]"); }
        assetRepository.save(asset);
    }

    @Transactional
    public void deleteAsset(Long id) {
        Asset asset = assetRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Asset", id));

        List<Port> ports = portRepository.findByAssetId(id);
        for (Port port : ports) {
            webFingerprintRepository.deleteByPortId(port.getId());
            sslCertificateRepository.deleteByPortId(port.getId());
            assetVulnerabilityRepository.deleteByPortId(port.getId());
        }
        portRepository.deleteByAssetId(id);
        assetVulnerabilityRepository.deleteByAssetId(id);
        subdomainRepository.deleteAllByAssetId(id);
        scanAssetMappingRepository.deleteByAssetId(id);
        assetRepository.delete(asset);
    }

    @Transactional
    public Asset mergeAssets(List<Long> sourceIds, Long targetId) {
        Asset target = assetRepository.findById(targetId)
                .orElseThrow(() -> new ResourceNotFoundException("Asset", targetId));

        if (sourceIds.contains(targetId)) {
            sourceIds = sourceIds.stream().filter(id -> !id.equals(targetId)).collect(Collectors.toList());
        }

        Set<String> seenPorts = new HashSet<>();
        for (Port p : portRepository.findByAssetId(targetId)) {
            seenPorts.add(p.getPortNumber() + "/" + p.getProtocol());
        }

        for (Long sourceId : sourceIds) {
            Asset source = assetRepository.findById(sourceId).orElse(null);
            if (source == null) continue;

            // 迁移端口到目标资产
            for (Port port : portRepository.findByAssetId(sourceId)) {
                String key = port.getPortNumber() + "/" + port.getProtocol();
                if (!seenPorts.contains(key)) {
                    port.setAsset(target);
                    portRepository.save(port);
                    seenPorts.add(key);
                }
            }

            // 迁移扫描映射
            List<ScanAssetMapping> mappings = scanAssetMappingRepository.findByAssetId(sourceId);
            for (ScanAssetMapping m : mappings) {
                m.setAsset(target);
                scanAssetMappingRepository.save(m);
            }

            // 更新目标资产信息（取最全的）
            if (target.getHostname() == null && source.getHostname() != null) {
                target.setHostname(source.getHostname());
            }
            if (target.getOsFingerprint() == null && source.getOsFingerprint() != null) {
                target.setOsFingerprint(source.getOsFingerprint());
            }
            if (target.getMacAddress() == null && source.getMacAddress() != null) {
                target.setMacAddress(source.getMacAddress());
            }

            // 删除源资产
            assetRepository.delete(source);
        }

        long portCount = portRepository.countByAssetId(targetId);
        target.setOpenPortCount((int) portCount);
        assetRepository.save(target);

        return target;
    }

    public TopologyResponse getTopology(String username, boolean isAdmin) {
        List<Asset> assets = isAdmin
                ? assetRepository.findTopRisk(Pageable.unpaged())
                : assetRepository.findTopRiskByCreatedBy(username, Pageable.unpaged());
        if (assets.size() > 100) assets = assets.subList(0, 100);

        // Build nodes with richer data
        List<TopologyResponse.Node> nodes = assets.stream().map(a -> {
            List<Port> ports = portRepository.findByAssetId(a.getId());
            List<String> labels = new ArrayList<>(ports.stream()
                    .filter(p -> p.getServiceName() != null)
                    .map(Port::getServiceName).distinct().limit(5).collect(Collectors.toList()));
            // Also collect web framework / CMS labels from fingerprint
            for (Port p : ports) {
                webFingerprintRepository.findByPortId(p.getId()).ifPresent(wf -> {
                    if (wf.getCmsName() != null && !labels.contains(wf.getCmsName())) labels.add(wf.getCmsName());
                    if (wf.getFrameworkName() != null && !labels.contains(wf.getFrameworkName())) labels.add(wf.getFrameworkName());
                });
            }
            List<String> finalLabels = labels.subList(0, Math.min(labels.size(), 5));

            String subnet = a.getIpAddress() != null ?
                    a.getIpAddress().substring(0, a.getIpAddress().lastIndexOf('.')) : "default";
            return TopologyResponse.Node.builder()
                    .id(a.getId()).ipAddress(a.getIpAddress())
                    .hostname(a.getHostname()).openPortCount(a.getOpenPortCount())
                    .criticalVulnCount(a.getCriticalVulnCount())
                    .subnet(subnet).group(subnet)
                    .serviceLabels(finalLabels).build();
        }).collect(Collectors.toList());

        // Build links with multiple strategies
        List<TopologyResponse.Link> links = new ArrayList<>();
        Set<String> addedLinks = new HashSet<>();
        Map<String, List<TopologyResponse.Node>> subnetGroups = nodes.stream()
                .collect(Collectors.groupingBy(TopologyResponse.Node::getSubnet));

        // Strategy 1: Intra-subnet connections
        for (List<TopologyResponse.Node> group : subnetGroups.values()) {
            if (group.size() < 2) continue;

            // Find gateway nodes (.1 or .254)
            List<TopologyResponse.Node> gateways = group.stream()
                    .filter(n -> n.getIpAddress() != null &&
                            (n.getIpAddress().endsWith(".1") || n.getIpAddress().endsWith(".254")))
                    .collect(Collectors.toList());

            // Gateway-to-node links
            for (TopologyResponse.Node gw : gateways) {
                for (TopologyResponse.Node other : group) {
                    if (other.getId() != gw.getId()) {
                        String key = gw.getId() + "-" + other.getId();
                        if (addedLinks.add(key)) {
                            links.add(TopologyResponse.Link.builder()
                                    .source(gw.getId()).target(other.getId())
                                    .type("gateway").ports(new ArrayList<>()).build());
                        }
                    }
                }
            }

            // Connect nodes sharing common ports
            for (int i = 0; i < group.size(); i++) {
                for (int j = i + 1; j < group.size(); j++) {
                    List<String> common = new ArrayList<>(group.get(i).getServiceLabels());
                    common.retainAll(group.get(j).getServiceLabels());
                    if (!common.isEmpty()) {
                        String key = group.get(i).getId() + "-" + group.get(j).getId();
                        if (addedLinks.add(key)) {
                            links.add(TopologyResponse.Link.builder()
                                    .source(group.get(i).getId()).target(group.get(j).getId())
                                    .type("service").ports(new ArrayList<>()).build());
                        }
                    }
                }
            }

            // Subnet adjacency (limit to avoid dense graphs)
            if (gateways.isEmpty()) {
                for (int i = 0; i < group.size() - 1 && i < 20; i++) {
                    String key = group.get(i).getId() + "-" + group.get(i + 1).getId();
                    if (addedLinks.add(key)) {
                        links.add(TopologyResponse.Link.builder()
                                .source(group.get(i).getId()).target(group.get(i + 1).getId())
                                .type("network").ports(List.of()).build());
                    }
                }
            }
        }

        // Strategy 2: Cross-subnet connections via shared services
        for (int i = 0; i < nodes.size(); i++) {
            for (int j = i + 1; j < nodes.size(); j++) {
                if (nodes.get(i).getSubnet().equals(nodes.get(j).getSubnet())) continue;
                List<String> common = new ArrayList<>(nodes.get(i).getServiceLabels());
                common.retainAll(nodes.get(j).getServiceLabels());
                if (!common.isEmpty()) {
                    String key = nodes.get(i).getId() + "-" + nodes.get(j).getId();
                    if (addedLinks.add(key)) {
                        links.add(TopologyResponse.Link.builder()
                                .source(nodes.get(i).getId()).target(nodes.get(j).getId())
                                .type("service").ports(new ArrayList<>()).build());
                    }
                }
            }
        }

        // Strategy 3: Fallback — if no links generated at all, create a ring around the highest-risk node
        if (links.isEmpty() && nodes.size() >= 2) {
            // Find the node with most critical vulns as the center
            TopologyResponse.Node center = nodes.stream()
                    .max(Comparator.comparingInt(TopologyResponse.Node::getCriticalVulnCount)
                            .thenComparingInt(TopologyResponse.Node::getOpenPortCount))
                    .orElse(nodes.get(0));

            for (TopologyResponse.Node other : nodes) {
                if (other.getId() != center.getId()) {
                    links.add(TopologyResponse.Link.builder()
                            .source(center.getId()).target(other.getId())
                            .type("network").ports(List.of()).build());
                }
            }
        } else if (links.isEmpty() && nodes.size() == 1) {
            // Single node with a self-loop marker or just show it without links
            // G6 will still display the single node
        }

        return TopologyResponse.builder().nodes(nodes).links(links).build();
    }
}
