package com.serverscout.config;

import com.serverscout.service.OperationLogService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    @Value("${app.security.rate-limit.enabled:true}")
    private boolean enabled;

    @Value("${app.security.rate-limit.window-seconds:60}")
    private int windowSeconds;

    @Value("${app.security.rate-limit.login-limit:30}")
    private int loginLimit;

    @Value("${app.security.rate-limit.register-limit:10}")
    private int registerLimit;

    @Value("${app.security.rate-limit.scan-create-limit:120}")
    private int scanCreateLimit;

    private final Map<String, WindowCounter> counters = new ConcurrentHashMap<>();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) throws Exception {
        if (!enabled) {
            return true;
        }

        String uri = request.getRequestURI();
        String method = request.getMethod();

        int limit;
        String key;

        if (uri.equals("/api/auth/login") && "POST".equalsIgnoreCase(method)) {
            key = "login:" + OperationLogService.getClientIp(request);
            limit = loginLimit;
        } else if (uri.equals("/api/auth/register") && "POST".equalsIgnoreCase(method)) {
            key = "register:" + OperationLogService.getClientIp(request);
            limit = registerLimit;
        } else if (uri.equals("/api/v1/scan-tasks") && "POST".equalsIgnoreCase(method)) {
            String user = resolvePrincipal(request);
            key = "scan-create:" + user;
            limit = scanCreateLimit;
        } else {
            return true;
        }

        long windowMs = Math.max(1L, (long) windowSeconds) * 1000L;
        WindowCounter counter = counters.computeIfAbsent(key, k -> new WindowCounter());
        synchronized (counter) {
            long now = System.currentTimeMillis();
            counter.prune(now, windowMs);
            if (counter.count >= limit) {
                response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                response.setCharacterEncoding("UTF-8");
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write(
                        "{\"code\":429,\"message\":\"请求过于频繁，请稍后重试\"}");
                log.warn("Rate limit exceeded: key={}, count={}, limit={}", key, counter.count, limit);
                return false;
            }
            counter.hits.add(now);
            counter.count++;
        }
        return true;
    }

    private String resolvePrincipal(HttpServletRequest request) {
        if (request.getUserPrincipal() != null && request.getUserPrincipal().getName() != null) {
            return request.getUserPrincipal().getName();
        }
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getName() != null && !"anonymousUser".equals(auth.getName())) {
            return auth.getName();
        }
        return OperationLogService.getClientIp(request);
    }

    private static final class WindowCounter {
        final java.util.List<Long> hits = new java.util.ArrayList<>();
        int count;

        void prune(long now, long windowMs) {
            long cutoff = now - windowMs;
            hits.removeIf(t -> t < cutoff);
            count = hits.size();
        }
    }
}
