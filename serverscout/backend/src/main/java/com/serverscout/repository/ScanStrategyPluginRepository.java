package com.serverscout.repository;

import com.serverscout.entity.ScanStrategyPlugin;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ScanStrategyPluginRepository extends JpaRepository<ScanStrategyPlugin, Long> {
    Optional<ScanStrategyPlugin> findByScanType(String scanType);
    List<ScanStrategyPlugin> findAllByEnabledTrue();
    boolean existsByScanType(String scanType);
}
