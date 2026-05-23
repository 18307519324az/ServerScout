package com.serverscout.service;

import com.serverscout.entity.ScanTask;
import com.serverscout.repository.ScanTaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.time.Duration;

/**
 * Sends scan completion notifications via webhook.
 * Supports DingTalk, Feishu (Lark), and WeCom (企业微信) bot webhooks.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookNotificationService {

    private final ScanTaskRepository scanTaskRepository;
    private final SystemConfigService configService;

    @Async
    public void sendScanCompletedNotification(Long taskId) {
        ScanTask task = scanTaskRepository.findById(taskId).orElse(null);
        if (task == null) return;

        String status = task.getStatus();
        String statusEmoji = "completed".equals(status) ? "✅" : "❌";
        String duration = task.getStartedAt() != null && task.getCompletedAt() != null
                ? formatDuration(Duration.between(task.getStartedAt(), task.getCompletedAt()))
                : "N/A";

        String title = "ServerScout 扫描任务" + ("completed".equals(status) ? "完成" : "失败");

        String markdown = String.format(
                "### %s %s\n" +
                "> 任务: %s\n" +
                "> 目标: %s | 类型: %s\n" +
                "> 资产: %d 个 | 端口: %d 个\n" +
                "> 耗时: %s | 时间: %s",
                statusEmoji, title,
                task.getName(), task.getTargetRange(), task.getScanType(),
                task.getTotalAssets(), task.getTotalPorts(),
                duration, task.getCompletedAt());

        // Send to each configured webhook
        sendDingTalk(title, markdown);
        sendFeishu(title, markdown);
        sendWeCom(title, markdown);
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
