package com.serverscout.repository;

import com.serverscout.entity.HoneypotDetection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

public interface HoneypotDetectionRepository extends JpaRepository<HoneypotDetection, Long> {
    List<HoneypotDetection> findByAssetId(Long assetId);

    @Query("SELECT COUNT(DISTINCT hd.asset.id) FROM HoneypotDetection hd")
    long countDistinctAssets();

    @Query("SELECT hd.honeypotType, COUNT(DISTINCT hd.asset.id) FROM HoneypotDetection hd GROUP BY hd.honeypotType")
    List<Object[]> countByHoneypotType();

    void deleteByAssetId(Long assetId);
}
