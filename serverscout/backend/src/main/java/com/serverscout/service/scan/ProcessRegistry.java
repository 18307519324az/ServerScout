package com.serverscout.service.scan;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class ProcessRegistry {

    private final ConcurrentHashMap<Long, List<Process>> taskProcesses = new ConcurrentHashMap<>();

    public void register(Long taskId, Process process) {
        taskProcesses.computeIfAbsent(taskId, k -> new ArrayList<>()).add(process);
    }

    public void destroyAll(Long taskId) {
        List<Process> processes = taskProcesses.remove(taskId);
        if (processes == null || processes.isEmpty()) return;

        for (Process process : processes) {
            if (process.isAlive()) {
                process.destroyForcibly();
                log.info("Force-killed process for task {}", taskId);
            }
        }
    }

    public void cleanup(Long taskId) {
        taskProcesses.remove(taskId);
    }
}
