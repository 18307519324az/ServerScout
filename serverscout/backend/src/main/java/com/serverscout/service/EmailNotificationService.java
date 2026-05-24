package com.serverscout.service;

import com.serverscout.entity.*;
import com.serverscout.repository.*;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailNotificationService {

    private final SystemConfigService configService;
    private final ScanAssetMappingRepository scanAssetMappingRepository;
    private final PortRepository portRepository;
    private final AssetVulnerabilityRepository vulnRepository;
    private final CrawledUrlRepository crawledUrlRepository;

    @Async
    public void sendScanCompletedEmail(Long taskId, ScanTask task) {
        String enabled = configService.getConfig("email-enabled", "false");
        if (!"true".equals(enabled)) return;

        String recipient = configService.getConfig("email-recipient", "");
        if (recipient.isEmpty()) {
            log.debug("Email recipient not configured, skipping email notification");
            return;
        }

        String smtpHost = configService.getConfig("email-smtp-host", "");
        if (smtpHost.isEmpty()) {
            log.debug("SMTP host not configured, skipping email notification");
            return;
        }

        try {
            JavaMailSender mailSender = createMailSender();

            String status = task.getStatus();
            String statusColor = "completed".equals(status) ? "#16a34a" : "#dc2626";
            String statusText = "completed".equals(status) ? "已完成" : "失败";
            String statusIcon = "completed".equals(status) ? "✓" : "✗";
            String duration = task.getStartedAt() != null && task.getCompletedAt() != null
                    ? formatDuration(Duration.between(task.getStartedAt(), task.getCompletedAt()))
                    : "N/A";
            String timeStr = task.getCompletedAt() != null
                    ? DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.of("Asia/Shanghai")).format(task.getCompletedAt())
                    : "N/A";

            StringBuilder body = new StringBuilder();
            body.append("""
                    <div style="font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Arial, sans-serif; max-width: 640px; margin: 0 auto; background: #ffffff; border-radius: 12px; overflow: hidden; box-shadow: 0 1px 3px rgba(0,0,0,0.1);">
                      <div style="background: linear-gradient(135deg, #1e40af, #3b82f6); padding: 24px 32px;">
                        <h2 style="color: #ffffff; margin: 0; font-size: 20px;">ServerScout 扫描报告</h2>
                        <p style="color: #bfdbfe; margin: 6px 0 0; font-size: 13px;">自动化安全巡检平台</p>
                      </div>
                      <div style="padding: 24px 32px;">
                    """);

            // Summary table
            body.append(String.format("""
                        <table style="width: 100%%; border-collapse: collapse; margin-bottom: 24px;">
                          <tr><td style="padding: 6px 8px; color: #6b7280; font-size: 13px; width: 80px;">任务名称</td>
                              <td style="padding: 6px 8px; font-weight: 600; font-size: 13px;">%s</td></tr>
                          <tr><td style="padding: 6px 8px; color: #6b7280; font-size: 13px;">扫描目标</td>
                              <td style="padding: 6px 8px; font-size: 13px;">%s</td></tr>
                          <tr><td style="padding: 6px 8px; color: #6b7280; font-size: 13px;">扫描类型</td>
                              <td style="padding: 6px 8px; font-size: 13px;">%s</td></tr>
                          <tr><td style="padding: 6px 8px; color: #6b7280; font-size: 13px;">任务状态</td>
                              <td style="padding: 6px 8px; font-size: 13px;"><span style="display:inline-block; padding:2px 10px; border-radius:12px; background:%s; color:#fff; font-weight:600;">%s %s</span></td></tr>
                          <tr><td style="padding: 6px 8px; color: #6b7280; font-size: 13px;">耗时 / 时间</td>
                              <td style="padding: 6px 8px; font-size: 13px;">%s / %s</td></tr>
                        </table>
                    """,
                    escHtml(task.getName()),
                    escHtml(task.getTargetRange()),
                    escHtml(task.getScanType()),
                    statusColor, statusIcon, statusText,
                    escHtml(duration), escHtml(timeStr)));

            // Detailed results (only if completed)
            if ("completed".equals(status)) {
                List<ScanAssetMapping> mappings = scanAssetMappingRepository.findByScanTaskIdWithAsset(taskId);
                List<Asset> assets = mappings.stream().map(ScanAssetMapping::getAsset).collect(Collectors.toList());
                long totalPorts = 0;
                List<AssetVulnerability> allVulns = new ArrayList<>();
                for (Asset asset : assets) {
                    if (asset.getId() != null) {
                        totalPorts += portRepository.countByAssetId(asset.getId());
                        allVulns.addAll(vulnRepository.findByAssetIdWithCve(asset.getId()));
                    }
                }
                List<CrawledUrl> crawled = crawledUrlRepository.findByTaskIdOrderByCrawlDepthAsc(taskId);

                // --- Assets & Ports Section ---
                if (!assets.isEmpty()) {
                    body.append(String.format("""
                            <div style="margin-bottom: 20px;">
                              <h3 style="font-size: 15px; color: #1f2937; border-bottom: 2px solid #e5e7eb; padding-bottom: 6px; margin: 0 0 12px;">📋 发现资产 (%d个) | 开放端口 (%d个)</h3>
                            """, assets.size(), totalPorts));
                    int maxAssets = 15;
                    int shown = 0;
                    for (Asset asset : assets) {
                        if (shown >= maxAssets) {
                            body.append(String.format("<p style=\"color:#6b7280;font-size:12px;margin:4px 0;\">...及其他 %d 个资产</p>", assets.size() - maxAssets));
                            break;
                        }
                        shown++;
                        String host = asset.getHostname() != null ? asset.getHostname() : asset.getIpAddress();
                        String os = asset.getOsFingerprint() != null
                                ? " <span style=\"color:#6b7280;font-size:11px;\">(" + escHtml(asset.getOsFingerprint()) + ")</span>" : "";
                        body.append(String.format("<p style=\"margin:8px 0 2px;font-weight:600;font-size:13px;\">%d. %s%s</p>", shown, escHtml(host), os));

                        List<Port> ports = portRepository.findByAssetId(asset.getId());
                        if (!ports.isEmpty()) {
                            body.append("<div style=\"margin-left:18px;font-size:12px;\">");
                            int maxPorts = 10;
                            int pShown = 0;
                            for (Port p : ports) {
                                if (pShown >= maxPorts) {
                                    body.append(String.format("<span style=\"color:#9ca3af;\">...及其他 %d 个端口</span><br/>", ports.size() - maxPorts));
                                    break;
                                }
                                pShown++;
                                String svc = p.getServiceName() != null ? " — " + escHtml(p.getServiceName()) : "";
                                if (p.getServiceVersion() != null) svc += " " + escHtml(p.getServiceVersion());
                                body.append(String.format("<code style=\"background:#f3f4f6;padding:1px 5px;border-radius:3px;\">%d/%s</code>%s<br/>",
                                        p.getPortNumber(), p.getProtocol() != null ? p.getProtocol() : "tcp", svc));
                            }
                            body.append("</div>");
                        }
                    }
                    body.append("</div>");
                }

                // --- Vulnerabilities Section ---
                if (!allVulns.isEmpty()) {
                    body.append(String.format("""
                            <div style="margin-bottom: 20px;">
                              <h3 style="font-size: 15px; color: #1f2937; border-bottom: 2px solid #e5e7eb; padding-bottom: 6px; margin: 0 0 12px;">🛡️ 漏洞信息 (%d个)</h3>
                            """, allVulns.size()));

                    // Severity summary
                    Map<String, Long> sevCount = allVulns.stream()
                            .collect(Collectors.groupingBy(
                                    v -> v.getCveDatabase().getSeverity() != null ? v.getCveDatabase().getSeverity() : "info",
                                    Collectors.counting()));
                    body.append("<div style=\"margin-bottom:10px;font-size:12px;\">");
                    String[] sevOrder = {"critical", "high", "medium", "low", "info"};
                    String[] sevColors = {"#dc2626", "#ea580c", "#ca8a04", "#16a34a", "#6b7280"};
                    for (int i = 0; i < sevOrder.length; i++) {
                        Long c = sevCount.get(sevOrder[i]);
                        if (c != null) {
                            body.append(String.format("<span style=\"display:inline-block;margin-right:8px;padding:2px 8px;border-radius:4px;background:%s;color:#fff;\">%s: %d</span>",
                                    sevColors[i], sevOrder[i].toUpperCase(), c));
                        }
                    }
                    body.append("</div>");

                    // Sort: critical/high first
                    allVulns.sort((a, b) -> {
                        String sa = a.getCveDatabase().getSeverity();
                        String sb = b.getCveDatabase().getSeverity();
                        return Arrays.asList(sevOrder).indexOf(sa != null ? sa : "info")
                                - Arrays.asList(sevOrder).indexOf(sb != null ? sb : "info");
                    });

                    int maxVulns = 20;
                    int vShown = 0;
                    body.append("<table style=\"width:100%;border-collapse:collapse;font-size:12px;\">");
                    body.append("<tr style=\"background:#f9fafb;\"><th style=\"padding:4px 8px;text-align:left;\">严重程度</th><th style=\"padding:4px 8px;text-align:left;\">CVE</th><th style=\"padding:4px 8px;text-align:left;\">描述</th></tr>");
                    for (AssetVulnerability v : allVulns) {
                        if (vShown >= maxVulns) break;
                        vShown++;
                        CveDatabase cve = v.getCveDatabase();
                        String sev = cve.getSeverity() != null ? cve.getSeverity() : "info";
                        int si = Arrays.asList(sevOrder).indexOf(sev);
                        String color = si >= 0 ? sevColors[si] : "#6b7280";
                        String desc = cve.getDescription() != null ? escHtml(cve.getDescription()) : "—";
                        if (desc.length() > 100) desc = desc.substring(0, 100) + "...";
                        body.append(String.format("<tr><td style=\"padding:4px 8px;\"><span style=\"display:inline-block;padding:1px 6px;border-radius:3px;background:%s;color:#fff;font-size:11px;\">%s</span></td><td style=\"padding:4px 8px;font-family:monospace;\">%s</td><td style=\"padding:4px 8px;\">%s</td></tr>",
                                color, sev.toUpperCase(), escHtml(cve.getCveId() != null ? cve.getCveId() : "N/A"), desc));
                    }
                    body.append("</table>");
                    if (allVulns.size() > maxVulns)
                        body.append(String.format("<p style=\"color:#9ca3af;font-size:11px;margin-top:4px;\">...及其他 %d 个漏洞</p>", allVulns.size() - maxVulns));
                    body.append("</div>");
                }

                // --- Crawled URLs Section ---
                if (!crawled.isEmpty()) {
                    body.append(String.format("""
                            <div style="margin-bottom: 20px;">
                              <h3 style="font-size: 15px; color: #1f2937; border-bottom: 2px solid #e5e7eb; padding-bottom: 6px; margin: 0 0 12px;">🌐 爬虫结果 (%d个URL)</h3>
                            """, crawled.size()));
                    body.append("<table style=\"width:100%;border-collapse:collapse;font-size:12px;\">");
                    body.append("<tr style=\"background:#f9fafb;\"><th style=\"padding:4px 8px;text-align:left;\">URL</th><th style=\"padding:4px 8px;text-align:left;\">状态码</th><th style=\"padding:4px 8px;text-align:left;\">标题</th></tr>");
                    int maxCrawl = 15;
                    int cShown = 0;
                    for (CrawledUrl c : crawled) {
                        if (cShown >= maxCrawl) break;
                        cShown++;
                        String httpCode = c.getHttpStatus() != null ? String.valueOf(c.getHttpStatus()) : "—";
                        String title = c.getTitle() != null ? escHtml(c.getTitle()) : "—";
                        if (title.length() > 50) title = title.substring(0, 50) + "...";
                        String codeColor = c.getHttpStatus() != null && c.getHttpStatus() < 400 ? "#16a34a" : "#dc2626";
                        body.append(String.format("<tr><td style=\"padding:4px 8px;font-family:monospace;font-size:11px;word-break:break-all;\">%s</td><td style=\"padding:4px 8px;\"><span style=\"color:%s;font-weight:600;\">%s</span></td><td style=\"padding:4px 8px;\">%s</td></tr>",
                                escHtml(c.getUrl()), codeColor, httpCode, title));
                    }
                    body.append("</table>");
                    if (crawled.size() > maxCrawl)
                        body.append(String.format("<p style=\"color:#9ca3af;font-size:11px;margin-top:4px;\">...及其他 %d 个 URL</p>", crawled.size() - maxCrawl));
                    body.append("</div>");
                }

                // Total summary
                body.append(String.format("""
                        <div style="background:#f0f9ff; border-left:4px solid #3b82f6; padding:10px 14px; border-radius:0 8px 8px 0; font-size:13px; color:#1e40af;">
                          总计: %d 个资产 · %d 个端口 · %d 个漏洞 · %d 个URL
                        </div>
                        """, assets.size(), totalPorts, allVulns.size(), crawled.size()));
            }

            body.append("""
                      </div>
                      <div style="background:#f9fafb; padding: 14px 32px; border-top: 1px solid #e5e7eb;">
                        <p style="color: #9ca3af; font-size: 11px; margin: 0;">本邮件由 ServerScout 自动发送 · 请勿回复</p>
                      </div>
                    </div>
                    """);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            String fromAddress = configService.getConfig("email-smtp-username", "");
            if (!fromAddress.isEmpty()) {
                helper.setFrom(fromAddress);
            }
            helper.setTo(recipient);
            helper.setSubject(String.format("[ServerScout] 扫描%s — %s",
                    "completed".equals(status) ? "完成" : "失败", task.getName()));
            helper.setText(body.toString(), true);
            mailSender.send(message);
            log.info("Email notification sent to {} for task {}", recipient, taskId);
        } catch (Exception e) {
            log.warn("Failed to send email notification: {}", e.getMessage(), e);
        }
    }

    private JavaMailSender createMailSender() {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(configService.getConfig("email-smtp-host", "smtp.qq.com"));
        sender.setPort(Integer.parseInt(configService.getConfig("email-smtp-port", "587")));
        sender.setUsername(configService.getConfig("email-smtp-username", ""));
        sender.setPassword(configService.getConfig("email-smtp-password", ""));

        Properties props = sender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", "true");
        boolean ssl = "true".equals(configService.getConfig("email-smtp-ssl", "false"));
        if (ssl) {
            props.put("mail.smtp.ssl.enable", "true");
            props.put("mail.smtp.starttls.enable", "false");
            props.put("mail.smtp.socketFactory.port", String.valueOf(sender.getPort()));
            props.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
        } else {
            props.put("mail.smtp.starttls.enable", "true");
            props.put("mail.smtp.ssl.enable", "false");
        }
        props.put("mail.debug", "false");
        return sender;
    }

    private String escHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
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
