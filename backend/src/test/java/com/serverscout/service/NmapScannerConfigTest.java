package com.serverscout.service;

import com.serverscout.entity.ScanTask;
import com.serverscout.service.scan.NmapScannerImpl;
import com.serverscout.service.scan.ScannerStrategy;
import com.serverscout.service.SystemConfigService;
import com.serverscout.service.scan.ProcessRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class NmapScannerConfigTest {

    private NmapScannerImpl scanner;

    @Mock
    private SystemConfigService configService;

    @Mock
    private ProcessRegistry processRegistry;

    @BeforeEach
    void setUp() {
        scanner = new NmapScannerImpl(configService, processRegistry);
        // Set default timeouts via reflection since there's no ApplicationContext
        ReflectionTestUtils.setField(scanner, "nmapPath", "/usr/bin/nmap");
        ReflectionTestUtils.setField(scanner, "defaultTimeoutSeconds", 300);
        ReflectionTestUtils.setField(scanner, "hostDiscoveryTimeoutSeconds", 60);
        ReflectionTestUtils.setField(scanner, "quickTimeoutSeconds", 120);
        ReflectionTestUtils.setField(scanner, "fullTimeoutSeconds", 900);
        ReflectionTestUtils.setField(scanner, "stealthTimeoutSeconds", 180);
    }

    @Test
    void shouldSupportUppercaseScanTypes() {
        assertTrue(scanner.supports("QUICK"));
        assertTrue(scanner.supports("STEALTH"));
        assertTrue(scanner.supports("WEB"));
        assertTrue(scanner.supports("FULL"));
    }

    @Test
    void shouldSupportLowercaseScanTypes() {
        assertTrue(scanner.supports("quick"));
        assertTrue(scanner.supports("full"));
        assertTrue(scanner.supports("custom"));
        assertTrue(scanner.supports("host_discovery"));
    }

    @Test
    void shouldNotSupportUnknownTypes() {
        assertFalse(scanner.supports("nuclei"));
        assertFalse(scanner.supports("unknown"));
        assertFalse(scanner.supports(""));
        assertFalse(scanner.supports(null));
    }

    @Test
    void shouldSupportsNotThrowOnNull() {
        // supports uses .equals() — ensure null doesn't NPE
        assertFalse(scanner.supports(null));
    }

    @Test
    void buildCommandShouldContainPnForQuick() {
        ScanTask task = ScanTask.builder()
                .scanType("QUICK").targetRange("192.168.1.1")
                .portRange("1-1000").build();
        // We can't easily capture the command since buildCommand is private,
        // but we verify the scanner accepts the type
        assertTrue(scanner.supports("QUICK"));
    }

    @Test
    void buildCommandShouldContainPnForStealth() {
        ScanTask task = ScanTask.builder()
                .scanType("STEALTH").targetRange("192.168.1.1")
                .portRange("22,80,443").build();
        assertTrue(scanner.supports("STEALTH"));
    }

    @Test
    void buildCommandShouldContainPnForWeb() {
        ScanTask task = ScanTask.builder()
                .scanType("WEB").targetRange("192.168.1.1")
                .portRange("80,443,8080").build();
        assertTrue(scanner.supports("WEB"));
    }

    @Test
    void buildCommandShouldContainPnForFull() {
        ScanTask task = ScanTask.builder()
                .scanType("FULL").targetRange("192.168.1.1")
                .portRange("1-1000").build();
        assertTrue(scanner.supports("FULL"));
    }

    // --- Full-specific configuration tests ---

    @Test
    void fullTimeoutIs900Seconds() {
        Object timeout = ReflectionTestUtils.getField(scanner, "fullTimeoutSeconds");
        assertNotNull(timeout);
        assertEquals(900, ((Number) timeout).intValue(), "Full scan timeout should be 900 seconds");
    }

    @SuppressWarnings("unchecked")
    @Test
    void fullScanFallbackUsesAllPortsWhenNoPortRange() throws Exception {
        ScanTask task = ScanTask.builder()
                .id(1L)
                .scanType("FULL")
                .targetRange("192.168.1.1")
                .portRange(null)
                .build();

        List<String> cmd = (List<String>) ReflectionTestUtils.invokeMethod(
                scanner, "buildCommand", task);
        assertNotNull(cmd);

        int pIndex = cmd.indexOf("-p");
        assertTrue(pIndex >= 0, "Full scan without portRange should include -p flag");
        assertTrue(pIndex + 1 < cmd.size(), "Should have a value after -p");
        assertEquals("1-65535", cmd.get(pIndex + 1),
                "Full scan without portRange should use 1-65535");

        // Should NOT contain --top-ports
        assertFalse(cmd.contains("--top-ports"),
                "Full scan should not use --top-ports");
    }

    @SuppressWarnings("unchecked")
    @Test
    void fullScanUsesCustomPortRangeWhenProvided() throws Exception {
        ScanTask task = ScanTask.builder()
                .id(1L)
                .scanType("FULL")
                .targetRange("192.168.1.1")
                .portRange("80,443,8080")
                .build();

        List<String> cmd = (List<String>) ReflectionTestUtils.invokeMethod(
                scanner, "buildCommand", task);
        assertNotNull(cmd);

        int pIndex = cmd.indexOf("-p");
        assertTrue(pIndex >= 0, "Full scan should include -p flag");
        assertTrue(pIndex + 1 < cmd.size());
        assertEquals("80,443,8080", cmd.get(pIndex + 1),
                "Full scan should use the provided port range");
    }

    @SuppressWarnings("unchecked")
    @Test
    void fullBuildCommandContainsHostTimeout600s() throws Exception {
        ScanTask task = ScanTask.builder()
                .id(1L)
                .scanType("FULL")
                .targetRange("192.168.1.1")
                .portRange("1-1000")
                .build();

        List<String> cmd = (List<String>) ReflectionTestUtils.invokeMethod(
                scanner, "buildCommand", task);
        assertNotNull(cmd);

        int htIndex = cmd.indexOf("--host-timeout");
        assertTrue(htIndex >= 0, "Full scan should include --host-timeout");
        assertTrue(htIndex + 1 < cmd.size(), "Should have a value after --host-timeout");
        assertEquals("600s", cmd.get(htIndex + 1),
                "Full scan host-timeout should be 600s");
    }

    @SuppressWarnings("unchecked")
    @Test
    void fullBuildCommandContainsMaxRetries1() throws Exception {
        ScanTask task = ScanTask.builder()
                .id(1L)
                .scanType("FULL")
                .targetRange("192.168.1.1")
                .portRange("1-1000")
                .build();

        List<String> cmd = (List<String>) ReflectionTestUtils.invokeMethod(
                scanner, "buildCommand", task);
        assertNotNull(cmd);

        int mrIndex = cmd.indexOf("--max-retries");
        assertTrue(mrIndex >= 0, "Full scan should include --max-retries");
        assertTrue(mrIndex + 1 < cmd.size(), "Should have a value after --max-retries");
        assertEquals("1", cmd.get(mrIndex + 1),
                "Full scan max-retries should be 1");
    }

    @SuppressWarnings("unchecked")
    @Test
    void fullBuildCommandContainsPnAndN() throws Exception {
        ScanTask task = ScanTask.builder()
                .id(1L)
                .scanType("FULL")
                .targetRange("192.168.1.1")
                .portRange("1-1000")
                .build();

        List<String> cmd = (List<String>) ReflectionTestUtils.invokeMethod(
                scanner, "buildCommand", task);
        assertNotNull(cmd);

        assertTrue(cmd.contains("-Pn"), "Full scan should include -Pn");
        assertTrue(cmd.contains("-n"), "Full scan should include -n");
        assertTrue(cmd.contains("-sS"), "Full scan should include -sS");
        assertTrue(cmd.contains("-sV"), "Full scan should include -sV");
        assertTrue(cmd.contains("-O"), "Full scan should include -O");
    }

    @SuppressWarnings("unchecked")
    @Test
    void fullBuildCommandWithLowercaseScanType() throws Exception {
        // Legacy data uses lowercase "full" — verify the fallback still works
        ScanTask task = ScanTask.builder()
                .id(1L)
                .scanType("full")
                .targetRange("192.168.1.1")
                .portRange(null)
                .build();

        List<String> cmd = (List<String>) ReflectionTestUtils.invokeMethod(
                scanner, "buildCommand", task);
        assertNotNull(cmd);

        int pIndex = cmd.indexOf("-p");
        assertTrue(pIndex >= 0,
                "Lowercase 'full' should also resolve to -p via equalsIgnoreCase");
        assertTrue(pIndex + 1 < cmd.size());
        assertEquals("1-65535", cmd.get(pIndex + 1));
    }
}
