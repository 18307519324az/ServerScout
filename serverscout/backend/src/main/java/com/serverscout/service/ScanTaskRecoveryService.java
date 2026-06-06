package com.serverscout.service;

import com.serverscout.entity.ScanTask;
import com.serverscout.repository.ScanTaskRepository;
import com.serverscout.service.scan.TargetConcurrencyLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScanTaskRecoveryService {

    private final ScanTaskRepository scanTaskRepository;
    private final ScanExecutionService scanExecutionService;
    private final TargetConcurrencyLimiter targetConcurrencyLimiter;

    @EventListener(ApplicationReadyEvent.class)
    public void recoverInterruptedTasks() {
        List<ScanTask> tasks = scanTaskRepository.findByStatusIn(List.of("pending", "running"));
        for (ScanTask task : tasks) {
            if ("running".equals(task.getStatus())) {
                targetConcurrencyLimiter.forceReleaseByTarget(task.getTargetRange());
                task.setStatus("pending");
                task.setProgress(1);
                task.setErrorMessage("Recovered after application restart");
                scanTaskRepository.save(task);
            }
            log.info("Re-queueing scan task {} after application startup", task.getId());
            scanExecutionService.executeScan(task.getId());
        }
    }
}
