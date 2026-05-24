package com.serverscout.config;

import com.serverscout.service.SystemConfigService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.util.Arrays;
import java.util.List;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${app.cors.allowed-origins:http://localhost:5173,http://localhost:5174}")
    private String allowedOrigins;

    private final SystemConfigService configService;
    private final RateLimitInterceptor rateLimitInterceptor;

    public WebConfig(SystemConfigService configService, RateLimitInterceptor rateLimitInterceptor) {
        this.configService = configService;
        this.rateLimitInterceptor = rateLimitInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(rateLimitInterceptor)
                .addPathPatterns("/api/auth/login", "/api/auth/register", "/api/v1/scan-tasks");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Serve frontend static files from classpath:/static/
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String resourcePath, Resource location) {
                        Resource resource = null;
                        try {
                            resource = super.getResource(resourcePath, location);
                        } catch (Exception ignored) {
                        }
                        // SPA fallback: if resource not found, serve index.html
                        if (resource == null || !resource.exists()) {
                            try {
                                Resource index = location.createRelative("index.html");
                                if (index.exists()) {
                                    return index;
                                }
                            } catch (Exception ignored) {
                            }
                        }
                        return resource;
                    }
                });
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // Priority: system_config DB > application.yml env var > default
        String dbOrigins = configService.getConfig("cors-allowed-origins", "");
        String effective = !dbOrigins.isEmpty() ? dbOrigins : allowedOrigins;

        List<String> origins = Arrays.stream(effective.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();

        registry.addMapping("/api/**")
                .allowedOrigins(origins.toArray(new String[0]))
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true);
    }
}
