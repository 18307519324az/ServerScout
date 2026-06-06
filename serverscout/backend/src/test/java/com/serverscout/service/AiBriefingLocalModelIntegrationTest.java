package com.serverscout.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.serverscout.dto.AiBriefingRequest;
import com.serverscout.dto.AiBriefingResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class AiBriefingLocalModelIntegrationTest {

    @Test
    @EnabledIfEnvironmentVariable(named = "RUN_LOCAL_LLM_TEST", matches = "true")
    void generatesBriefUsingRunningLocalLanguageModel() {
        AiBriefingService service = new AiBriefingService(new ObjectMapper());
        ReflectionTestUtils.setField(service, "baseUrl", "http://127.0.0.1:11434/v1/chat/completions");
        ReflectionTestUtils.setField(service, "apiKey", "");
        ReflectionTestUtils.setField(service, "model", "qwen2.5-1.5b-instruct");
        ReflectionTestUtils.setField(service, "timeoutSeconds", 180);

        AiBriefingResponse response = service.generate(new AiBriefingRequest(
                "Asset 10.0.0.8 exposes port 443. Nginx is affected by CVE-2024-1234 with CVSS 9.8.",
                "en"));

        assertEquals("llm", response.mode());
        assertFalse(response.sections().isEmpty());
        assertFalse(response.inputSummary().isBlank());
    }
}
