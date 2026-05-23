package com.serverscout.config;

import com.serverscout.service.SystemConfigService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;
import java.util.List;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${app.cors.allowed-origins:http://localhost:5173,http://localhost:5174}")
    private String allowedOrigins;

    private final SystemConfigService configService;

    public WebConfig(SystemConfigService configService) {
        this.configService = configService;
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
