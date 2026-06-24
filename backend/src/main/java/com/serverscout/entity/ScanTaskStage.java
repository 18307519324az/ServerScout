package com.serverscout.entity;

import com.serverscout.entity.enums.ScanStageCode;
import com.serverscout.entity.enums.ScanStageStatus;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "scan_task_stage",
       uniqueConstraints = @UniqueConstraint(columnNames = {"task_id", "stage_code"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ScanTaskStage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "task_id", nullable = false)
    private Long taskId;

    @Enumerated(EnumType.STRING)
    @Column(name = "stage_code", nullable = false, length = 32)
    private ScanStageCode stageCode;

    @Column(name = "stage_name", nullable = false, length = 32)
    private String stageName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ScanStageStatus status;

    @Column(nullable = false)
    private Integer progress;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.status == null) this.status = ScanStageStatus.PENDING;
        if (this.progress == null) this.progress = 0;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
