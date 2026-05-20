package com.serverscout.controller;

import com.serverscout.entity.Subdomain;
import com.serverscout.service.SubdomainService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/subdomains")
@RequiredArgsConstructor
public class SubdomainController {

    private final SubdomainService subdomainService;

    @PostMapping("/enumerate")
    public ResponseEntity<Map<String, Object>> enumerate(@RequestBody Map<String, String> body) {
        String domain = body.get("domain");
        if (domain == null || domain.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "domain is required"));
        }
        SubdomainService.SubdomainResult result = subdomainService.enumerate(domain);
        return ResponseEntity.ok(Map.of(
                "domain", result.domain(),
                "total", result.total(),
                "newCount", result.newCount(),
                "sources", result.sources()
        ));
    }

    @GetMapping("/domain/{domain}")
    public ResponseEntity<Map<String, Object>> getByDomain(@PathVariable String domain) {
        return ResponseEntity.ok(subdomainService.getStats(domain));
    }

    @GetMapping("/asset/{assetId}")
    public ResponseEntity<List<Subdomain>> getByAsset(@PathVariable Long assetId) {
        return ResponseEntity.ok(subdomainService.getSubdomainsByAsset(assetId));
    }
}
