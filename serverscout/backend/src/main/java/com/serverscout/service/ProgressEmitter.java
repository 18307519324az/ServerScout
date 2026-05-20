package com.serverscout.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
@Service
public class ProgressEmitter {

    private final Map<Long, List<SseEmitter>> taskEmitters = new ConcurrentHashMap<>();

    public SseEmitter createEmitter(Long taskId) {
        SseEmitter emitter = new SseEmitter(0L);
        taskEmitters.computeIfAbsent(taskId, k -> new CopyOnWriteArrayList<>()).add(emitter);

        emitter.onCompletion(() -> removeEmitter(taskId, emitter));
        emitter.onTimeout(() -> removeEmitter(taskId, emitter));
        emitter.onError(e -> removeEmitter(taskId, emitter));

        return emitter;
    }

    public void sendProgress(Long taskId, int progress, String currentTarget, int assetsFound) {
        List<SseEmitter> emitters = taskEmitters.get(taskId);
        if (emitters == null) return;

        Map<String, Object> data = new HashMap<>();
        data.put("progress", progress);
        data.put("currentTarget", currentTarget);
        data.put("assetsFound", assetsFound);
        data.put("timestamp", System.currentTimeMillis());

        sendEvent(taskId, emitters, "progress", data);
    }

    /** Emit a discovered asset with its ports in real time */
    public void sendDiscoveredAsset(Long taskId, String ip, String hostname,
                                     String osInfo, int portCount, List<Map<String, Object>> ports) {
        List<SseEmitter> emitters = taskEmitters.get(taskId);
        if (emitters == null) return;

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

    /** Emit a discovered vulnerability in real time */
    public void sendDiscoveredVuln(Long taskId, String severity, String cveId,
                                    String name, String url, String affected) {
        List<SseEmitter> emitters = taskEmitters.get(taskId);
        if (emitters == null) return;

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

    /** Emit web fingerprint discovery */
    public void sendDiscoveredFingerprint(Long taskId, String ip, int port,
                                           String server, String framework, String cms, String title) {
        List<SseEmitter> emitters = taskEmitters.get(taskId);
        if (emitters == null) return;

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
        List<SseEmitter> emitters = taskEmitters.remove(taskId);
        if (emitters == null) return;
        sendEvent(taskId, emitters, "completed", Map.of("taskId", taskId));
    }

    public void sendError(Long taskId, String message) {
        List<SseEmitter> emitters = taskEmitters.remove(taskId);
        if (emitters == null) return;
        sendEvent(taskId, emitters, "error", Map.of("message", message));
    }

    private void sendEvent(Long taskId, List<SseEmitter> emitters, String name, Object data) {
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name(name).data(data));
            } catch (IOException e) {
                emitter.completeWithError(e);
                removeEmitter(taskId, emitter);
            }
        }
    }

    private void removeEmitter(Long taskId, SseEmitter emitter) {
        List<SseEmitter> emitters = taskEmitters.get(taskId);
        if (emitters != null) emitters.remove(emitter);
    }
}
