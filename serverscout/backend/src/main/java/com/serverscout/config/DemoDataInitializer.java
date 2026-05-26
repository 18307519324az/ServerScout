package com.serverscout.config;

import com.serverscout.entity.*;
import com.serverscout.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Seeds demo data with 12+ assets across 3 subnets for platform demonstration.
 * Only runs when fewer than 5 assets exist (first-run scenario).
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Order(2)
public class DemoDataInitializer implements CommandLineRunner {

    private final AssetRepository assetRepository;
    private final PortRepository portRepository;
    private final WebFingerprintRepository webFingerprintRepository;
    private final AssetVulnerabilityRepository avRepository;
    private final CveDatabaseRepository cveRepository;
    private final ScanAssetMappingRepository mappingRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final ScanTaskRepository scanTaskRepository;
    private final CrawledUrlRepository crawledUrlRepository;

    @Override
    @Transactional
    public void run(String... args) {
        // Seed crawled URLs regardless of existing asset data
        try {
            long crawledCount = crawledUrlRepository.count();
            log.info("Crawled URL count: {}", crawledCount);
            if (crawledCount == 0) {
                log.info("Seeding demo crawled URLs...");
                seedAllCrawledUrls();
                log.info("Crawled URLs seeded: {}", crawledUrlRepository.count());
            }
        } catch (Exception e) {
            log.warn("Failed to check/seed crawled URLs: {}", e.getMessage());
        }

        if (assetRepository.count() >= 5) {
            log.info("Demo data: {} assets exist, skipping seed", assetRepository.count());
            return;
        }

        log.info("Seeding demo data with 12+ targets...");

        try {

        createDemoUser("demo_user", "demo123", "演示用户", "MALE", "USER", "demo@serverscout.local");
        createDemoUser("security_ops", "ops123", "安全运营", "FEMALE", "USER", "secops@serverscout.local");

        ScanTask demoTask = scanTaskRepository.findAll().stream()
                .filter(t -> "演示数据导入".equals(t.getName())).findFirst()
                .orElse(null);
        if (demoTask == null) {
            demoTask = ScanTask.builder()
                    .name("演示数据导入").targetRange("demo").scanType("full")
                    .status("completed").progress(100).totalAssets(12).totalPorts(45)
                    .startedAt(Instant.now().minusSeconds(3600))
                    .completedAt(Instant.now().minusSeconds(1800))
                    .createdBy("admin").createdAt(Instant.now().minusSeconds(7200))
                    .build();
            demoTask = scanTaskRepository.save(demoTask);
        }

        Instant now = Instant.now();

        // ===== Subnet 1: 192.168.1.0/24 =====
        Asset web01 = createAsset("192.168.1.10", "web01.internal.local", "Linux Ubuntu 22.04",
                new int[]{22, 80, 443, 3306, 8080}, demoTask, now);
        addPortServices(web01, new int[]{22, 80, 443, 3306, 8080},
                new String[]{"ssh", "http", "https", "mysql", "http-proxy"},
                new String[]{"OpenSSH", "Apache HTTP Server", "Apache HTTP Server", "MySQL", "Apache Tomcat"},
                new String[]{null, "2.4.49", null, "8.0.32", "9.0.30"});
        addFingerprint(web01, 80, 200, "Apache/2.4.49", "WordPress", "5.8.2",
                "PHP/7.4,MySQL/8.0,WordPress/5.8.2", "企业门户 - 首页");
        addFingerprint(web01, 8080, 200, "Apache-Coyote/1.1", "Spring", "5.3.17",
                "Java/17,Spring Boot/2.7,React/18", "内部管理系统 API");
        addVuln(web01, "CVE-2021-41773", "open", now);
        addVuln(web01, "CVE-2021-44228", "open", now);
        addVuln(web01, "CVE-2022-22965", "open", now);

        Asset web02 = createAsset("192.168.1.20", "web02.internal.local", "CentOS 7.9",
                new int[]{22, 80, 443}, demoTask, now);
        addPortServices(web02, new int[]{22, 80, 443},
                new String[]{"ssh", "http", "https"},
                new String[]{"OpenSSH", "nginx", "nginx"},
                new String[]{null, "1.20.1", null});
        addFingerprint(web02, 80, 200, "nginx/1.20.1", "Vue.js", "3.2",
                "Vue.js/3.2,nginx/1.20.1", "开发测试环境");

        Asset db01 = createAsset("192.168.1.30", "db01.internal.local", "Ubuntu 20.04 LTS",
                new int[]{22, 3306, 5432, 6379}, demoTask, now);
        addPortServices(db01, new int[]{22, 3306, 5432, 6379},
                new String[]{"ssh", "mysql", "postgresql", "redis"},
                new String[]{"OpenSSH", "MySQL", "PostgreSQL", "Redis"},
                new String[]{null, "8.0.32", "14.5", "6.2.6"});

        Asset gitlab = createAsset("192.168.1.40", "gitlab.internal.local", "Ubuntu 20.04",
                new int[]{22, 80, 443, 5000}, demoTask, now);
        addPortServices(gitlab, new int[]{22, 80, 443, 5000},
                new String[]{"ssh", "http", "https", "http"},
                new String[]{"OpenSSH", "GitLab", "GitLab", "Docker Registry"},
                new String[]{null, "14.10.0", null, "2.8"});
        addFingerprint(gitlab, 80, 302, "nginx/1.18.0 + GitLab", "GitLab", "14.10.0",
                "Ruby/3.0,Rails/6.1,PostgreSQL/13", "GitLab CE");
        addVuln(gitlab, "CVE-2021-22205", "open", now);

        // ===== Subnet 2: 10.0.0.0/24 (DMZ) =====
        Asset nginxLb = createAsset("10.0.0.10", "lb.dmz.local", "Ubuntu 22.04",
                new int[]{22, 80, 443, 9090}, demoTask, now);
        addPortServices(nginxLb, new int[]{22, 80, 443, 9090},
                new String[]{"ssh", "http", "https", "http"},
                new String[]{"OpenSSH", "nginx", "nginx", "HAProxy"},
                new String[]{null, "1.24.0", null, "2.6.9"});
        addFingerprint(nginxLb, 9090, 200, null, "HAProxy", "2.6.9", "HAProxy/2.6.9", "HAProxy Stats");

        Asset jenkins = createAsset("10.0.0.20", "jenkins.dmz.local", "Debian 11",
                new int[]{22, 8080, 50000}, demoTask, now);
        addPortServices(jenkins, new int[]{22, 8080, 50000},
                new String[]{"ssh", "http", "http"},
                new String[]{"OpenSSH", "Jenkins", "Jenkins JNLP"},
                new String[]{null, "2.441", "2.441"});
        addFingerprint(jenkins, 8080, 200, "Jetty(9.4)", "Jenkins", "2.441",
                "Java/11,Jenkins/2.441", "Jenkins CI/CD");
        addVuln(jenkins, "CVE-2018-1000861", "open", now);
        addVuln(jenkins, "CVE-2024-23897", "open", now);

        Asset monitor = createAsset("10.0.0.30", "monitor.dmz.local", "Ubuntu 20.04",
                new int[]{22, 3000, 9090, 9100}, demoTask, now);
        addPortServices(monitor, new int[]{22, 3000, 9090, 9100},
                new String[]{"ssh", "http", "http", "http"},
                new String[]{"OpenSSH", "Grafana", "Prometheus", "Node Exporter"},
                new String[]{null, "8.3.0", "2.40.0", "1.5.0"});
        addFingerprint(monitor, 3000, 200, null, "Grafana", "8.3.0",
                "Go,Grafana/8.3.0", "Grafana Dashboard");
        addVuln(monitor, "CVE-2021-43798", "open", now);

        Asset apiGw = createAsset("10.0.0.40", "api.dmz.local", "Alpine Linux 3.17",
                new int[]{22, 8443, 8000}, demoTask, now);
        addPortServices(apiGw, new int[]{22, 8443, 8000},
                new String[]{"ssh", "https", "http"},
                new String[]{"OpenSSH", "Kong", "Spring Boot"},
                new String[]{null, "3.5.2", "3.2.0"});
        addFingerprint(apiGw, 8443, 200, null, "Kong", "3.5.2",
                "OpenResty,Kong/3.5.2", "Kong API Gateway Admin");
        addVuln(apiGw, "CVE-2024-3651", "open", now);

        // ===== Subnet 3: 172.16.0.0/24 (External) =====
        Asset extWeb = createAsset("172.16.0.10", "www.example-company.com", "CentOS 8",
                new int[]{22, 80, 443}, demoTask, now);
        addPortServices(extWeb, new int[]{22, 80, 443},
                new String[]{"ssh", "http", "https"},
                new String[]{"OpenSSH", "Apache HTTP Server", "Apache HTTP Server"},
                new String[]{null, "2.4.48", null});
        addFingerprint(extWeb, 80, 200, "Apache/2.4.48", "WordPress", "5.8.2",
                "PHP/7.4,MySQL/8.0,WordPress/5.8.2,jQuery/3.6.0", "Example Company 官网");
        addVuln(extWeb, "CVE-2021-40438", "open", now);

        Asset extMail = createAsset("172.16.0.20", "mail.example-company.com", "Ubuntu 20.04",
                new int[]{25, 143, 443, 993}, demoTask, now);
        addPortServices(extMail, new int[]{25, 143, 443, 993},
                new String[]{"smtp", "imap", "https", "imaps"},
                new String[]{"Postfix", "Dovecot", "nginx", "Dovecot"},
                new String[]{null, null, null, null});

        Asset f5Bigip = createAsset("172.16.0.30", "f5.example-company.com", "F5 BIG-IP",
                new int[]{22, 443, 8443}, demoTask, now);
        addPortServices(f5Bigip, new int[]{22, 443, 8443},
                new String[]{"ssh", "https", "https"},
                new String[]{"OpenSSH", "F5 BIG-IP", "F5 BIG-IP TMUI"},
                new String[]{null, "15.1.2", "15.1.2"});
        addFingerprint(f5Bigip, 8443, 200, "BIG-IP/15.1.2", "F5 BIG-IP", "15.1.2",
                "F5 BIG-IP/15.1.2,TMUI", "F5 BIG-IP 管理界面");
        addVuln(f5Bigip, "CVE-2021-22986", "open", now);
        addVuln(f5Bigip, "CVE-2022-1388", "open", now);

        // Set critical vuln counts
        updateCriticalCount(web01, 3);
        updateCriticalCount(gitlab, 1);
        updateCriticalCount(jenkins, 1);
        updateCriticalCount(f5Bigip, 2);

        // Seed demo crawled URLs for web assets (Goby-style crawler + screenshot)
        seedCrawledUrls(demoTask, web01, getPort(web01, 80),
                new String[][]{
                        {"http://192.168.1.10:80/", "/", "200", "企业门户 - 首页", "text/html; charset=utf-8",
                         "企业门户网站首页 公司简介 产品中心 新闻动态 联系我们 版权所有 © 2024",
                         "12", "0", "120"},
                        {"http://192.168.1.10:80/about", "/about", "200", "关于我们 - 企业门户", "text/html; charset=utf-8",
                         "关于我们 公司成立于2010年 专注于网络安全领域 拥有多项核心技术专利",
                         "8", "1", "95"},
                        {"http://192.168.1.10:80/products", "/products", "200", "产品中心 - 企业门户", "text/html; charset=utf-8",
                         "产品列表 防火墙 入侵检测系统 Web应用防火墙 安全审计系统",
                         "15", "1", "180"},
                        {"http://192.168.1.10:80/contact", "/contact", "200", "联系我们 - 企业门户", "text/html; charset=utf-8",
                         "联系方式 地址 电话 邮箱 在线留言表单",
                         "6", "1", "89"},
                });
        seedCrawledUrls(demoTask, web01, getPort(web01, 8080),
                new String[][]{
                        {"http://192.168.1.10:8080/", "/", "200", "内部管理系统 API", "text/html; charset=utf-8",
                         "API管理后台 用户管理 系统配置 日志审计 版本信息 Spring Boot 2.7",
                         "10", "0", "145"},
                        {"http://192.168.1.10:8080/swagger-ui.html", "/swagger-ui.html", "200", "Swagger UI - API文档", "text/html; charset=utf-8",
                         "API接口文档 GET POST PUT DELETE 用户接口 资产管理 扫描任务 漏洞管理",
                         "25", "1", "230"},
                });
        seedCrawledUrls(demoTask, web02, getPort(web02, 80),
                new String[][]{
                        {"http://192.168.1.20:80/", "/", "200", "开发测试环境", "text/html; charset=utf-8",
                         "开发环境 测试服务 版本信息 Vue.js 3.2 构建工具 Vite",
                         "5", "0", "65"},
                });
        seedCrawledUrls(demoTask, jenkins, getPort(jenkins, 8080),
                new String[][]{
                        {"http://10.0.0.20:8080/", "/", "200", "Jenkins CI/CD", "text/html; charset=utf-8",
                         "Jenkins 持续集成 构建队列 任务列表 Pipeline Multibranch",
                         "20", "0", "310"},
                        {"http://10.0.0.20:8080/job/build-backend", "/job/build-backend", "200", "build-backend [Jenkins]", "text/html; charset=utf-8",
                         "构建任务 后端编译 状态成功 最近构建 #145",
                         "8", "1", "200"},
                });
        seedCrawledUrls(demoTask, extWeb, getPort(extWeb, 80),
                new String[][]{
                        {"http://172.16.0.10:80/", "/", "200", "Example Company 官网", "text/html; charset=utf-8",
                         "Example Company 全球领先的解决方案 数字化转型 云服务 安全咨询",
                         "18", "0", "250"},
                });

        log.info("Demo data seeded: 12 assets, {} ports, {} crawled URLs",
                portRepository.count(), crawledUrlRepository.count());

        } catch (Exception e) {
            log.warn("Demo data seeding failed: {} — app will start without demo data", e.getMessage());
        }
    }

