package com.serverscout.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.Instant;

@Entity
@Table(name = "operation_logs", indexes = {
    @Index(name = "idx_oplog_user", columnList = "username"),
    @Index(name = "idx_oplog_time", columnList = "createdAt"),
    @Index(name = "idx_oplog_type", columnList = "operationType")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OperationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Column(length = 64, nullable = false)
    private String username;

    @Column(name = "operation_type", length = 32, nullable = false)
    private String operationType;

    @Column(name = "target", length = 256)
    private String target;

    @Column(columnDefinition = "TEXT")
    private String detail;

    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    @Column(name = "user_agent", length = 512)
    private String userAgent;

    @Column(name = "request_method", length = 8)
    private String requestMethod;

    @Column(name = "request_uri", length = 256)
    private String requestUri;

    @Column(name = "status_code")
    private Integer statusCode;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
