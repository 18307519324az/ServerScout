package com.serverscout.repository;

import com.serverscout.entity.CrawledUrl;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CrawledUrlRepository extends JpaRepository<CrawledUrl, Long> {

    List<CrawledUrl> findByAssetIdOrderByCrawlDepthAsc(Long assetId);

    List<CrawledUrl> findByPortIdOrderByCrawlDepthAsc(Long portId);

    List<CrawledUrl> findByTaskIdOrderByCrawlDepthAsc(Long taskId);

    @Query("SELECT COUNT(c) FROM CrawledUrl c WHERE c.task.id = :taskId")
    long countByTaskId(@Param("taskId") Long taskId);

    @Query("SELECT c FROM CrawledUrl c WHERE c.screenshotPath IS NOT NULL AND c.task.id = :taskId")
    List<CrawledUrl> findWithScreenshotsByTaskId(@Param("taskId") Long taskId);

    boolean existsByUrl(String url);

    void deleteByAssetId(Long assetId);

    void deleteByPortIdIn(List<Long> portIds);

    void deleteByTaskId(Long taskId);
}