    private void createDemoUser(String username, String password, String name,
                                 String gender, String role, String email) {
        if (userRepository.existsByUsername(username)) return;
        User user = User.builder()
                .username(username).password(passwordEncoder.encode(password))
                .name(name).gender(gender).role(role).email(email)
                .enabled(true).createdAt(Instant.now()).build();
        userRepository.save(user);
    }

    private Asset createAsset(String ip, String hostname, String os,
                               int[] ports, ScanTask task, Instant now) {
        var existing = assetRepository.findByIpAddress(ip);
        if (existing.isPresent()) {
            Asset asset = existing.get();
            // Refresh task link and ensure mapping exists for the current demo task
            asset.setTask(task);
            asset.setLastScanTime(now);
            assetRepository.save(asset);
            if (mappingRepository.findByScanTaskIdAndAssetId(task.getId(), asset.getId()).isEmpty()) {
                ScanAssetMapping mapping = ScanAssetMapping.builder()
                        .scanTask(task).asset(asset).scanTime(now)
                        .isNew(false).portsFound(asset.getOpenPortCount() != null ? asset.getOpenPortCount() : ports.length).build();
                mappingRepository.save(mapping);
            }
            return asset;
        }

        Asset asset = Asset.builder()
                .task(task).ipAddress(ip).hostname(hostname)
                .osFingerprint(os).status("alive")
                .openPortCount(ports.length).criticalVulnCount(0)
                .tags("[]").lastScanTime(now).firstSeenTime(now.minusSeconds(86400 * 7))
                .build();
        asset = assetRepository.save(asset);

        ScanAssetMapping mapping = ScanAssetMapping.builder()
                .scanTask(task).asset(asset).scanTime(now)
                .isNew(false).portsFound(ports.length).build();
        mappingRepository.save(mapping);

        return asset;
    }

