package com.serverscout.service;

import com.serverscout.entity.OperationLog;
import com.serverscout.repository.OperationLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class OperationLogService {

    private final OperationLogRepository repository;

    @Async
    public void log(OperationLog entry) {
        try {
            if (entry.getCreatedAt() == null) {
                entry.setCreatedAt(Instant.now());
            }
            repository.save(entry);
        } catch (Exception e) {
            log.debug("Failed to save operation log: {}", e.getMessage());
        }
    }

    public void logLogin(Long userId, String username, String ip, String userAgent, boolean success) {
        log(OperationLog.builder()
                .userId(userId)
                .username(username)
                .operationType(success ? "LOGIN_SUCCESS" : "LOGIN_FAILED")
                .target("用户登录")
                .detail(success ? "登录成功" : "登录失败: 用户名或密码错误")
                .ipAddress(ip)
                .userAgent(userAgent)
                .build());
    }

    public void logApiCall(String username, String method, String uri,
                           int statusCode, long durationMs, String ip, String userAgent) {
        log(OperationLog.builder()
                .username(username != null ? username : "anonymous")
                .operationType("API_CALL")
                .target(method + " " + uri)
                .requestMethod(method)
                .requestUri(uri)
                .statusCode(statusCode)
                .durationMs(durationMs)
                .ipAddress(ip)
                .userAgent(userAgent)
                .build());
    }

    public static String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isBlank() && !"unknown".equalsIgnoreCase(ip)) {
            return ip.split(",")[0].trim();
        }
        ip = request.getHeader("X-Real-IP");
        if (ip != null && !ip.isBlank() && !"unknown".equalsIgnoreCase(ip)) {
            return ip;
        }
        return request.getRemoteAddr();
    }

    public Page<OperationLog> search(String username, String type,
                                     Instant start, Instant end, Pageable pageable) {
        return repository.search(username, type, start, end, pageable);
    }

    public Page<OperationLog> findByUser(String username, Pageable pageable) {
        return repository.findByUsernameOrderByCreatedAtDesc(username, pageable);
    }

    @Scheduled(cron = "0 0 3 * * ?")
    public void cleanOldLogs() {
        Instant cutoff = Instant.now().minus(90, ChronoUnit.DAYS);
        repository.deleteByCreatedAtBefore(cutoff);
        log.info("Cleaned operation logs older than 90 days");
    }
}
