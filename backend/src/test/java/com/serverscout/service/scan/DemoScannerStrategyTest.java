package com.serverscout.service.scan;

import com.serverscout.entity.ScanTask;
import com.serverscout.service.ProgressEmitter;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class DemoScannerStrategyTest {

    private final ProgressEmitter progressEmitter = mock(ProgressEmitter.class);

    private DemoScannerStrategy createStrategy(boolean demoMode) {
        DemoScannerStrategy strategy = new DemoScannerStrategy(progressEmitter);
        ReflectionTestUtils.setField(strategy, "demoMode", demoMode);
        return strategy;
    }

    @Test
    void supportsReturnsTrueWhenDemoModeEnabled() {
        DemoScannerStrategy strategy = createStrategy(true);
        assertThat(strategy.supports("quick")).isTrue();
        assertThat(strategy.supports("full")).isTrue();
        assertThat(strategy.supports("nuclei")).isTrue();
        assertThat(strategy.supports("custom")).isTrue();
    }

    @Test
    void supportsReturnsFalseWhenDemoModeDisabled() {
        DemoScannerStrategy strategy = createStrategy(false);
        assertThat(strategy.supports("quick")).isFalse();
        assertThat(strategy.supports("full")).isFalse();
        assertThat(strategy.supports("nuclei")).isFalse();
        assertThat(strategy.supports("custom")).isFalse();
    }

    @Test
    void executeQuickScanReturnsAssetsWithPorts() {
        DemoScannerStrategy strategy = createStrategy(true);
        ScanTask task = ScanTask.builder()
                .id(1L)
                .scanType("quick")
                .targetRange("127.0.0.1")
                .build();

        ScanResult result = strategy.execute(task);

        assertThat(result).isNotNull();
        assertThat(result.getAssets()).isNotEmpty().hasSizeBetween(2, 3);
        for (ScanResult.AssetEntry asset : result.getAssets()) {
            assertThat(asset.getIpAddress()).isNotNull();
            assertThat(asset.getPorts()).isNotEmpty().hasSizeBetween(2, 4);
            assertThat(asset.getPorts().get(0).getServiceName()).isNotNull();
        }
        // Quick scans don't return vulns from the strategy — vulns come via nuclei phase
        assertThat(result.getVulnerabilities()).isEmpty();
    }

    @Test
    void executeFullScanReturnsMoreAssets() {
        DemoScannerStrategy strategy = createStrategy(true);
        ScanTask task = ScanTask.builder()
                .id(2L)
                .scanType("full")
                .targetRange("192.168.1.0/24")
                .build();

        ScanResult result = strategy.execute(task);

        assertThat(result).isNotNull();
        assertThat(result.getAssets()).isNotEmpty().hasSizeBetween(3, 5);
        for (ScanResult.AssetEntry asset : result.getAssets()) {
            assertThat(asset.getIpAddress()).startsWith("192.168.1.");
            assertThat(asset.getPorts()).isNotEmpty().hasSizeBetween(3, 8);
            assertThat(asset.getHostname()).isNotNull();
        }
    }

    @Test
    void executeNucleiScanReturnsVulnerabilities() {
        DemoScannerStrategy strategy = createStrategy(true);
        ScanTask task = ScanTask.builder()
                .id(3L)
                .scanType("nuclei")
                .targetRange("10.0.0.1")
                .build();

        ScanResult result = strategy.execute(task);

        assertThat(result).isNotNull();
        assertThat(result.getVulnerabilities()).isNotEmpty().hasSizeBetween(5, 8);
        assertThat(result.getAssets()).isEmpty();

        // Verify vuln entries have required fields
        for (ScanResult.VulnEntry vuln : result.getVulnerabilities()) {
            assertThat(vuln.getTemplate()).startsWith("CVE-");
            assertThat(vuln.getSeverity()).isIn("LOW", "MEDIUM", "HIGH", "CRITICAL");
            assertThat(vuln.getName()).startsWith("[Demo]");
            assertThat(vuln.getMatched()).isNotEmpty();
        }
    }

    @Test
    void executeWithFallbackTargetUsesDefaultIps() {
        DemoScannerStrategy strategy = createStrategy(true);
        ScanTask task = ScanTask.builder()
                .id(4L)
                .scanType("quick")
                .targetRange("invalid-target")
                .build();

        ScanResult result = strategy.execute(task);

        assertThat(result).isNotNull();
        assertThat(result.getAssets()).isNotEmpty();
        // Fallback IPs start with 192.168.56
        assertThat(result.getAssets().get(0).getIpAddress()).startsWith("192.168.56.");
    }

    @Test
    void executeWithNullTargetUsesFallbackIps() {
        DemoScannerStrategy strategy = createStrategy(true);
        ScanTask task = ScanTask.builder()
                .id(5L)
                .scanType("quick")
                .targetRange(null)
                .build();

        ScanResult result = strategy.execute(task);

        assertThat(result).isNotNull();
        assertThat(result.getAssets()).isNotEmpty();
        assertThat(result.getAssets().get(0).getIpAddress()).startsWith("192.168.56.");
    }

    @Test
    void vulnEntriesCoverCriticalAndHighSeverities() {
        DemoScannerStrategy strategy = createStrategy(true);
        ScanTask task = ScanTask.builder()
                .id(6L)
                .scanType("nuclei")
                .targetRange("172.16.0.1")
                .build();

        ScanResult result = strategy.execute(task);

        List<String> severities = result.getVulnerabilities().stream()
                .map(ScanResult.VulnEntry::getSeverity)
                .distinct()
                .toList();
        // First 7 template entries are CRITICAL and HIGH; MEDIUM and LOW start from index 7+
        assertThat(severities).contains("CRITICAL", "HIGH");
        assertThat(result.getVulnerabilities()).hasSizeBetween(5, 8);
    }
}
