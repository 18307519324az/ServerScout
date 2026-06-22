package com.serverscout.service.scan;

import com.serverscout.entity.ScanTask;
import com.serverscout.service.ProgressEmitter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@Order(0)
@ConditionalOnProperty(name = "app.scan.demo-mode", havingValue = "true")
@RequiredArgsConstructor
public class DemoScannerStrategy implements ScannerStrategy {

    private final ProgressEmitter progressEmitter;

    @Value("${app.scan.demo-mode:false}")
    private boolean demoMode;

    @Override
    public boolean supports(String scanType) {
        return demoMode;
    }

    @Override
    public ScanResult execute(ScanTask task) {
        String scanType = task.getScanType();
        Long taskId = task.getId();
        log.info("DemoScanner executing for task {} (type={})", taskId, scanType);

        // Build target IPs
        List<String> targetIps = deriveIps(task.getTargetRange(), "full".equals(scanType) ? 5 : 3);

        // Stage 1: target validation
        progressEmitter.sendProgress(taskId, 10, "Validating target...", 0);
        sleep(300);

        // Stage 2: port scanning
        progressEmitter.sendProgress(taskId, 25, "Scanning ports...", 0);
        sleep(500);

        // Generate mock assets with ports
        List<ScanResult.AssetEntry> assets = generateAssets(targetIps, scanType);

        // Stage 3: service identification
        progressEmitter.sendProgress(taskId, 40, "Identifying services...", assets.size());
        sleep(400);

        // "nuclei" scan type returns vulnerabilities only
        if ("nuclei".equals(scanType)) {
            List<ScanResult.VulnEntry> vulns = generateVulns(targetIps, "nuclei");
            progressEmitter.sendProgress(taskId, 75, "Vulnerability detection completed", 0);
            sleep(100);
            progressEmitter.sendProgress(taskId, 100, "Demo scan completed", assets.size());
            log.info("DemoScanner generated {} vulns for task {}", vulns.size(), taskId);
            return ScanResult.builder()
                    .assets(List.of())
                    .vulnerabilities(vulns)
                    .build();
        }

        // Stages 4-6: only for quick/full
        boolean isFull = "full".equals(scanType);
        if (isFull) {
            progressEmitter.sendProgress(taskId, 57, "Running vulnerability detection...", assets.size());
            sleep(500);
        }

        progressEmitter.sendProgress(taskId, 75, "Performing risk analysis...", assets.size());
        sleep(300);

        progressEmitter.sendProgress(taskId, 90, "Saving results...", assets.size());
        sleep(200);

        progressEmitter.sendProgress(taskId, 100, "Demo scan completed", assets.size());

        log.info("DemoScanner generated {} assets for task {}", assets.size(), taskId);
        return ScanResult.builder()
                .assets(assets)
                .vulnerabilities(List.of())
                .build();
    }

    // ========== IP derivation ==========

    private List<String> deriveIps(String targetRange, int count) {
        if (targetRange == null || targetRange.isBlank()) {
            return fallbackIps(count);
        }

        // Single IP: 192.168.1.10
        if (targetRange.matches("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}$")) {
            return incrementIps(targetRange, count);
        }

        // CIDR: 192.168.1.0/24
        if (targetRange.contains("/")) {
            String base = targetRange.substring(0, targetRange.indexOf('/'));
            // Replace last octet with .10 base for increment
            int lastDot = base.lastIndexOf('.');
            if (lastDot > 0) {
                String prefix = base.substring(0, lastDot);
                List<String> ips = new ArrayList<>();
                for (int i = 0; i < count; i++) {
                    ips.add(prefix + "." + (10 + i * 10));
                }
                return ips;
            }
        }

        // Unrecognized format
        return fallbackIps(count);
    }

    private List<String> incrementIps(String baseIp, int count) {
        List<String> ips = new ArrayList<>();
        String prefix = baseIp.substring(0, baseIp.lastIndexOf('.') + 1);
        int last = Integer.parseInt(baseIp.substring(baseIp.lastIndexOf('.') + 1));
        for (int i = 0; i < count; i++) {
            ips.add(prefix + (last + i));
        }
        return ips;
    }

