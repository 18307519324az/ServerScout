package com.serverscout.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.serverscout.entity.Asset;
import com.serverscout.entity.CveDatabase;
import com.serverscout.entity.Port;
import com.serverscout.repository.AssetRepository;
import com.serverscout.repository.CveDatabaseRepository;
import com.serverscout.repository.PortRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * External threat intelligence service.
 * Integrates Shodan InternetDB (free), NVD CVE API, AlienVault OTX, and more.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExternalIntelService {

    private final ObjectMapper objectMapper;
    private final AssetRepository assetRepository;
    private final PortRepository portRepository;
    private final CveDatabaseRepository cveDatabaseRepository;

    private static final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    // Simple in-memory cache with 10-minute TTL
    private final Map<String, CacheEntry<?>> cache = new ConcurrentHashMap<>();
    private static final long CACHE_TTL_MS = 10 * 60 * 1000;

    // ==================== Shodan InternetDB (Free, no API key) ====================

    /**
     * Lookup IP address using Shodan InternetDB free API.
     * Returns open ports, CVEs, hostnames, CPEs, and tags.
     */
    public Map<String, Object> lookupIpInternetDb(String ip) {
        String cacheKey = "internetdb:" + ip;
        @SuppressWarnings("unchecked")
        CacheEntry<Map<String, Object>> cached = (CacheEntry<Map<String, Object>>) cache.get(cacheKey);
        if (cached != null && !cached.isExpired()) {
            return cached.data();
        }

        try {
            String url = "https://internetdb.shodan.io/" + ip;
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .header("User-Agent", "ServerScout/1.0")
                    .GET().build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonNode node = objectMapper.readTree(response.body());

                Map<String, Object> result = new LinkedHashMap<>();
                result.put("ip", ip);
                result.put("ports", toList(node.get("ports")));
                result.put("vulns", toList(node.get("vulns")));
                result.put("hostnames", toList(node.get("hostnames")));
                result.put("cpes", toList(node.get("cpes")));
                result.put("tags", toList(node.get("tags")));

                // Enrich: describe each CVE
                List<String> vulns = toList(node.get("vulns"));
                List<Map<String, Object>> vulnDetails = new ArrayList<>();
                if (vulns != null) {
                    for (String cveId : vulns) {
                        try {
                            Map<String, Object> cveInfo = lookupCveDetails(cveId);
                            vulnDetails.add(cveInfo);
                        } catch (Exception e) {
                            vulnDetails.add(Map.of("cveId", cveId, "severity", "unknown"));
                        }
                    }
                }
                result.put("vulnDetails", vulnDetails);
                result.put("source", "internetdb");

                // Cross-reference with local asset database
                assetRepository.findByIpAddress(ip).ifPresent(asset -> {
                    result.put("localAssetId", asset.getId());
                    result.put("localHostname", asset.getHostname() != null ? asset.getHostname() : "");
                    result.put("localStatus", asset.getStatus() != null ? asset.getStatus() : "unknown");
                });

                cache.put(cacheKey, new CacheEntry<>(result));
                return result;
            }
        } catch (Exception e) {
            log.warn("InternetDB lookup failed for {}: {}", ip, e.getMessage());
        }

        // Fallback: check local asset database
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ip", ip);
        result.put("ports", List.of());
        result.put("vulns", List.of());
        result.put("hostnames", List.of());
        result.put("cpes", List.of());
        result.put("tags", List.of());
        result.put("vulnDetails", List.of());

        Optional<Asset> localAsset = assetRepository.findByIpAddress(ip);
        if (localAsset.isPresent()) {
            Asset asset = localAsset.get();
            result.put("localAssetId", asset.getId());
            result.put("localHostname", asset.getHostname() != null ? asset.getHostname() : "");
            result.put("localStatus", asset.getStatus() != null ? asset.getStatus() : "unknown");
            result.put("localOpenPorts", asset.getOpenPortCount() != null ? asset.getOpenPortCount() : 0);
            result.put("localCriticalVulns", asset.getCriticalVulnCount() != null ? asset.getCriticalVulnCount() : 0);

            List<Port> ports = portRepository.findByAssetId(asset.getId());
            List<Integer> portNumbers = ports.stream().map(Port::getPortNumber).distinct().sorted().toList();
            result.put("ports", portNumbers.stream().map(String::valueOf).toList());
            result.put("localPortDetails", ports.stream().map(p -> {
                Map<String, Object> pd = new LinkedHashMap<>();
                pd.put("port", p.getPortNumber());
                pd.put("protocol", p.getProtocol() != null ? p.getProtocol() : "tcp");
                pd.put("service", p.getServiceName() != null ? p.getServiceName() : "");
                pd.put("version", p.getServiceVersion() != null ? p.getServiceVersion() : "");
                return pd;
            }).toList());
            result.put("source", "local");
        } else {
            result.put("source", "none");
        }

        cache.put(cacheKey, new CacheEntry<>(result));
        return result;
    }

    // ==================== NVD CVE API v2 ====================

    /**
     * Fetch CVE details from NVD NIST API v2.
     */
    public Map<String, Object> lookupCveDetails(String cveId) {
        String cacheKey = "cve:" + cveId;
        @SuppressWarnings("unchecked")
        CacheEntry<Map<String, Object>> cached = (CacheEntry<Map<String, Object>>) cache.get(cacheKey);
        if (cached != null && !cached.isExpired()) {
            return cached.data();
        }

        try {
            String url = "https://services.nvd.nist.gov/rest/json/cves/2.0?cveId=" + cveId;
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(15))
                    .header("User-Agent", "ServerScout/1.0")
                    .GET().build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(response.body());
                JsonNode vulns = root.get("vulnerabilities");

                Map<String, Object> result = new LinkedHashMap<>();
                result.put("cveId", cveId);

                if (vulns != null && vulns.size() > 0) {
                    JsonNode cve = vulns.get(0).get("cve");
                    result.put("description", extractEnglishDescription(cve.get("descriptions")));
                    result.put("published", cve.get("published") != null ? cve.get("published").asText() : null);
                    result.put("lastModified", cve.get("lastModified") != null ? cve.get("lastModified").asText() : null);

                    // Metrics
                    JsonNode metrics = cve.get("metrics");
                    if (metrics != null) {
                        JsonNode cvss31 = metrics.get("cvssMetricV31");
                        JsonNode cvss30 = metrics.get("cvssMetricV30");
                        JsonNode cvss20 = metrics.get("cvssMetricV2");
                        JsonNode primary = null;

                        if (cvss31 != null && cvss31.size() > 0) {
                            primary = cvss31.get(0).get("cvssData");
                        } else if (cvss30 != null && cvss30.size() > 0) {
                            primary = cvss30.get(0).get("cvssData");
                        } else if (cvss20 != null && cvss20.size() > 0) {
                            primary = cvss20.get(0).get("cvssData");
                        }

                        if (primary != null) {
                            result.put("cvssScore", primary.get("baseScore").asDouble());
                            result.put("severity", primary.get("baseSeverity") != null
                                    ? primary.get("baseSeverity").asText().toLowerCase() : "unknown");
                            result.put("vectorString", primary.has("vectorString")
                                    ? primary.get("vectorString").asText() : null);
                            result.put("attackVector", primary.has("attackVector")
                                    ? primary.get("attackVector").asText() : null);
                            result.put("exploitabilityScore", cvss31 != null ? cvss31.get(0).get("exploitabilityScore").asDouble() : 0);
                            result.put("impactScore", cvss31 != null ? cvss31.get(0).get("impactScore").asDouble() : 0);
                        }
                    }

                    // Weaknesses (CWE)
                    JsonNode weaknesses = cve.get("weaknesses");
                    if (weaknesses != null && weaknesses.size() > 0) {
                        List<String> cwes = new ArrayList<>();
                        for (JsonNode w : weaknesses) {
                            JsonNode desc = w.get("description");
                            if (desc != null) {
                                for (JsonNode d : desc) {
                                    if ("en".equals(d.get("lang").asText()) && d.has("value")) {
                                        cwes.add(d.get("value").asText());
                                    }
                                }
                            }
                        }
                        result.put("cwes", cwes);
                    }

                    // Configurations (affected products)
                    JsonNode configs = cve.get("configurations");
                    if (configs != null && configs.size() > 0) {
                        Set<String> products = new LinkedHashSet<>();
                        for (JsonNode config : configs) {
                            JsonNode nodes = config.get("nodes");
                            if (nodes != null) {
                                for (JsonNode node : nodes) {
                                    JsonNode cpeMatch = node.get("cpeMatch");
                                    if (cpeMatch != null) {
                                        for (JsonNode cpe : cpeMatch) {
                                            if (cpe.get("criteria") != null) {
                                                String criteria = cpe.get("criteria").asText();
                                                // Parse CPE: cpe:2.3:a:vendor:product:version:...
                                                String[] parts = criteria.split(":");
                                                if (parts.length >= 5) {
                                                    products.add(parts[3] + ":" + parts[4]);
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        result.put("affectedProducts", new ArrayList<>(products));
                    }

                    // References
                    JsonNode refs = cve.get("references");
                    if (refs != null) {
                        List<Map<String, String>> references = new ArrayList<>();
                        for (JsonNode ref : refs) {
                            references.add(Map.of(
                                    "url", ref.get("url") != null ? ref.get("url").asText() : "",
                                    "source", ref.get("source") != null ? ref.get("source").asText() : "NVD"
                            ));
                        }
                        result.put("references", references);
                    }
                }

                cache.put(cacheKey, new CacheEntry<>(result));
                return result;
            }
        } catch (Exception e) {
            log.warn("NVD CVE lookup failed for {}: {}", cveId, e.getMessage());
        }
        return Map.of("cveId", cveId, "error", "Failed to fetch CVE details");
    }

    /**
     * Search CVEs by keyword from NVD API.
     */
    public Map<String, Object> searchCves(String keyword, int page, int size) {
        try {
            String url = "https://services.nvd.nist.gov/rest/json/cves/2.0"
                    + "?keywordSearch=" + java.net.URLEncoder.encode(keyword, "UTF-8")
                    + "&resultsPerPage=" + size
                    + "&startIndex=" + (page * size);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(20))
                    .header("User-Agent", "ServerScout/1.0")
                    .GET().build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(response.body());
                List<Map<String, Object>> results = new ArrayList<>();
                JsonNode vulns = root.get("vulnerabilities");

                if (vulns != null) {
                    for (JsonNode v : vulns) {
                        JsonNode cve = v.get("cve");
                        Map<String, Object> item = new LinkedHashMap<>();
                        item.put("cveId", cve.get("id").asText());
                        item.put("description", extractEnglishDescription(cve.get("descriptions")));
                        item.put("published", cve.get("published") != null ? cve.get("published").asText() : null);

                        JsonNode metrics = cve.get("metrics");
                        if (metrics != null) {
                            JsonNode cvss31 = metrics.get("cvssMetricV31");
                            if (cvss31 != null && cvss31.size() > 0) {
                                JsonNode cvss = cvss31.get(0).get("cvssData");
                                item.put("cvssScore", cvss.get("baseScore").asDouble());
                                item.put("severity", cvss.get("baseSeverity") != null
                                        ? cvss.get("baseSeverity").asText().toLowerCase() : "unknown");
                            }
                        }
                        results.add(item);
                    }
                }

                int totalResults = root.get("totalResults").asInt();
                return Map.of(
                        "content", results,
                        "totalResults", totalResults,
                        "page", page,
                        "size", size
                );
            }
        } catch (Exception e) {
            log.warn("NVD CVE search failed: {}", e.getMessage());
        }
        return Map.of("content", List.of(), "totalResults", 0);
    }

    /**
     * Get latest CVEs from NVD API.
     */
    public List<Map<String, Object>> getLatestCves(int limit) {
        try {
            String url = "https://services.nvd.nist.gov/rest/json/cves/2.0"
                    + "?pubStartDate=" + java.time.LocalDate.now().minusDays(30).toString() + "T00:00:00.000"
                    + "&pubEndDate=" + java.time.LocalDate.now().toString() + "T23:59:59.999"
                    + "&resultsPerPage=" + limit;

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(20))
                    .header("User-Agent", "ServerScout/1.0")
                    .GET().build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(response.body());
                List<Map<String, Object>> results = new ArrayList<>();
                JsonNode vulns = root.get("vulnerabilities");

                if (vulns != null) {
                    for (JsonNode v : vulns) {
                        JsonNode cve = v.get("cve");
                        Map<String, Object> item = new LinkedHashMap<>();
                        item.put("cveId", cve.get("id").asText());
                        item.put("description", extractEnglishDescription(cve.get("descriptions")));
                        item.put("published", cve.get("published") != null ? cve.get("published").asText() : null);

                        JsonNode metrics = cve.get("metrics");
                        if (metrics != null) {
                            JsonNode cvss31 = metrics.get("cvssMetricV31");
                            if (cvss31 != null && cvss31.size() > 0) {
                                JsonNode cvss = cvss31.get(0).get("cvssData");
                                item.put("cvssScore", cvss.get("baseScore").asDouble());
                                item.put("severity", cvss.get("baseSeverity") != null
                                        ? cvss.get("baseSeverity").asText().toLowerCase() : "unknown");
                            }
                        }
                        results.add(item);
                    }
                }
                return results;
            }
        } catch (Exception e) {
            log.warn("Latest CVEs fetch failed: {}", e.getMessage());
        }
        return List.of();
    }

    // ==================== AlienVault OTX ====================

    /**
     * Query AlienVault OTX for domain intelligence (passive DNS, subdomains).
     * Free API, no key required for basic queries.
     */
    public Map<String, Object> lookupDomainOtx(String domain) {
        String cacheKey = "otx:" + domain;
        @SuppressWarnings("unchecked")
        CacheEntry<Map<String, Object>> cached = (CacheEntry<Map<String, Object>>) cache.get(cacheKey);
        if (cached != null && !cached.isExpired()) {
            return cached.data();
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("domain", domain);

        try {
            // Passive DNS
            String url = "https://otx.alienvault.com/api/v1/indicators/domain/" + domain + "/passive_dns";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(15))
                    .header("User-Agent", "ServerScout/1.0")
                    .GET().build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(response.body());
                List<Map<String, Object>> records = new ArrayList<>();
                Set<String> subdomains = new LinkedHashSet<>();

                JsonNode dnsRecords = root.get("passive_dns");
                if (dnsRecords != null) {
                    for (JsonNode record : dnsRecords) {
                        String hostname = record.has("hostname") ? record.get("hostname").asText() : null;
                        String address = record.has("address") ? record.get("address").asText() : null;
                        if (hostname != null && hostname.endsWith("." + domain)) {
                            subdomains.add(hostname);
                            records.add(Map.of(
                                    "hostname", hostname,
                                    "ip", address != null ? address : "",
                                    "recordType", record.has("record_type") ? record.get("record_type").asText() : "A"
                            ));
                        }
                    }
                }
                result.put("passiveDnsCount", records.size());
                result.put("subdomains", new ArrayList<>(subdomains));
                result.put("passiveDnsRecords", records);
            }
        } catch (Exception e) {
            log.warn("OTX lookup failed for {}: {}", domain, e.getMessage());
        }

        // URLScan.io subdomains
        try {
            String urlScanUrl = "https://urlscan.io/api/v1/search/?q=domain:" + domain + "&size=20";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(urlScanUrl))
                    .timeout(Duration.ofSeconds(15))
                    .header("User-Agent", "ServerScout/1.0")
                    .GET().build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(response.body());
                List<Map<String, Object>> urlScanResults = new ArrayList<>();
                JsonNode results = root.get("results");
                if (results != null) {
                    for (JsonNode r : results) {
                        String pageUrl = r.has("page") && r.get("page").has("url")
                                ? r.get("page").get("url").asText() : null;
                        if (pageUrl != null) {
                            try {
                                String host = new java.net.URL(pageUrl).getHost();
                                urlScanResults.add(Map.of(
                                        "url", pageUrl,
                                        "host", host,
                                        "screenshot", r.has("screenshot") ? r.get("screenshot").asText() : ""
                                ));
                            } catch (Exception ignored) {}
                        }
                    }
                }
                result.put("urlScanResults", urlScanResults);
            }
        } catch (Exception e) {
            log.warn("URLScan lookup failed for {}: {}", domain, e.getMessage());
        }

        cache.put(cacheKey, new CacheEntry<>(result));
        return result;
    }

    /**
     * Get IP reputation from AlienVault OTX (free).
     */
    public Map<String, Object> lookupIpReputation(String ip) {
        String cacheKey = "otx-ip:" + ip;
        @SuppressWarnings("unchecked")
        CacheEntry<Map<String, Object>> cached = (CacheEntry<Map<String, Object>>) cache.get(cacheKey);
        if (cached != null && !cached.isExpired()) {
            return cached.data();
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ip", ip);

        try {
            String url = "https://otx.alienvault.com/api/v1/indicators/IPv4/" + ip + "/general";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(15))
                    .header("User-Agent", "ServerScout/1.0")
                    .GET().build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(response.body());
                result.put("pulseCount", root.has("pulse_info") && root.get("pulse_info").has("count")
                        ? root.get("pulse_info").get("count").asInt() : 0);
                result.put("country", root.has("country_name") ? root.get("country_name").asText() : "unknown");
                result.put("city", root.has("city") ? root.get("city").asText() : "unknown");
                result.put("whois", root.has("whois") ? root.get("whois").asText() : null);

                // Extract pulses (threat intelligence reports)
                JsonNode pulseInfo = root.get("pulse_info");
                if (pulseInfo != null && pulseInfo.has("pulses")) {
                    List<Map<String, Object>> pulses = new ArrayList<>();
                    for (JsonNode pulse : pulseInfo.get("pulses")) {
                        pulses.add(Map.of(
                                "name", pulse.has("name") ? pulse.get("name").asText() : "",
                                "tags", pulse.has("tags") ? toList(pulse.get("tags")) : List.of(),
                                "created", pulse.has("created") ? pulse.get("created").asText() : ""
                        ));
                    }
                    result.put("pulses", pulses);
                }
            }
        } catch (Exception e) {
            log.warn("OTX IP reputation lookup failed for {}: {}", ip, e.getMessage());
        }

        cache.put(cacheKey, new CacheEntry<>(result));
        return result;
    }

    // ==================== EPSS (Exploit Prediction Scoring System) ====================

    /**
     * Get EPSS score for a CVE (predicts likelihood of exploitation).
     * EPSS is free and maintained by FIRST.org.
     */
    public Map<String, Object> getEpssScore(String cveId) {
        String cacheKey = "epss:" + cveId;
        @SuppressWarnings("unchecked")
        CacheEntry<Map<String, Object>> cached = (CacheEntry<Map<String, Object>>) cache.get(cacheKey);
        if (cached != null && !cached.isExpired()) {
            return cached.data();
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("cveId", cveId);

        try {
            String url = "https://api.first.org/data/v1/epss?cve=" + cveId;
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .header("User-Agent", "ServerScout/1.0")
                    .GET().build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(response.body());
                JsonNode data = root.get("data");
                if (data != null && data.size() > 0) {
                    JsonNode epssData = data.get(0);
                    result.put("epss", epssData.has("epss") ? epssData.get("epss").asDouble() : 0);
                    result.put("percentile", epssData.has("percentile") ? epssData.get("percentile").asDouble() : 0);
                    result.put("date", epssData.has("date") ? epssData.get("date").asText() : null);
                }
            }
        } catch (Exception e) {
            log.warn("EPSS lookup failed for {}: {}", cveId, e.getMessage());
        }

        cache.put(cacheKey, new CacheEntry<>(result));
        return result;
    }

    // ==================== Combined Intelligence Report ====================

    /**
     * Generate a combined intelligence report for a given IP or domain.
     */
    public Map<String, Object> getCombinedReport(String target) {
        boolean isIp = target.matches("^\\d+\\.\\d+\\.\\d+\\.\\d+$");
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("target", target);
        report.put("type", isIp ? "ip" : "domain");

        if (isIp) {
            // IP Intelligence
            report.put("internetDb", lookupIpInternetDb(target));
            report.put("reputation", lookupIpReputation(target));
        } else {
            // Domain Intelligence
            report.put("otx", lookupDomainOtx(target));
        }

        return report;
    }

    // ==================== Scheduled CVE Refresh ====================

    /**
     * Daily refresh of the latest CVEs from NVD API.
     * Runs at 3:07 AM every day to avoid rate limiting.
     */
    @Scheduled(cron = "0 7 3 * * *")
    public void scheduledCveRefresh() {
        log.info("Starting scheduled CVE refresh from NVD...");
        try {
            List<Map<String, Object>> latest = getLatestCves(50);
            int added = 0;
            for (Map<String, Object> cveData : latest) {
                String cveId = (String) cveData.get("cveId");
                if (cveId != null && cveDatabaseRepository.findByCveId(cveId).isEmpty()) {
                    try {
                        CveDatabase cve = CveDatabase.builder()
                                .cveId(cveId)
                                .description((String) cveData.get("description"))
                                .severity((String) cveData.get("severity"))
                                .cvssScore(cveData.get("cvssScore") != null
                                        ? java.math.BigDecimal.valueOf(((Number) cveData.get("cvssScore")).doubleValue())
                                        : null)
                                .publicationDate(cveData.get("published") != null
                                        ? java.time.LocalDate.parse(((String) cveData.get("published")).substring(0, 10))
                                        : null)
                                .build();
                        cveDatabaseRepository.save(cve);
                        added++;
                    } catch (Exception e) {
                        log.debug("Failed to save CVE {}: {}", cveId, e.getMessage());
                    }
                }
            }
            log.info("CVE refresh complete: {} new CVEs added", added);
        } catch (Exception e) {
            log.warn("Scheduled CVE refresh failed: {}", e.getMessage());
        }
    }

    // ==================== Utilities ====================

    private String extractEnglishDescription(JsonNode descriptions) {
        if (descriptions != null) {
            for (JsonNode desc : descriptions) {
                if ("en".equals(desc.get("lang").asText())) {
                    return desc.get("value").asText();
                }
            }
        }
        return "No description available";
    }

    private List<String> toList(JsonNode node) {
        if (node == null || !node.isArray()) return List.of();
        List<String> list = new ArrayList<>();
        for (JsonNode item : node) {
            list.add(item.asText());
        }
        return list;
    }

    private record CacheEntry<T>(T data, long timestamp) {
        CacheEntry(T data) { this(data, System.currentTimeMillis()); }
        boolean isExpired() { return System.currentTimeMillis() - timestamp > CACHE_TTL_MS; }
    }
}
