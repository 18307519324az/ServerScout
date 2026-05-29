package com.serverscout.service.scan;

import com.serverscout.entity.ScanStrategyPlugin;
import com.serverscout.entity.ScanTask;
import com.serverscout.repository.ScanStrategyPluginRepository;
import com.serverscout.util.ScanException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class CustomCommandScanner implements ScannerStrategy {

    private final ScanStrategyPluginRepository pluginRepository;
    private final ProcessRegistry processRegistry;

    private static final java.util.Set<String> BUILTIN_TYPES =
            java.util.Set.of("quick", "full", "custom", "vuln", "nuclei");

    @Override
    public boolean supports(String scanType) {
        // Never intercept built-in scan types — those belong to NmapScanner/NucleiScanner
        if (BUILTIN_TYPES.contains(scanType)) return false;
        return pluginRepository.existsByScanType(scanType);
    }

    @Override
    public ScanResult execute(ScanTask task) {
        ScanStrategyPlugin plugin = pluginRepository.findByScanType(task.getScanType())
                .orElseThrow(() -> new ScanException("Plugin not found: " + task.getScanType()));

        if (!plugin.getEnabled()) {
            throw new ScanException("Plugin is disabled: " + plugin.getName());
        }

        try {
            String command = buildCommand(plugin, task);
            log.info("Executing plugin [{}]: {}", plugin.getName(), command);

            ProcessBuilder pb;
            boolean isWin = System.getProperty("os.name", "").toLowerCase().contains("win");
            if (isWin) {
                pb = new ProcessBuilder("cmd.exe", "/c", command);
            } else {
                pb = new ProcessBuilder("sh", "-c", command);
            }
            pb.redirectErrorStream(true);
            Process process = pb.start();
            processRegistry.register(task.getId(), process);

            List<String> outputLines = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    outputLines.add(line);
                }
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                log.warn("Plugin [{}] exited with code {}", plugin.getName(), exitCode);
            }

            return parseOutput(plugin, outputLines);

        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ScanException("Plugin execution failed: " + plugin.getName(), e);
        }
    }

    private String buildCommand(ScanStrategyPlugin plugin, ScanTask task) {
        String cmd = plugin.getCommandTemplate();
        if (cmd == null) cmd = "";
        cmd = cmd.replace("{target}", task.getTargetRange() != null ? task.getTargetRange() : "");
        cmd = cmd.replace("{port_range}", task.getPortRange() != null ? task.getPortRange() : "1-1000");
        cmd = cmd.replace("{task_name}", task.getName() != null ? task.getName() : "serverscout-task");
        return cmd;
    }

    private ScanResult parseOutput(ScanStrategyPlugin plugin, List<String> lines) {
        List<ScanResult.VulnEntry> vulns = new ArrayList<>();

        if ("line".equals(plugin.getResultParser()) && plugin.getFindingRegex() != null) {
            Pattern pattern = Pattern.compile(plugin.getFindingRegex());
            for (String line : lines) {
                Matcher m = pattern.matcher(line);
                if (m.find()) {
                    vulns.add(ScanResult.VulnEntry.builder()
                            .severity(extractGroup(m, "severity", "medium"))
                            .name(extractGroup(m, "name", line))
                            .url(extractGroup(m, "url", ""))
                            .matched(line.trim())
                            .template(plugin.getScanType())
                            .build());
                }
            }
        } else {
            // Raw output — wrap each non-empty line as a finding
            for (String line : lines) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty()) {
                    vulns.add(ScanResult.VulnEntry.builder()
                            .severity("info")
                            .name(trimmed.length() > 120 ? trimmed.substring(0, 117) + "..." : trimmed)
                            .matched(trimmed)
                            .template(plugin.getScanType())
                            .build());
                }
            }
        }

        return ScanResult.builder()
                .assets(new ArrayList<>())
                .vulnerabilities(vulns)
                .build();
    }

    private String extractGroup(Matcher m, String name, String defaultVal) {
        try {
            String val = m.group(name);
            return val != null ? val : defaultVal;
        } catch (IllegalArgumentException e) {
            return defaultVal;
        }
    }
}
