package com.serverscout.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.serverscout.entity.Asset;
import com.serverscout.entity.Subdomain;
import com.serverscout.repository.AssetRepository;
import com.serverscout.repository.SubdomainRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubdomainService {

    private final SubdomainRepository subdomainRepository;
    private final AssetRepository assetRepository;
    private final ObjectMapper objectMapper;

    private static final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private static final String[] COMMON_SUBDOMAINS = {
            "www", "mail", "ftp", "webmail", "smtp", "pop", "pop3", "imap",
            "admin", "api", "dev", "test", "staging", "demo", "beta",
            "blog", "shop", "store", "cdn", "static", "assets", "media",
            "app", "m", "mobile", "wap",
            "portal", "login", "sso", "auth", "oauth", "openid",
            "vpn", "remote", "gateway", "proxy", "dns", "ns1", "ns2",
            "db", "database", "mysql", "sql", "mongo", "redis",
            "jenkins", "git", "gitlab", "jira", "confluence", "wiki",
            "monitor", "monitoring", "status", "health", "grafana", "kibana",
            "docs", "doc", "help", "support", "kb", "faq",
            "forum", "community", "chat", "news", "events",
            "cloud", "files", "file", "download", "upload", "backup",
            "secure", "ssl", "security", "firewall",
            "intranet", "internal", "corp", "office",
            "erp", "crm", "hr", "oa",
            "sandbox", "uat", "qa", "preprod", "stg"
    };

    public record SubdomainResult(String domain, int total, int newCount, List<String> sources) {}

    /**
     * Enumerate subdomains for a given domain from all sources.
     */
    public SubdomainResult enumerate(String domain) {
        String cleanDomain = cleanDomain(domain);
        Set<String> found = new LinkedHashSet<>();
        List<String> activeSources = new ArrayList<>();

        // Source 1: Certificate Transparency (crt.sh)
        try {
            Set<String> crtResults = queryCrtSh(cleanDomain);
            found.addAll(crtResults);
            if (!crtResults.isEmpty()) activeSources.add("crtsh");
            log.info("crt.sh found {} subdomains for {}", crtResults.size(), cleanDomain);
        } catch (Exception e) {
            log.warn("crt.sh query failed for {}: {}", cleanDomain, e.getMessage());
        }

        // Source 2: DNS brute force
        try {
            Set<String> dnsResults = dnsBruteForce(cleanDomain);
            found.addAll(dnsResults);
            if (!dnsResults.isEmpty()) activeSources.add("dns_brute");
            log.info("DNS brute found {} subdomains for {}", dnsResults.size(), cleanDomain);
        } catch (Exception e) {
            log.warn("DNS brute failed for {}: {}", cleanDomain, e.getMessage());
        }

        // Save new results with correct source tracking
        int newCount = 0;
        Set<String> crtResults = activeSources.contains("crtsh") ? queryCrtSh(cleanDomain) : Set.of();
        for (String subdomain : found) {
            if (subdomain.length() > 512) continue;
            boolean exists = subdomainRepository.findBySubdomainAndSource(subdomain, "crtsh").isPresent()
                    || subdomainRepository.findBySubdomainAndSource(subdomain, "dns_brute").isPresent();
            if (!exists) {
                // Resolve IP
                String resolvedIp = resolveDns(subdomain);
                // Link to existing asset if IP is known
                Asset asset = null;
                if (resolvedIp != null) {
                    asset = assetRepository.findByIpAddress(resolvedIp).orElse(null);
                }

                // Determine source based on which query found this subdomain
                String source = crtResults.contains(subdomain) ? "crtsh" : "dns_brute";

                Subdomain entity = Subdomain.builder()
                        .domain(cleanDomain)
                        .subdomain(subdomain)
                        .ipAddress(resolvedIp)
                        .source(source)
                        .asset(asset)
                        .build();
                subdomainRepository.save(entity);
                newCount++;
            }
        }

        // Update source for existing entries that were also found via DNS
        for (String subdomain : found) {
            Optional<Subdomain> existing = subdomainRepository.findBySubdomainAndSource(subdomain, "crtsh");
            if (existing.isEmpty()) {
                existing = subdomainRepository.findBySubdomainAndSource(subdomain, "dns_brute");
            }
            existing.ifPresent(s -> {
                s.setLastSeenTime(Instant.now());
                subdomainRepository.save(s);
            });
        }

        log.info("Subdomain enum for {}: {} total, {} new", cleanDomain, found.size(), newCount);
        return new SubdomainResult(cleanDomain, found.size(), newCount, activeSources);
    }

    /**
     * Query crt.sh Certificate Transparency logs for subdomains.
     */
    private Set<String> queryCrtSh(String domain) {
        Set<String> results = new LinkedHashSet<>();
        try {
            String url = "https://crt.sh/?q=%25." + domain + "&output=json";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(15))
                    .header("User-Agent", "ServerScout/1.0")
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonNode nodes = objectMapper.readTree(response.body());
                for (JsonNode node : nodes) {
                    String nameValue = node.has("name_value") ? node.get("name_value").asText() : null;
                    if (nameValue != null) {
                        for (String name : nameValue.split("\\n")) {
                            name = name.trim().toLowerCase().replaceAll("^\\*\\.", "");
                            if (name.endsWith("." + domain) && !name.equals(domain) && name.length() > domain.length() + 1) {
                                results.add(name);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("crt.sh request failed: {}", e.getMessage());
        }
        return results;
    }

    /**
     * DNS brute force common subdomains.
     */
    private Set<String> dnsBruteForce(String domain) {
        Set<String> results = new LinkedHashSet<>();
        for (String prefix : COMMON_SUBDOMAINS) {
            String subdomain = prefix + "." + domain;
            if (resolveDns(subdomain) != null) {
                results.add(subdomain);
            }
            // Small delay to avoid overwhelming DNS
            try { Thread.sleep(20); } catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
        }
        return results;
    }

    /**
     * Resolve hostname to IP address.
     */
    private String resolveDns(String hostname) {
        try {
            InetAddress addr = InetAddress.getByName(hostname);
            return addr.getHostAddress();
        } catch (Exception e) {
            return null;
        }
    }

    private String cleanDomain(String domain) {
        return domain.toLowerCase().replaceAll("^https?://", "")
                .replaceAll(":\\d+$", "")
                .replaceAll("/.*$", "")
                .trim();
    }

    /**
     * Get all subdomains for a domain.
     */
    public List<Subdomain> getSubdomains(String domain) {
        return subdomainRepository.findByDomain(cleanDomain(domain));
    }

    /**
     * Get subdomains linked to an asset.
     */
    public List<Subdomain> getSubdomainsByAsset(Long assetId) {
        return subdomainRepository.findByAssetId(assetId);
    }

    /**
     * Get all subdomain statistics for a domain.
     */
    public Map<String, Object> getStats(String domain) {
        String cleanDomain = cleanDomain(domain);
        List<Subdomain> all = subdomainRepository.findByDomain(cleanDomain);
        return Map.of(
                "domain", cleanDomain,
                "totalSubdomains", all.size(),
                "resolvedCount", all.stream().filter(s -> s.getIpAddress() != null).count(),
                "sources", all.stream().map(Subdomain::getSource).distinct().collect(Collectors.toList()),
                "subdomains", all.stream().map(s -> Map.of(
                        "subdomain", s.getSubdomain(),
                        "ip", s.getIpAddress() != null ? s.getIpAddress() : "",
                        "source", s.getSource()
                )).collect(Collectors.toList())
        );
    }
}
