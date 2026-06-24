package com.serverscout.controller;

import com.serverscout.dto.ScanTaskResponse;
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

        ScanTaskResponse task = ScanTaskResponse.builder().id(1L).status("running").build();
        when(scanService.getTaskDetail(1L)).thenReturn(task);
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
        ScanTaskResponse task = ScanTaskResponse.builder().id(2L).status("running").build();
        when(scanService.getTaskDetail(2L)).thenReturn(task);
        when(scanTaskStageService.listByTaskId(2L)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/scan-tasks/2/stages"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void shouldFinalizeUnfinishedStagesForCompletedTask() throws Exception {
        ScanTaskResponse task = ScanTaskResponse.builder().id(3L).status("completed").build();
        when(scanService.getTaskDetail(3L)).thenReturn(task);

        ScanTaskStage skipped = ScanTaskStage.builder()
                .id(1L).taskId(3L)
                .stageCode(ScanStageCode.NOTIFICATION)
                .stageName("通知回调")
                .status(ScanStageStatus.SKIPPED)
                .progress(100).build();
        when(scanTaskStageService.listByTaskId(3L)).thenReturn(List.of(skipped));

        mockMvc.perform(get("/api/v1/scan-tasks/3/stages"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].status").value("SKIPPED"));

        // verify ensureNoPendingStagesForTerminalTask was called
        verify(scanTaskStageService).ensureNoPendingStagesForTerminalTask(3L, "completed");
    }

    @Test
    void shouldNotFinalizeForRunningTask() throws Exception {
        ScanTaskResponse task = ScanTaskResponse.builder().id(4L).status("running").build();
        when(scanService.getTaskDetail(4L)).thenReturn(task);

        ScanTaskStage running = ScanTaskStage.builder()
                .id(1L).taskId(4L)
                .stageCode(ScanStageCode.PORT_SCAN)
                .stageName("端口扫描")
                .status(ScanStageStatus.RUNNING)
                .progress(50).build();
        when(scanTaskStageService.listByTaskId(4L)).thenReturn(List.of(running));

        mockMvc.perform(get("/api/v1/scan-tasks/4/stages"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].status").value("RUNNING"));

        // verify ensureNoPendingStagesForTerminalTask was called (should be no-op)
        verify(scanTaskStageService).ensureNoPendingStagesForTerminalTask(4L, "running");
    }
}
