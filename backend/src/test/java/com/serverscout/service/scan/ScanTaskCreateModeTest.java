package com.serverscout.service.scan;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.serverscout.dto.CreateScanTaskRequest;
import com.serverscout.entity.ScanTask;
import com.serverscout.repository.ScanTaskRepository;
import com.serverscout.service.ScanService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@WithMockUser(username = "admin", roles = "ADMIN")
class ScanTaskCreateModeTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ScanTaskRepository scanTaskRepository;

    private CreateScanTaskRequest createRequest(boolean authorized) {
        CreateScanTaskRequest req = new CreateScanTaskRequest();
        req.setName("Mode Test Task");
        req.setTargetRange("192.168.1.1");
        req.setScanType("quick");
        req.setAuthorized(authorized);
        return req;
    }

    @Test
    void demoModeCreatesTaskWithScanModeDemo() throws Exception {
        // In test profile, demo-mode defaults to true from application.yml
        CreateScanTaskRequest req = createRequest(false);

        String json = mockMvc.perform(post("/api/v1/scan-tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // Verify scanMode in response
        assertThat(json).contains("scanMode");
        assertThat(json).contains("DEMO");
    }

    @Test
    void demoModeDoesNotRequireAuthorization() throws Exception {
        CreateScanTaskRequest req = createRequest(false);

        mockMvc.perform(post("/api/v1/scan-tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());
    }

    @Test
    void scanModeIsPersistedInDatabase() throws Exception {
        CreateScanTaskRequest req = createRequest(false);
        req.setName("Persist Test");

        String json = mockMvc.perform(post("/api/v1/scan-tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // Parse ID from response and check DB
        // The response is ApiResponse<ScanTaskResponse>, data contains id
        // We can extract id with JSON path
        String idStr = com.jayway.jsonpath.JsonPath.read(json, "$.data.id").toString();
        Long taskId = Long.parseLong(idStr);

        ScanTask saved = scanTaskRepository.findById(taskId).orElseThrow();
        assertThat(saved.getScanMode()).isEqualTo("DEMO");
    }

    @Test
    void responseContainsScanModeField() throws Exception {
        CreateScanTaskRequest req = createRequest(false);

        mockMvc.perform(post("/api/v1/scan-tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.scanMode").exists());
    }
}
