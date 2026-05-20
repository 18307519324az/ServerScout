package com.serverscout.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "asset", uniqueConstraints =
    @UniqueConstraint(columnNames = {"ip_address"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Asset {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id")
    private ScanTask task;

    @Column(name = "ip_address", nullable = false, length = 45)
    private String ipAddress;

    @Column(length = 256)
    private String hostname;

    @Column(name = "os_fingerprint", length = 128)
    private String osFingerprint;

    @Column(name = "os_version", length = 64)
    private String osVersion;

    @Column(length = 16)
    private String status;

    @Column(name = "open_port_count")
    private Integer openPortCount;

    @Column(name = "critical_vuln_count")
    private Integer criticalVulnCount;

    @Column(columnDefinition = "json")
    private String tags;

    @Column(name = "mac_address", length = 20)
    private String macAddress;

    @Column(name = "last_scan_time")
    private Instant lastScanTime;

    @Column(name = "first_seen_time")
    private Instant firstSeenTime;

    @Column(name = "discovered_at", nullable = false)
    private Instant discoveredAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        this.discoveredAt = now;
        this.updatedAt = now;
        this.lastScanTime = now;
        this.firstSeenTime = now;
        if (this.status == null) this.status = "alive";
        if (this.openPortCount == null) this.openPortCount = 0;
        if (this.criticalVulnCount == null) this.criticalVulnCount = 0;
        if (this.tags == null) this.tags = "[]";
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
