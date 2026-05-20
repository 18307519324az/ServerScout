package com.serverscout.repository;

import com.serverscout.entity.Asset;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AssetRepository extends JpaRepository<Asset, Long> {
    Page<Asset> findByTaskId(Long taskId, Pageable pageable);

    List<Asset> findByTaskId(Long taskId);

    @Query("SELECT a FROM Asset a WHERE " +
           "(:keyword IS NULL OR LOWER(a.ipAddress) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "OR LOWER(a.hostname) LIKE LOWER(CONCAT('%', :keyword, '%'))) AND " +
           "(:status IS NULL OR a.status = :status)")
    Page<Asset> search(@Param("keyword") String keyword,
                       @Param("status") String status,
                       Pageable pageable);

    @Query("SELECT a FROM Asset a WHERE a.status = 'alive' " +
           "ORDER BY a.criticalVulnCount DESC")
    List<Asset> findTopRisk(Pageable pageable);

    long countByStatus(String status);

    Optional<Asset> findByIpAddress(String ipAddress);

    @Query("SELECT a FROM Asset a WHERE a.ipAddress IN :ips")
    List<Asset> findByIpAddressIn(@Param("ips") List<String> ips);

    boolean existsByIpAddress(String ipAddress);

    @Query("SELECT COUNT(a) FROM Asset a WHERE a.lastScanTime >= :since")
    long countScannedSince(@Param("since") java.time.Instant since);
}
