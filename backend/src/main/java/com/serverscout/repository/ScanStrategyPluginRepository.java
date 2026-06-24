package com.serverscout.repository;

import com.serverscout.entity.ScanStrategyPlugin;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface ScanStrategyPluginRepository extends JpaRepository<ScanStrategyPlugin, Long> {
    Optional<ScanStrategyPlugin> findByScanType(String scanType);
    List<ScanStrategyPlugin> findAllByEnabledTrue();
    boolean existsByScanType(String scanType);

    /** Plugins owned by user or system-level (createdBy is null) — for admin use */
    @Query("SELECT p FROM ScanStrategyPlugin p WHERE p.createdBy = :createdBy OR p.createdBy IS NULL")
    List<ScanStrategyPlugin> findByCreatedByOrCreatedByIsNull(@Param("createdBy") String createdBy);

    /** Plugins owned by a specific user only — for non-admin data isolation */
    List<ScanStrategyPlugin> findByCreatedBy(String createdBy);

    /** Enabled plugins (user-owned + system) for scan-type listing — admin */
    @Query("SELECT p FROM ScanStrategyPlugin p WHERE p.enabled = true AND (p.createdBy = :createdBy OR p.createdBy IS NULL)")
    List<ScanStrategyPlugin> findEnabledByUserOrSystem(@Param("createdBy") String createdBy);

    /** Enabled plugins for a specific user only — non-admin data isolation */
    List<ScanStrategyPlugin> findByCreatedByAndEnabledTrue(String createdBy);
}
