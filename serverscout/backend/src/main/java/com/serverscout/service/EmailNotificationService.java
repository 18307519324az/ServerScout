package com.serverscout.service;

import com.serverscout.entity.ScanTask;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Properties;

@Slf4j
@Service
public class EmailNotificationService {

    private final SystemConfigService configService;

    public EmailNotificationService(SystemConfigService configService) {
        this.configService = configService;
    }

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
            String statusEmoji = "completed".equals(status) ? "✓" : "✗";
            String duration = task.getStartedAt() != null && task.getCompletedAt() != null
                    ? formatDuration(Duration.between(task.getStartedAt(), task.getCompletedAt()))
                    : "N/A";

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            String fromAddress = configService.getConfig("email-smtp-username", "");
            if (!fromAddress.isEmpty()) {
                helper.setFrom(fromAddress);
            }
            helper.setTo(recipient);
            helper.setSubject(String.format("[ServerScout] 扫描任务%s - %s",
                    "completed".equals(status) ? "完成" : "失败", task.getName()));

            String body = String.format("""
                    <div style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto;">
                      <h2 style="color: #2563eb;">ServerScout 扫描报告</h2>
                      <hr style="border: 1px solid #e5e7eb;" />
                      <table style="width: 100%%; border-collapse: collapse;">
                        <tr><td style="padding: 8px; color: #6b7280;">任务名称</td>
                            <td style="padding: 8px; font-weight: bold;">%s</td></tr>
                        <tr><td style="padding: 8px; color: #6b7280;">扫描目标</td>
                            <td style="padding: 8px;">%s</td></tr>
                        <tr><td style="padding: 8px; color: #6b7280;">扫描类型</td>
                            <td style="padding: 8px;">%s</td></tr>
                        <tr><td style="padding: 8px; color: #6b7280;">任务状态</td>
                            <td style="padding: 8px; color: %s;">%s %s</td></tr>
                        <tr><td style="padding: 8px; color: #6b7280;">发现资产</td>
                            <td style="padding: 8px; font-weight: bold;">%d 个</td></tr>
                        <tr><td style="padding: 8px; color: #6b7280;">开放端口</td>
                            <td style="padding: 8px; font-weight: bold;">%d 个</td></tr>
                        <tr><td style="padding: 8px; color: #6b7280;">耗时</td>
                            <td style="padding: 8px;">%s</td></tr>
                      </table>
                      <hr style="border: 1px solid #e5e7eb; margin-top: 16px;" />
                      <p style="color: #9ca3af; font-size: 12px;">本邮件由 ServerScout 自动发送</p>
                    </div>
                    """,
                    task.getName(),
                    task.getTargetRange(),
                    task.getScanType(),
                    "completed".equals(status) ? "#16a34a" : "#dc2626",
                    statusEmoji,
                    "completed".equals(status) ? "已完成" : "失败",
                    task.getTotalAssets(),
                    task.getTotalPorts(),
                    duration);

            helper.setText(body, true);
            mailSender.send(message);
            log.info("Email notification sent to {} for task {}", recipient, taskId);
        } catch (Exception e) {
            log.warn("Failed to send email notification: {}", e.getMessage());
        }
    }

    /**
     * Create a JavaMailSender from system config (supports web-based SMTP configuration).
     */
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

    private String formatDuration(Duration d) {
        long hours = d.toHours();
        long minutes = d.toMinutes() % 60;
        long seconds = d.getSeconds() % 60;
        if (hours > 0) return String.format("%dh %dm %ds", hours, minutes, seconds);
        if (minutes > 0) return String.format("%dm %ds", minutes, seconds);
        return seconds + "s";
    }
}
