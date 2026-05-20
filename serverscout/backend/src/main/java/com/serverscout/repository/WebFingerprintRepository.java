package com.serverscout.repository;

import com.serverscout.entity.WebFingerprint;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WebFingerprintRepository extends JpaRepository<WebFingerprint, Long> {
    Optional<WebFingerprint> findByPortId(Long portId);
}
