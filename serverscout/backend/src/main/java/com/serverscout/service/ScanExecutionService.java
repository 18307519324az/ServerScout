package com.serverscout.service;

import com.serverscout.entity.*;
import com.serverscout.repository.*;
import com.serverscout.service.scan.*;
import com.serverscout.util.ScanException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScanExecutionService {

    private final ScanTaskRepository scanTaskRepository;
    private final AssetRepository assetRepository;
    private final PortRepository portRepository;
    private final ScanAssetMappingRepository scanAssetMappingRepository;
    private final List<ScannerStrategy> scannerStrategies;
    private final ProgressEmitter progressEmitter;
    private final HttpProbeService httpProbeService;
    private final SslCertService sslCertService;
    private final SubdomainService subdomainService;
    private final CveDatabaseRepository cveDatabaseRepository;
    private final AssetVulnerabilityRepository assetVulnerabilityRepository;
    private final CertTransparencyService certTransparencyService;
    private final WebFingerprintRepository webFingerprintRepository;
    private final WebVulnDetectorService webVulnDetectorService;
    private final WebhookNotificationService webhookService;

    @Async("scanExecutor")
    public void executeScan(Long taskId) {
        ScanTask task = scanTaskRepository.findById(taskId).orElse(null);
        if (task == null) { log.error("Scan task {} not found", taskId); return; }

        try {
            task.setStatus("running");
            task.setStartedAt(Instant.now());
            scanTaskRepository.save(task);
            progressEmitter.sendProgress(taskId, 5, "正在启动端口扫描...", 0);

            ScannerStrategy strategy = scannerStrategies.stream()
                    .filter(s -> s.supports(task.getScanType()))
                    .findFirst()
                    .orElseThrow(() -> new ScanException("No scanner for type: " + task.getScanType()));

            ScanResult result = strategy.execute(task);

            if (isCancelled(taskId)) return;
            progressEmitter.sendProgress(taskId, 30,
                    "端口扫描完成，发现 " + result.getAssets().size() + " 个主机", result.getAssets().size());

            // Phase 1: Save/merge assets and ports with real-time discovery events
            int assetCount = saveScanResults(task, result);
            if (isCancelled(taskId)) return;

            task.setProgress(40);
            scanTaskRepository.save(task);
            progressEmitter.sendProgress(taskId, 40,
                    "资产数据已入库，新增 " + assetCount + " 个", assetCount);

            // Phase 1.5: Subdomain enumeration (if target is a domain)
            if (isDomainName(task.getTargetRange())) {
                try {
                    progressEmitter.sendProgress(taskId, 42, "正在枚举子域名...", assetCount);
                    SubdomainService.SubdomainResult subResult = subdomainService.enumerate(task.getTargetRange());
                    if (isCancelled(taskId)) return;
                    progressEmitter.sendProgress(taskId, 44,
                            "子域名枚举完成，发现 " + subResult.total() + " 个", assetCount);
                    log.info("Task {}: subdomain enum found {} subdomains", taskId, subResult.total());
                } catch (Exception e) {
                    log.warn("Subdomain enumeration failed: {}", e.getMessage());
                }
            }

            // Phase 2: HTTP probe for web services
            if (Boolean.TRUE.equals(task.getEnableFingerprint())) {
                progressEmitter.sendProgress(taskId, 45, "正在探测 Web 服务指纹...", assetCount);
                probeWebServices(task);
                if (isCancelled(taskId)) return;
                task.setProgress(55);
                scanTaskRepository.save(task);
                progressEmitter.sendProgress(taskId, 55, "Web 指纹识别完成", assetCount);
            } else {
                task.setProgress(55);
            }

            // Phase 2.5: Vulnerability scanning (Nuclei + CVE matching)
            if (Boolean.TRUE.equals(task.getEnableVulnScan())) {
                progressEmitter.sendProgress(taskId, 57, "正在执行漏洞扫描...", assetCount);
                runVulnScan(task);
                if (isCancelled(taskId)) return;
                task.setProgress(65);
                scanTaskRepository.save(task);
                progressEmitter.sendProgress(taskId, 65, "漏洞扫描完成", assetCount);

                progressEmitter.sendProgress(taskId, 67, "正在进行 CVE 匹配...", assetCount);
                int matched = matchCves(task);
                if (isCancelled(taskId)) return;
                task.setProgress(70);
                scanTaskRepository.save(task);
                progressEmitter.sendProgress(taskId, 70,
                        "CVE 匹配完成，发现 " + matched + " 个漏洞", assetCount);
            } else {
                task.setProgress(70);
            }

            // Phase 2.7: Web vulnerability detection (SQLi/XSS/CSRF)
            if (Boolean.TRUE.equals(task.getEnableVulnScan())) {
                progressEmitter.sendProgress(taskId, 72, "正在进行Web漏洞专项检测...", assetCount);
                List<ScanAssetMapping> allMappings = scanAssetMappingRepository
                        .findByScanTaskIdWithAsset(task.getId());
                List<Asset> detectedAssets = allMappings.stream()
                        .map(ScanAssetMapping::getAsset).distinct().collect(Collectors.toList());
                int webVulnCount = webVulnDetectorService.detect(task, detectedAssets,
                        portRepository, webFingerprintRepository);
                if (isCancelled(taskId)) return;
                task.setProgress(75);
                scanTaskRepository.save(task);
                progressEmitter.sendProgress(taskId, 75,
                        "Web漏洞检测完成，发现 " + webVulnCount + " 个风险", assetCount);
            }

            // Phase 3: SSL cert collection
            progressEmitter.sendProgress(taskId, 78, "正在采集 SSL 证书...", assetCount);
            collectSslCerts(task);
            if (isCancelled(taskId)) return;
            task.setProgress(85);
            scanTaskRepository.save(task);
            progressEmitter.sendProgress(taskId, 85, "SSL 证书采集完成", task.getTotalPorts());

            // Phase 3.5: Certificate transparency analysis
            try {
                progressEmitter.sendProgress(taskId, 87, "正在分析证书透明度...", assetCount);
                var ctResult = certTransparencyService.analyzeCertificates(task);
                if (isCancelled(taskId)) return;
                progressEmitter.sendProgress(taskId, 90,
                        "证书透明度分析完成，发现 " + ctResult.get("uniqueDomains") + " 个域名", assetCount);
                log.info("Task {}: CT analysis found {} domains", taskId, ctResult.get("uniqueDomains"));
            } catch (Exception e) {
                log.warn("Certificate transparency analysis failed: {}", e.getMessage());
            }

            task.setProgress(90);
            task.setStatus("completed");
            task.setProgress(100);
            task.setCompletedAt(Instant.now());
            scanTaskRepository.save(task);
            progressEmitter.sendCompleted(taskId);
            log.info("Scan task {} completed successfully", taskId);
            webhookService.sendScanCompletedNotification(taskId);

        } catch (Exception e) {
            if (isCancelled(taskId)) return;
            log.error("Scan failed for task {}", taskId, e);
            task.setStatus("failed");
            task.setErrorMessage(e.getMessage() != null ? e.getMessage() : "扫描执行异常");
            try { scanTaskRepository.save(task); } catch (Exception ex) { log.error("Failed to save task", ex); }
            progressEmitter.sendError(taskId, task.getErrorMessage());
            webhookService.sendScanCompletedNotification(taskId);
        }
    }

    private boolean isCancelled(Long taskId) {
        ScanTask task = scanTaskRepository.findById(taskId).orElse(null);
        if (task != null && "cancelled".equals(task.getStatus())) {
            log.info("Scan task {} was cancelled, stopping execution", taskId);
            progressEmitter.sendProgress(taskId, task.getProgress(), "扫描已取消", task.getTotalAssets());
            progressEmitter.sendError(taskId, "扫描已被用户取消");
            return true;
        }
        return false;
    }

    @Transactional
    protected int saveScanResults(ScanTask task, ScanResult result) {
        int newAssets = 0;
        int totalPorts = 0;

        for (ScanResult.AssetEntry ae : result.getAssets()) {
            Optional<Asset> existingOpt = assetRepository.findByIpAddress(ae.getIpAddress());
            Asset asset;
            boolean isNew = false;

            if (existingOpt.isPresent()) {
                asset = existingOpt.get();
                if (ae.getHostname() != null) {
                    appendHostname(asset, ae.getHostname());
                }
                if (ae.getOsFingerprint() != null) asset.setOsFingerprint(ae.getOsFingerprint());
                if (ae.getOsVersion() != null) asset.setOsVersion(ae.getOsVersion());
                if (ae.getMacAddress() != null) asset.setMacAddress(ae.getMacAddress());
                asset.setStatus("alive");
                asset.setLastScanTime(Instant.now());
            } else {
                asset = Asset.builder()
                        .task(task).ipAddress(ae.getIpAddress())
                        .hostname(ae.getHostname()).macAddress(ae.getMacAddress())
                        .osFingerprint(ae.getOsFingerprint()).osVersion(ae.getOsVersion())
                        .status("alive").openPortCount(0).criticalVulnCount(0).tags("[]")
                        .build();
                isNew = true;
                newAssets++;
            }

            asset = assetRepository.save(asset);
            int portCount = syncPorts(asset, ae.getPorts());
            long actualPortCount = portRepository.countByAssetId(asset.getId());
            asset.setOpenPortCount((int) actualPortCount);
            assetRepository.save(asset);

            ScanAssetMapping mapping = scanAssetMappingRepository
                    .findByScanTaskIdAndAssetId(task.getId(), asset.getId())
                    .orElse(ScanAssetMapping.builder().scanTask(task).asset(asset).build());
            mapping.setScanTime(Instant.now());
            mapping.setIsNew(isNew);
            mapping.setPortsFound(portCount);
            scanAssetMappingRepository.save(mapping);

            // Emit real-time discovery of this asset and its ports
            List<Map<String, Object>> portInfoList = ae.getPorts().stream()
                    .map(p -> {
                        Map<String, Object> pi = new HashMap<>();
                        pi.put("port", p.getPortNumber());
                        pi.put("protocol", p.getProtocol());
                        pi.put("service", p.getServiceName() != null ? p.getServiceName() : "");
                        pi.put("version", p.getServiceVersion() != null ? p.getServiceVersion() : "");
                        pi.put("product", p.getServiceProduct() != null ? p.getServiceProduct() : "");
                        return pi;
                    })
                    .collect(Collectors.toList());

            progressEmitter.sendDiscoveredAsset(task.getId(), ae.getIpAddress(),
                    ae.getHostname(), ae.getOsFingerprint(), portCount, portInfoList);
            progressEmitter.sendProgress(task.getId(), 30 + (int)((double)(newAssets + totalPorts) / Math.max(result.getAssets().size(), 1) * 10),
                    "发现主机: " + ae.getIpAddress() + " (" + portCount + " 端口)",
                    newAssets + totalPorts);

            totalPorts += portCount;
        }

        task.setTotalAssets(result.getAssets().size());
        task.setTotalPorts(totalPorts);
        log.info("Scan {} saved: {} new, {} touched, {} ports",
                task.getId(), newAssets, result.getAssets().size(), totalPorts);
        return newAssets;
    }

    private int syncPorts(Asset asset, List<ScanResult.PortEntry> portEntries) {
        int count = 0;
        for (ScanResult.PortEntry pe : portEntries) {
            Optional<Port> existingPort = portRepository
                    .findByAssetIdAndPortNumberAndProtocol(asset.getId(), pe.getPortNumber(), pe.getProtocol());

            if (existingPort.isPresent()) {
                Port port = existingPort.get();
                if (pe.getServiceName() != null) port.setServiceName(pe.getServiceName());
                if (pe.getServiceVersion() != null) port.setServiceVersion(pe.getServiceVersion());
                if (pe.getServiceProduct() != null) port.setServiceProduct(pe.getServiceProduct());
                if (pe.getState() != null) port.setState(pe.getState());
                if (pe.getBanner() != null) port.setBanner(pe.getBanner());
                port.setIsWebService(isWebPort(pe.getPortNumber(), pe.getServiceName()));
                portRepository.save(port);
            } else {
                Port port = Port.builder()
                        .asset(asset).portNumber(pe.getPortNumber())
                        .protocol(pe.getProtocol() != null ? pe.getProtocol() : "tcp")
                        .serviceName(pe.getServiceName()).serviceVersion(pe.getServiceVersion())
                        .serviceProduct(pe.getServiceProduct())
                        .state(pe.getState() != null ? pe.getState() : "open")
                        .banner(pe.getBanner())
                        .isWebService(isWebPort(pe.getPortNumber(), pe.getServiceName()))
                        .build();
                portRepository.save(port);
            }
            count++;
        }
        return count;
    }

    private void probeWebServices(ScanTask task) {
        List<ScanAssetMapping> mappings = scanAssetMappingRepository.findByScanTaskIdWithAsset(task.getId());
        List<Asset> assets = mappings.stream().map(ScanAssetMapping::getAsset).distinct().collect(Collectors.toList());
        if (assets.size() > 50) { assets = assets.subList(0, 50); }

        int probed = 0;
        for (Asset asset : assets) {
            String ip = asset.getIpAddress();
            for (Port port : portRepository.findByAssetId(asset.getId())) {
                if (Boolean.TRUE.equals(port.getIsWebService())) {
                    try {
                        HttpProbeService.ProbeResult pr = httpProbeService.probePort(ip, port);
                        if (pr != null) {
                            httpProbeService.saveProbeResult(port, pr);
                            probed++;
                            // Emit fingerprint discovery in real time
                            progressEmitter.sendDiscoveredFingerprint(task.getId(), ip,
                                    port.getPortNumber(), pr.serverHeader(), pr.frameworkName(),
                                    pr.cmsName(), pr.title());
                            progressEmitter.sendProgress(task.getId(), 45 + (int)((double)probed / Math.max(assets.size(), 1) * 10),
                                    "发现 Web 服务: " + ip + ":" + port.getPortNumber()
                                            + (pr.cmsName() != null ? " [" + pr.cmsName() + "]" : ""),
                                    task.getTotalAssets());
                        }
                    } catch (Exception e) { log.debug("HTTP probe error: {}", e.getMessage()); }
                }
            }
        }
        log.info("Task {}: HTTP probed {} web services", task.getId(), probed);
    }

    private void collectSslCerts(ScanTask task) {
        List<ScanAssetMapping> mappings = scanAssetMappingRepository.findByScanTaskIdWithAsset(task.getId());
        List<Asset> assets = mappings.stream().map(ScanAssetMapping::getAsset).distinct().collect(Collectors.toList());
        int certs = 0;

        for (Asset asset : assets) {
            String ip = asset.getIpAddress();
            for (Port port : portRepository.findByAssetId(asset.getId())) {
                if (port.getPortNumber() == 443 || port.getPortNumber() == 8443 ||
                        port.getPortNumber() == 636 || port.getPortNumber() == 993 ||
                        port.getPortNumber() == 995 ||
                        (port.getServiceName() != null &&
                         port.getServiceName().toLowerCase().contains("https"))) {
                    try {
                        SslCertService.SslCertResult cr = sslCertService.fetchCertificate(ip, port);
                        if (cr != null) { sslCertService.saveCertResult(port, cr); certs++; }
                    } catch (Exception e) { log.debug("SSL cert error: {}", e.getMessage()); }
                }
            }
        }
        log.info("Task {}: collected {} SSL certificates", task.getId(), certs);
    }

    private void runVulnScan(ScanTask task) {
        try {
            ScannerStrategy vulnStrategy = scannerStrategies.stream()
                    .filter(s -> s.supports("nuclei"))
                    .findFirst()
                    .orElse(null);
            if (vulnStrategy == null) {
                log.warn("No nuclei scanner found");
                return;
            }
            ScanResult vulnResult = vulnStrategy.execute(task);
            if (vulnResult.getVulnerabilities() != null && !vulnResult.getVulnerabilities().isEmpty()) {
                List<ScanAssetMapping> mappings = scanAssetMappingRepository.findByScanTaskIdWithAsset(task.getId());
                List<Asset> assets = mappings.stream().map(ScanAssetMapping::getAsset).distinct().collect(Collectors.toList());

                for (ScanResult.VulnEntry vuln : vulnResult.getVulnerabilities()) {
                    CveDatabase cve = cveDatabaseRepository.findByCveId(vuln.getTemplate())
                            .orElse(null);
                    if (cve == null && vuln.getTemplate() != null && vuln.getTemplate().startsWith("CVE-")) {
                        cve = CveDatabase.builder()
                                .cveId(vuln.getTemplate())
                                .description(vuln.getName())
                                .severity(vuln.getSeverity())
                                .affectedSoftware(vuln.getMatched())
                                .build();
                        cve = cveDatabaseRepository.save(cve);
                    }

                    // Emit vuln discovery in real time
                    progressEmitter.sendDiscoveredVuln(task.getId(), vuln.getSeverity(),
                            vuln.getTemplate(), vuln.getName(), vuln.getUrl(), vuln.getMatched());

                    for (Asset asset : assets) {
                        if (vuln.getUrl() != null && vuln.getUrl().contains(asset.getIpAddress())) {
                            boolean exists = assetVulnerabilityRepository.findByAssetId(asset.getId())
                                    .stream().anyMatch(av -> av.getCveDatabase() != null
                                            && av.getCveDatabase().getCveId() != null
                                            && av.getCveDatabase().getCveId().equals(vuln.getTemplate()));
                            if (!exists && cve != null) {
                                AssetVulnerability av = AssetVulnerability.builder()
                                        .asset(asset).cveDatabase(cve)
                                        .status("open").build();
                                assetVulnerabilityRepository.save(av);
                            }
                        }
                    }
                }
            }
            log.info("Task {}: nuclei found {} vulns", task.getId(),
                    vulnResult.getVulnerabilities() != null ? vulnResult.getVulnerabilities().size() : 0);
        } catch (Exception e) {
            log.warn("Vulnerability scan failed: {}", e.getMessage());
        }
    }

    // Only match CVEs against web middleware, frameworks, CMS, and server software
    private static final Set<String> GENERIC_SERVICES = Set.of(
            "http", "https", "http-proxy", "tcpwrapped", "ssh", "ftp", "smtp",
            "dns", "dhcp", "snmp", "telnet", "pop3", "imap", "nfs", "ntp",
            "netbios-ssn", "microsoft-ds", "msrpc", "mysql", "postgresql",
            "rdp", "vnc", "sip", "rtsp", "ipp", "cups", "upnp");

    // OS-level and generic software CVEs to skip entirely
    private static final Set<String> SKIP_CVE_AFFECTED = Set.of(
            "microsoft windows", "microsoft windows server", "microsoft windows 10",
            "microsoft windows 7", "microsoft windows 8.1", "microsoft windows 11",
            "microsoft windows rdp", "microsoft http.sys", "microsoft windows print spooler",
            "linux kernel", "sudo", "polkit", "glibc", "gnu c library",
            "runc", "arm mali", "arm mali gpu driver", "libcurl",
            "keepass", "pymatgen", "langchain", "ollama", "xz utils",
            "microsoft exchange server", "microsoft sharepoint",
            "microsoft office", "microsoft netlogon", "microsoft smbv3",
            "solarwinds", "solarwinds orion", "cobalt strike", "exiftool",
            "pear archive_tar", "phpunit", "systeminformation",
            "systeminformation (npm)", "anscale ray", "goanywhere mft",
            "http/2", "vbulletin", "sap netweaver");

    // Domain keywords that indicate web-app level software
    private static final Set<String> WEB_APP_KEYWORDS = Set.of(
            "apache", "nginx", "tomcat", "spring", "wordpress", "drupal", "joomla",
            "jenkins", "laravel", "rails", "django", "flask", "express", "node",
            "react", "vue", "angular", "jquery", "bootstrap", "php", "asp.net",
            "struts", "weblogic", "websphere", "jboss", "wildfly", "jetty",
            "iis", "caddy", "haproxy", "traefik", "envoy", "kong",
            "grafana", "kibana", "elasticsearch", "logstash", "solr", "lucene",
            "redis", "memcached", "rabbitmq", "activemq", "kafka", "rocketmq",
            "zookeeper", "consul", "etcd", "vault", "keycloak",
            "gitlab", "github", "bitbucket", "confluence", "jira",
            "nextcloud", "owncloud", "moodle", "magento", "shopify",
            "f5", "citrix", "fortinet", "pulse", "sonicwall", "cisco",
            "vmware", "vcenter", "esxi", "oracle", "mysql", "postgresql",
            "mariadb", "mongodb", "cassandra", "couchdb", "couchbase",
            "elastic", "splunk", "datadog", "new relic", "dynatrace");

    private int matchCves(ScanTask task) {
        int matched = 0;
        try {
            List<ScanAssetMapping> mappings = scanAssetMappingRepository.findByScanTaskIdWithAsset(task.getId());
            List<Asset> assets = mappings.stream().map(ScanAssetMapping::getAsset).distinct().collect(Collectors.toList());

            for (Asset asset : assets) {
                List<Port> ports = portRepository.findByAssetId(asset.getId());
                Set<String> matchedProducts = new HashSet<>();

                // Only match CVEs against web-service ports or those with fingerprint data
                for (Port port : ports) {
                    // Collect product names from web fingerprint first (most accurate)
                    java.util.Optional<WebFingerprint> wfOpt = webFingerprintRepository.findByPortId(port.getId());
                    if (wfOpt.isPresent()) {
                        WebFingerprint wf = wfOpt.get();
                        if (wf.getCmsName() != null && wf.getCmsName().length() >= 3) {
                            matchedProducts.add(wf.getCmsName().toLowerCase());
                        }
                        if (wf.getFrameworkName() != null && wf.getFrameworkName().length() >= 3) {
                            matchedProducts.add(wf.getFrameworkName().toLowerCase());
                        }
                        if (wf.getWafName() != null && wf.getWafName().length() >= 3) {
                            matchedProducts.add(wf.getWafName().toLowerCase());
                        }
                        if (wf.getServerHeader() != null && wf.getServerHeader().length() >= 3) {
                            matchedProducts.add(wf.getServerHeader().toLowerCase());
                        }
                    }

                    // Collect product names from port service detection (web ports only)
                    if (Boolean.TRUE.equals(port.getIsWebService())) {
                        String product = port.getServiceProduct() != null ? port.getServiceProduct().toLowerCase().trim() : "";
                        String svcName = port.getServiceName() != null ? port.getServiceName().toLowerCase().trim() : "";
                        if (product.length() >= 3 && !GENERIC_SERVICES.contains(product)
                                && isWebAppProduct(product)) {
                            matchedProducts.add(product);
                        }
                        // Also check service name if it's a known web-app product
                        if (!svcName.isEmpty() && !GENERIC_SERVICES.contains(svcName)
                                && isWebAppProduct(svcName)) {
                            matchedProducts.add(svcName);
                        }
                    }
                }

                // Match CVEs for each detected web product
                for (String product : matchedProducts) {
                    List<CveDatabase> cves = cveDatabaseRepository.findByAffectedSoftwareContaining(product);
                    for (CveDatabase cve : cves) {
                        if (cve.getAffectedSoftware() != null
                                && SKIP_CVE_AFFECTED.contains(cve.getAffectedSoftware().toLowerCase())) {
                            continue;
                        }
                        boolean alreadyLinked = assetVulnerabilityRepository.findByAssetId(asset.getId())
                                .stream().anyMatch(av -> av.getCveDatabase() != null
                                        && av.getCveDatabase().getId().equals(cve.getId()));
                        if (!alreadyLinked) {
                            AssetVulnerability av = AssetVulnerability.builder()
                                    .asset(asset).cveDatabase(cve)
                                    .status("open").build();
                            assetVulnerabilityRepository.save(av);
                            matched++;
                            if ("critical".equalsIgnoreCase(cve.getSeverity())) {
                                asset.setCriticalVulnCount(
                                        (asset.getCriticalVulnCount() == null ? 0 : asset.getCriticalVulnCount()) + 1);
                            }
                            // Emit CVE match discovery
                            progressEmitter.sendDiscoveredVuln(task.getId(),
                                    cve.getSeverity(), cve.getCveId(), cve.getDescription(),
                                    asset.getIpAddress(), cve.getAffectedSoftware());
                        }
                    }
                }
                assetRepository.save(asset);
            }
        } catch (Exception e) {
            log.warn("CVE matching failed: {}", e.getMessage());
        }
        return matched;
    }

    /** Check if a product name is likely a web application, not an OS or system utility */
    private boolean isWebAppProduct(String product) {
        if (product == null || product.length() < 3) return false;
        // Must contain at least one web-app keyword
        for (String keyword : WEB_APP_KEYWORDS) {
            if (product.contains(keyword)) return true;
        }
        // Check if product looks like a web technology (has version, known patterns)
        return product.contains("web") || product.contains("http")
                || product.contains("cms") || product.contains("blog")
                || product.contains("shop") || product.contains("forum")
                || product.contains("wiki") || product.contains("portal");
    }

    private boolean isDomainName(String target) {
        if (target == null) return false;
        return !target.matches("^[\\d.]+(/\\d+)?$") && target.contains(".");
    }

    /**
     * Append a new hostname to the asset's hostname aliases, keeping existing hostname as primary.
     */
    private void appendHostname(Asset asset, String newHostname) {
        if (newHostname == null || newHostname.isBlank()) return;

        String current = asset.getHostname();
        // If no current hostname, set it directly
        if (current == null || current.isBlank()) {
            asset.setHostname(newHostname);
            return;
        }

        // If same as current, nothing to do
        if (current.equalsIgnoreCase(newHostname)) return;

        // Check existing aliases
        Set<String> allHostnames = getHostnameAliases(asset);
        if (allHostnames.contains(newHostname.toLowerCase())) return;

        // Add current as alias if not already there, then set new as primary
        allHostnames.add(current.toLowerCase());
        allHostnames.add(newHostname.toLowerCase());
        // Switch: new becomes primary, old goes to aliases
        asset.setHostnameAliases(toJsonArray(allHostnames));
    }

    private Set<String> getHostnameAliases(Asset asset) {
        Set<String> result = new LinkedHashSet<>();
        String aliases = asset.getHostnameAliases();
        if (aliases != null && !aliases.isBlank()) {
            try {
                String[] parts = aliases.replace("[", "").replace("]", "").replace("\"", "").split(",");
                for (String p : parts) {
                    String trimmed = p.trim();
                    if (!trimmed.isEmpty()) result.add(trimmed.toLowerCase());
                }
            } catch (Exception e) {
                // ignore parse errors
            }
        }
        return result;
    }

    private String toJsonArray(Collection<String> items) {
        return "[" + items.stream().map(s -> "\"" + s + "\"").collect(Collectors.joining(",")) + "]";
    }

    private boolean isWebPort(int port, String serviceName) {
        if (port == 80 || port == 443 || port == 8080 || port == 8443 ||
            port == 8000 || port == 8888 || port == 3000 || port == 8443) return true;
        if (serviceName != null) {
            String s = serviceName.toLowerCase();
            return s.contains("http") || s.contains("https") || s.contains("ssl/http")
                || s.contains("www") || s.contains("web");
        }
        return false;
    }
}
