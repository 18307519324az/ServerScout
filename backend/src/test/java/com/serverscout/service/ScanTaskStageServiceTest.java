package com.serverscout.service;

import com.serverscout.entity.ScanTaskStage;
import com.serverscout.entity.enums.ScanStageCode;
import com.serverscout.entity.enums.ScanStageStatus;
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
}
