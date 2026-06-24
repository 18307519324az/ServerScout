package com.serverscout.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "risk_score_detail", uniqueConstraints =
    @UniqueConstraint(columnNames = {"task_id", "asset_id"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class RiskScoreDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "task_id", nullable = false)
    private Long taskId;

    @Column(name = "asset_id", nullable = false)
    private Long assetId;

    @Column(name = "asset_ip", length = 45)
    private String assetIp;

    @Column(name = "asset_name", length = 256)
    private String assetName;

    @Column(nullable = false)
    private Integer assetExposureScore;

    @Column(nullable = false)
    private Integer vulnerabilitySeverityScore;

    @Column(nullable = false)
    private Integer serviceRiskScore;

    @Column(nullable = false)
    private Integer exploitabilityScore;

    @Column(nullable = false)
    private Integer businessImportanceScore;

    @Column(nullable = false)
    private Integer remediationDeduction;

    @Column(name = "final_risk_score", nullable = false)
    private Integer finalRiskScore;

    @Column(name = "risk_level", nullable = false, length = 16)
    private String riskLevel;

    @Column(name = "risk_reason", columnDefinition = "TEXT")
    private String riskReason;

    @Column(name = "repair_suggestion", columnDefinition = "TEXT")
    private String repairSuggestion;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.assetExposureScore == null) this.assetExposureScore = 0;
        if (this.vulnerabilitySeverityScore == null) this.vulnerabilitySeverityScore = 0;
        if (this.serviceRiskScore == null) this.serviceRiskScore = 0;
        if (this.exploitabilityScore == null) this.exploitabilityScore = 0;
        if (this.businessImportanceScore == null) this.businessImportanceScore = 0;
        if (this.remediationDeduction == null) this.remediationDeduction = 0;
        if (this.finalRiskScore == null) this.finalRiskScore = 0;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
