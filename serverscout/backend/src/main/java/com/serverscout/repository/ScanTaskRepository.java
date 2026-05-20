package com.serverscout.repository;

import com.serverscout.entity.ScanTask;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScanTaskRepository extends JpaRepository<ScanTask, Long> {
    Page<ScanTask> findByStatus(String status, Pageable pageable);
    long countByStatus(String status);
}
