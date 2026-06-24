package com.serverscout.service;

import com.serverscout.dto.CreateScanTaskRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CreateScanTaskRequestTest {

    @Test
    void shouldDefaultEnableCrawlerToFalse() {
        CreateScanTaskRequest req = new CreateScanTaskRequest();
        assertFalse(req.getEnableCrawler(), "enableCrawler should default to false");
    }

    @Test
    void shouldDefaultEnableFingerprintToTrue() {
        CreateScanTaskRequest req = new CreateScanTaskRequest();
        assertTrue(req.getEnableFingerprint(), "enableFingerprint should default to true");
    }

    @Test
    void shouldDefaultEnableVulnScanToFalse() {
        CreateScanTaskRequest req = new CreateScanTaskRequest();
        assertFalse(req.getEnableVulnScan(), "enableVulnScan should default to false");
    }

    @Test
    void shouldDefaultScanTypeToQuick() {
        CreateScanTaskRequest req = new CreateScanTaskRequest();
        assertEquals("QUICK", req.getScanType(), "scanType should default to QUICK");
    }
}
