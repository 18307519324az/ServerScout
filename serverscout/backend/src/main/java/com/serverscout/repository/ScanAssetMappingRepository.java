package com.serverscout.repository;

import com.serverscout.entity.ScanAssetMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ScanAssetMappingRepository extends JpaRepository<ScanAssetMapping, Long> {

    List<ScanAssetMapping> findByScanTaskId(Long scanTaskId);

    @Query("SELECT m FROM ScanAssetMapping m JOIN FETCH m.asset WHERE m.scanTask.id = :scanTaskId")
    List<ScanAssetMapping> findByScanTaskIdWithAsset(@Param("scanTaskId") Long scanTaskId);

    List<ScanAssetMapping> findByAssetId(Long assetId);

    Optional<ScanAssetMapping> findByScanTaskIdAndAssetId(Long scanTaskId, Long assetId);

    @Query("SELECT COUNT(m) FROM ScanAssetMapping m WHERE m.asset.id = :assetId")
    long countByAssetId(@Param("assetId") Long assetId);

    @Query("SELECT COUNT(m) FROM ScanAssetMapping m WHERE m.isNew = true " +
           "AND m.scanTime > :since")
    long countNewAssetsSince(@Param("since") java.time.Instant since);
}
