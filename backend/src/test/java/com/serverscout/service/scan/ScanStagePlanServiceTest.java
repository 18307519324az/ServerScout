package com.serverscout.service.scan;

import com.serverscout.entity.ScanTask;
import com.serverscout.entity.enums.ScanStageCode;
import com.serverscout.service.ScanTaskStageService;
import com.serverscout.service.WebhookNotificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ScanStagePlanServiceTest {

    @Mock
    private ScanTaskStageService scanTaskStageService;

    @Mock
    private WebhookNotificationService webhookNotificationService;

    @InjectMocks
    private ScanStagePlanService scanStagePlanService;

    @Test
    void shouldSkipWebProbeWhenCrawlerDisabledQuickScan() {
        ScanTask task = ScanTask.builder()
                .id(1L).scanType("quick")
                .enableCrawler(false)
                .enableVulnScan(false)
                .enableFingerprint(true)
                .build();

        when(webhookNotificationService.isConfigured()).thenReturn(true);

        scanStagePlanService.applyInitialStagePlan(1L, task);

        verify(scanTaskStageService).skipIfPending(1L, ScanStageCode.WEB_PROBE,
                "未启用爬虫发现，跳过 Web 探测");
        verify(scanTaskStageService).skipIfPending(1L, ScanStageCode.VULNERABILITY_SCAN,
                "未启用漏洞检测，跳过漏洞检测");
        verify(scanTaskStageService).skipIfPending(1L, ScanStageCode.CVE_MATCH,
                "未启用漏洞检测，跳过 CVE 匹配");
    }

    @Test
    void shouldNotSkipWebProbeWhenCrawlerEnabled() {
        ScanTask task = ScanTask.builder()
                .id(1L).scanType("quick")
                .enableCrawler(true)
                .enableVulnScan(true)
                .enableFingerprint(true)
                .build();

        when(webhookNotificationService.isConfigured()).thenReturn(true);

        scanStagePlanService.applyInitialStagePlan(1L, task);

        verify(scanTaskStageService, never()).skipIfPending(eq(1L), eq(ScanStageCode.WEB_PROBE), anyString());
        verify(scanTaskStageService, never()).skipIfPending(eq(1L), eq(ScanStageCode.VULNERABILITY_SCAN), anyString());
        verify(scanTaskStageService, never()).skipIfPending(eq(1L), eq(ScanStageCode.CVE_MATCH), anyString());
    }

    @Test
    void shouldSkipVulnAndCveWhenVulnScanDisabledFullScan() {
        ScanTask task = ScanTask.builder()
                .id(1L).scanType("full")
                .enableCrawler(true)
                .enableVulnScan(false)
                .enableFingerprint(true)
                .build();

        when(webhookNotificationService.isConfigured()).thenReturn(true);

        scanStagePlanService.applyInitialStagePlan(1L, task);

        verify(scanTaskStageService, never()).skipIfPending(eq(1L), eq(ScanStageCode.WEB_PROBE), anyString());
        verify(scanTaskStageService).skipIfPending(1L, ScanStageCode.VULNERABILITY_SCAN,
                "未启用漏洞检测");
        verify(scanTaskStageService).skipIfPending(1L, ScanStageCode.CVE_MATCH,
                "未启用漏洞检测，跳过 CVE 匹配");
    }

    @Test
    void shouldSkipWebProbeWhenCrawlerDisabledFullScan() {
        ScanTask task = ScanTask.builder()
                .id(1L).scanType("full")
                .enableCrawler(false)
                .enableVulnScan(true)
                .enableFingerprint(true)
                .build();

        when(webhookNotificationService.isConfigured()).thenReturn(true);

        scanStagePlanService.applyInitialStagePlan(1L, task);

        verify(scanTaskStageService).skipIfPending(1L, ScanStageCode.WEB_PROBE,
                "未启用爬虫发现，跳过 Web 探测");
        verify(scanTaskStageService, never()).skipIfPending(eq(1L), eq(ScanStageCode.VULNERABILITY_SCAN), anyString());
        verify(scanTaskStageService, never()).skipIfPending(eq(1L), eq(ScanStageCode.CVE_MATCH), anyString());
    }

    @Test
    void shouldSkipNotificationWhenWebhookNotConfigured() {
        ScanTask task = ScanTask.builder()
                .id(1L).scanType("quick")
                .enableCrawler(true)
                .enableVulnScan(true)
                .enableFingerprint(true)
                .build();

        when(webhookNotificationService.isConfigured()).thenReturn(false);

        scanStagePlanService.applyInitialStagePlan(1L, task);

        verify(scanTaskStageService).skipIfPending(1L, ScanStageCode.NOTIFICATION,
                "未配置通知回调地址，跳过通知");
    }

    @Test
    void hostDiscoveryPlanShouldSkipFingerprintAndVulnAndWebProbe() {
        ScanTask task = ScanTask.builder()
                .id(1L).scanType("quick")
                .enableFingerprint(false)
                .enableVulnScan(false)
                .enableCrawler(false)
                .build();

        when(webhookNotificationService.isConfigured()).thenReturn(true);

        scanStagePlanService.applyInitialStagePlan(1L, task);

        verify(scanTaskStageService).skipIfPending(1L, ScanStageCode.SERVICE_FINGERPRINT,
                "主机发现模式不执行服务识别");
        verify(scanTaskStageService).skipIfPending(1L, ScanStageCode.WEB_PROBE,
                "未启用爬虫发现，跳过 Web 探测");
        verify(scanTaskStageService).skipIfPending(1L, ScanStageCode.VULNERABILITY_SCAN,
                "主机发现模式不执行漏洞检测");
        verify(scanTaskStageService).skipIfPending(1L, ScanStageCode.CVE_MATCH,
                "主机发现模式不执行 CVE 匹配");
    }
}
