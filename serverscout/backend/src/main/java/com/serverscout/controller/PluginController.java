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
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/plugins")
@RequiredArgsConstructor
public class PluginController {

    private final ScanStrategyPluginRepository pluginRepository;

    @GetMapping
    public ApiResponse<List<ScanStrategyPlugin>> listPlugins() {
        return ApiResponse.success(pluginRepository.findAll());
    }

    @GetMapping("/{id}")
    public ApiResponse<ScanStrategyPlugin> getPlugin(@PathVariable Long id) {
        return pluginRepository.findById(id)
                .map(ApiResponse::success)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Plugin not found"));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ApiResponse<ScanStrategyPlugin> createPlugin(@Valid @RequestBody CreatePluginRequest request) {
        if (pluginRepository.existsByScanType(request.getScanType())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Plugin scanType already exists: " + request.getScanType());
        }
        ScanStrategyPlugin plugin = ScanStrategyPlugin.builder()
                .name(request.getName())
                .scanType(request.getScanType())
                .description(request.getDescription())
                .commandTemplate(request.getCommandTemplate())
                .resultParser(request.getResultParser() != null ? request.getResultParser() : "line")
                .findingRegex(request.getFindingRegex())
                .enabled(true)
                .build();
        plugin = pluginRepository.save(plugin);
        log.info("Plugin created: {} (scanType={})", plugin.getName(), plugin.getScanType());
        return ApiResponse.success(plugin);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    public ApiResponse<ScanStrategyPlugin> updatePlugin(@PathVariable Long id,
                                                         @Valid @RequestBody CreatePluginRequest request) {
        ScanStrategyPlugin plugin = pluginRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Plugin not found"));

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
    public ApiResponse<ScanStrategyPlugin> togglePlugin(@PathVariable Long id) {
        ScanStrategyPlugin plugin = pluginRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Plugin not found"));
        plugin.setEnabled(!plugin.getEnabled());
        plugin = pluginRepository.save(plugin);
        log.info("Plugin {} {}", plugin.getName(), plugin.getEnabled() ? "enabled" : "disabled");
        return ApiResponse.success(plugin);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deletePlugin(@PathVariable Long id) {
        ScanStrategyPlugin plugin = pluginRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Plugin not found"));
        pluginRepository.delete(plugin);
        log.info("Plugin deleted: {} (id={})", plugin.getName(), id);
        return ApiResponse.success(null);
    }

    /** List available scan types: built-in + user plugins */
    @GetMapping("/scan-types")
    public ApiResponse<List<String>> listScanTypes() {
        List<String> builtin = List.of("quick", "full", "custom", "vuln", "nuclei");
        List<String> pluginTypes = pluginRepository.findAllByEnabledTrue().stream()
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
