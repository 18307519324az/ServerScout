package com.serverscout.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "scan_asset_mapping")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ScanAssetMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "scan_task_id", nullable = false)
    private ScanTask scanTask;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id", nullable = false)
    private Asset asset;

    @Column(name = "scan_time", nullable = false)
    private Instant scanTime;

    @Column(name = "is_new")
    private Boolean isNew;

    @Column(name = "ports_found")
    private Integer portsFound;

    @PrePersist
    protected void onCreate() {
        if (this.scanTime == null) this.scanTime = Instant.now();
        if (this.isNew == null) this.isNew = true;
        if (this.portsFound == null) this.portsFound = 0;
    }
}
