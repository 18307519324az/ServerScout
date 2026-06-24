package com.serverscout.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies that SecurityConfig's filter-level auth handling
 * (AuthenticationEntryPoint + AccessDeniedHandler) returns
 * proper JSON — these do NOT go through @RestControllerAdvice.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PluginControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("5b. 不带 Token → SecurityConfig AuthenticationEntryPoint 返回 401 JSON")
    void shouldRequireAuthForPluginEndpoints() throws Exception {
        mockMvc.perform(get("/api/v1/plugins"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40100))
                .andExpect(jsonPath("$.message", containsString("登录")));
    }

    @Test
    @DisplayName("5c. 不带 Token → 扫描接口同样返回 401 JSON")
    void shouldRequireAuthForScanTypes() throws Exception {
        mockMvc.perform(get("/api/v1/plugins/scan-types"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40100));
    }
}
