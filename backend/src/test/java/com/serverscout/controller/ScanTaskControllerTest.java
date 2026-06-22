package com.serverscout.controller;

import com.serverscout.entity.ScanTaskStage;
import com.serverscout.entity.enums.ScanStageCode;
import com.serverscout.entity.enums.ScanStageStatus;
import com.serverscout.exception.ResourceNotFoundException;
import com.serverscout.service.ScanService;
import com.serverscout.service.ScanTaskStageService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@WithMockUser(username = "admin", roles = "ADMIN")
class ScanTaskControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ScanService scanService;

    @MockBean
    private ScanTaskStageService scanTaskStageService;

    @Test
    void shouldReturnStagesForExistingTask() throws Exception {
        ScanTaskStage stage = ScanTaskStage.builder()
                .id(1L).taskId(1L)
                .stageCode(ScanStageCode.TARGET_VALIDATION)
                .stageName("目标校验")
                .status(ScanStageStatus.SUCCESS)
                .progress(100).build();

        when(scanService.getTaskDetail(1L)).thenReturn(null);
        when(scanTaskStageService.listByTaskId(1L)).thenReturn(List.of(stage));

        mockMvc.perform(get("/api/v1/scan-tasks/1/stages"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].stageCode").value("TARGET_VALIDATION"))
                .andExpect(jsonPath("$.data[0].stageName").value("目标校验"))
                .andExpect(jsonPath("$.data[0].status").value("SUCCESS"));
    }

    @Test
    void shouldReturn404WhenTaskNotFound() throws Exception {
        when(scanService.getTaskDetail(999L)).thenThrow(new ResourceNotFoundException("Task not found"));

        mockMvc.perform(get("/api/v1/scan-tasks/999/stages"))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldReturnEmptyListWhenNoStages() throws Exception {
        when(scanService.getTaskDetail(2L)).thenReturn(null);
        when(scanTaskStageService.listByTaskId(2L)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/scan-tasks/2/stages"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(0));
    }
}
