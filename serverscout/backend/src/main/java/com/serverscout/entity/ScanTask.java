package com.serverscout.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "scan_task")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ScanTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(name = "target_range", nullable = false, length = 512)
    private String targetRange;

    @Column(name = "scan_type", length = 32)
    private String scanType;

    @Column(name = "port_range", length = 64)
    private String portRange;

    @Column(name = "enable_fingerprint")
    private Boolean enableFingerprint;

    @Column(name = "enable_vuln_scan")
    private Boolean enableVulnScan;

    @Column(name = "enable_crawler")
    private Boolean enableCrawler;

    @Column(length = 16, nullable = false)
    private String status;

    @Column(nullable = false)
    private Integer progress;

    @Column(name = "total_assets")
    private Integer totalAssets;

    @Column(name = "total_ports")
    private Integer totalPorts;

    @Column(name = "config_json", columnDefinition = "TEXT")
    private String configJson;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "created_by", length = 64)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
        if (this.progress == null) this.progress = 0;
        if (this.totalAssets == null) this.totalAssets = 0;
        if (this.totalPorts == null) this.totalPorts = 0;
        if (this.status == null) this.status = "pending";
    }

    @PreUpdate
    protected void onUpdate() {
        if (this.progress == null) this.progress = 0;
    }
}
