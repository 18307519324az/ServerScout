package com.serverscout.config;

import com.serverscout.entity.CveDatabase;
import com.serverscout.repository.CveDatabaseRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@Component
public class CveDataInitializer implements CommandLineRunner {

    private final CveDatabaseRepository cveRepository;

    public CveDataInitializer(CveDatabaseRepository cveRepository) {
        this.cveRepository = cveRepository;
    }

    @Override
    public void run(String... args) {
        long existingCount = cveRepository.count();
        if (existingCount > 0) {
            log.info("CVE database already has {} entries, checking for new ones...", existingCount);
        }

        List<CveDatabase> seeds = List.of(
            // ==================== Web Servers ====================
            cve("CVE-2021-41773", "Apache HTTPD 路径穿越 RCE", "critical", 9.8f,
                "Apache HTTP Server", "2.4.49", "升级到 Apache 2.4.50+"),
            cve("CVE-2021-42013", "Apache HTTPD 路径穿越 RCE (绕过)", "critical", 9.8f,
                "Apache HTTP Server", "2.4.49 - 2.4.50", "升级到 Apache 2.4.51+"),
            cve("CVE-2019-0211", "Apache HTTPD 权限提升", "high", 7.8f,
                "Apache HTTP Server", "2.4.17 - 2.4.38", "升级到 Apache 2.4.39+"),
            cve("CVE-2021-40438", "Apache HTTPD SSRF", "high", 7.7f,
                "Apache HTTP Server", "2.4.48", "升级到 Apache 2.4.49+"),

            // ==================== Apache Tomcat ====================
            cve("CVE-2020-1938", "Apache Tomcat AJP 文件包含漏洞 (Ghostcat)", "high", 7.5f,
                "Apache Tomcat", "6.x, 7.0 - 7.0.99, 8.5.0 - 8.5.50, 9.0.0 - 9.0.30",
                "升级到 Tomcat 7.0.100+ / 8.5.51+ / 9.0.31+"),
            cve("CVE-2017-12615", "Apache Tomcat PUT 方法任意文件上传", "high", 8.1f,
                "Apache Tomcat", "7.0.0 - 7.0.81", "升级 Tomcat 或禁用 PUT 方法"),
            cve("CVE-2017-12617", "Apache Tomcat 远程代码执行", "high", 8.1f,
                "Apache Tomcat", "7.0.0 - 7.0.80", "升级到 Tomcat 7.0.81+"),
            cve("CVE-2022-45143", "Apache Tomcat JsonErrorReportValve 信息泄露", "high", 7.5f,
                "Apache Tomcat", "8.5.83", "升级到 Tomcat 8.5.84+"),

            // ==================== Java Web Frameworks ====================
            cve("CVE-2021-44228", "Apache Log4j2 JNDI 远程代码执行漏洞 (Log4Shell)", "critical", 10.0f,
                "Apache Log4j2", "2.0-beta9 - 2.14.1", "升级到 Log4j 2.17.0 或更高版本"),
            cve("CVE-2022-22965", "Spring Framework RCE 漏洞 (Spring4Shell)", "critical", 9.8f,
                "Spring Framework", "5.3.0 - 5.3.17, 5.2.0 - 5.2.19",
                "升级到 Spring Framework 5.3.18+ / 5.2.20+"),
            cve("CVE-2022-22947", "Spring Cloud Gateway 代码注入漏洞", "critical", 9.8f,
                "Spring Cloud Gateway", "3.1.0, 3.0.0 - 3.0.6",
                "升级到 Spring Cloud Gateway 3.1.1+ / 3.0.7+"),
            cve("CVE-2022-22963", "Spring Cloud Function SpEL RCE", "critical", 9.8f,
                "Spring Cloud Function", "3.1.6, 3.2.2",
                "升级到 Spring Cloud Function 3.1.7+ / 3.2.3+"),
            cve("CVE-2023-20863", "Spring Framework DoS 漏洞", "high", 7.5f,
                "Spring Framework", "5.2.0 - 6.0.7", "升级到 Spring Framework 6.0.8+"),

            // ==================== Apache Struts ====================
            cve("CVE-2017-5638", "Apache Struts2 Jakarta 解析器 RCE", "critical", 10.0f,
                "Apache Struts2", "2.3.5 - 2.3.31, 2.5.0 - 2.5.10",
                "升级到 Struts 2.3.32+ / 2.5.10.1+"),
            cve("CVE-2018-11776", "Apache Struts2 命名空间 RCE", "critical", 9.8f,
                "Apache Struts2", "2.3 - 2.3.34, 2.5 - 2.5.16",
                "升级到 Struts 2.3.35+ / 2.5.17+"),
            cve("CVE-2017-9791", "Apache Struts2 插件 RCE", "critical", 9.8f,
                "Apache Struts2", "2.3.x, 2.5.x < 2.5.13", "升级到 Struts 2.5.13+"),

            // ==================== Apache Commons ====================
            cve("CVE-2022-42889", "Apache Commons Text 变量插值 RCE (Text4Shell)", "critical", 9.8f,
                "Apache Commons Text", "1.5.0 - 1.9.0", "升级到 Commons Text 1.10.0+"),
            cve("CVE-2022-41852", "Apache Commons JXPath RCE", "critical", 9.8f,
                "Apache Commons JXPath", "< 1.3-rc2", "升级 Commons JXPath"),

            // ==================== Jackson / JSON ====================
            cve("CVE-2020-36179", "FasterXML jackson-databind 反序列化 RCE", "critical", 9.8f,
                "FasterXML jackson-databind", "< 2.9.10.8", "升级到 jackson-databind 2.9.10.8+"),
            cve("CVE-2020-26217", "XStream 反序列化 RCE", "critical", 9.8f,
                "XStream", "< 1.4.14", "升级到 XStream 1.4.14+"),

            // ==================== Oracle WebLogic ====================
            cve("CVE-2019-2725", "Oracle WebLogic Server 反序列化 RCE", "critical", 9.8f,
                "Oracle WebLogic Server", "10.3.6.0.0, 12.1.3.0.0, 12.2.1.3.0", "安装 Oracle 安全补丁"),
            cve("CVE-2020-14882", "Oracle WebLogic Server Console RCE", "critical", 9.8f,
                "Oracle WebLogic Server", "10.3.6.0.0 - 14.1.1.0.0", "安装 Oracle 10月 2020 CPU 补丁"),
            cve("CVE-2017-10271", "Oracle WebLogic Server WLS RCE", "critical", 9.8f,
                "Oracle WebLogic Server", "10.3.6.0.0, 12.1.3.0.0, 12.2.1.1.0, 12.2.1.2.0",
                "安装 Oracle 10月 2017 CPU 补丁"),
            cve("CVE-2020-2555", "Oracle Coherence 反序列化 RCE", "critical", 9.8f,
                "Oracle Coherence", "3.7.1.17, 12.1.3.0.0, 12.2.1.3.0, 12.2.1.4.0",
                "安装 Oracle 1月 2020 CPU 补丁"),

            // ==================== Apache Shiro ====================
            cve("CVE-2016-4437", "Apache Shiro 反序列化漏洞 (Shiro-550)", "critical", 9.8f,
                "Apache Shiro", "< 1.2.5", "升级到 Shiro 1.2.5+"),

            // ==================== CMS: WordPress ====================
            cve("CVE-2022-21661", "WordPress WP_Query SQL 注入漏洞", "high", 7.5f,
                "WordPress", "5.8.2 之前版本", "升级到 WordPress 5.8.3+"),
            cve("CVE-2021-29447", "WordPress Media Library XML 解析 SSRF", "high", 7.7f,
                "WordPress", "4.7 - 5.7.1", "升级到 WordPress 5.7.2+"),
            cve("CVE-2020-35489", "WordPress Contact Form 7 插件文件上传 RCE", "critical", 9.8f,
                "WordPress Contact Form 7", "< 5.3.2", "升级 Contact Form 7 到 5.3.2+"),
            cve("CVE-2019-9978", "WordPress Social Warfare 插件 XSS RCE", "critical", 9.8f,
                "WordPress Social Warfare", "< 3.5.4", "升级 Social Warfare 到 3.5.4+"),
            cve("CVE-2022-29455", "WordPress Elementor 插件 XSS 漏洞", "medium", 5.4f,
                "WordPress Elementor", "< 3.5.6", "升级 Elementor 到 3.5.6+"),
            cve("CVE-2023-32243", "WordPress Essential Addons 权限提升", "critical", 9.8f,
                "WordPress Essential Addons", "5.4.1 - 5.7.1", "升级到 5.7.2+"),

            // ==================== CMS: Drupal ====================
            cve("CVE-2018-7600", "Drupal 远程代码执行漏洞 (Drupalgeddon2)", "critical", 9.8f,
                "Drupal", "6.x, 7.x, 8.x < 8.5.1", "升级到 Drupal 8.5.1+"),
            cve("CVE-2019-6340", "Drupal REST 模块 RCE", "critical", 9.8f,
                "Drupal", "8.5.x, 8.6.x < 8.6.10", "升级到 Drupal 8.6.10+"),

            // ==================== Atlassian ====================
            cve("CVE-2021-26084", "Atlassian Confluence OGNL 注入漏洞", "critical", 9.8f,
                "Atlassian Confluence", "< 6.13.23, 6.14.0 - 7.11.6", "升级到 Confluence 7.12.3+"),
            cve("CVE-2022-26134", "Atlassian Confluence OGNL 注入漏洞", "critical", 9.8f,
                "Atlassian Confluence", "1.3.0 - 7.4.17, 7.13.0 - 7.13.7, 7.14.0 - 7.14.3",
                "升级到 Confluence 7.4.18+"),
            cve("CVE-2023-22527", "Atlassian Confluence OGNL 注入 RCE", "critical", 10.0f,
                "Atlassian Confluence", "8.0.x - 8.5.3", "升级到 Confluence 8.5.4+"),
            cve("CVE-2024-21683", "Atlassian Confluence 代码注入 RCE", "critical", 9.8f,
                "Atlassian Confluence", "< 7.19.17, < 8.5.5", "升级到 Confluence 8.5.5+"),
            cve("CVE-2022-36804", "Atlassian Bitbucket Server RCE", "critical", 8.8f,
                "Atlassian Bitbucket Server", "6.10.17 - 8.3.2", "升级到 Bitbucket 8.3.3+"),

            // ==================== Jenkins ====================
            cve("CVE-2018-1000861", "Jenkins Stapler RCE", "critical", 9.8f,
                "Jenkins", "< 2.153, < 2.138.3 LTS", "升级到 Jenkins 2.153+ / 2.138.3+ LTS"),
            cve("CVE-2024-23897", "Jenkins CLI 任意文件读取", "high", 7.5f,
                "Jenkins", "2.441 - 2.442", "升级到 Jenkins 2.443+"),

            // ==================== GitLab / DevOps ====================
            cve("CVE-2021-22205", "GitLab CE/EE 远程代码执行", "critical", 10.0f,
                "GitLab", "11.9.0 - 13.8.8, 13.9.0 - 13.9.6, 13.10.0 - 13.10.3",
                "升级到 GitLab 13.10.4+"),

            // ==================== PHP Ecosystem ====================
            cve("CVE-2019-11043", "PHP-FPM fastcgi RCE", "critical", 9.8f,
                "PHP FPM", "< 7.1.33, < 7.2.24, < 7.3.11", "升级到 PHP 7.3.11+"),
            cve("CVE-2017-9841", "PHPUnit eval-stdin.php RCE", "critical", 9.8f,
                "PHPUnit", "< 4.8.28, < 5.6.3", "升级 PHPUnit 或删除 eval-stdin.php"),
            cve("CVE-2018-10561", "GPON 路由器认证绕过", "critical", 9.8f,
                "GPON Router", "多种型号", "更新固件"),

            // ==================== Ruby on Rails ====================
            cve("CVE-2019-5418", "Ruby on Rails 任意文件读取", "high", 7.5f,
                "Ruby on Rails", "3.x - 5.x", "升级到 Rails 5.2.2.1+"),
            cve("CVE-2020-8163", "Ruby on Rails 远程代码执行", "critical", 9.8f,
                "Ruby on Rails", "< 5.2.4.3, < 6.0.3.1", "升级到 Rails 5.2.4.3+ / 6.0.3.1+"),

            // ==================== Laravel ====================
            cve("CVE-2021-3129", "Laravel Ignition debug 模式 RCE", "critical", 9.8f,
                "Laravel", "8.4.2 及以下", "不要在生产环境开启 debug 模式"),

            // ==================== Yii ====================
            cve("CVE-2020-15148", "Yii 2 反序列化 RCE", "high", 7.4f,
                "Yii Framework", "2.0 - 2.0.37", "升级到 Yii 2.0.38+"),

            // ==================== SSL / TLS ====================
            cve("CVE-2014-0160", "OpenSSL Heartbleed 信息泄露", "high", 7.5f,
                "OpenSSL", "1.0.1 - 1.0.1f", "升级到 OpenSSL 1.0.1g+"),
            cve("CVE-2016-2107", "OpenSSL 填充 Oracle 漏洞", "medium", 5.9f,
                "OpenSSL", "1.0.1 - 1.0.1s, 1.0.2 - 1.0.2g", "升级到 OpenSSL 1.0.1t+ / 1.0.2h+"),

            // ==================== Grafana / Monitoring ====================
            cve("CVE-2021-43798", "Grafana 任意文件读取漏洞", "high", 7.5f,
                "Grafana", "8.0.0 - 8.3.0", "升级到 Grafana 8.3.1+"),

            // ==================== F5 / Citrix / ADC ====================
            cve("CVE-2021-22986", "F5 BIG-IP iControl REST RCE", "critical", 9.8f,
                "F5 BIG-IP", "16.0.0 - 16.0.1, 15.1.0 - 15.1.2, 14.1.0 - 14.1.4", "升级到修复版本"),
            cve("CVE-2020-5902", "F5 BIG-IP TMUI RCE", "critical", 9.8f,
                "F5 BIG-IP", "11.6.1 - 15.1.0", "升级到修复版本"),
            cve("CVE-2022-1388", "F5 BIG-IP iControl REST 认证绕过 RCE", "critical", 9.8f,
                "F5 BIG-IP", "11.6.1 - 16.1.2", "升级到修复版本"),
            cve("CVE-2019-19781", "Citrix ADC/Gateway 目录遍历漏洞", "critical", 9.8f,
                "Citrix ADC, Citrix Gateway", "10.5, 11.1, 12.0, 12.1, 13.0", "升级到修复版本"),
            cve("CVE-2020-8193", "Citrix ADC/Gateway 权限提升", "high", 7.2f,
                "Citrix ADC, Citrix Gateway", "< 13.0-58.30", "升级到 13.0-58.30+"),
            cve("CVE-2023-3519", "Citrix NetScaler ADC/Gateway 代码注入 RCE", "critical", 9.8f,
                "Citrix NetScaler ADC", "13.1 < 13.1-49.13, 13.0 < 13.0-91.13", "升级到修复版本"),

            // ==================== Fortinet ====================
            cve("CVE-2022-40684", "FortiOS/FortiProxy 认证绕过", "critical", 9.8f,
                "Fortinet FortiOS", "7.0.0 - 7.0.6, 7.2.0 - 7.2.1", "升级到 FortiOS 7.0.7+ / 7.2.2+"),
            cve("CVE-2023-27997", "FortiOS SSL-VPN RCE", "critical", 9.8f,
                "Fortinet FortiOS", "6.0.x, 6.2.x, 6.4.x, 7.0.x, 7.2.x", "升级到修复版本"),
            cve("CVE-2024-21762", "Fortinet FortiOS SSLVPN 越界写入 RCE", "critical", 9.8f,
                "Fortinet FortiOS", "6.0 - 7.4.1", "升级到 FortiOS 最新版本"),

            // ==================== Cisco ====================
            cve("CVE-2020-3452", "Cisco ASA/FTD 任意文件读取", "high", 7.5f,
                "Cisco ASA, Cisco FTD", "多种版本", "升级到修复版本"),
            cve("CVE-2021-1497", "Cisco HyperFlex HX RCE", "critical", 9.8f,
                "Cisco HyperFlex HX", "< 4.0(2e)", "升级到 HyperFlex HX 4.0(2e)+"),

            // ==================== Pulse Secure / VPN ====================
            cve("CVE-2019-11510", "Pulse Secure 任意文件读取", "critical", 10.0f,
                "Pulse Connect Secure", "9.0R1 - 9.0R3.4, 8.3R1 - 8.3R7.1", "升级到修复版本"),
            cve("CVE-2021-22893", "Pulse Connect Secure RCE", "critical", 10.0f,
                "Pulse Connect Secure", "9.0R3/9.1R1", "升级到修复版本"),

            // ==================== SonicWall ====================
            cve("CVE-2021-20016", "SonicWall SMA100 SQL 注入", "critical", 9.8f,
                "SonicWall SMA100", "< 10.2.0.5-29sv", "升级到修复版本"),

            // ==================== VMware ====================
            cve("CVE-2021-21972", "VMware vCenter Server RCE", "critical", 9.8f,
                "VMware vCenter Server", "6.5, 6.7, 7.0 < 7.0 U2c", "升级到 vCenter 7.0 U2c+"),
            cve("CVE-2021-22005", "VMware vCenter Server 文件上传 RCE", "critical", 9.8f,
                "VMware vCenter Server", "6.7, 7.0 < 7.0 U2c", "升级到 vCenter 7.0 U2c+"),

            // ==================== ManageEngine ====================
            cve("CVE-2021-40539", "ManageEngine ADSelfService Plus RCE", "critical", 9.8f,
                "ManageEngine ADSelfService Plus", "< 6114", "升级到 6114+"),
            cve("CVE-2022-47966", "ManageEngine 多产品 RCE", "critical", 9.8f,
                "ManageEngine", "多种产品 < 2022.12.22", "升级到修复版本"),

            // ==================== Message Queues / Data ====================
            cve("CVE-2023-33246", "Apache RocketMQ RCE", "critical", 9.8f,
                "Apache RocketMQ", "4.x - 5.1.0", "升级到 RocketMQ 5.1.1+"),
            cve("CVE-2023-37582", "Apache RocketMQ NameServer RCE", "high", 7.8f,
                "Apache RocketMQ", "< 5.1.2", "升级到 RocketMQ 5.1.2+"),
            cve("CVE-2023-25194", "Apache Kafka Connect RCE", "critical", 9.8f,
                "Apache Kafka", "2.3.0 - 3.3.2", "升级到 Kafka 3.4.0+"),

            // ==================== API Gateway ====================
            cve("CVE-2024-3651", "Kong API Gateway 授权绕过", "high", 7.5f,
                "Kong API Gateway", "< 3.5.3", "升级到 Kong 3.5.3+"),

            // ==================== HTTP/2 Protocol ====================
            cve("CVE-2023-44487", "HTTP/2 Rapid Reset DoS", "high", 7.5f,
                "HTTP/2", "广泛影响服务器和代理软件", "更新受影响的 HTTP/2 实现"),

            // ==================== JetBrains ====================
            cve("CVE-2024-27198", "JetBrains TeamCity 认证绕过", "critical", 9.8f,
                "JetBrains TeamCity", "< 2023.11.4", "升级到 TeamCity 2023.11.4+"),

            // ==================== Databases ====================
            cve("CVE-2024-1597", "PostgreSQL JDBC SQL 注入 (pgjdbc)", "high", 8.8f,
                "PostgreSQL JDBC Driver", "< 42.7.2", "升级 pgjdbc 到 42.7.2+")
        );

        int added = 0;
        for (CveDatabase cve : seeds) {
            if (cveRepository.findByCveId(cve.getCveId()).isEmpty()) {
                try {
                    cveRepository.save(cve);
                    added++;
                } catch (Exception e) {
                    log.debug("Failed to save CVE {}: {}", cve.getCveId(), e.getMessage());
                }
            }
        }
        log.info("CVE seed: {} total, {} newly added", seeds.size(), added);
    }

    private static CveDatabase cve(String id, String desc, String sev, float score,
                                    String software, String versions, String fix) {
        return CveDatabase.builder()
                .cveId(id).description(desc)
                .severity(sev).cvssScore(BigDecimal.valueOf(score))
                .affectedSoftware(software).affectedVersionRange(versions)
                .fixSuggestion(fix)
                .build();
    }
}
