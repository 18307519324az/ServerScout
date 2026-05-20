package com.serverscout.repository;

import com.serverscout.entity.CveDatabase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CveDatabaseRepository extends JpaRepository<CveDatabase, Long> {
    Optional<CveDatabase> findByCveId(String cveId);

    List<CveDatabase> findByAffectedSoftware(String affectedSoftware);

    @Query("SELECT c FROM CveDatabase c WHERE LOWER(c.affectedSoftware) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    List<CveDatabase> findByAffectedSoftwareContaining(@Param("keyword") String keyword);

    List<CveDatabase> findBySeverity(String severity);

    long countBySeverity(String severity);
}
