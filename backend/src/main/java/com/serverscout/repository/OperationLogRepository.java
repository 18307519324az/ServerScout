package com.serverscout.repository;

import com.serverscout.entity.OperationLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface OperationLogRepository extends JpaRepository<OperationLog, Long> {

    Page<OperationLog> findByUsernameOrderByCreatedAtDesc(String username, Pageable pageable);

    Page<OperationLog> findByOperationTypeOrderByCreatedAtDesc(String operationType, Pageable pageable);

    @Query("SELECT o FROM OperationLog o WHERE " +
           "(:username IS NULL OR o.username LIKE %:username%) AND " +
           "(:type IS NULL OR o.operationType = :type) AND " +
           "(:startTime IS NULL OR o.createdAt >= :startTime) AND " +
           "(:endTime IS NULL OR o.createdAt <= :endTime) " +
           "ORDER BY o.createdAt DESC")
    Page<OperationLog> search(@Param("username") String username,
                              @Param("type") String type,
                              @Param("startTime") Instant startTime,
                              @Param("endTime") Instant endTime,
                              Pageable pageable);

    void deleteByCreatedAtBefore(Instant before);

    long count();

    @Query("SELECT o FROM OperationLog o WHERE " +
           "(:username IS NULL OR o.username LIKE %:username%) AND " +
           "(:type IS NULL OR o.operationType = :type) AND " +
           "(:startTime IS NULL OR o.createdAt >= :startTime) AND " +
           "(:endTime IS NULL OR o.createdAt <= :endTime) " +
           "ORDER BY o.createdAt DESC")
    java.util.List<OperationLog> findAllForExport(@Param("username") String username,
                                                   @Param("type") String type,
                                                   @Param("startTime") Instant startTime,
                                                   @Param("endTime") Instant endTime);
}
