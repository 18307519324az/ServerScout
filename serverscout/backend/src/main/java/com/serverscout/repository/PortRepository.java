package com.serverscout.repository;

import com.serverscout.entity.Port;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PortRepository extends JpaRepository<Port, Long> {
    List<Port> findByAssetId(Long assetId);

    long countByIsWebServiceTrue();

    @Query("SELECT p.portNumber, COUNT(p) FROM Port p " +
           "WHERE p.state = 'open' GROUP BY p.portNumber " +
           "ORDER BY COUNT(p) DESC")
    List<Object[]> findPortDistribution();

    @Query("SELECT p.portNumber, COUNT(p) FROM Port p " +
           "JOIN p.asset a JOIN a.task t " +
           "WHERE p.state = 'open' AND t.createdBy = :createdBy " +
           "GROUP BY p.portNumber ORDER BY COUNT(p) DESC")
    List<Object[]> findPortDistributionByCreatedBy(@Param("createdBy") String createdBy);

    Optional<Port> findByAssetIdAndPortNumberAndProtocol(Long assetId, int portNumber, String protocol);

    long countByAssetId(Long assetId);

    @Query("SELECT COUNT(p) FROM Port p JOIN p.asset a JOIN a.task t " +
           "WHERE t.createdBy = :createdBy")
    long countByCreatedBy(@Param("createdBy") String createdBy);

    @Query("SELECT COUNT(p) FROM Port p JOIN p.asset a JOIN a.task t " +
           "WHERE p.isWebService = true AND t.createdBy = :createdBy")
    long countWebServiceByCreatedBy(@Param("createdBy") String createdBy);
}
