package com.serverscout.config;

import com.serverscout.service.OperationLogService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private static final int LOGIN_LIMIT = 5;
    private static final int REGISTER_LIMIT = 3;
    private static final int SCAN_CREATE_LIMIT = 10;
    private static final int WINDOW_MINUTES = 1;

    private final Map<String, WindowCounter> counters = new ConcurrentHashMap<>();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) throws Exception {
        String uri = request.getRequestURI();
        String method = request.getMethod();

        int limit;
        String key;

        if (uri.equals("/api/auth/login") && "POST".equalsIgnoreCase(method)) {
            key = "login:" + OperationLogService.getClientIp(request);
            limit = LOGIN_LIMIT;
        } else if (uri.equals("/api/auth/register") && "POST".equalsIgnoreCase(method)) {
            key = "register:" + OperationLogService.getClientIp(request);
            limit = REGISTER_LIMIT;
        } else if (uri.equals("/api/v1/scan-tasks") && "POST".equalsIgnoreCase(method)) {
            String user = request.getUserPrincipal() != null
                    ? request.getUserPrincipal().getName() : OperationLogService.getClientIp(request);
            key = "scan-create:" + user;
            limit = SCAN_CREATE_LIMIT;
        } else {
            return true;
        }

        WindowCounter counter = counters.computeIfAbsent(key, k -> new WindowCounter());
        synchronized (counter) {
            long now = System.currentTimeMillis();
            counter.prune(now);
            if (counter.count >= limit) {
                response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write(
                        "{\"code\":429,\"message\":\"请求过于频繁，请稍后再试\"}");
                log.warn("Rate limit exceeded: key={}, count={}, limit={}", key, counter.count, limit);
                return false;
            }
            counter.hits.add(now);
            counter.count++;
        }
        return true;
    }

    private static class WindowCounter {
        final java.util.List<Long> hits = new java.util.ArrayList<>();
        int count;

        void prune(long now) {
            long cutoff = now - TimeUnit.MINUTES.toMillis(WINDOW_MINUTES);
            hits.removeIf(t -> t < cutoff);
            count = hits.size();
        }
    }
}
