package com.serverscout.service;

import com.serverscout.dto.CreateScanTaskRequest;
import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for scanType normalization logic (trim + toUpperCase).
 * <p>
 * The actual normalization is done in {@link ScanService#createTask}.
 * These tests verify the normalization behavior independently.
 */
class ScanTypeNormalizeTest {

    /** Mirrors the normalization logic in ScanService.createTask() */
    private String normalize(String scanType) {
        return Optional.ofNullable(scanType)
                .filter(s -> !s.isBlank())
                .orElse("QUICK")
                .trim()
                .toUpperCase(Locale.ROOT);
    }

    @Test
    void shouldNormalizeQuickVariantsToUpperCase() {
        assertEquals("QUICK", normalize("quick"));
        assertEquals("QUICK", normalize("QUICK"));
        assertEquals("QUICK", normalize("Quick"));
        assertEquals("QUICK", normalize(" QUICK "));
    }

    @Test
    void shouldNormalizeFullVariantsToUpperCase() {
        assertEquals("FULL", normalize("full"));
        assertEquals("FULL", normalize("FULL"));
        assertEquals("FULL", normalize("Full"));
        assertEquals("FULL", normalize("  full  "));
    }

    @Test
    void shouldNormalizeOtherValidTypes() {
        assertEquals("STEALTH", normalize("stealth"));
        assertEquals("WEB", normalize("web"));
        assertEquals("CUSTOM", normalize("custom"));
        assertEquals("NUCLEI", normalize("nuclei"));
    }

    @Test
    void shouldDefaultToQuickWhenNull() {
        assertEquals("QUICK", normalize(null));
    }

    @Test
    void shouldDefaultToQuickWhenBlank() {
        assertEquals("QUICK", normalize(""));
        assertEquals("QUICK", normalize("   "));
    }

    @Test
    void shouldHaveDefaultScanTypeUppercase() {
        CreateScanTaskRequest req = new CreateScanTaskRequest();
        assertEquals("QUICK", req.getScanType(),
                "DTO default scanType should be QUICK (uppercase)");
    }
}
