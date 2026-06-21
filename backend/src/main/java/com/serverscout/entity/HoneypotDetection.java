package com.serverscout.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "honeypot_detection")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class HoneypotDetection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id", nullable = false)
    private Asset asset;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rule_id")
    private HoneypotRule rule;

    @Column(name = "honeypot_type", nullable = false, length = 64)
    private String honeypotType;

    @Column(name = "honeypot_category", length = 32)
    private String honeypotCategory;

    @Column(name = "match_evidence", columnDefinition = "TEXT")
    private String matchEvidence;

    @Column(length = 16)
    private String confidence;

    @Column(name = "detection_method", length = 32)
    private String detectionMethod;

    @Column(name = "matched_port")
    private Integer matchedPort;

    @Column(name = "matched_at", nullable = false)
    private Instant matchedAt;

    @PrePersist
    protected void onCreate() {
        this.matchedAt = Instant.now();
    }
}
