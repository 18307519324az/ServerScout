package com.serverscout.controller;

import com.serverscout.dto.ApiResponse;
import com.serverscout.entity.ScanStrategyPlugin;
import com.serverscout.repository.ScanStrategyPluginRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/plugins")
@RequiredArgsConstructor
public class PluginController {

    private static final java.util.Set<String> BUILTIN_SCAN_TYPES =
            java.util.Set.of("QUICK", "FULL", "CUSTOM", "VULN", "NUCLEI", "STEALTH", "WEB");

    private final ScanStrategyPluginRepository pluginRepository;

    private String getUsername(Authentication auth) {
        return auth != null ? auth.getName() : null;
    }

    private boolean isAdmin(Authentication auth) {
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }

    /** Verify ownership or admin role */
    private void checkOwnership(ScanStrategyPlugin plugin, Authentication auth) {
        String username = getUsername(auth);
        if (isAdmin(auth)) return;
        if (plugin.getCreatedBy() != null && !plugin.getCreatedBy().equals(username)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied: not your plugin");
        }
    }

    @GetMapping
    public ApiResponse<List<ScanStrategyPlugin>> listPlugins(Authentication auth) {
        if (isAdmin(auth)) {
            return ApiResponse.success(pluginRepository.findAll());
        }
        String username = getUsername(auth);
        // Regular users see only their own plugins (strict isolation)
        return ApiResponse.success(pluginRepository.findByCreatedBy(username));
    }

    @GetMapping("/{id}")
    public ApiResponse<ScanStrategyPlugin> getPlugin(@PathVariable Long id, Authentication auth) {
        ScanStrategyPlugin plugin = pluginRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Plugin not found"));
        checkOwnership(plugin, auth);
        return ApiResponse.success(plugin);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ApiResponse<ScanStrategyPlugin> createPlugin(@Valid @RequestBody CreatePluginRequest request,
                                                         Authentication auth) {
        if (pluginRepository.existsByScanType(request.getScanType())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Plugin scanType already exists: " + request.getScanType());
        }
        if (BUILTIN_SCAN_TYPES.contains(request.getScanType())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cannot override built-in scan type: " + request.getScanType());
        }
        ScanStrategyPlugin plugin = ScanStrategyPlugin.builder()
                .name(request.getName())
                .scanType(request.getScanType())
                .description(request.getDescription())
                .commandTemplate(request.getCommandTemplate())
                .resultParser(request.getResultParser() != null ? request.getResultParser() : "line")
                .findingRegex(request.getFindingRegex())
                .enabled(true)
                .createdBy(getUsername(auth))
                .build();
        plugin = pluginRepository.save(plugin);
        log.info("Plugin created: {} (scanType={}) by {}", plugin.getName(), plugin.getScanType(), plugin.getCreatedBy());
        return ApiResponse.success(plugin);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    public ApiResponse<ScanStrategyPlugin> updatePlugin(@PathVariable Long id,
                                                         @Valid @RequestBody CreatePluginRequest request,
                                                         Authentication auth) {
        ScanStrategyPlugin plugin = pluginRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Plugin not found"));
        checkOwnership(plugin, auth);

        plugin.setName(request.getName());
        plugin.setScanType(request.getScanType());
        plugin.setDescription(request.getDescription());
        plugin.setCommandTemplate(request.getCommandTemplate());
        plugin.setResultParser(request.getResultParser() != null ? request.getResultParser() : "line");
        plugin.setFindingRegex(request.getFindingRegex());
        plugin = pluginRepository.save(plugin);
        log.info("Plugin updated: {} (id={})", plugin.getName(), plugin.getId());
        return ApiResponse.success(plugin);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/{id}/toggle")
    public ApiResponse<ScanStrategyPlugin> togglePlugin(@PathVariable Long id, Authentication auth) {
        ScanStrategyPlugin plugin = pluginRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Plugin not found"));
        checkOwnership(plugin, auth);
        plugin.setEnabled(!plugin.getEnabled());
        plugin = pluginRepository.save(plugin);
        log.info("Plugin {} {}", plugin.getName(), plugin.getEnabled() ? "enabled" : "disabled");
        return ApiResponse.success(plugin);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deletePlugin(@PathVariable Long id, Authentication auth) {
        ScanStrategyPlugin plugin = pluginRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Plugin not found"));
        checkOwnership(plugin, auth);
        pluginRepository.delete(plugin);
        log.info("Plugin deleted: {} (id={})", plugin.getName(), id);
        return ApiResponse.success(null);
    }

    /** List available scan types: built-in + user plugins */
    @GetMapping("/scan-types")
    public ApiResponse<List<String>> listScanTypes(Authentication auth) {
        List<String> builtin = List.of("QUICK", "FULL", "CUSTOM", "VULN", "NUCLEI", "STEALTH", "WEB");
        List<ScanStrategyPlugin> plugins;
        if (isAdmin(auth)) {
            plugins = pluginRepository.findAllByEnabledTrue();
        } else {
            plugins = pluginRepository.findByCreatedByAndEnabledTrue(getUsername(auth));
        }
        List<String> pluginTypes = plugins.stream()
                .map(ScanStrategyPlugin::getScanType)
                .filter(t -> !builtin.contains(t))
                .toList();
        return ApiResponse.success(
                java.util.stream.Stream.concat(builtin.stream(), pluginTypes.stream()).toList());
    }

    @Data
    public static class CreatePluginRequest {
        @NotBlank private String name;
        @NotBlank private String scanType;
        private String description;
        @NotBlank private String commandTemplate;
        private String resultParser;
        private String findingRegex;
    }
}
