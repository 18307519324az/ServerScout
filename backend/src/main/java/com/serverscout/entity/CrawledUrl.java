package com.serverscout.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "crawled_urls", indexes = {
    @Index(name = "idx_crawled_asset", columnList = "asset_id"),
    @Index(name = "idx_crawled_port", columnList = "port_id"),
    @Index(name = "idx_crawled_task", columnList = "task_id")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class CrawledUrl {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id", nullable = false)
    private Asset asset;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "port_id")
    private Port port;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id")
    private ScanTask task;

    @Column(nullable = false, length = 2048)
    private String url;

    @Column(name = "path", length = 1024)
    private String path;

    @Column(name = "http_status")
    private Integer httpStatus;

    @Column(name = "content_type", length = 128)
    private String contentType;

    @Column(name = "title", length = 512)
    private String title;

    @Column(name = "body_text", columnDefinition = "MEDIUMTEXT")
    private String bodyText;

    @Column(name = "links_found")
    private Integer linksFound;

    @Column(name = "crawl_depth")
    private Integer crawlDepth;

    @Column(name = "response_time_ms")
    private Long responseTimeMs;

    @Column(name = "is_dynamic")
    @Builder.Default
    private Boolean isDynamic = false;

    @Column(name = "screenshot_path", length = 256)
    private String screenshotPath;

    @Column(name = "crawled_at", nullable = false)
    private Instant crawledAt;

    @PrePersist
    protected void onCreate() {
        this.crawledAt = Instant.now();
        if (this.crawlDepth == null) this.crawlDepth = 0;
        if (this.linksFound == null) this.linksFound = 0;
    }
}
