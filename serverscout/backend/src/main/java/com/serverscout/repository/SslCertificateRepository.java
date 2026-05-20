package com.serverscout.repository;

import com.serverscout.entity.SslCertificate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SslCertificateRepository extends JpaRepository<SslCertificate, Long> {

    Optional<SslCertificate> findByPortId(Long portId);

    List<SslCertificate> findByIsExpiredTrue();

    long countByIsExpiredTrue();
}
