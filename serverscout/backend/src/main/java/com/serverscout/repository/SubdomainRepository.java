package com.serverscout.repository;

import com.serverscout.entity.Subdomain;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SubdomainRepository extends JpaRepository<Subdomain, Long> {

    List<Subdomain> findByDomain(String domain);

    List<Subdomain> findByAssetId(Long assetId);

    Optional<Subdomain> findBySubdomainAndSource(String subdomain, String source);

    long countByDomain(String domain);

    List<Subdomain> findByIpAddress(String ipAddress);
}
