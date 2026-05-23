package com.serverscout.repository;

import com.serverscout.entity.WebFingerprint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface WebFingerprintRepository extends JpaRepository<WebFingerprint, Long> {
    Optional<WebFingerprint> findByPortId(Long portId);

    @Query("SELECT wf FROM WebFingerprint wf JOIN wf.port p JOIN p.asset a JOIN a.task t " +
           "WHERE t.createdBy = :createdBy")
    List<WebFingerprint> findAllByCreatedBy(@Param("createdBy") String createdBy);

    void deleteByPortId(Long portId);
}
