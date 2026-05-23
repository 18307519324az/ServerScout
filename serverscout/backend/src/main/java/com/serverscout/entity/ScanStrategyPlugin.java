package com.serverscout.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.Instant;

@Entity
@Table(name = "scan_strategy_plugins")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScanStrategyPlugin {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String name;

    @Column(name = "scan_type", nullable = false, unique = true, length = 64)
    private String scanType;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "command_template", nullable = false, columnDefinition = "TEXT")
    private String commandTemplate;

    @Column(name = "result_parser", length = 32)
    @Builder.Default
    private String resultParser = "line";

    @Column(name = "finding_regex", columnDefinition = "TEXT")
    private String findingRegex;

    @Column(nullable = false)
    @Builder.Default
    private Boolean enabled = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at")
    @Builder.Default
    private Instant updatedAt = Instant.now();

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = Instant.now();
    }
}
