package com.serverscout.service;

import com.serverscout.entity.ScanTask;
import com.serverscout.entity.ScanTaskStage;
import com.serverscout.entity.enums.ScanStageCode;
import com.serverscout.entity.enums.ScanStageStatus;
import com.serverscout.repository.ScanTaskRepository;
import com.serverscout.repository.ScanTaskStageRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ScanTaskStageServiceTest {

    @Mock
    private ScanTaskStageRepository stageRepository;

    @Mock
    private ScanTaskRepository scanTaskRepository;

    @InjectMocks
    private ScanTaskStageService scanTaskStageService;

    @Captor
    private ArgumentCaptor<ScanTaskStage> stageCaptor;

    @Test
    void shouldInitStagesForNewTask() {
        when(stageRepository.findByTaskIdOrderByIdAsc(1L)).thenReturn(List.of());
        when(stageRepository.save(any(ScanTaskStage.class))).thenAnswer(inv -> inv.getArgument(0));

        scanTaskStageService.initStages(1L);

        verify(stageRepository, times(ScanStageCode.values().length)).save(stageCaptor.capture());
        List<ScanTaskStage> saved = stageCaptor.getAllValues();
        assertEquals(ScanStageCode.values().length, saved.size());
        assertTrue(saved.stream().allMatch(s -> s.getStatus() == ScanStageStatus.PENDING));
        assertTrue(saved.stream().allMatch(s -> s.getProgress() == 0));
    }

    @Test
    void shouldSkipInitIfStagesExist() {
        when(stageRepository.findByTaskIdOrderByIdAsc(1L)).thenReturn(List.of(new ScanTaskStage()));

        scanTaskStageService.initStages(1L);

        verify(stageRepository, never()).save(any());
    }

    @Test
    void shouldMarkStageRunning() {
        ScanTaskStage stage = ScanTaskStage.builder()
                .taskId(1L).stageCode(ScanStageCode.PORT_SCAN).stageName("端口扫描")
                .status(ScanStageStatus.PENDING).progress(0).build();
        when(stageRepository.findByTaskIdAndStageCode(1L, ScanStageCode.PORT_SCAN))
                .thenReturn(Optional.of(stage));

        scanTaskStageService.markRunning(1L, ScanStageCode.PORT_SCAN, "Scanning ports...");

        assertEquals(ScanStageStatus.RUNNING, stage.getStatus());
        assertNotNull(stage.getStartedAt());
        assertEquals("Scanning ports...", stage.getSummary());
    }

    @Test
    void shouldMarkStageProgress() {
        ScanTaskStage stage = ScanTaskStage.builder()
                .taskId(1L).stageCode(ScanStageCode.PORT_SCAN).stageName("端口扫描")
                .status(ScanStageStatus.RUNNING).progress(30).build();
        when(stageRepository.findByTaskIdAndStageCode(1L, ScanStageCode.PORT_SCAN))
                .thenReturn(Optional.of(stage));

        scanTaskStageService.markProgress(1L, ScanStageCode.PORT_SCAN, 50, "Halfway done");

        assertEquals(50, stage.getProgress());
        assertEquals("Halfway done", stage.getSummary());
    }

    @Test
    void shouldMarkStageSuccess() {
        ScanTaskStage stage = ScanTaskStage.builder()
                .taskId(1L).stageCode(ScanStageCode.TARGET_VALIDATION).stageName("目标校验")
                .status(ScanStageStatus.RUNNING).progress(60).build();
        stage.setStartedAt(java.time.Instant.now().minusSeconds(5));
        when(stageRepository.findByTaskIdAndStageCode(1L, ScanStageCode.TARGET_VALIDATION))
                .thenReturn(Optional.of(stage));

        scanTaskStageService.markSuccess(1L, ScanStageCode.TARGET_VALIDATION, "All targets valid");

        assertEquals(ScanStageStatus.SUCCESS, stage.getStatus());
        assertEquals(100, stage.getProgress());
        assertNotNull(stage.getFinishedAt());
        assertNotNull(stage.getDurationMs());
        assertTrue(stage.getDurationMs() > 0);
    }

    @Test
    void shouldMarkStageFailed() {
        ScanTaskStage stage = ScanTaskStage.builder()
                .taskId(1L).stageCode(ScanStageCode.PORT_SCAN).stageName("端口扫描")
                .status(ScanStageStatus.RUNNING).progress(30).build();
        when(stageRepository.findByTaskIdAndStageCode(1L, ScanStageCode.PORT_SCAN))
                .thenReturn(Optional.of(stage));

        scanTaskStageService.markFailed(1L, ScanStageCode.PORT_SCAN, "Connection timeout");

        assertEquals(ScanStageStatus.FAILED, stage.getStatus());
        assertEquals("Connection timeout", stage.getErrorMessage());
        assertNotNull(stage.getFinishedAt());
    }

    @Test
    void shouldMarkStageSkipped() {
        ScanTaskStage stage = ScanTaskStage.builder()
                .taskId(1L).stageCode(ScanStageCode.WEB_PROBE).stageName("Web 探测")
                .status(ScanStageStatus.PENDING).progress(0).build();
        when(stageRepository.findByTaskIdAndStageCode(1L, ScanStageCode.WEB_PROBE))
                .thenReturn(Optional.of(stage));

        scanTaskStageService.markSkipped(1L, ScanStageCode.WEB_PROBE, "Skipped in quick scan");

        assertEquals(ScanStageStatus.SKIPPED, stage.getStatus());
        assertEquals(100, stage.getProgress());
        assertNotNull(stage.getFinishedAt());
    }

    @Test
    void shouldListStagesByTaskId() {
        when(stageRepository.findByTaskIdOrderByIdAsc(1L)).thenReturn(List.of(
                ScanTaskStage.builder().id(1L).taskId(1L).stageCode(ScanStageCode.TARGET_VALIDATION).build(),
                ScanTaskStage.builder().id(2L).taskId(1L).stageCode(ScanStageCode.PORT_SCAN).build()
        ));

        List<ScanTaskStage> result = scanTaskStageService.listByTaskId(1L);

        assertEquals(2, result.size());
    }

    @Test
    void shouldFindOrCreateStageWhenNotFound() {
        when(stageRepository.findByTaskIdAndStageCode(1L, ScanStageCode.PORT_SCAN))
                .thenReturn(Optional.empty());
        when(stageRepository.save(any(ScanTaskStage.class))).thenAnswer(inv -> inv.getArgument(0));

        scanTaskStageService.markRunning(1L, ScanStageCode.PORT_SCAN, "Auto-created");

        verify(stageRepository, times(2)).save(stageCaptor.capture());
        List<ScanTaskStage> allSaved = stageCaptor.getAllValues();
        assertEquals(ScanStageCode.PORT_SCAN, allSaved.get(0).getStageCode());
        assertEquals("端口扫描", allSaved.get(0).getStageName());
        assertEquals(ScanStageStatus.RUNNING, allSaved.get(1).getStatus());
        assertEquals("Auto-created", allSaved.get(1).getSummary());
    }

    // --- skipIfPending tests ---

    @Test
    void shouldSkipOnlyWhenPending() {
        // PENDING stage should be skipped
        ScanTaskStage pending = ScanTaskStage.builder()
                .taskId(1L).stageCode(ScanStageCode.WEB_PROBE)
                .status(ScanStageStatus.PENDING).progress(0).build();
        when(stageRepository.findByTaskIdAndStageCode(1L, ScanStageCode.WEB_PROBE))
                .thenReturn(Optional.of(pending));

        scanTaskStageService.skipIfPending(1L, ScanStageCode.WEB_PROBE, "Skipped");

        assertEquals(ScanStageStatus.SKIPPED, pending.getStatus());
        assertEquals(100, pending.getProgress());
        assertNotNull(pending.getFinishedAt());
        verify(stageRepository).save(pending);
    }

    @Test
    void shouldNotSkipWhenNotPending() {
        for (ScanStageStatus status : List.of(
                ScanStageStatus.RUNNING, ScanStageStatus.SUCCESS,
                ScanStageStatus.FAILED, ScanStageStatus.SKIPPED)) {
            ScanTaskStage stage = ScanTaskStage.builder()
                    .taskId(1L).stageCode(ScanStageCode.WEB_PROBE)
                    .status(status).progress(50).build();
            when(stageRepository.findByTaskIdAndStageCode(1L, ScanStageCode.WEB_PROBE))
                    .thenReturn(Optional.of(stage));

            scanTaskStageService.skipIfPending(1L, ScanStageCode.WEB_PROBE, "Should not change");

            assertEquals(status, stage.getStatus(), "Stage " + status + " should not be changed");
            assertEquals(50, stage.getProgress());
        }
    }

    // --- finalizeUnfinishedStages tests ---

    @Test
    void shouldFinalizePendingToSkipped() {
        ScanTaskStage stage = ScanTaskStage.builder()
                .taskId(1L).stageCode(ScanStageCode.NOTIFICATION)
                .status(ScanStageStatus.PENDING).progress(0).build();
        when(stageRepository.findByTaskIdOrderByIdAsc(1L)).thenReturn(List.of(stage));

        scanTaskStageService.finalizeUnfinishedStages(1L);

        assertEquals(ScanStageStatus.SKIPPED, stage.getStatus());
        assertEquals(100, stage.getProgress());
        assertNotNull(stage.getFinishedAt());
        assertEquals("任务已结束，阶段未执行", stage.getSummary());
        verify(stageRepository).saveAll(anyList());
    }

    @Test
    void shouldFinalizeRunningToFailed() {
        ScanTaskStage stage = ScanTaskStage.builder()
                .taskId(1L).stageCode(ScanStageCode.VULNERABILITY_SCAN)
                .status(ScanStageStatus.RUNNING).progress(50).build();
        stage.setStartedAt(java.time.Instant.now().minusSeconds(10));
        when(stageRepository.findByTaskIdOrderByIdAsc(1L)).thenReturn(List.of(stage));

        scanTaskStageService.finalizeUnfinishedStages(1L, "扫描执行失败");

        assertEquals(ScanStageStatus.FAILED, stage.getStatus());
        assertNotNull(stage.getFinishedAt());
        assertNotNull(stage.getDurationMs());
        assertTrue(stage.getDurationMs() > 0);
        assertEquals("扫描执行失败", stage.getErrorMessage());
        verify(stageRepository).saveAll(anyList());
    }

    @Test
    void shouldNotOverwriteTerminalStates() {
        ScanTaskStage success = ScanTaskStage.builder()
                .taskId(1L).stageCode(ScanStageCode.PORT_SCAN)
                .status(ScanStageStatus.SUCCESS).progress(100).build();
        ScanTaskStage failed = ScanTaskStage.builder()
                .taskId(1L).stageCode(ScanStageCode.RISK_ANALYSIS)
                .status(ScanStageStatus.FAILED).progress(100).build();
        ScanTaskStage skipped = ScanTaskStage.builder()
                .taskId(1L).stageCode(ScanStageCode.WEB_PROBE)
                .status(ScanStageStatus.SKIPPED).progress(100).build();
        when(stageRepository.findByTaskIdOrderByIdAsc(1L)).thenReturn(List.of(success, failed, skipped));

        scanTaskStageService.finalizeUnfinishedStages(1L);

        assertEquals(ScanStageStatus.SUCCESS, success.getStatus());
        assertEquals(ScanStageStatus.FAILED, failed.getStatus());
        assertEquals(ScanStageStatus.SKIPPED, skipped.getStatus());
        // saveAll should not be called since nothing changed
        verify(stageRepository, never()).saveAll(anyList());
    }

    // --- repairCompletedTaskPendingStages tests ---

    @Test
    void shouldRepairCompletedTasksWithPendingStages() {
        ScanTask task = ScanTask.builder().id(1L).status("completed").build();
        when(scanTaskRepository.findByStatusIn(anyList())).thenReturn(List.of(task));

        ScanTaskStage pending = ScanTaskStage.builder()
                .taskId(1L).stageCode(ScanStageCode.WEB_PROBE)
                .status(ScanStageStatus.PENDING).progress(0).build();
        when(stageRepository.findByTaskIdOrderByIdAsc(1L)).thenReturn(List.of(pending));

        int repaired = scanTaskStageService.repairCompletedTaskPendingStages();

        assertEquals(1, repaired);
        assertEquals(ScanStageStatus.SKIPPED, pending.getStatus());
        verify(stageRepository).saveAll(anyList());
    }

    // --- ensureNoPendingStagesForTerminalTask tests ---

    @Test
    void shouldFinalizeWhenTaskCompleted() {
        ScanTaskStage pending = ScanTaskStage.builder()
                .taskId(1L).stageCode(ScanStageCode.NOTIFICATION)
                .status(ScanStageStatus.PENDING).progress(0).build();
        when(stageRepository.findByTaskIdOrderByIdAsc(1L)).thenReturn(List.of(pending));

        scanTaskStageService.ensureNoPendingStagesForTerminalTask(1L, "completed");

        assertEquals(ScanStageStatus.SKIPPED, pending.getStatus());
        verify(stageRepository).saveAll(anyList());
    }

    @Test
    void shouldFinalizeWhenTaskFailed() {
        ScanTaskStage pending = ScanTaskStage.builder()
                .taskId(1L).stageCode(ScanStageCode.NOTIFICATION)
                .status(ScanStageStatus.PENDING).progress(0).build();
        when(stageRepository.findByTaskIdOrderByIdAsc(1L)).thenReturn(List.of(pending));

        scanTaskStageService.ensureNoPendingStagesForTerminalTask(1L, "failed");

        assertEquals(ScanStageStatus.SKIPPED, pending.getStatus());
        verify(stageRepository).saveAll(anyList());
    }

    @Test
    void shouldFinalizeWhenTaskCancelled() {
        ScanTaskStage pending = ScanTaskStage.builder()
                .taskId(1L).stageCode(ScanStageCode.NOTIFICATION)
                .status(ScanStageStatus.PENDING).progress(0).build();
        when(stageRepository.findByTaskIdOrderByIdAsc(1L)).thenReturn(List.of(pending));

        scanTaskStageService.ensureNoPendingStagesForTerminalTask(1L, "cancelled");

        assertEquals(ScanStageStatus.SKIPPED, pending.getStatus());
        verify(stageRepository).saveAll(anyList());
    }

    @Test
    void shouldNotFinalizeWhenTaskRunning() {
        scanTaskStageService.ensureNoPendingStagesForTerminalTask(1L, "running");

        verify(stageRepository, never()).findByTaskIdOrderByIdAsc(any());
        verify(stageRepository, never()).saveAll(any());
    }

    @Test
    void shouldNotFinalizeWhenTaskPending() {
        scanTaskStageService.ensureNoPendingStagesForTerminalTask(1L, "pending");

        verify(stageRepository, never()).findByTaskIdOrderByIdAsc(any());
        verify(stageRepository, never()).saveAll(any());
    }

    @Test
    void shouldNotRepairFullyFinishedTasks() {
        ScanTask task = ScanTask.builder().id(1L).status("completed").build();
        when(scanTaskRepository.findByStatusIn(anyList())).thenReturn(List.of(task));

        ScanTaskStage success = ScanTaskStage.builder()
                .taskId(1L).stageCode(ScanStageCode.PORT_SCAN)
                .status(ScanStageStatus.SUCCESS).progress(100).build();
        when(stageRepository.findByTaskIdOrderByIdAsc(1L)).thenReturn(List.of(success));

        int repaired = scanTaskStageService.repairCompletedTaskPendingStages();

        assertEquals(0, repaired);
        verify(stageRepository, never()).saveAll(anyList());
    }
}
