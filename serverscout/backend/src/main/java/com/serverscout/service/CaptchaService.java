package com.serverscout.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class CaptchaService {

    private final ConcurrentHashMap<String, CaptchaEntry> store = new ConcurrentHashMap<>();

    public Map<String, Object> generate() {
        int a = (int) (Math.random() * 20) + 1;
        int b = (int) (Math.random() * 20) + 1;
        int op = (int) (Math.random() * 3); // 0=+, 1=-, 2=*
        String question;
        int answer;
        switch (op) {
            case 0: question = a + " + " + b + " = ?"; answer = a + b; break;
            case 1: question = (a + b) + " - " + b + " = ?"; answer = a; break;
            default: question = a + " × " + b + " = ?"; answer = a * b; break;
        }

        String id = UUID.randomUUID().toString().substring(0, 8);
        store.put(id, new CaptchaEntry(answer, System.currentTimeMillis()));

        return Map.of("captchaId", id, "question", question);
    }

    public boolean validate(String id, String userAnswer) {
        CaptchaEntry entry = store.remove(id);
        if (entry == null) return false;
        if (System.currentTimeMillis() - entry.createdAt > 300_000) return false;
        try {
            return Integer.parseInt(userAnswer.trim()) == entry.answer;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    @Scheduled(fixedRate = 300_000)
    public void cleanExpired() {
        long cutoff = System.currentTimeMillis() - 300_000;
        store.entrySet().removeIf(e -> e.getValue().createdAt < cutoff);
    }

    private record CaptchaEntry(int answer, long createdAt) {}
}
