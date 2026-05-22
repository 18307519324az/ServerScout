package com.serverscout.repository;

import com.serverscout.entity.ScanTask;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ScanTaskRepository extends JpaRepository<ScanTask, Long> {
    Page<ScanTask> findByStatus(String status, Pageable pageable);
    long countByStatus(String status);

    Page<ScanTask> findByCreatedBy(String createdBy, Pageable pageable);
    Page<ScanTask> findByCreatedByAndStatus(String createdBy, String status, Pageable pageable);
    long countByCreatedByAndStatus(String createdBy, String status);

    @Query("SELECT t.id FROM ScanTask t WHERE t.createdBy = :createdBy")
    List<Long> findIdsByCreatedBy(@Param("createdBy") String createdBy);
}
