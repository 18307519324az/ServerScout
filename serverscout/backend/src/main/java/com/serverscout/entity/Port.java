package com.serverscout.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "port", uniqueConstraints =
    @UniqueConstraint(columnNames = {"asset_id", "port_number", "protocol"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Port {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id", nullable = false)
    private Asset asset;

    @Column(name = "port_number", nullable = false)
    private Integer portNumber;

    @Column(length = 8)
    private String protocol;

    @Column(name = "service_name", length = 64)
    private String serviceName;

    @Column(name = "service_version", length = 128)
    private String serviceVersion;

    @Column(name = "service_product", length = 128)
    private String serviceProduct;

    @Column(length = 16)
    private String state;

    @Column(columnDefinition = "TEXT")
    private String banner;

    @Column(name = "is_web_service")
    private Boolean isWebService;

    @Column(name = "first_seen_at", nullable = false)
    private Instant firstSeenAt;

    @PrePersist
    protected void onCreate() {
        this.firstSeenAt = Instant.now();
        if (this.protocol == null) this.protocol = "tcp";
        if (this.state == null) this.state = "open";
        if (this.isWebService == null) this.isWebService = false;
    }
}
