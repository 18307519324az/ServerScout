package com.serverscout.service;

import com.serverscout.entity.ScanTaskStage;
import com.serverscout.entity.enums.ScanStageCode;
import com.serverscout.entity.enums.ScanStageStatus;
import com.serverscout.repository.ScanTaskRepository;
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
    private final ScanTaskRepository scanTaskRepository;

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

    @Transactional
    public void skipIfPending(Long taskId, ScanStageCode code, String summary) {
        stageRepository.findByTaskIdAndStageCode(taskId, code).ifPresent(stage -> {
            if (stage.getStatus() != ScanStageStatus.PENDING) return;
            stage.setStatus(ScanStageStatus.SKIPPED);
            stage.setFinishedAt(Instant.now());
            stage.setProgress(100);
            stage.setSummary(summary);
            stageRepository.save(stage);
            log.info("Stage {} for task {} skipped: {}", code, taskId, summary);
        });
    }

    @Transactional
    public void finalizeUnfinishedStages(Long taskId) {
        finalizeUnfinishedStages(taskId, null);
    }

    @Transactional
    public void finalizeUnfinishedStages(Long taskId, String reason) {
        List<ScanTaskStage> stages = stageRepository.findByTaskIdOrderByIdAsc(taskId);
        if (stages.isEmpty()) return;

        Instant now = Instant.now();
        int updated = 0;
        for (ScanTaskStage stage : stages) {
            if (stage.getStatus() == ScanStageStatus.PENDING) {
                stage.setStatus(ScanStageStatus.SKIPPED);
                stage.setFinishedAt(now);
                stage.setProgress(100);
                stage.setSummary(reason != null ? reason : "任务已结束，阶段未执行");
                updated++;
            } else if (stage.getStatus() == ScanStageStatus.RUNNING) {
                stage.setStatus(ScanStageStatus.FAILED);
                stage.setFinishedAt(now);
                if (stage.getStartedAt() != null) {
                    stage.setDurationMs(Duration.between(stage.getStartedAt(), now).toMillis());
                }
                stage.setErrorMessage(reason != null ? reason : "任务已结束，阶段未完成");
                updated++;
            }
        }
        if (updated > 0) {
            stageRepository.saveAll(stages);
        }
        log.info("Finalized {} unfinished stages for task {}", updated, taskId);
    }

    @Transactional
    public int repairCompletedTaskPendingStages() {
        List<com.serverscout.entity.ScanTask> terminalTasks = scanTaskRepository.findByStatusIn(
                java.util.List.of("completed", "failed", "cancelled"));
        int repaired = 0;
        for (com.serverscout.entity.ScanTask task : terminalTasks) {
            List<ScanTaskStage> stages = stageRepository.findByTaskIdOrderByIdAsc(task.getId());
            boolean hasUnfinished = stages.stream().anyMatch(s ->
                    s.getStatus() == ScanStageStatus.PENDING || s.getStatus() == ScanStageStatus.RUNNING);
            if (hasUnfinished) {
                finalizeUnfinishedStages(task.getId(), "历史任务修复：任务已完成，未执行阶段标记为已跳过");
                repaired++;
            }
        }
        log.info("Repair completed for {} tasks", repaired);
        return repaired;
    }

    /**
     * Check if a task status string represents a terminal state (completed/failed/cancelled).
     */
    private boolean isTerminalTaskStatus(String taskStatus) {
        if (taskStatus == null) return false;
        return "completed".equalsIgnoreCase(taskStatus)
                || "failed".equalsIgnoreCase(taskStatus)
                || "cancelled".equalsIgnoreCase(taskStatus)
                || "canceled".equalsIgnoreCase(taskStatus);
    }

    /**
     * Ensure no stages are left in PENDING or RUNNING state for a task that has
     * reached a terminal status. This is a safe-guard that checks the task status
     * before acting — no-op if the status is not terminal.
     */
    @Transactional
    public void ensureNoPendingStagesForTerminalTask(Long taskId, String taskStatus) {
        if (!isTerminalTaskStatus(taskStatus)) {
            log.debug("Task {} status is '{}', not terminal, skipping finalize", taskId, taskStatus);
            return;
        }
        finalizeUnfinishedStages(taskId, "任务已进入终态，未执行阶段自动标记为已跳过");
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
