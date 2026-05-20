package com.serverscout.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "ssl_certificate")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SslCertificate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "port_id", nullable = false)
    private Port port;

    @Column(name = "subject", length = 512)
    private String subject;

    @Column(name = "issuer", length = 512)
    private String issuer;

    @Column(name = "not_before")
    private Instant notBefore;

    @Column(name = "not_after")
    private Instant notAfter;

    @Column(name = "serial_number", length = 128)
    private String serialNumber;

    @Column(name = "fingerprint_sha256", length = 64)
    private String fingerprintSha256;

    @Column(name = "san", columnDefinition = "TEXT")
    private String san;

    @Column(name = "sig_alg", length = 64)
    private String sigAlg;

    @Column(name = "key_size")
    private Integer keySize;

    @Column(name = "is_expired")
    private Boolean isExpired;

    @Column(name = "discovered_at", nullable = false)
    private Instant discoveredAt;

    @PrePersist
    protected void onCreate() {
        if (this.discoveredAt == null) this.discoveredAt = Instant.now();
    }
}