    private void addPortServices(Asset asset, int[] portNums, String[] services,
                                  String[] products, String[] versions) {
        for (int i = 0; i < portNums.length; i++) {
            int pn = portNums[i];
            String svc = i < services.length ? services[i] : null;
            String prod = i < products.length ? products[i] : null;
            String ver = i < versions.length ? versions[i] : null;

            // Skip if port already exists
            if (portRepository.findByAssetIdAndPortNumberAndProtocol(asset.getId(), pn, "tcp").isPresent()) {
                continue;
            }

            Port port = Port.builder()
                    .asset(asset).portNumber(pn).protocol("tcp")
                    .serviceName(svc).serviceProduct(prod).serviceVersion(ver)
                    .state("open").isWebService(pn == 80 || pn == 443 || pn == 8080
                            || pn == 8443 || pn == 3000 || pn == 8000 || pn == 9090 || pn == 5000)
                    .build();
            portRepository.save(port);
        }
    }

    private void addFingerprint(Asset asset, int portNum, int httpStatus,
                                 String server, String cms, String cmsVer,
                                 String techStack, String title) {
        Port targetPort = portRepository.findByAssetId(asset.getId()).stream()
                .filter(p -> p.getPortNumber() == portNum).findFirst().orElse(null);
        if (targetPort == null) return;
        if (webFingerprintRepository.findByPortId(targetPort.getId()).isPresent()) return;

        WebFingerprint wf = WebFingerprint.builder()
                .port(targetPort).httpStatus(httpStatus).serverHeader(server)
                .cmsName(cms).cmsVersion(cmsVer).techStack(techStack).title(title).build();
        webFingerprintRepository.save(wf);
    }

