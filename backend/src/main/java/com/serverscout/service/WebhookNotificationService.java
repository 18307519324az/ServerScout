package com.serverscout.service;

import com.serverscout.entity.*;
import com.serverscout.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Sends scan completion notifications via webhook.
 * Supports DingTalk, Feishu (Lark), and WeCom (企业微信) bot webhooks.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookNotificationService {

    private final ScanTaskRepository scanTaskRepository;
    private final ScanAssetMappingRepository scanAssetMappingRepository;
    private final PortRepository portRepository;
    private final AssetVulnerabilityRepository vulnRepository;
    private final CrawledUrlRepository crawledUrlRepository;
    private final SystemConfigService configService;
    private final EmailNotificationService emailService;

    @Async
    public void sendScanCompletedNotification(Long taskId) {
        ScanTask task = scanTaskRepository.findById(taskId).orElse(null);
        if (task == null) return;

        // Check global webhook toggle
        if (!"true".equals(configService.getConfig("webhook-enabled", "true"))) {
            log.debug("Webhook globally disabled, skipping");
            return;
        }

        String status = task.getStatus();
        String statusEmoji = "completed".equals(status) ? "✅" : "❌";
        String duration = task.getStartedAt() != null && task.getCompletedAt() != null
                ? formatDuration(Duration.between(task.getStartedAt(), task.getCompletedAt()))
                : "N/A";

        String title = "ServerScout 扫描" + ("completed".equals(status) ? "完成" : "失败") + " — " + task.getName();

        // Build detailed markdown report
        StringBuilder md = new StringBuilder();
        md.append(String.format("### %s %s\n\n", statusEmoji, title));
        md.append(String.format("> **任务名称**: %s | **目标**: %s | **类型**: %s\n",
                task.getName(), task.getTargetRange(), task.getScanType()));
        md.append(String.format("> **状态**: %s | **耗时**: %s | **时间**: %s\n\n",
                "completed".equals(status) ? "✓ 已完成" : "✗ 失败", duration, task.getCompletedAt()));

        // Query detailed results
        if ("completed".equals(status)) {
            List<ScanAssetMapping> mappings = scanAssetMappingRepository.findByScanTaskIdWithAsset(taskId);
            List<Asset> assets = mappings.stream().map(ScanAssetMapping::getAsset).collect(Collectors.toList());
            long assetCount = assets.size();
            long portCount = 0;
            long vulnCount = 0;
            long crawledCount = 0;

            // --- Assets & Ports ---
            if (!assets.isEmpty()) {
                md.append("---\n");
                md.append(String.format("#### 📋 发现资产 (%d个)\n\n", assetCount));
                int assetIdx = 0;
                int maxAssets = 8; // limit to keep message readable
                for (Asset asset : assets) {
                    if (assetIdx >= maxAssets) {
                        md.append(String.format("> *...及其他 %d 个资产*\n\n", assetCount - maxAssets));
                        break;
                    }
                    assetIdx++;
                    List<Port> ports = portRepository.findByAssetId(asset.getId());
                    portCount += ports.size();
                    String host = asset.getHostname() != null ? asset.getHostname() : asset.getIpAddress();
                    String os = asset.getOsFingerprint() != null ? " | OS: " + asset.getOsFingerprint() : "";
                    md.append(String.format("**%d. %s**%s\n", assetIdx, host, os));
                    if (!ports.isEmpty()) {
                        int portIdx = 0;
                        int maxPorts = 6;
                        for (Port p : ports) {
                            if (portIdx >= maxPorts) {
                                md.append(String.format("  > *...及其他 %d 个端口*\n", ports.size() - maxPorts));
                                break;
                            }
                            portIdx++;
                            String svc = p.getServiceName() != null ? " — " + p.getServiceName() : "";
                            if (p.getServiceVersion() != null) svc += " " + p.getServiceVersion();
                            md.append(String.format("  - `%d/%s`%s\n", p.getPortNumber(),
                                    p.getProtocol() != null ? p.getProtocol() : "tcp", svc));
                        }
                    }
                    md.append("\n");
                }
            }

            // --- Vulnerabilities ---
            List<AssetVulnerability> vulns = new ArrayList<>();
            for (Asset asset : assets) {
                vulns.addAll(vulnRepository.findByAssetIdWithCve(asset.getId()));
            }
            vulnCount = vulns.size();
            if (!vulns.isEmpty()) {
                md.append("---\n");
                md.append(String.format("#### 🛡️ 漏洞信息 (%d个)\n\n", vulnCount));
                // Group by severity
                Map<String, Long> sevCount = vulns.stream()
                        .collect(Collectors.groupingBy(
                                v -> v.getCveDatabase().getSeverity() != null ? v.getCveDatabase().getSeverity() : "unknown",
                                Collectors.counting()));
                md.append("> ");
                String[] sevOrder = {"critical", "high", "medium", "low", "info"};
                for (String sev : sevOrder) {
                    Long c = sevCount.get(sev);
                    if (c != null) {
                        String icon = switch (sev) {
                            case "critical" -> "🔴"; case "high" -> "🟠";
                            case "medium" -> "🟡"; case "low" -> "🟢";
                            default -> "🔵";
                        };
                        md.append(String.format("%s %s: %d  |  ", icon, sev.toUpperCase(), c));
                    }
                }
                md.append("\n\n");
                int vulnIdx = 0;
                int maxVulns = 6;
                // Show critical/high first
                vulns.sort((a, b) -> {
                    String sa = a.getCveDatabase().getSeverity();
                    String sb = b.getCveDatabase().getSeverity();
                    return Arrays.asList(sevOrder).indexOf(sa != null ? sa : "info")
                            - Arrays.asList(sevOrder).indexOf(sb != null ? sb : "info");
                });
                for (AssetVulnerability v : vulns) {
                    if (vulnIdx >= maxVulns) {
                        md.append(String.format("> *...及其他 %d 个漏洞*\n\n", vulnCount - maxVulns));
                        break;
                    }
                    vulnIdx++;
                    CveDatabase cve = v.getCveDatabase();
                    String sev = cve.getSeverity() != null ? cve.getSeverity().toUpperCase() : "UNKNOWN";
                    String sevIcon = switch (cve.getSeverity()) {
                        case "critical" -> "🔴"; case "high" -> "🟠";
                        case "medium" -> "🟡"; case "low" -> "🟢";
                        default -> "🔵";
                    };
                    String cveId = cve.getCveId() != null ? cve.getCveId() : "N/A";
                    String desc = cve.getDescription() != null ? cve.getDescription() : "";
                    if (desc.length() > 80) desc = desc.substring(0, 80) + "...";
                    md.append(String.format("%s **%s** — %s\n> %s\n\n", sevIcon, cveId, sev, desc));
                }
            }

            // --- Crawled URLs ---
            List<CrawledUrl> crawled = crawledUrlRepository.findByTaskIdOrderByCrawlDepthAsc(taskId);
            crawledCount = crawled.size();
            if (!crawled.isEmpty()) {
                md.append("---\n");
                md.append(String.format("#### 🌐 爬虫信息 (%d个URL)\n\n", crawledCount));
                int crawlIdx = 0;
                int maxCrawl = 6;
                for (CrawledUrl c : crawled) {
                    if (crawlIdx >= maxCrawl) {
                        md.append(String.format("> *...及其他 %d 个 URL*\n", crawledCount - maxCrawl));
                        break;
                    }
                    crawlIdx++;
                    String httpStatus = c.getHttpStatus() != null ? String.valueOf(c.getHttpStatus()) : "—";
                    String titleStr = c.getTitle() != null && !c.getTitle().isEmpty() ? " — *" + c.getTitle() + "*" : "";
                    md.append(String.format("- `%s` (%s)%s\n", c.getUrl(), httpStatus, titleStr));
                }
                md.append("\n");
            }

            // Summary line
            md.append(String.format("---\n> 📊 **总计**: %d 个资产 | %d 个端口 | %d 个漏洞 | %d 个URL\n",
                    assetCount, portCount, vulnCount, crawledCount));
        }

        // Send to each configured webhook (respect per-platform toggles)
        if ("true".equals(configService.getConfig("webhook-dingtalk-enabled", "true")))
            sendDingTalk(title, md.toString());
        if ("true".equals(configService.getConfig("webhook-feishu-enabled", "true")))
            sendFeishu(title, md.toString());
        if ("true".equals(configService.getConfig("webhook-wecom-enabled", "true")))
            sendWeCom(title, md.toString());

        // Send email notification
        emailService.sendScanCompletedEmail(taskId, task);
    }

    private void sendDingTalk(String title, String markdown) {
        String webhookUrl = configService.getConfig("webhook-dingtalk", "");
        if (webhookUrl.isEmpty()) return;

        try {
            String payload = String.format(
                    "{\"msgtype\":\"markdown\",\"markdown\":{\"title\":\"%s\",\"text\":\"%s\"}}",
                    escapeJson(title), escapeJson(markdown));
            postJson(webhookUrl, payload);
            log.info("DingTalk notification sent for scan");
        } catch (Exception e) {
            log.warn("DingTalk webhook failed: {}", e.getMessage());
        }
    }

    private void sendFeishu(String title, String markdown) {
        String webhookUrl = configService.getConfig("webhook-feishu", "");
        if (webhookUrl.isEmpty()) return;

        try {
            String payload = String.format(
                    "{\"msg_type\":\"interactive\",\"card\":{\"header\":{\"title\":{\"tag\":\"plain_text\",\"content\":\"%s\"}},\"elements\":[{\"tag\":\"markdown\",\"content\":\"%s\"}]}}",
                    escapeJson(title), escapeJson(markdown));
            postJson(webhookUrl, payload);
            log.info("Feishu notification sent for scan");
        } catch (Exception e) {
            log.warn("Feishu webhook failed: {}", e.getMessage());
        }
    }

    private void sendWeCom(String title, String markdown) {
        String webhookUrl = configService.getConfig("webhook-wecom", "");
        if (webhookUrl.isEmpty()) return;

        try {
            String payload = String.format(
                    "{\"msgtype\":\"markdown\",\"markdown\":{\"content\":\"%s\"}}",
                    escapeJson(markdown));
            postJson(webhookUrl, payload);
            log.info("WeCom notification sent for scan");
        } catch (Exception e) {
            log.warn("WeCom webhook failed: {}", e.getMessage());
        }
    }

    private void postJson(String urlString, String json) throws Exception {
        URL url = URI.create(urlString).toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);
        conn.setRequestProperty("Content-Type", "application/json");

        try (OutputStream os = conn.getOutputStream()) {
            os.write(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }

        int code = conn.getResponseCode();
        conn.disconnect();
        if (code >= 400) {
            log.warn("Webhook returned HTTP {}", code);
        }
    }

    private String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }

    private String formatDuration(Duration d) {
        long hours = d.toHours();
        long minutes = d.toMinutes() % 60;
        long seconds = d.getSeconds() % 60;
        if (hours > 0) return String.format("%dh %dm %ds", hours, minutes, seconds);
        if (minutes > 0) return String.format("%dm %ds", minutes, seconds);
        return seconds + "s";
    }
}
