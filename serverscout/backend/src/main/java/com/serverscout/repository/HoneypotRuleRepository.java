package com.serverscout.repository;

import com.serverscout.entity.HoneypotRule;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface HoneypotRuleRepository extends JpaRepository<HoneypotRule, Long> {
    List<HoneypotRule> findByEnabledTrue();
    List<HoneypotRule> findByHoneypotType(String honeypotType);
    List<HoneypotRule> findByMatchType(String matchType);
}
