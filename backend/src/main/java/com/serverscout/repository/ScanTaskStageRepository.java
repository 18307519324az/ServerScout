package com.serverscout.repository;

import com.serverscout.entity.ScanTaskStage;
import com.serverscout.entity.enums.ScanStageCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface ScanTaskStageRepository extends JpaRepository<ScanTaskStage, Long> {

    List<ScanTaskStage> findByTaskIdOrderByIdAsc(Long taskId);

    Optional<ScanTaskStage> findByTaskIdAndStageCode(Long taskId, ScanStageCode stageCode);

    @Transactional
    void deleteByTaskId(Long taskId);
}
