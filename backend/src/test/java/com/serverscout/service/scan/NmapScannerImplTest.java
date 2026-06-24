package com.serverscout.service.scan;

import com.serverscout.entity.ScanTask;
import com.serverscout.service.SystemConfigService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NmapScannerImplTest {

    @Test
    void explicitPortRangeOverridesQuickTopPorts() {
        SystemConfigService configService = mock(SystemConfigService.class);
        when(configService.getConfig("nmap-path", "nmap")).thenReturn("nmap");
        NmapScannerImpl scanner = new NmapScannerImpl(configService, mock(ProcessRegistry.class));
        ReflectionTestUtils.setField(scanner, "nmapPath", "nmap");
        ScanTask task = ScanTask.builder()
                .scanType("quick")
                .portRange("22,80,443")
                .targetRange("127.0.0.1")
                .build();

        @SuppressWarnings("unchecked")
        List<String> command = (List<String>) ReflectionTestUtils.invokeMethod(scanner, "buildCommand", task);

        assertThat(command).containsSubsequence("-p", "22,80,443");
        assertThat(command).doesNotContain("--top-ports");
    }

    @Test
    void quickScanWithoutExplicitPortsUsesTopPorts() {
        SystemConfigService configService = mock(SystemConfigService.class);
        when(configService.getConfig("nmap-path", "nmap")).thenReturn("nmap");
        NmapScannerImpl scanner = new NmapScannerImpl(configService, mock(ProcessRegistry.class));
        ReflectionTestUtils.setField(scanner, "nmapPath", "nmap");
        ScanTask task = ScanTask.builder()
                .scanType("quick")
                .targetRange("127.0.0.1")
                .build();

        @SuppressWarnings("unchecked")
        List<String> command = (List<String>) ReflectionTestUtils.invokeMethod(scanner, "buildCommand", task);

        assertThat(command).containsSubsequence("--top-ports", "100");
        assertThat(command).doesNotContain("-p");
    }
}
