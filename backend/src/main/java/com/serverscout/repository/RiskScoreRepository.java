package com.serverscout.repository;

import com.serverscout.entity.RiskScoreDetail;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RiskScoreRepository extends JpaRepository<RiskScoreDetail, Long> {

    List<RiskScoreDetail> findByTaskId(Long taskId);

    List<RiskScoreDetail> findByAssetId(Long assetId);

    Optional<RiskScoreDetail> findByTaskIdAndAssetId(Long taskId, Long assetId);

    void deleteByTaskId(Long taskId);

    void deleteByAssetId(Long assetId);

    @Query("SELECT r FROM RiskScoreDetail r ORDER BY r.finalRiskScore DESC")
    List<RiskScoreDetail> findTopRisks(Pageable pageable);

    @Query("SELECT r FROM RiskScoreDetail r WHERE r.assetIp IN " +
           "(SELECT a.ipAddress FROM Asset a JOIN a.task t WHERE t.createdBy = :createdBy) " +
           "ORDER BY r.finalRiskScore DESC")
    List<RiskScoreDetail> findTopRisksByCreatedBy(@Param("createdBy") String createdBy, Pageable pageable);
}
