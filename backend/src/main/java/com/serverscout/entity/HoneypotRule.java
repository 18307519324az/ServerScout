package com.serverscout.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "honeypot_rule")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class HoneypotRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "rule_name", nullable = false, length = 128)
    private String ruleName;

    @Column(name = "match_type", nullable = false, length = 32)
    private String matchType;

    @Column(name = "match_field", length = 64)
    private String matchField;

    @Column(name = "match_pattern", nullable = false, columnDefinition = "TEXT")
    private String matchPattern;

    @Column(name = "honeypot_type", nullable = false, length = 64)
    private String honeypotType;

    @Column(name = "honeypot_category", length = 32)
    private String honeypotCategory;

    @Column(length = 16)
    private String confidence;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private Boolean enabled;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
        if (this.enabled == null) this.enabled = true;
        if (this.confidence == null) this.confidence = "MEDIUM";
    }
}
