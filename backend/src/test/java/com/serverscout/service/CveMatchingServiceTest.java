package com.serverscout.service;

import com.serverscout.entity.CveDatabase;
import com.serverscout.repository.CveDatabaseRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CveMatchingServiceTest {

    @Mock private CveDatabaseRepository cveDatabaseRepository;

    @InjectMocks
    private CveMatchingService cveMatchingService;

    @Test
    void shouldMatchExactVersion() {
        CveDatabase cve = CveDatabase.builder()
                .cveId("CVE-2021-41773").description("Apache Path Traversal").severity("critical")
                .cvssScore(BigDecimal.valueOf(9.8)).affectedSoftware("Apache HTTP Server")
                .affectedVersionRange("2.4.49").fixSuggestion("Upgrade to 2.4.51").build();
        when(cveDatabaseRepository.findByAffectedSoftwareContaining(anyString())).thenReturn(List.of(cve));

        List<CveMatchingService.CveMatchResult> results =
                cveMatchingService.matchVersions("Apache HTTP Server", "2.4.49");

        assertEquals(1, results.size());
        assertEquals("CVE-2021-41773", results.get(0).cveId());
        assertEquals("EXACT", results.get(0).confidence().name());
    }

    @Test
    void shouldMatchVersionInRange() {
        CveDatabase cve = CveDatabase.builder()
                .cveId("CVE-2021-44228").description("Log4Shell").severity("critical")
                .cvssScore(BigDecimal.valueOf(10.0)).affectedSoftware("Apache Log4j2")
                .affectedVersionRange("2.0 - 2.14.1").fixSuggestion("Upgrade to 2.17.0").build();
        when(cveDatabaseRepository.findByAffectedSoftwareContaining(anyString())).thenReturn(List.of(cve));

        List<CveMatchingService.CveMatchResult> results =
                cveMatchingService.matchVersions("Apache Log4j2", "2.10.0");

        assertEquals(1, results.size());
        assertEquals("CVE-2021-44228", results.get(0).cveId());
    }

    @Test
    void shouldNotMatchVersionOutsideRange() {
        CveDatabase cve = CveDatabase.builder()
                .cveId("CVE-2021-44228").description("Log4Shell").severity("critical")
                .cvssScore(BigDecimal.valueOf(10.0)).affectedSoftware("Apache Log4j2")
                .affectedVersionRange("2.0 - 2.14.1").fixSuggestion("Upgrade to 2.17.0").build();
        when(cveDatabaseRepository.findByAffectedSoftwareContaining(anyString())).thenReturn(List.of(cve));

        List<CveMatchingService.CveMatchResult> results =
                cveMatchingService.matchVersions("Apache Log4j2", "2.17.0");

        assertTrue(results.isEmpty());
    }

    @Test
    void shouldMatchLessThanRange() {
        CveDatabase cve = CveDatabase.builder()
                .cveId("CVE-2020-1938").description("Ghostcat").severity("high")
                .cvssScore(BigDecimal.valueOf(7.5)).affectedSoftware("Apache Tomcat")
                .affectedVersionRange("< 7.0.100").fixSuggestion("Upgrade").build();
        when(cveDatabaseRepository.findByAffectedSoftwareContaining(anyString())).thenReturn(List.of(cve));

        List<CveMatchingService.CveMatchResult> results =
                cveMatchingService.matchVersions("Apache Tomcat", "7.0.50");

        assertEquals(1, results.size());
    }

    @Test
    void shouldMatchGreaterThanOrEqualRange() {
        CveDatabase cve = CveDatabase.builder()
                .cveId("CVE-2023-44487").description("HTTP/2 DoS").severity("high")
                .cvssScore(BigDecimal.valueOf(7.5)).affectedSoftware("HTTP/2")
                .affectedVersionRange(">= 1.0").fixSuggestion("Update").build();
        when(cveDatabaseRepository.findByAffectedSoftwareContaining(anyString())).thenReturn(List.of(cve));

        List<CveMatchingService.CveMatchResult> results =
                cveMatchingService.matchVersions("HTTP/2", "2.0");

        assertEquals(1, results.size());
    }

    @Test
    void shouldReturnEmptyForNullProduct() {
        List<CveMatchingService.CveMatchResult> results =
                cveMatchingService.matchVersions(null, "1.0");
        assertTrue(results.isEmpty());
    }

    @Test
    void shouldReturnEmptyForShortProductName() {
        List<CveMatchingService.CveMatchResult> results =
                cveMatchingService.matchVersions("a", "1.0");
        assertTrue(results.isEmpty());
    }
}
