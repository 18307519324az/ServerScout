package com.serverscout.controller;

import com.serverscout.service.ScannerToolAvailabilityService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SystemModeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void getSystemMode_shouldReturnDemoModeInfo() throws Exception {
        mockMvc.perform(get("/api/v1/system/mode"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(20000))
                .andExpect(jsonPath("$.data.mode").value("DEMO"))
                .andExpect(jsonPath("$.data.demoMode").value(true))
                .andExpect(jsonPath("$.data.scannerMode").value("DEMO"))
                .andExpect(jsonPath("$.data.actualBehavior").isString())
                .andExpect(jsonPath("$.data.switchGuide").isString())
                .andExpect(jsonPath("$.data.safetyNotice").isString());
    }
}
