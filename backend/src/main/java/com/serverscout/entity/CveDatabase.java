package com.serverscout.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "cve_database")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CveDatabase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cve_id", length = 32, nullable = false, unique = true)
    private String cveId;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "cvss_score", precision = 3, scale = 1)
    private java.math.BigDecimal cvssScore;

    @Column(length = 16)
    private String severity;

    @Column(name = "affected_software", length = 256)
    private String affectedSoftware;

    @Column(name = "affected_version_range", length = 256)
    private String affectedVersionRange;

    @Column(name = "fix_suggestion", columnDefinition = "TEXT")
    private String fixSuggestion;

    @Column(name = "publication_date")
    private LocalDate publicationDate;

    @Column(name = "last_updated", nullable = false)
    private Instant lastUpdated;

    @PrePersist
    @PreUpdate
    protected void onUpdate() {
        this.lastUpdated = Instant.now();
    }
}
