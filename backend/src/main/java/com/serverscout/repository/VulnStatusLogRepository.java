package com.serverscout.repository;

import com.serverscout.entity.VulnStatusLog;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface VulnStatusLogRepository extends JpaRepository<VulnStatusLog, Long> {
    List<VulnStatusLog> findByVulnerabilityIdOrderByChangedAtDesc(Long vulnerabilityId);
}
