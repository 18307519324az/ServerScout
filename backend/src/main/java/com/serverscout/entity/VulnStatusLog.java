package com.serverscout.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.Instant;

@Entity
@Table(name = "vuln_status_logs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VulnStatusLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "vulnerability_id", nullable = false)
    private Long vulnerabilityId;

    @Column(name = "from_status", length = 32)
    private String fromStatus;

    @Column(name = "to_status", nullable = false, length = 32)
    private String toStatus;

    @Column(columnDefinition = "TEXT")
    private String note;

    @Column(name = "changed_by", length = 64)
    private String changedBy;

    @Column(name = "changed_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant changedAt = Instant.now();
}
