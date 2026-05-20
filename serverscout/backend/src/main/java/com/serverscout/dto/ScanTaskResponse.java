package com.serverscout.dto;

import lombok.Builder;
import lombok.Data;
import java.time.Instant;

@Data @Builder
public class ScanTaskResponse {
    private Long id;
    private String name;
    private String targetRange;
    private String scanType;
    private String portRange;
    private String status;
    private Integer progress;
    private Integer totalAssets;
    private Integer totalPorts;
    private Instant startedAt;
    private Instant completedAt;
    private Instant createdAt;
    private String errorMessage;
    private ScanSummary summary;
}
