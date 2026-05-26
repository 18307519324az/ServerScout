package com.serverscout.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PluginControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldRequireAuthForPluginEndpoints() throws Exception {
        // /api/v1/** requires authentication — expect 403 without token
        mockMvc.perform(get("/api/v1/plugins"))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldRequireAuthForScanTypes() throws Exception {
        mockMvc.perform(get("/api/v1/plugins/scan-types"))
                .andExpect(status().isForbidden());
    }
}
