package com.serverscout.repository;

import com.serverscout.entity.Port;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface PortRepository extends JpaRepository<Port, Long> {
    List<Port> findByAssetId(Long assetId);

    long countByIsWebServiceTrue();

    @Query("SELECT p.portNumber, COUNT(p) FROM Port p " +
           "WHERE p.state = 'open' GROUP BY p.portNumber " +
           "ORDER BY COUNT(p) DESC")
    List<Object[]> findPortDistribution();

    Optional<Port> findByAssetIdAndPortNumberAndProtocol(Long assetId, int portNumber, String protocol);

    long countByAssetId(Long assetId);
}
