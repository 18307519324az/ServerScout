package com.serverscout.service;

import com.serverscout.entity.*;
import com.serverscout.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RiskScoreServiceTest {

    @Mock private RiskScoreRepository riskScoreRepository;
    @Mock private AssetRepository assetRepository;
    @Mock private PortRepository portRepository;
    @Mock private AssetVulnerabilityRepository assetVulnerabilityRepository;
    @Mock private ScanTaskRepository scanTaskRepository;

    @InjectMocks
    private RiskScoreService riskScoreService;

    private Asset createAsset(Long id, String ip, String hostname, String tags) {
        return Asset.builder().id(id).ipAddress(ip).hostname(hostname).tags(tags).build();
    }

    private Port createPort(Long id, Integer portNumber, String serviceName, String state, Boolean isWebService) {
        return Port.builder()
                .id(id).portNumber(portNumber).serviceName(serviceName)
                .state(state).isWebService(isWebService).build();
    }

    private CveDatabase createCve(String cveId, String severity, String fixSuggestion) {
        return CveDatabase.builder().cveId(cveId).severity(severity).fixSuggestion(fixSuggestion).build();
    }

    private AssetVulnerability createVuln(Long id, String status, CveDatabase cve) {
        return AssetVulnerability.builder()
                .id(id)
                .status(status)
                .cveDatabase(cve)
                .build();
    }

    @Test
    void calculateForAsset_withoutVulns_shouldReturnLowScore() {
        Long assetId = 1L;
        Asset asset = createAsset(assetId, "192.168.1.10", "web-server", "[]");
        when(assetRepository.findById(assetId)).thenReturn(Optional.of(asset));
        when(portRepository.findByAssetId(assetId)).thenReturn(List.of(
                createPort(1L, 80, "HTTP", "open", true),
                createPort(2L, 443, "HTTPS", "open", true)
        ));
        when(assetVulnerabilityRepository.findByAssetIdWithCve(assetId)).thenReturn(List.of());
        when(riskScoreRepository.findByTaskIdAndAssetId(anyLong(), eq(assetId))).thenReturn(Optional.empty());
        when(riskScoreRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        RiskScoreDetail result = riskScoreService.calculateForAsset(1L, assetId);

        assertNotNull(result);
        assertTrue(result.getFinalRiskScore() <= 40, "Score should be low when no vulns: " + result.getFinalRiskScore());
        assertEquals("192.168.1.10", result.getAssetIp());
        assertEquals(0, result.getVulnerabilitySeverityScore());
        assertNotNull(result.getRiskReason());
        assertNotNull(result.getRepairSuggestion());
    }

    @Test
    void calculateForAsset_withCriticalVulns_shouldReturnHighScore() {
        Long assetId = 2L;
        Asset asset = createAsset(assetId, "10.0.0.1", "prod-db-01", "[\"production\",\"database\"]");
        when(assetRepository.findById(assetId)).thenReturn(Optional.of(asset));
        // 21+ open ports → assetExposureScore = 90
        when(portRepository.findByAssetId(assetId)).thenReturn(List.of(
                createPort(1L, 3306, "MySQL", "open", false),
                createPort(2L, 22, "SSH", "open", false),
                createPort(3L, 80, "HTTP", "open", true),
                createPort(4L, 443, "HTTPS", "open", true),
                createPort(5L, 8080, "Spring Boot", "open", true),
                createPort(6L, 6379, "Redis", "open", false),
                createPort(7L, 5432, "PostgreSQL", "open", false),
                createPort(8L, 8443, "HTTPS", "open", true),
                createPort(9L, 9090, "Admin Panel", "open", false),
                createPort(10L, 27017, "MongoDB", "open", false),
                createPort(11L, 1433, "SQL Server", "open", false),
                createPort(12L, 1521, "Oracle DB", "open", false),
                createPort(13L, 25, "SMTP", "open", false),
                createPort(14L, 53, "DNS", "open", false),
                createPort(15L, 110, "POP3", "open", false),
                createPort(16L, 143, "IMAP", "open", false),
                createPort(17L, 389, "LDAP", "open", false),
                createPort(18L, 993, "IMAPS", "open", true),
                createPort(19L, 995, "POP3S", "open", true),
                createPort(20L, 5900, "VNC", "open", false),
                createPort(21L, 5901, "VNC", "open", false)
        ));

        // 3 critical vulns with no fix suggestions → high severity but minimal remediation deduction
        CveDatabase criticalCve1 = CveDatabase.builder()
                .cveId("CVE-2021-44228").severity("critical").affectedSoftware("Apache Log4j2")
                .fixSuggestion(null).build();
        CveDatabase criticalCve2 = CveDatabase.builder()
                .cveId("CVE-2022-22965").severity("critical").affectedSoftware("Spring Framework")
                .fixSuggestion(null).build();
        CveDatabase criticalCve3 = CveDatabase.builder()
                .cveId("CVE-2023-44487").severity("critical").affectedSoftware("HTTP/2")
                .fixSuggestion(null).build();

        when(assetVulnerabilityRepository.findByAssetIdWithCve(assetId)).thenReturn(List.of(
                createVuln(1L, "open", criticalCve1),
                createVuln(2L, "open", criticalCve2),
                createVuln(3L, "open", criticalCve3)
        ));
        when(riskScoreRepository.findByTaskIdAndAssetId(anyLong(), eq(assetId))).thenReturn(Optional.empty());
        when(riskScoreRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        RiskScoreDetail result = riskScoreService.calculateForAsset(1L, assetId);

        assertNotNull(result);
        assertTrue(result.getVulnerabilitySeverityScore() >= 50);
        assertTrue(result.getServiceRiskScore() >= 75);
        assertTrue(result.getBusinessImportanceScore() >= 60,
                "Core production asset should have high business importance: " + result.getBusinessImportanceScore());
        assertTrue(result.getFinalRiskScore() >= 80,
                "Score should be HIGH or CRITICAL with critical vulns + many ports + core asset: " + result.getFinalRiskScore());
        assertNotNull(result.getRiskReason());
        assertTrue(result.getRepairSuggestion().contains("CVE-2021-44228") || result.getRepairSuggestion().contains("Log4j"));
    }

    @Test
    void calculateForAsset_withHighRiskPorts_shouldReturnHigherServiceRisk() {
        Long assetId = 3L;
        Asset asset = createAsset(assetId, "10.0.0.2", "test-server", "[]");
        when(assetRepository.findById(assetId)).thenReturn(Optional.of(asset));
        when(portRepository.findByAssetId(assetId)).thenReturn(List.of(
                createPort(1L, 3306, "MySQL", "open", false),
                createPort(2L, 6379, "Redis", "open", false),
                createPort(3L, 22, "SSH", "open", false)
        ));
        when(assetVulnerabilityRepository.findByAssetIdWithCve(assetId)).thenReturn(List.of());
        when(riskScoreRepository.findByTaskIdAndAssetId(anyLong(), eq(assetId))).thenReturn(Optional.empty());
        when(riskScoreRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        RiskScoreDetail result = riskScoreService.calculateForAsset(1L, assetId);

        assertNotNull(result);
        assertTrue(result.getServiceRiskScore() >= 75, "High-risk DB ports should push service risk to 75+");
        // Business importance should be reduced (test keyword matches GENERAL_KEYWORDS)
        assertTrue(result.getBusinessImportanceScore() <= 25,
                "Business importance should be low for test servers: " + result.getBusinessImportanceScore());
    }

    @Test
    void calculateForAsset_repairSuggestions_shouldContainPortSpecificAdvice() {
        Long assetId = 4L;
        Asset asset = createAsset(assetId, "10.0.0.3", "web", "[]");
        when(assetRepository.findById(assetId)).thenReturn(Optional.of(asset));
        when(portRepository.findByAssetId(assetId)).thenReturn(List.of(
                createPort(1L, 22, "SSH", "open", false),
                createPort(2L, 3389, "RDP", "open", false)
        ));
        when(assetVulnerabilityRepository.findByAssetIdWithCve(assetId)).thenReturn(List.of());
        when(riskScoreRepository.findByTaskIdAndAssetId(anyLong(), eq(assetId))).thenReturn(Optional.empty());
        when(riskScoreRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        RiskScoreDetail result = riskScoreService.calculateForAsset(1L, assetId);

        assertNotNull(result);
        assertNotNull(result.getRepairSuggestion());
        assertFalse(result.getRepairSuggestion().isBlank());
        assertTrue(result.getRepairSuggestion().contains("SSH"),
                "Repair suggestion should mention SSH for port 22");
        assertTrue(result.getRepairSuggestion().contains("RDP"),
                "Repair suggestion should mention RDP for port 3389");
    }

    @Test
    void calculateForTask_shouldCalculateForAllAssets() {
        Long taskId = 1L;
        ScanTask task = ScanTask.builder().id(taskId).name("Test Task").build();
        when(scanTaskRepository.findById(taskId)).thenReturn(Optional.of(task));

        Asset asset1 = createAsset(1L, "10.0.0.1", "server-1", "[]");
        Asset asset2 = createAsset(2L, "10.0.0.2", "server-2", "[]");
        when(assetRepository.findByTaskId(taskId)).thenReturn(List.of(asset1, asset2));

        when(assetRepository.findById(1L)).thenReturn(Optional.of(asset1));
        when(assetRepository.findById(2L)).thenReturn(Optional.of(asset2));
        when(portRepository.findByAssetId(1L)).thenReturn(List.of(createPort(1L, 80, "HTTP", "open", true)));
        when(portRepository.findByAssetId(2L)).thenReturn(List.of(createPort(2L, 443, "HTTPS", "open", true)));
        when(assetVulnerabilityRepository.findByAssetIdWithCve(1L)).thenReturn(List.of());
        when(assetVulnerabilityRepository.findByAssetIdWithCve(2L)).thenReturn(List.of());
        when(riskScoreRepository.findByTaskIdAndAssetId(taskId, 1L)).thenReturn(Optional.empty());
        when(riskScoreRepository.findByTaskIdAndAssetId(taskId, 2L)).thenReturn(Optional.empty());
        when(riskScoreRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        List<RiskScoreDetail> results = riskScoreService.calculateForTask(taskId);

        assertNotNull(results);
        assertEquals(2, results.size());
    }

    @Test
    void calculateForTask_withNoAssets_shouldReturnEmpty() {
        Long taskId = 999L;
        ScanTask task = ScanTask.builder().id(taskId).build();
        when(scanTaskRepository.findById(taskId)).thenReturn(Optional.of(task));
        when(assetRepository.findByTaskId(taskId)).thenReturn(List.of());

        List<RiskScoreDetail> results = riskScoreService.calculateForTask(taskId);

        assertNotNull(results);
        assertTrue(results.isEmpty());
    }

    @Test
    void listByTaskId_shouldReturnRiskScores() {
        Long taskId = 1L;
        RiskScoreDetail detail = RiskScoreDetail.builder()
                .id(1L).taskId(taskId).assetId(1L)
                .assetIp("10.0.0.1").finalRiskScore(75).riskLevel("HIGH")
                .build();
        when(riskScoreRepository.findByTaskId(taskId)).thenReturn(List.of(detail));

        List<RiskScoreDetail> results = riskScoreService.listByTaskId(taskId);

        assertEquals(1, results.size());
        assertEquals(75, results.get(0).getFinalRiskScore());
        assertEquals("HIGH", results.get(0).getRiskLevel());
    }

    @Test
    void topRisks_shouldReturnOrderedResults() {
        RiskScoreDetail detail1 = RiskScoreDetail.builder().id(1L).finalRiskScore(90).riskLevel("CRITICAL").build();
        RiskScoreDetail detail2 = RiskScoreDetail.builder().id(2L).finalRiskScore(45).riskLevel("MEDIUM").build();
        when(riskScoreRepository.findTopRisks(any())).thenReturn(List.of(detail1, detail2));

        List<RiskScoreDetail> results = riskScoreService.topRisks(10, "admin", true);

        assertEquals(2, results.size());
        assertEquals(90, results.get(0).getFinalRiskScore());
    }

    @Test
    void recalculateForTask_shouldDeleteAndRecreateInOneTransaction() {
        Long taskId = 1L;
        ScanTask task = ScanTask.builder().id(taskId).name("Test Task").build();
        when(scanTaskRepository.findById(taskId)).thenReturn(Optional.of(task));

        Asset asset1 = createAsset(1L, "10.0.0.1", "server-a", "[]");
        Asset asset2 = createAsset(2L, "10.0.0.2", "server-b", "[]");
        when(assetRepository.findByTaskId(taskId)).thenReturn(List.of(asset1, asset2));
        when(assetRepository.findById(1L)).thenReturn(Optional.of(asset1));
        when(assetRepository.findById(2L)).thenReturn(Optional.of(asset2));
        when(portRepository.findByAssetId(1L)).thenReturn(List.of(createPort(1L, 80, "HTTP", "open", true)));
        when(portRepository.findByAssetId(2L)).thenReturn(List.of(createPort(2L, 443, "HTTPS", "open", true)));
        when(assetVulnerabilityRepository.findByAssetIdWithCve(1L)).thenReturn(List.of());
        when(assetVulnerabilityRepository.findByAssetIdWithCve(2L)).thenReturn(List.of());
        when(riskScoreRepository.findByTaskIdAndAssetId(taskId, 1L)).thenReturn(Optional.empty());
        when(riskScoreRepository.findByTaskIdAndAssetId(taskId, 2L)).thenReturn(Optional.empty());
        when(riskScoreRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        // Call recalculateForTask — atomic delete + create in one transaction
        List<RiskScoreDetail> results = riskScoreService.recalculateForTask(taskId);

        verify(riskScoreRepository, times(1)).deleteByTaskId(taskId);
        assertNotNull(results);
        assertEquals(2, results.size(), "Should have exactly 2 risk score records for 2 assets");

        // Verify no duplicate assetId in results (each assetId appears exactly once)
        long uniqueAssetIds = results.stream().map(RiskScoreDetail::getAssetId).distinct().count();
        assertEquals(2, uniqueAssetIds, "No duplicate (taskId, assetId) pairs should exist");

        // Call again — simulate idempotent behavior (delete then recreate)
        when(riskScoreRepository.findByTaskIdAndAssetId(taskId, 1L)).thenReturn(Optional.empty());
        when(riskScoreRepository.findByTaskIdAndAssetId(taskId, 2L)).thenReturn(Optional.empty());
        List<RiskScoreDetail> results2 = riskScoreService.recalculateForTask(taskId);

        assertNotNull(results2);
        assertEquals(2, results2.size(), "Second call should also return 2 results");
        verify(riskScoreRepository, times(2)).deleteByTaskId(taskId);
    }
}
