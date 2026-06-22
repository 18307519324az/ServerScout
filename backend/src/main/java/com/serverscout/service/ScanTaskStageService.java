package com.serverscout.service;

import com.serverscout.entity.ScanTaskStage;
import com.serverscout.entity.enums.ScanStageCode;
import com.serverscout.entity.enums.ScanStageStatus;
import com.serverscout.repository.ScanTaskStageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScanTaskStageService {

    private final ScanTaskStageRepository stageRepository;

    @Transactional
    public void initStages(Long taskId) {
        List<ScanTaskStage> existing = stageRepository.findByTaskIdOrderByIdAsc(taskId);
        if (!existing.isEmpty()) {
            log.debug("Stages already exist for task {}, skipping init", taskId);
            return;
        }

        for (ScanStageCode code : ScanStageCode.values()) {
            ScanTaskStage stage = ScanTaskStage.builder()
                    .taskId(taskId)
                    .stageCode(code)
                    .stageName(code.getDisplayName())
                    .status(ScanStageStatus.PENDING)
                    .progress(0)
                    .build();
            stageRepository.save(stage);
        }
        log.info("Initialized {} stages for task {}", ScanStageCode.values().length, taskId);
    }

    @Transactional
    public void markRunning(Long taskId, ScanStageCode code, String summary) {
        ScanTaskStage stage = findOrCreate(taskId, code);
        stage.setStatus(ScanStageStatus.RUNNING);
        stage.setStartedAt(Instant.now());
        stage.setSummary(summary);
        stageRepository.save(stage);
        log.debug("Stage {} for task {} is now RUNNING: {}", code, taskId, summary);
    }

    @Transactional
    public void markProgress(Long taskId, ScanStageCode code, int progress, String summary) {
        ScanTaskStage stage = findOrCreate(taskId, code);
        if (stage.getStatus() == ScanStageStatus.PENDING) {
            stage.setStatus(ScanStageStatus.RUNNING);
            stage.setStartedAt(Instant.now());
        }
        stage.setProgress(progress);
        stage.setSummary(summary);
        stageRepository.save(stage);
    }

    @Transactional
    public void markSuccess(Long taskId, ScanStageCode code, String summary) {
        ScanTaskStage stage = findOrCreate(taskId, code);
        stage.setStatus(ScanStageStatus.SUCCESS);
        Instant now = Instant.now();
        stage.setFinishedAt(now);
        if (stage.getStartedAt() != null) {
            stage.setDurationMs(Duration.between(stage.getStartedAt(), now).toMillis());
        }
        stage.setProgress(100);
        stage.setSummary(summary);
        stageRepository.save(stage);
        log.info("Stage {} for task {} completed successfully", code, taskId);
    }

    @Transactional
    public void markFailed(Long taskId, ScanStageCode code, String errorMessage) {
        ScanTaskStage stage = findOrCreate(taskId, code);
        stage.setStatus(ScanStageStatus.FAILED);
        Instant now = Instant.now();
        stage.setFinishedAt(now);
        if (stage.getStartedAt() != null) {
            stage.setDurationMs(Duration.between(stage.getStartedAt(), now).toMillis());
        }
        stage.setErrorMessage(errorMessage);
        stageRepository.save(stage);
        log.warn("Stage {} for task {} failed: {}", code, taskId, errorMessage);
    }

    @Transactional
    public void markSkipped(Long taskId, ScanStageCode code, String summary) {
        ScanTaskStage stage = findOrCreate(taskId, code);
        stage.setStatus(ScanStageStatus.SKIPPED);
        stage.setFinishedAt(Instant.now());
        stage.setSummary(summary);
        stage.setProgress(100);
        stageRepository.save(stage);
        log.info("Stage {} for task {} skipped: {}", code, taskId, summary);
    }

    @Transactional(readOnly = true)
    public List<ScanTaskStage> listByTaskId(Long taskId) {
        return stageRepository.findByTaskIdOrderByIdAsc(taskId);
    }

    private ScanTaskStage findOrCreate(Long taskId, ScanStageCode code) {
        return stageRepository.findByTaskIdAndStageCode(taskId, code)
                .orElseGet(() -> {
                    ScanTaskStage stage = ScanTaskStage.builder()
                            .taskId(taskId)
                            .stageCode(code)
                            .stageName(code.getDisplayName())
                            .status(ScanStageStatus.PENDING)
                            .progress(0)
                            .build();
                    return stageRepository.save(stage);
                });
    }
}
