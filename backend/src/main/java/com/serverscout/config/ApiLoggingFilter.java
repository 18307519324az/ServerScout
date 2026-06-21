package com.serverscout.config;

import com.serverscout.service.OperationLogService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class ApiLoggingFilter extends OncePerRequestFilter {

    private final OperationLogService logService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        // Skip static resources and auth endpoints (login is logged separately)
        String uri = request.getRequestURI();
        String method = request.getMethod();

        if (uri.startsWith("/api/auth/") || uri.startsWith("/api-docs")
                || uri.startsWith("/swagger") || uri.startsWith("/docs")
                || uri.contains("/progress") || uri.contains("/screenshot")) {
            chain.doFilter(request, response);
            return;
        }

        if (!uri.startsWith("/api/")) {
            chain.doFilter(request, response);
            return;
        }

        long start = System.currentTimeMillis();
        ContentCachingResponseWrapper responseWrapper = new ContentCachingResponseWrapper(response);

        try {
            chain.doFilter(request, responseWrapper);
        } finally {
            long duration = System.currentTimeMillis() - start;
            int status = responseWrapper.getStatus();

            // Only log state-changing operations and errors
            if (shouldLog(method, status)) {
                String username = "anonymous";
                var auth = org.springframework.security.core.context.SecurityContextHolder
                        .getContext().getAuthentication();
                if (auth != null && auth.getName() != null && !auth.getName().equals("anonymousUser")) {
                    username = auth.getName();
                }

                String ip = OperationLogService.getClientIp(request);
                String ua = request.getHeader("User-Agent");

                logService.logApiCall(username, method, uri, status, duration,
                        ip != null ? ip : "0.0.0.0",
                        ua != null ? (ua.length() > 500 ? ua.substring(0, 500) : ua) : "");
            }

            responseWrapper.copyBodyToResponse();
        }
    }

    private boolean shouldLog(String method, int status) {
        // Log all non-GET requests, and error responses from authenticated users only
        if (!"GET".equalsIgnoreCase(method)) return true;
        // Skip GET auth errors (401/403) — these are stale/expired tokens, not real operations
        if (status == 401 || status == 403) return false;
        return status >= 400;
    }
}
