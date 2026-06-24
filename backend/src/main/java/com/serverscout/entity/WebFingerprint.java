package com.serverscout.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "web_fingerprint")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class WebFingerprint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "port_id", nullable = false)
    private Port port;

    @Column(name = "http_status")
    private Integer httpStatus;

    @Column(name = "server_header", length = 128)
    private String serverHeader;

    @Column(name = "framework_name", length = 64)
    private String frameworkName;

    @Column(name = "framework_version", length = 32)
    private String frameworkVersion;

    @Column(length = 256)
    private String title;

    @Column(name = "response_headers", columnDefinition = "TEXT")
    private String responseHeaders;

    @Column(name = "body_hash", length = 64)
    private String bodyHash;

    @Column(name = "favicon_hash", length = 32)
    private String faviconHash;

    @Column(name = "cms_name", length = 64)
    private String cmsName;

    @Column(name = "cms_version", length = 32)
    private String cmsVersion;

    @Column(name = "waf_name", length = 32)
    private String wafName;

    @Column(name = "tech_stack", columnDefinition = "TEXT")
    private String techStack;

    @Column(name = "response_summary", columnDefinition = "TEXT")
    private String responseSummary;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
    }
}
