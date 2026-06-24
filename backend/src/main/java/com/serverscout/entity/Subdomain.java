package com.serverscout.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "subdomain", uniqueConstraints =
    @UniqueConstraint(columnNames = {"subdomain", "source"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Subdomain {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 256)
    private String domain;

    @Column(nullable = false, length = 512)
    private String subdomain;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(nullable = false, length = 32)
    private String source;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id")
    private Asset asset;

    @Column(name = "first_seen_time", nullable = false)
    private Instant firstSeenTime;

    @Column(name = "last_seen_time", nullable = false)
    private Instant lastSeenTime;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        this.firstSeenTime = now;
        this.lastSeenTime = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.lastSeenTime = Instant.now();
    }
}
