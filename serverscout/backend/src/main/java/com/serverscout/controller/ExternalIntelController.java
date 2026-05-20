package com.serverscout.controller;

import com.serverscout.dto.ApiResponse;
import com.serverscout.service.ExternalIntelService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * External Threat Intelligence API.
 * Integrates Shodan InternetDB, NVD CVE, AlienVault OTX, EPSS.
 */
@RestController
@RequestMapping("/api/v1/intel")
@RequiredArgsConstructor
public class ExternalIntelController {

    private final ExternalIntelService externalIntelService;

    /** Lookup IP via Shodan InternetDB (free, no key) */
    @GetMapping("/ip/{ip}")
    public ApiResponse<Map<String, Object>> lookupIp(@PathVariable String ip) {
        return ApiResponse.success(externalIntelService.lookupIpInternetDb(ip));
    }

    /** Get CVE details from NVD NIST */
    @GetMapping("/cve/{cveId}")
    public ApiResponse<Map<String, Object>> getCveDetails(@PathVariable String cveId) {
        return ApiResponse.success(externalIntelService.lookupCveDetails(cveId));
    }

    /** Search CVEs from NVD */
    @GetMapping("/cves/search")
    public ApiResponse<Map<String, Object>> searchCves(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(externalIntelService.searchCves(keyword, page, size));
    }

    /** Get latest CVEs from NVD */
    @GetMapping("/cves/latest")
    public ApiResponse<?> getLatestCves(@RequestParam(defaultValue = "20") int limit) {
        return ApiResponse.success(externalIntelService.getLatestCves(limit));
    }

    /** Get EPSS exploitation prediction score for a CVE */
    @GetMapping("/cve/{cveId}/epss")
    public ApiResponse<Map<String, Object>> getEpssScore(@PathVariable String cveId) {
        return ApiResponse.success(externalIntelService.getEpssScore(cveId));
    }

    /** Get domain intelligence from AlienVault OTX + URLScan */
    @GetMapping("/domain/{domain}")
    public ApiResponse<Map<String, Object>> lookupDomain(@PathVariable String domain) {
        return ApiResponse.success(externalIntelService.lookupDomainOtx(domain));
    }

    /** Get IP reputation from AlienVault OTX */
    @GetMapping("/ip/{ip}/reputation")
    public ApiResponse<Map<String, Object>> getIpReputation(@PathVariable String ip) {
        return ApiResponse.success(externalIntelService.lookupIpReputation(ip));
    }

    /** Combined intelligence report for IP or domain */
    @GetMapping("/report")
    public ApiResponse<Map<String, Object>> getCombinedReport(@RequestParam String target) {
        return ApiResponse.success(externalIntelService.getCombinedReport(target));
    }
}
