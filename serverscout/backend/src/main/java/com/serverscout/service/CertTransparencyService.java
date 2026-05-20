package com.serverscout.service;

import com.serverscout.entity.*;
import com.serverscout.repository.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
public class CertTransparencyService {

    private final SslCertificateRepository sslCertRepository;
    private final SubdomainService subdomainService;
    private final PortRepository portRepository;
    private final ScanAssetMappingRepository scanAssetMappingRepository;

    public CertTransparencyService(SslCertificateRepository sslCertRepository,
                                   SubdomainService subdomainService,
                                   PortRepository portRepository,
                                   ScanAssetMappingRepository scanAssetMappingRepository) {
        this.sslCertRepository = sslCertRepository;
        this.subdomainService = subdomainService;
        this.portRepository = portRepository;
        this.scanAssetMappingRepository = scanAssetMappingRepository;
    }

    /**
     * Analyze SSL certificate SAN data for a given scan task.
     */
    public Map<String, Object> analyzeCertificates(ScanTask task) {
        List<ScanAssetMapping> mappings = scanAssetMappingRepository.findByScanTaskIdWithAsset(task.getId());
        Set<String> domains = new LinkedHashSet<>();
        int certCount = 0;

        for (ScanAssetMapping mapping : mappings) {
            Asset asset = mapping.getAsset();
            List<Port> ports = portRepository.findByAssetId(asset.getId());
            for (Port port : ports) {
                Optional<SslCertificate> certOpt = sslCertRepository.findByPortId(port.getId());
                if (certOpt.isPresent()) {
                    SslCertificate cert = certOpt.get();
                    certCount++;
                    if (cert.getSan() != null) {
                        for (String san : cert.getSan().split(",")) {
                            String cleaned = cleanDomain(san.trim().toLowerCase());
                            if (isValidDomain(cleaned)) {
                                domains.add(extractRootDomain(cleaned));
                            }
                        }
                    }
                    if (cert.getSubject() != null) {
                        String cn = extractCN(cert.getSubject());
                        if (cn != null && isValidDomain(cn)) {
                            domains.add(extractRootDomain(cn));
                        }
                    }
                }
            }
        }

        int totalSubdomains = 0;
        for (String domain : domains) {
            try {
                SubdomainService.SubdomainResult result = subdomainService.enumerate(domain);
                totalSubdomains += result.total();
            } catch (Exception e) {
                log.warn("Subdomain enum failed for {}: {}", domain, e.getMessage());
            }
        }

        return Map.of(
                "certificates", certCount,
                "uniqueDomains", domains.size(),
                "domains", new ArrayList<>(domains),
                "totalSubdomainsFound", totalSubdomains
        );
    }

    private String cleanDomain(String s) {
        return s.replaceAll("\\*\\.", "").replaceAll("^\\s+|\\s+$", "");
    }

    private boolean isValidDomain(String s) {
        return s != null && s.contains(".") && !s.matches("^\\d+\\.\\d+\\.\\d+\\.\\d+$");
    }

    private String extractRootDomain(String domain) {
        String[] parts = domain.split("\\.");
        if (parts.length <= 2) return domain;
        String tld = parts[parts.length - 1];
        String sld = parts[parts.length - 2];
        if (sld.length() <= 3 && parts.length > 2 && parts[parts.length - 3].length() <= 4) {
            return parts[parts.length - 3] + "." + sld + "." + tld;
        }
        return sld + "." + tld;
    }

    private String extractCN(String subject) {
        if (subject == null) return null;
        for (String part : subject.split(",")) {
            part = part.trim();
            if (part.toLowerCase().startsWith("cn=")) {
                return part.substring(3).trim();
            }
        }
        return null;
    }
}
