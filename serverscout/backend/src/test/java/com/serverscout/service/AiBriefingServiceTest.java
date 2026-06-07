package com.serverscout.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.serverscout.dto.AiBriefingRequest;
import com.serverscout.dto.AiBriefingResponse;
import com.serverscout.exception.BadRequestException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiBriefingServiceTest {

    private AiBriefingService service;

    @BeforeEach
    void setUp() {
        service = new AiBriefingService(new ObjectMapper());
        ReflectionTestUtils.setField(service, "baseUrl", "");
        ReflectionTestUtils.setField(service, "apiKey", "");
        ReflectionTestUtils.setField(service, "model", "");
        ReflectionTestUtils.setField(service, "timeoutSeconds", 5);
    }

    @Test
    void outputChangesWithInputAndSupportsFreeFormEvidence() {
        AiBriefingResponse first = service.generate(new AiBriefingRequest(
                "AWS host 10.0.0.8 runs Nginx on ports 80,443. CVE-2024-1234 CVSS 9.8 EPSS 0.92.",
                "en"));
        AiBriefingResponse second = service.generate(new AiBriefingRequest(
                "Internal Redis service at cache.example.local uses port 6379. Medium risk authentication review.",
                "en"));

        assertEquals("local-analysis", first.mode());
        assertTrue(first.detectedSignals().get("cves").contains("CVE-2024-1234"));
        assertTrue(first.detectedSignals().get("ports").contains("443"));
        assertNotEquals(first.inputSummary(), second.inputSummary());
        assertNotEquals(first.sections().get(0).body(), second.sections().get(0).body());
        assertTrue(first.sections().stream().anyMatch(section -> section.key().equals("vulnerability")));
        assertFalse(second.sections().stream().anyMatch(section -> section.key().equals("vulnerability")));
    }

    @Test
    void rejectsEmptyAndUnrelatedInput() {
        assertThrows(BadRequestException.class,
                () -> service.generate(new AiBriefingRequest(" ", "en")));
        assertThrows(BadRequestException.class,
                () -> service.generate(new AiBriefingRequest("Please write a recipe for chocolate cake.", "en")));
        assertThrows(BadRequestException.class,
                () -> service.generate(new AiBriefingRequest("Write a travel story about Java and Docker.", "en")));
    }

    @Test
    void acceptsJsonLikeEvidenceWithoutRequiringDemoFormat() {
        AiBriefingResponse response = service.generate(new AiBriefingRequest(
                """
                {
                  "host": "10.0.0.9",
                  "service": "redis",
                  "port": 6379,
                  "risk": "authentication disabled"
                }
                """,
                "en"));

        assertTrue(response.detectedSignals().get("ips").contains("10.0.0.9"));
        assertTrue(response.detectedSignals().get("ports").contains("6379"));
        assertTrue(response.sections().stream().anyMatch(section -> section.key().equals("exposure")));
    }

    @Test
    void returnsChineseBriefWhenRequested() {
        AiBriefingResponse response = service.generate(new AiBriefingRequest(
                "资产 192.168.1.10 开放端口 22、443，发现高危漏洞 CVE-2021-44228，CVSS 10.0。",
                "zh"));

        assertEquals("zh", response.language());
        assertEquals("执行摘要", response.sections().get(0).title());
        assertFalse(response.sections().isEmpty());
    }

    @Test
    void callsConfiguredLanguageModelAndUsesItsStructuredResponse() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        AtomicReference<String> receivedBody = new AtomicReference<>();
        AtomicInteger requestCount = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat/completions", exchange -> {
            receivedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            String validContent = mapper.writeValueAsString(Map.of(
                    "inputSummary", "Model understood the Nginx finding.",
                    "sections", List.of(Map.of(
                            "key", "model-summary",
                            "title", "Model summary",
                            "body", "Patch the affected Nginx service.",
                            "items", List.of("Validate the affected version"))),
                    "warnings", List.of()));
            String modelContent = requestCount.incrementAndGet() == 1 ? "{\"sections\":[" : validContent;
            byte[] response = mapper.writeValueAsBytes(Map.of(
                    "choices", List.of(Map.of("message", Map.of("content", modelContent)))));
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();

        try {
            ReflectionTestUtils.setField(service, "baseUrl",
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/chat/completions");
            ReflectionTestUtils.setField(service, "apiKey", "test-key");
            ReflectionTestUtils.setField(service, "model", "test-model");

            AiBriefingResponse response = service.generate(new AiBriefingRequest(
                    "Asset 10.0.0.8 runs Nginx on port 443 with CVE-2024-1234.", "en"));

            assertEquals("llm", response.mode());
            assertEquals("Model summary", response.sections().get(0).title());
            assertTrue(receivedBody.get().contains("CVE-2024-1234"));
            assertTrue(receivedBody.get().contains("test-model"));
            assertEquals(2, requestCount.get());
        } finally {
            server.stop(0);
        }
    }
}
