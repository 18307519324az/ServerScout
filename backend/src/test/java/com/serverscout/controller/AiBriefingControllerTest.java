package com.serverscout.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.serverscout.exception.GlobalExceptionHandler;
import com.serverscout.service.AiBriefingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class AiBriefingControllerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        AiBriefingService service = new AiBriefingService(new ObjectMapper());
        mockMvc = standaloneSetup(new AiBriefingController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void generatesInputDrivenBriefThroughHttpEndpoint() throws Exception {
        mockMvc.perform(post("/api/v1/ai-briefing/generate")
                        .contentType("application/json")
                        .content("""
                                {
                                  "evidence": "Asset 10.0.0.8 runs Nginx on port 443 with CVE-2024-1234 and CVSS 9.8.",
                                  "locale": "en"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mode").value("local-analysis"))
                .andExpect(jsonPath("$.data.detectedSignals.cves[0]").value("CVE-2024-1234"))
                .andExpect(jsonPath("$.data.sections[0].body").value(
                        org.hamcrest.Matchers.containsString("CVE-2024-1234")));
    }

    @Test
    void rejectsUnrelatedInputThroughHttpEndpoint() throws Exception {
        mockMvc.perform(post("/api/v1/ai-briefing/generate")
                        .contentType("application/json")
                        .content("""
                                {
                                  "evidence": "Please write a recipe for chocolate cake.",
                                  "locale": "en"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "The input is unrelated to security scans, assets, services, or vulnerabilities."));
    }
}
