package com.serverscout.controller;

import com.serverscout.entity.RiskScoreDetail;
import com.serverscout.service.RiskScoreService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@WithMockUser(username = "admin", roles = "ADMIN")
class RiskScoreControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RiskScoreService riskScoreService;

    @Test
    void getByTask_shouldReturnRiskScores() throws Exception {
        RiskScoreDetail detail = RiskScoreDetail.builder()
                .id(1L).taskId(1L).assetId(1L)
                .assetIp("10.0.0.1").finalRiskScore(75).riskLevel("HIGH")
                .assetExposureScore(30).vulnerabilitySeverityScore(85)
                .serviceRiskScore(75).exploitabilityScore(60)
                .businessImportanceScore(60).remediationDeduction(10)
                .riskReason("高风险原因").repairSuggestion("修复建议")
                .build();

        when(riskScoreService.listByTaskId(1L)).thenReturn(List.of(detail));

        mockMvc.perform(get("/api/v1/risk-scores/task/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].assetIp").value("10.0.0.1"))
                .andExpect(jsonPath("$.data[0].finalRiskScore").value(75))
                .andExpect(jsonPath("$.data[0].riskLevel").value("HIGH"))
                .andExpect(jsonPath("$.data[0].assetExposureScore").value(30));
    }

    @Test
    void getByTask_withNoData_shouldReturnEmptyArray() throws Exception {
        when(riskScoreService.listByTaskId(999L)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/risk-scores/task/999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void getByAsset_shouldReturnRiskScores() throws Exception {
        RiskScoreDetail detail = RiskScoreDetail.builder()
                .id(1L).assetId(1L).taskId(1L)
                .assetIp("10.0.0.1").finalRiskScore(45).riskLevel("MEDIUM")
                .build();

        when(riskScoreService.listByAssetId(1L)).thenReturn(List.of(detail));

        mockMvc.perform(get("/api/v1/risk-scores/asset/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].finalRiskScore").value(45))
                .andExpect(jsonPath("$.data[0].riskLevel").value("MEDIUM"));
    }

    @Test
    void getTopRisks_shouldReturnOrderedResults() throws Exception {
        RiskScoreDetail detail1 = RiskScoreDetail.builder()
                .id(1L).assetIp("10.0.0.1").finalRiskScore(95).riskLevel("CRITICAL")
                .build();
        RiskScoreDetail detail2 = RiskScoreDetail.builder()
                .id(2L).assetIp("10.0.0.2").finalRiskScore(50).riskLevel("MEDIUM")
                .build();

        when(riskScoreService.topRisks(eq(5), anyString(), eq(true))).thenReturn(List.of(detail1, detail2));

        mockMvc.perform(get("/api/v1/risk-scores/top?limit=5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].finalRiskScore").value(95))
                .andExpect(jsonPath("$.data[0].riskLevel").value("CRITICAL"))
                .andExpect(jsonPath("$.data[1].finalRiskScore").value(50));
    }

    @Test
    void getTopRisks_withDefaultLimit_shouldUse10() throws Exception {
        when(riskScoreService.topRisks(eq(10), anyString(), eq(true))).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/risk-scores/top"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void recalculate_shouldDeleteAndRecreate() throws Exception {
        when(riskScoreService.recalculateForTask(1L)).thenReturn(List.of(
                RiskScoreDetail.builder().id(1L).finalRiskScore(75).build()
        ));

        mockMvc.perform(post("/api/v1/risk-scores/task/1/recalculate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.taskId").value(1))
                .andExpect(jsonPath("$.data.scored").value(1));

        verify(riskScoreService, times(1)).recalculateForTask(1L);
    }

    @Test
    void recalculate_withNoAssets_shouldReturnZeroScored() throws Exception {
        when(riskScoreService.recalculateForTask(999L)).thenReturn(List.of());

        mockMvc.perform(post("/api/v1/risk-scores/task/999/recalculate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.taskId").value(999))
                .andExpect(jsonPath("$.data.scored").value(0));

        verify(riskScoreService, times(1)).recalculateForTask(999L);
    }

    @Test
    @WithMockUser(username = "user", roles = "USER")
    void getTopRisks_asNonAdmin_shouldFilterByUser() throws Exception {
        when(riskScoreService.topRisks(eq(10), eq("user"), eq(false))).thenReturn(List.of(
                RiskScoreDetail.builder().id(1L).finalRiskScore(60).riskLevel("HIGH").build()
        ));

        mockMvc.perform(get("/api/v1/risk-scores/top"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].finalRiskScore").value(60));

        verify(riskScoreService, times(1)).topRisks(10, "user", false);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void endpoints_shouldRequireAuthentication() throws Exception {
        // All endpoints should be accessible with auth
        mockMvc.perform(get("/api/v1/risk-scores/task/1"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/risk-scores/asset/1"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/risk-scores/top"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/risk-scores/task/1/recalculate"))
                .andExpect(status().isOk());
    }
}