    private List<String> fallbackIps(int count) {
        List<String> ips = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            ips.add("192.168.56." + (10 + i * 10));
        }
        return ips;
    }

    // ========== Port pool ==========

    private static final int[][] PORT_POOL = {
            {22, 80, 443, 3306, 6379, 8080, 9200}
    };

    private static final String[] PORT_SERVICES = {
            "ssh", "http", "https", "mysql", "redis", "http-proxy", "elasticsearch"
    };

    private static final String[] PORT_PRODUCTS = {
            "OpenSSH", "nginx", "nginx", "MySQL", "Redis", "Spring Boot", "Elasticsearch"
    };

    private static final String[] PORT_VERSIONS = {
            "8.9", "1.22", "1.22", "8.0", "7.0", "3.2.0", "8.11.0"
    };

    // ========== Asset generation ==========

    private List<ScanResult.AssetEntry> generateAssets(List<String> ips, String scanType) {
        List<ScanResult.AssetEntry> assets = new ArrayList<>();
        String[] hostnames = {"web.demo.internal", "app.demo.internal", "db.demo.internal",
                "api.demo.internal", "monitor.demo.internal"};
        String[] oses = {"Linux Ubuntu 22.04", "Linux CentOS 7.9", "Linux Ubuntu 20.04",
                "Alpine Linux 3.17", "Debian 11"};
        int hostCount = Math.min(ips.size(), hostnames.length);

        for (int i = 0; i < hostCount; i++) {
            boolean isFull = "full".equals(scanType);
            int portCount = isFull ? 4 + (i % 4) : 2 + (i % 2);
            portCount = Math.min(portCount, PORT_POOL[0].length);

            List<ScanResult.PortEntry> ports = new ArrayList<>();
            for (int p = 0; p < portCount; p++) {
                ports.add(ScanResult.PortEntry.builder()
                        .portNumber(PORT_POOL[0][p])
                        .protocol("tcp")
                        .serviceName(PORT_SERVICES[p])
                        .serviceProduct(PORT_PRODUCTS[p])
                        .serviceVersion(PORT_VERSIONS[p])
                        .state("open")
                        .build());
            }

            assets.add(ScanResult.AssetEntry.builder()
                    .ipAddress(ips.get(i))
                    .hostname(hostnames[i])
                    .osFingerprint(oses[i % oses.length])
                    .osVersion("6.x")
                    .ports(ports)
                    .build());
        }
        return assets;
    }

    // ========== Vulnerability generation ==========

    private static final String[][] DEMO_VULN_TEMPLATES = {
            // template (CVE ID), name, severity, matched software, url path
            {"CVE-2021-41773", "Apache HTTPD 路径穿越 RCE", "CRITICAL", "Apache HTTP Server 2.4.49", "/cgi-bin"},
            {"CVE-2021-44228", "Apache Log4j2 JNDI 远程代码执行 (Log4Shell)", "CRITICAL", "Apache Log4j2 2.14.1", "/api/log"},
            {"CVE-2022-22965", "Spring Framework RCE (Spring4Shell)", "CRITICAL", "Spring Framework 5.3.17", "/"},
            {"CVE-2023-34362", "MOVEit Transfer SQL 注入 RCE", "CRITICAL", "MOVEit Transfer", "/api"},
            {"CVE-2020-0796", "SMBv3 远程代码执行 (SMBGhost)", "HIGH", "Windows 10 SMBv3", null},
            {"CVE-2021-40438", "Apache HTTPD SSRF", "HIGH", "Apache HTTP Server 2.4.48", "/proxy"},
            {"CVE-2022-22947", "Spring Cloud Gateway 代码注入", "HIGH", "Spring Cloud Gateway 3.1.0", "/gateway"},
            {"CVE-2021-43798", "Grafana 任意文件读取", "MEDIUM", "Grafana 8.3.0", "/public"},
            {"CVE-2022-26134", "Atlassian Confluence OGNL 注入", "MEDIUM", "Atlassian Confluence 7.13.0", null},
            {"CVE-2024-23897", "Jenkins CLI 任意文件读取", "MEDIUM", "Jenkins 2.441", "/cli"},
            {"CVE-2019-0211", "Apache HTTPD 权限提升", "LOW", "Apache HTTP Server 2.4.38", "/cgi-bin"},
            {"CVE-2022-36804", "Atlassian Bitbucket Server RCE", "LOW", "Atlassian Bitbucket Server 8.3.0", null},
    };

    private List<ScanResult.VulnEntry> generateVulns(List<String> targetIps, String scanType) {
        List<ScanResult.VulnEntry> vulns = new ArrayList<>();
        boolean isFull = "full".equals(scanType) || "nuclei".equals(scanType);
        int vulnCount = isFull ? 7 : 2;

        for (int i = 0; i < vulnCount && i < DEMO_VULN_TEMPLATES.length; i++) {
            String[] tpl = DEMO_VULN_TEMPLATES[i];
            String ip = targetIps.get(i % targetIps.size());
            String url = tpl[4] != null ? "http://" + ip + ":80" + tpl[4] : "http://" + ip + ":80/";

            vulns.add(ScanResult.VulnEntry.builder()
                    .template(tpl[0])
                    .name("[Demo] " + tpl[1])
                    .severity(tpl[2])
                    .matched(tpl[3])
                    .url(url)
                    .build());
        }
        return vulns;
    }

    // ========== Stage simulation ==========

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
