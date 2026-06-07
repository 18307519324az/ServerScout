package com.serverscout.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.serverscout.dto.AiBriefingRequest;
import com.serverscout.dto.AiBriefingResponse;
import com.serverscout.exception.BadRequestException;
import com.serverscout.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiBriefingService {

    private static final Pattern CVE_PATTERN = Pattern.compile("(?i)\\bCVE-\\d{4}-\\d{4,7}\\b");
    private static final Pattern IP_PATTERN = Pattern.compile("\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b");
    private static final Pattern DOMAIN_PATTERN = Pattern.compile(
            "(?i)\\b(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.)+(?:com|net|org|io|cn|dev|cloud|local)\\b");
    private static final Pattern PORT_PATTERN = Pattern.compile(
            "(?i)(?:\\bports?\\b|端口)[\"']?\\s*[:：]?\\s*([0-9]{1,5}(?:\\s*[,/、]\\s*[0-9]{1,5})*)");
    private static final Pattern CVSS_PATTERN = Pattern.compile("(?i)CVSS\\s*[:：=]?\\s*(10(?:\\.0)?|[0-9](?:\\.\\d+)?)");
    private static final Pattern EPSS_PATTERN = Pattern.compile("(?i)EPSS\\s*[:：=]?\\s*(0(?:\\.\\d+)?|1(?:\\.0+)?|\\d{1,3}(?:\\.\\d+)?%)");

    private static final List<String> SECURITY_TERMS = List.of(
            "asset", "host", "server", "service", "port", "vulnerability", "vulnerabilities",
            "cve", "cvss", "epss", "exploit", "exposure", "risk", "scan", "nmap", "nuclei",
            "authentication", "authorization", "firewall", "ssl", "tls", "waf", "malware",
            "资产", "主机", "服务器", "服务", "端口", "漏洞", "利用", "暴露", "风险", "扫描",
            "认证", "授权", "防火墙", "证书", "攻击", "安全");

    private static final List<String> TECHNOLOGIES = List.of(
            "nginx", "apache", "spring", "java", "mysql", "redis", "docker", "kubernetes",
            "jenkins", "wordpress", "tomcat", "ssh", "rdp", "node.js", "react", "windows", "linux");

    private final ObjectMapper objectMapper;

    @Value("${app.ai.base-url:}")
    private String baseUrl;

    @Value("${app.ai.api-key:}")
    private String apiKey;

    @Value("${app.ai.model:}")
    private String model;

    @Value("${app.ai.timeout-seconds:45}")
    private int timeoutSeconds;

    @Value("${app.ai.max-attempts:2}")
    private int maxAttempts = 2;

    public AiBriefingResponse generate(AiBriefingRequest request) {
        String evidence = normalize(request.evidence());
        boolean chinese = request.locale() != null && request.locale().toLowerCase(Locale.ROOT).startsWith("zh");
        EvidenceAnalysis analysis = analyze(evidence);

        if (!analysis.relevant()) {
            throw new BadRequestException(chinese
                    ? "输入内容与安全扫描、资产、服务或漏洞分析无关，请提供相关证据。"
                    : "The input is unrelated to security scans, assets, services, or vulnerabilities.");
        }

        AiBriefingResponse local = buildLocalResponse(evidence, analysis, chinese, List.of());
        if (baseUrl == null || baseUrl.isBlank() || model == null || model.isBlank()) {
            return local;
        }

        Exception lastFailure = null;
        int attempts = Math.max(1, maxAttempts);
        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                return callModel(evidence, analysis, chinese);
            } catch (Exception ex) {
                lastFailure = ex;
                log.warn("AI briefing model call attempt {}/{} failed: {}", attempt, attempts, ex.getMessage());
            }
        }

        List<String> warnings = List.of(chinese
                ? "模型调用失败，已使用输入驱动的本地分析结果。"
                : "The model call failed, so an input-driven local analysis was returned.");
        if (lastFailure != null) {
            log.warn("AI briefing is using local analysis after all model attempts failed");
        }
        return buildLocalResponse(evidence, analysis, chinese, warnings);
    }

    EvidenceAnalysis analyze(String evidence) {
        String lower = evidence.toLowerCase(Locale.ROOT);
        int keywordHits = (int) SECURITY_TERMS.stream().filter(lower::contains).count();

        Map<String, List<String>> signals = new LinkedHashMap<>();
        putIfPresent(signals, "cves", extract(CVE_PATTERN, evidence, 12));
        putIfPresent(signals, "ips", extract(IP_PATTERN, evidence, 12));
        putIfPresent(signals, "domains", extract(DOMAIN_PATTERN, evidence, 8));
        putIfPresent(signals, "ports", extractPorts(evidence));
        putIfPresent(signals, "cvss", extract(CVSS_PATTERN, evidence, 8));
        putIfPresent(signals, "epss", extract(EPSS_PATTERN, evidence, 8));
        putIfPresent(signals, "technologies", TECHNOLOGIES.stream().filter(lower::contains).toList());

        List<String> severity = new ArrayList<>();
        for (String level : List.of("critical", "high", "medium", "low", "严重", "高危", "中危", "低危")) {
            if (lower.contains(level)) {
                severity.add(level);
            }
        }
        putIfPresent(signals, "severity", severity);

        int structuredSignals = signals.values().stream().mapToInt(List::size).sum();
        boolean relevant = (structuredSignals >= 1 && keywordHits >= 1) || keywordHits >= 3;
        return new EvidenceAnalysis(relevant, signals, keywordHits, structuredSignals);
    }

    private AiBriefingResponse callModel(String evidence, EvidenceAnalysis analysis, boolean chinese) throws Exception {
        String language = chinese ? "Chinese" : "English";
        String systemPrompt = """
                You are a security risk briefing assistant. Only analyze cybersecurity evidence.
                Return valid JSON with this exact shape:
                {"inputSummary":"string","sections":[{"key":"string","title":"string","body":"string","items":["string"]}],"warnings":["string"]}
                Return only compact valid JSON without markdown. Produce exactly 3 sections with at most 3 items each.
                Base every claim on the supplied evidence. Do not invent vulnerabilities.
                If evidence is incomplete, say so in warnings. Write in %s.
                """.formatted(language);
        String userPrompt = "Detected signals: " + objectMapper.writeValueAsString(analysis.signals())
                + "\n\nEvidence:\n" + evidence;

        Map<String, Object> payload = Map.of(
                "model", model,
                "temperature", 0.2,
                "response_format", Map.of("type", "json_object"),
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userPrompt)));

        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl))
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .header("Content-Type", "application/json");
        if (apiKey != null && !apiKey.isBlank()) {
            builder.header("Authorization", "Bearer " + apiKey);
        }

        HttpRequest httpRequest = builder.POST(HttpRequest.BodyPublishers.ofString(
                objectMapper.writeValueAsString(payload))).build();
        HttpResponse<String> response = HttpClient.newHttpClient().send(
                httpRequest, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new ServiceException("Model returned HTTP " + response.statusCode());
        }

        JsonNode root = objectMapper.readTree(response.body());
        String content = root.path("choices").path(0).path("message").path("content").asText();
        if (content.isBlank()) {
            throw new ServiceException("Model returned empty content");
        }
        JsonNode parsed = objectMapper.readTree(stripJsonFence(content));
        List<AiBriefingResponse.Section> sections = new ArrayList<>();
        for (JsonNode node : parsed.path("sections")) {
            List<String> items = new ArrayList<>();
            node.path("items").forEach(item -> items.add(item.asText()));
            sections.add(new AiBriefingResponse.Section(
                    node.path("key").asText("analysis"),
                    node.path("title").asText(),
                    node.path("body").asText(),
                    items));
        }
        if (sections.isEmpty()) {
            throw new ServiceException("Model returned no sections");
        }
        List<String> warnings = new ArrayList<>();
        parsed.path("warnings").forEach(item -> warnings.add(item.asText()));
        return new AiBriefingResponse(
                "llm",
                chinese ? "zh" : "en",
                parsed.path("inputSummary").asText(buildSummary(evidence)),
                analysis.signals(),
                sections,
                warnings);
    }

    private AiBriefingResponse buildLocalResponse(
            String evidence, EvidenceAnalysis analysis, boolean chinese, List<String> warnings) {
        Map<String, List<String>> signals = analysis.signals();
        List<String> cves = signals.getOrDefault("cves", List.of());
        List<String> assets = combine(signals.get("ips"), signals.get("domains"));
        List<String> ports = signals.getOrDefault("ports", List.of());
        List<String> technologies = signals.getOrDefault("technologies", List.of());
        List<String> scores = combine(signals.get("cvss"), signals.get("epss"));
        List<String> severity = signals.getOrDefault("severity", List.of());

        String assetText = assets.isEmpty() ? (chinese ? "未识别出明确资产" : "no explicit asset was detected")
                : String.join(", ", assets);
        String cveText = cves.isEmpty() ? (chinese ? "未识别出明确 CVE" : "no explicit CVE was detected")
                : String.join(", ", cves);
        String portText = ports.isEmpty() ? (chinese ? "未识别出明确端口" : "no explicit port was detected")
                : String.join(", ", ports);

        int assetCount = assets.size();
        int cveCount = cves.size();
        int portCount = ports.size();
        int techCount = technologies.size();

        List<AiBriefingResponse.Section> sections = new ArrayList<>();
        if (chinese) {
            // ── Summary ──
            StringBuilder summaryBody = new StringBuilder();
            summaryBody.append("本次分析识别到 ");
            if (assetCount > 0) summaryBody.append(assetCount).append(" 个资产（").append(assetText).append("）");
            if (cveCount > 0) {
                if (assetCount > 0) summaryBody.append("、");
                summaryBody.append(cveCount).append(" 个 CVE 漏洞（").append(cveText).append("）");
            }
            if (portCount > 0) {
                summaryBody.append("、").append(portCount).append(" 个开放端口（").append(portText).append("）");
            }
            if (techCount > 0) {
                summaryBody.append("、").append(techCount).append(" 个技术组件（").append(String.join(", ", technologies)).append("）");
            }
            summaryBody.append("。");
            if (!cves.isEmpty()) {
                summaryBody.append("本报告仅基于用户输入的扫描证据进行分析，建议结合人工研判确认漏洞可利用性。");
            }
            sections.add(new AiBriefingResponse.Section("summary", "执行摘要",
                    summaryBody.toString(), List.of()));

            // ── Signals ──
            sections.add(new AiBriefingResponse.Section("signals", "识别出的安全信号",
                    "系统从输入中提取了以下可用于风险分级、暴露面评估和漏洞处置的关键信号，建议逐项核对后纳入安全运营记录。",
                    buildSignalItems(portText, technologies, scores, true)));

            // ── Vulnerability ──
            if (!cves.isEmpty() || !scores.isEmpty()) {
                StringBuilder vulnBody = new StringBuilder();
                vulnBody.append("输入中共识别到 ").append(cveCount).append(" 个 CVE 编号");
                if (!scores.isEmpty()) {
                    vulnBody.append("，含 ").append(scores.size()).append(" 个 CVSS/EPSS 评分信号");
                }
                vulnBody.append("。");
                if (!severity.isEmpty()) {
                    vulnBody.append("其中涉及严重等级：").append(String.join("、", severity)).append("。");
                }
                vulnBody.append("建议按以下步骤处置：(1) 通过 NVD/CNVD 确认漏洞详情与受影响版本范围；(2) 核实本地资产是否实际运行受影响软件；(3) 评估漏洞利用条件（是否需要认证、是否为远程可利用）；(4) 对确认受影响的资产应用补丁或缓解措施。");
                sections.add(new AiBriefingResponse.Section("vulnerability", "漏洞风险研判",
                        vulnBody.toString(),
                        buildVulnerabilityItems(cves, scores, true)));
            }

            // ── Exposure ──
            if (!ports.isEmpty() || !assets.isEmpty()) {
                StringBuilder expBody = new StringBuilder();
                expBody.append("当前攻击面涉及 ").append(assetCount).append(" 个资产、").append(portCount).append(" 个端口。");
                if (!technologies.isEmpty()) {
                    expBody.append("检测到的技术组件包括 ").append(String.join("、", technologies)).append("，这些组件的已知漏洞可能增加攻击面风险。");
                }
                expBody.append("建议逐端口确认业务必要性：非必要的管理端口（如 SSH/22、RDP/3389、MySQL/3306）应对互联网关闭或限制访问来源 IP；Web 服务端口（80/443/8080）应部署 WAF 并启用 HTTPS。");
                sections.add(new AiBriefingResponse.Section("exposure", "暴露面研判",
                        expBody.toString(),
                        buildExposureItems(assets, ports, true)));
            }

            // ── Priority ──
            sections.add(new AiBriefingResponse.Section("priority", "处置优先级",
                    "建议按照「严重漏洞→对外暴露的高危服务→缺少修复的关键组件→信息收集类发现」的顺序分阶段处置，每个阶段设定明确的完成时限和责任人。",
                    buildPriorityItems(cves, ports, scores, true)));

            // ── Report ──
            List<String> reportItems = new ArrayList<>();
            if (!cves.isEmpty()) {
                reportItems.add("漏洞清单：已识别 " + cveCount + " 个 CVE（" + String.join(", ", cves) + "），建议逐一注明受影响资产、版本、CVSS 评分、修复状态及负责人。");
            }
            if (!ports.isEmpty()) {
                reportItems.add("端口暴露清单：已识别开放端口 " + portText + "，建议注明各端口的业务用途、暴露面大小及访问控制策略。");
            }
            if (!assets.isEmpty()) {
                reportItems.add("受影响资产清单：涉及 " + assetText + "，建议注明业务归属、安全等级及是否为互联网暴露面。");
            }
            if (!technologies.isEmpty()) {
                reportItems.add("技术组件清单：检测到 " + String.join("、", technologies) + "，建议追踪各组件的安全公告与补丁更新。");
            }
            reportItems.add("修复时间表：根据漏洞严重程度和暴露面优先级，制定分阶段的修复计划，明确时间节点和责任人。");
            reportItems.add("复测验证：修复完成后需对原漏洞重新扫描验证，确认修复措施已生效且未引入新的风险。");
            reportItems.add("残余风险声明：对于暂时无法修复的漏洞，应记录补偿控制措施（如网络隔离、访问限制、日志监控等）及接受风险的审批记录。");
            sections.add(new AiBriefingResponse.Section("report", "报告表述与输出建议",
                    "以下为可直接纳入安全评估报告的关键输出项，每项均基于本次输入证据提取。建议结合企业实际组织架构和风险偏好进行调整后交付业务方或管理层。",
                    reportItems));

        } else {
            // ── Summary ──
            StringBuilder summaryBody = new StringBuilder();
            summaryBody.append("This analysis identified ");
            if (assetCount > 0) summaryBody.append(assetCount).append(" assets (").append(assetText).append(")");
            if (cveCount > 0) {
                if (assetCount > 0) summaryBody.append(", ");
                summaryBody.append(cveCount).append(" CVEs (").append(cveText).append(")");
            }
            if (portCount > 0) {
                summaryBody.append(", ").append(portCount).append(" open ports (").append(portText).append(")");
            }
            if (techCount > 0) {
                summaryBody.append(", ").append(techCount).append(" technologies (").append(String.join(", ", technologies)).append(")");
            }
            summaryBody.append(".");
            if (!cves.isEmpty()) {
                summaryBody.append(" This report is based solely on the supplied scan evidence; combine with manual assessment to confirm exploitability.");
            }
            sections.add(new AiBriefingResponse.Section("summary", "Executive Summary",
                    summaryBody.toString(), List.of()));

            // ── Signals ──
            sections.add(new AiBriefingResponse.Section("signals", "Detected Security Signals",
                    "The following key signals were extracted for triage, exposure assessment, and remediation planning. Verify each item before incorporating into operational records.",
                    buildSignalItems(portText, technologies, scores, false)));

            // ── Vulnerability ──
            if (!cves.isEmpty() || !scores.isEmpty()) {
                StringBuilder vulnBody = new StringBuilder();
                vulnBody.append("The input contains ").append(cveCount).append(" CVE identifier(s)");
                if (!scores.isEmpty()) {
                    vulnBody.append(" and ").append(scores.size()).append(" CVSS/EPSS score signal(s)");
                }
                vulnBody.append(".");
                if (!severity.isEmpty()) {
                    vulnBody.append(" Severity levels detected: ").append(String.join(", ", severity)).append(".");
                }
                vulnBody.append(" Recommended workflow: (1) Verify each CVE against NVD for affected versions and exploit conditions; (2) Confirm whether local assets run the affected software; (3) Assess exploit prerequisites (auth required? remotely exploitable?); (4) Apply patches or mitigations to confirmed-affected assets.");
                sections.add(new AiBriefingResponse.Section("vulnerability", "Vulnerability Assessment",
                        vulnBody.toString(),
                        buildVulnerabilityItems(cves, scores, false)));
            }

            // ── Exposure ──
            if (!ports.isEmpty() || !assets.isEmpty()) {
                StringBuilder expBody = new StringBuilder();
                expBody.append("The attack surface spans ").append(assetCount).append(" asset(s) and ").append(portCount).append(" port(s).");
                if (!technologies.isEmpty()) {
                    expBody.append(" Detected technologies include ").append(String.join(", ", technologies)).append(" — known vulnerabilities in these components may increase attack surface risk.");
                }
                expBody.append(" Confirm per-port business necessity: non-essential admin ports (SSH/22, RDP/3389, MySQL/3306) should be firewalled from the internet; web ports (80/443/8080) should be fronted by a WAF with HTTPS enforced.");
                sections.add(new AiBriefingResponse.Section("exposure", "Exposure Assessment",
                        expBody.toString(),
                        buildExposureItems(assets, ports, false)));
            }

            // ── Priority ──
            sections.add(new AiBriefingResponse.Section("priority", "Remediation Priority",
                    "Process findings in stages: critical vulnerabilities → internet-facing high-risk services → unpatched key components → information-gathering findings. Assign clear deadlines and owners to each stage.",
                    buildPriorityItems(cves, ports, scores, false)));

            // ── Report ──
            List<String> reportItems = new ArrayList<>();
            if (!cves.isEmpty()) {
                reportItems.add("Vulnerability inventory: " + cveCount + " CVE(s) identified (" + String.join(", ", cves) + "). For each, list affected asset, version, CVSS score, remediation status, and owner.");
            }
            if (!ports.isEmpty()) {
                reportItems.add("Port exposure inventory: open ports " + portText + ". Document each port's business purpose, exposure scope, and access control policy.");
            }
            if (!assets.isEmpty()) {
                reportItems.add("Affected asset inventory: " + assetText + ". Include business ownership, security classification, and internet-exposure status for each.");
            }
            if (!technologies.isEmpty()) {
                reportItems.add("Technology inventory: " + String.join(", ", technologies) + ". Track security advisories and patch updates per component.");
            }
            reportItems.add("Remediation timeline: define phased remediation based on severity and exposure priority, with concrete deadlines and assigned owners.");
            reportItems.add("Retest verification: re-scan after remediation to confirm fixes are effective and no new risks were introduced.");
            reportItems.add("Residual risk statement: for findings that cannot be immediately remediated, document compensating controls (network isolation, access restrictions, log monitoring) and risk acceptance approval.");
            sections.add(new AiBriefingResponse.Section("report", "Report-Ready Output",
                    "The following items can be incorporated directly into a security assessment report. Adjust language to match your organization's structure and risk appetite before delivery to business stakeholders or management.",
                    reportItems));
        }

        List<String> resultWarnings = new ArrayList<>(warnings);
        if (signals.getOrDefault("cves", List.of()).isEmpty()) {
            resultWarnings.add(chinese ? "未识别到 CVE；建议补充漏洞编号或扫描发现。" : "No CVE was detected; add vulnerability identifiers or scan findings.");
        }
        if (signals.getOrDefault("ips", List.of()).isEmpty() && signals.getOrDefault("domains", List.of()).isEmpty()) {
            resultWarnings.add(chinese ? "未识别到资产标识；建议补充 IP、域名或主机名。" : "No asset identifier was detected; add an IP, domain, or hostname.");
        }
        return new AiBriefingResponse(
                "local-analysis",
                chinese ? "zh" : "en",
                buildSummary(evidence),
                signals,
                sections,
                resultWarnings);
    }

    private List<String> buildSignalItems(String portText, List<String> technologies, List<String> scores, boolean chinese) {
        List<String> items = new ArrayList<>();
        items.add((chinese ? "端口：" : "Ports: ") + portText);
        if (!technologies.isEmpty()) {
            items.add((chinese ? "技术组件：" : "Technologies: ") + String.join(", ", technologies));
        }
        if (!scores.isEmpty()) {
            items.add((chinese ? "风险评分信号：" : "Risk score signals: ") + String.join(", ", scores));
        }
        return items;
    }

    private List<String> buildVulnerabilityItems(List<String> cves, List<String> scores, boolean chinese) {
        List<String> items = new ArrayList<>();
        if (!cves.isEmpty()) {
            items.add((chinese ? "漏洞编号：" : "Vulnerability identifiers: ") + String.join(", ", cves));
        }
        if (!scores.isEmpty()) {
            items.add((chinese ? "评分信号：" : "Score signals: ") + String.join(", ", scores));
        }
        return items;
    }

    private List<String> buildExposureItems(List<String> assets, List<String> ports, boolean chinese) {
        List<String> items = new ArrayList<>();
        if (!assets.isEmpty()) {
            items.add((chinese ? "资产：" : "Assets: ") + String.join(", ", assets));
        }
        if (!ports.isEmpty()) {
            items.add((chinese ? "端口：" : "Ports: ") + String.join(", ", ports));
        }
        return items;
    }

    private List<String> buildPriorityItems(List<String> cves, List<String> ports, List<String> scores, boolean chinese) {
        List<String> items = new ArrayList<>();
        if (!scores.isEmpty()) {
            items.add(chinese ? "优先复核高 CVSS 或高 EPSS 项目，并确认利用条件。" : "Review high CVSS or EPSS findings first and confirm exploit conditions.");
        }
        if (!cves.isEmpty()) {
            items.add((chinese ? "核验漏洞影响版本与修复公告：" : "Verify affected versions and remediation advisories for: ") + String.join(", ", cves));
        }
        if (!ports.isEmpty()) {
            items.add((chinese ? "确认端口是否需要对目标网络开放：" : "Confirm whether these ports must remain exposed: ") + String.join(", ", ports));
        }
        items.add(chinese ? "补充负责人、修复期限和复测结果。" : "Add an owner, remediation deadline, and retest result.");
        return items;
    }

    private String normalize(String input) {
        if (input == null || input.isBlank()) {
            throw new BadRequestException("Evidence is required");
        }
        return input.replace("\r\n", "\n").trim();
    }

    private String buildSummary(String evidence) {
        String compact = evidence.replaceAll("\\s+", " ").trim();
        return compact.length() <= 360 ? compact : compact.substring(0, 357) + "...";
    }

    private List<String> extract(Pattern pattern, String input, int limit) {
        Set<String> values = new LinkedHashSet<>();
        Matcher matcher = pattern.matcher(input);
        while (matcher.find() && values.size() < limit) {
            values.add(matcher.group().toUpperCase(Locale.ROOT));
        }
        return new ArrayList<>(values);
    }

    private List<String> extractPorts(String input) {
        Set<String> ports = new LinkedHashSet<>();
        Matcher matcher = PORT_PATTERN.matcher(input);
        while (matcher.find() && ports.size() < 16) {
            for (String value : matcher.group(1).split("[,/、]")) {
                String port = value.trim();
                if (!port.isEmpty()) {
                    ports.add(port);
                }
            }
        }
        return new ArrayList<>(ports);
    }

    private void putIfPresent(Map<String, List<String>> signals, String key, List<String> values) {
        if (values != null && !values.isEmpty()) {
            signals.put(key, values);
        }
    }

    private List<String> combine(List<String> first, List<String> second) {
        List<String> result = new ArrayList<>();
        if (first != null) result.addAll(first);
        if (second != null) result.addAll(second);
        return result;
    }

    private String stripJsonFence(String value) {
        String trimmed = value.trim();
        if (trimmed.startsWith("```")) {
            trimmed = trimmed.replaceFirst("^```(?:json)?\\s*", "");
            trimmed = trimmed.replaceFirst("\\s*```$", "");
        }
        return trimmed;
    }

    record EvidenceAnalysis(boolean relevant, Map<String, List<String>> signals, int keywordHits, int structuredSignals) {
    }
}