    private void addVuln(Asset asset, String cveId, String status, Instant now) {
        CveDatabase cve = cveRepository.findByCveId(cveId).orElse(null);
        if (cve == null) return;

        boolean exists = avRepository.findByAssetId(asset.getId()).stream()
                .anyMatch(av -> av.getCveDatabase() != null && cveId.equals(av.getCveDatabase().getCveId()));
        if (exists) return;

        AssetVulnerability av = AssetVulnerability.builder()
                .asset(asset).cveDatabase(cve).status(status).discoveredAt(now).build();
        avRepository.save(av);
    }

    private void updateCriticalCount(Asset asset, int count) {
        asset.setCriticalVulnCount(count);
        assetRepository.save(asset);
    }

    private Port getPort(Asset asset, int portNum) {
        return portRepository.findByAssetId(asset.getId()).stream()
                .filter(p -> p.getPortNumber() == portNum).findFirst().orElse(null);
    }

    /**
     * Seed crawled URLs for existing assets (runs when assets exist but no crawler data).
     */
    private void seedAllCrawledUrls() {
        ScanTask demoTask = scanTaskRepository.findAll().stream()
                .filter(t -> "completed".equals(t.getStatus())).findFirst().orElse(null);
        if (demoTask == null) {
            ScanTask task = ScanTask.builder()
                    .name("演示数据导入").targetRange("demo").scanType("full")
                    .status("completed").progress(100).totalAssets(12).totalPorts(45)
                    .startedAt(Instant.now().minusSeconds(3600))
                    .completedAt(Instant.now().minusSeconds(1800))
                    .createdBy("admin").createdAt(Instant.now().minusSeconds(7200))
                    .build();
            demoTask = scanTaskRepository.save(task);
        }

        ScanTask finalTask = demoTask;
        assetRepository.findAll().forEach(asset -> {
            List<Port> webPorts = portRepository.findByAssetId(asset.getId()).stream()
                    .filter(p -> Boolean.TRUE.equals(p.getIsWebService())).toList();
            for (Port port : webPorts) {
                String ip = asset.getIpAddress();
                int pn = port.getPortNumber();
                String scheme = (pn == 443 || pn == 8443) ? "https" : "http";
                String base = scheme + "://" + ip + ":" + pn;

                String cms = null;
                var wf = webFingerprintRepository.findByPortId(port.getId());
                if (wf.isPresent()) {
                    cms = wf.get().getCmsName();
                }

                String title = (cms != null ? cms : (port.getServiceName() != null ? port.getServiceName() : "Web Service"))
                        + " - " + ip;

                // Seed root page
                if (!crawledUrlRepository.existsByUrl(base + "/")) {
                    CrawledUrl root = CrawledUrl.builder()
                            .asset(asset).port(port).task(finalTask)
                            .url(base + "/").path("/")
                            .httpStatus(200).contentType("text/html; charset=utf-8")
                            .title(title).bodyText(title + " 页面内容摘要。此页面由爬虫自动发现并记录。")
                            .linksFound(5 + (int)(Math.random() * 15)).crawlDepth(0)
                            .responseTimeMs(80L + (long)(Math.random() * 200))
                            .isDynamic(false).crawledAt(Instant.now().minusSeconds(1800))
                            .build();
                    crawledUrlRepository.save(root);
                }

                // Seed 1-2 sub-pages for primary web services
                if ((pn == 80 || pn == 443 || pn == 8080 || pn == 8443) && !crawledUrlRepository.existsByUrl(base + "/admin")) {
                    CrawledUrl sub = CrawledUrl.builder()
                            .asset(asset).port(port).task(finalTask)
                            .url(base + "/admin").path("/admin")
                            .httpStatus(pn == 8443 ? 401 : 200).contentType("text/html; charset=utf-8")
                            .title("管理后台 - " + ip)
                            .bodyText("管理后台登录页面。用户名 密码 验证码 登录按钮。")
                            .linksFound(3).crawlDepth(1)
                            .responseTimeMs(120L + (long)(Math.random() * 150))
                            .isDynamic(false).crawledAt(Instant.now().minusSeconds(1800))
                            .build();
                    crawledUrlRepository.save(sub);
                }
            }
        });
        log.info("Seeded {} demo crawled URLs", crawledUrlRepository.count());
    }

    private void seedCrawledUrls(ScanTask task, Asset asset, Port port, String[][] urls) {
        if (asset == null || port == null) return;
        for (String[] u : urls) {
            String fullUrl = u[0], path = u[1], httpStatus = u[2], title = u[3],
                   contentType = u[4], bodyText = u[5], linksFound = u[6],
                   depth = u[7], responseTime = u[8];

            if (crawledUrlRepository.existsByUrl(fullUrl)) continue;

            CrawledUrl crawled = CrawledUrl.builder()
                    .asset(asset).port(port).task(task)
                    .url(fullUrl).path(path)
                    .httpStatus(Integer.parseInt(httpStatus))
                    .contentType(contentType).title(title)
                    .bodyText(bodyText)
                    .linksFound(Integer.parseInt(linksFound))
                    .crawlDepth(Integer.parseInt(depth))
                    .responseTimeMs(Long.parseLong(responseTime))
                    .isDynamic(false)
                    .crawledAt(Instant.now().minusSeconds(1800))
                    .build();
            crawledUrlRepository.save(crawled);
        }
    }
}
