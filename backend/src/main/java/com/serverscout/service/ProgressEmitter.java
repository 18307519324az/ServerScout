package com.serverscout.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
@Service
public class ProgressEmitter {

    private static final long EMITTER_TIMEOUT_MS = 30 * 60 * 1000L; // 30 minutes max
    private static final long STALE_TASK_MS = 60 * 60 * 1000L; // 1 hour cleanup threshold

    private final Map<Long, List<TimedEmitter>> taskEmitters = new ConcurrentHashMap<>();

    private static class TimedEmitter {
        final SseEmitter emitter;
        final long createdAt;
        final Object lock = new Object();
        volatile boolean closed = false;

        TimedEmitter(SseEmitter emitter) {
            this.emitter = emitter;
            this.createdAt = System.currentTimeMillis();
        }

        boolean isOlderThan(long ms) {
            return System.currentTimeMillis() - createdAt > ms;
        }
    }

    public SseEmitter createEmitter(Long taskId) {
        SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT_MS);
        TimedEmitter te = new TimedEmitter(emitter);

        taskEmitters.computeIfAbsent(taskId, k -> new CopyOnWriteArrayList<>()).add(te);

        emitter.onCompletion(() -> removeEmitter(taskId, emitter));
        emitter.onTimeout(() -> {
            log.debug("SSE emitter timed out for task {}", taskId);
            removeEmitter(taskId, emitter);
        });
        emitter.onError(e -> {
            log.debug("SSE emitter error for task {}: {}", taskId, e.getMessage());
            removeEmitter(taskId, emitter);
        });

        return emitter;
    }

    public void sendProgress(Long taskId, int progress, String currentTarget, int assetsFound) {
        List<TimedEmitter> emitters = getActiveEmitters(taskId);
        if (emitters.isEmpty()) return;

        Map<String, Object> data = new HashMap<>();
        data.put("progress", progress);
        data.put("currentTarget", currentTarget);
        data.put("assetsFound", assetsFound);
        data.put("timestamp", System.currentTimeMillis());

        sendEvent(taskId, emitters, "progress", data);
    }

    public void sendDiscoveredAsset(Long taskId, String ip, String hostname,
                                     String osInfo, int portCount, List<Map<String, Object>> ports) {
        List<TimedEmitter> emitters = getActiveEmitters(taskId);
        if (emitters.isEmpty()) return;

        Map<String, Object> data = new HashMap<>();
        data.put("type", "asset");
        data.put("ip", ip);
        data.put("hostname", hostname != null ? hostname : "");
        data.put("osInfo", osInfo != null ? osInfo : "");
        data.put("portCount", portCount);
        data.put("ports", ports != null ? ports : List.of());
        data.put("timestamp", System.currentTimeMillis());

        sendEvent(taskId, emitters, "discovery", data);
    }

    public void sendDiscoveredVuln(Long taskId, String severity, String cveId,
                                    String name, String url, String affected) {
        List<TimedEmitter> emitters = getActiveEmitters(taskId);
        if (emitters.isEmpty()) return;

        Map<String, Object> data = new HashMap<>();
        data.put("type", "vuln");
        data.put("severity", severity);
        data.put("cveId", cveId);
        data.put("name", name);
        data.put("url", url);
        data.put("affected", affected);
        data.put("timestamp", System.currentTimeMillis());

        sendEvent(taskId, emitters, "discovery", data);
    }

    public void sendDiscoveredFingerprint(Long taskId, String ip, int port,
                                           String server, String framework, String cms, String title) {
        List<TimedEmitter> emitters = getActiveEmitters(taskId);
        if (emitters.isEmpty()) return;

        Map<String, Object> data = new HashMap<>();
        data.put("type", "fingerprint");
        data.put("ip", ip);
        data.put("port", port);
        data.put("server", server != null ? server : "");
        data.put("framework", framework != null ? framework : "");
        data.put("cms", cms != null ? cms : "");
        data.put("title", title != null ? title : "");
        data.put("timestamp", System.currentTimeMillis());

        sendEvent(taskId, emitters, "discovery", data);
    }

    public void sendCompleted(Long taskId) {
        List<TimedEmitter> emitters = getActiveEmitters(taskId);
        sendEvent(taskId, emitters, "completed", Map.of("taskId", taskId));
        // Complete all emitters and remove the entry
        for (TimedEmitter te : emitters) {
            safeComplete(te);
        }
        taskEmitters.remove(taskId);
    }

    public void sendError(Long taskId, String message) {
        List<TimedEmitter> emitters = getActiveEmitters(taskId);
        sendEvent(taskId, emitters, "error", Map.of("message", message));
        for (TimedEmitter te : emitters) {
            safeComplete(te);
        }
        taskEmitters.remove(taskId);
    }

    /** Explicit cleanup — call when a scan is cancelled */
    public void cleanup(Long taskId) {
        List<TimedEmitter> emitters = taskEmitters.remove(taskId);
        if (emitters != null) {
            for (TimedEmitter te : emitters) {
                safeComplete(te);
            }
            log.debug("Cleaned up {} emitters for task {}", emitters.size(), taskId);
        }
    }

    /**
     * Scheduled eviction of stale entries. Runs every 10 minutes.
     * Completes and removes emitter entries older than the stale threshold.
     * Normal cleanup happens via onCompletion/onTimeout/onError callbacks;
     * this is a safety net for edge cases where those don't fire.
     */
    @Scheduled(fixedRate = 600_000)
    public void evictStaleEntries() {
        int removed = 0;
        for (Iterator<Map.Entry<Long, List<TimedEmitter>>> iter = taskEmitters.entrySet().iterator(); iter.hasNext();) {
            Map.Entry<Long, List<TimedEmitter>> entry = iter.next();
            List<TimedEmitter> list = entry.getValue();
            if (list == null || list.isEmpty()) {
                iter.remove();
                removed++;
            } else if (list.stream().allMatch(te -> te.isOlderThan(STALE_TASK_MS))) {
                for (TimedEmitter te : list) {
                    safeComplete(te);
                }
                iter.remove();
                removed++;
                log.info("SSE eviction: cleaned up stale task {}", entry.getKey());
            }
        }
        if (removed > 0) {
            log.info("SSE eviction: removed {} stale task entries", removed);
        }
    }

    private List<TimedEmitter> getActiveEmitters(Long taskId) {
        List<TimedEmitter> emitters = taskEmitters.get(taskId);
        return emitters != null ? emitters : List.of();
    }

    private void sendEvent(Long taskId, List<TimedEmitter> emitters, String name, Object data) {
        for (TimedEmitter te : emitters) {
            try {
                if (te.closed) continue;
                synchronized (te.lock) {
                    if (te.closed) continue;
                    te.emitter.send(SseEmitter.event().name(name).data(data));
                }
            } catch (Exception e) {
                safeComplete(te);
                removeEmitter(taskId, te.emitter);
            }
        }
    }

    private void removeEmitter(Long taskId, SseEmitter emitter) {
        List<TimedEmitter> emitters = taskEmitters.get(taskId);
        if (emitters != null) {
            emitters.removeIf(te -> te.emitter == emitter);
            // Clean up empty lists to prevent memory leak
            if (emitters.isEmpty()) {
                taskEmitters.remove(taskId);
            }
        }
    }

    private void safeComplete(TimedEmitter te) {
        te.closed = true;
        try { te.emitter.complete(); } catch (Exception ignored) {}
    }
}
