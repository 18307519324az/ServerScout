package com.serverscout.service.scan;

import com.serverscout.entity.ScanTask;
import com.serverscout.util.ScanException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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
public class NucleiScannerImpl implements ScannerStrategy {

    @Value("${app.scan.nuclei-path}")
    private String nucleiPath;

    private static final Pattern FINDING_PATTERN = Pattern.compile(
            "\\[([^]]+)\\]\\s+\\[([^]]+)\\]\\s+\\[([^]]+)\\]\\s+(.+?)\\s+\\[(.+?)\\]");

    @Override
    public boolean supports(String scanType) {
        return "vuln".equals(scanType) || "nuclei".equals(scanType);
    }

    @Override
    public ScanResult execute(ScanTask task) {
        try {
            List<String> command = buildCommand(task);
            log.info("Executing Nuclei: {}", String.join(" ", command));

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            List<ScanResult.VulnEntry> vulns = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    Matcher m = FINDING_PATTERN.matcher(line);
                    if (m.find()) {
                        vulns.add(ScanResult.VulnEntry.builder()
                                .severity(m.group(1).trim().toLowerCase())
                                .template(m.group(2).trim())
                                .url(m.group(3).trim())
                                .name(m.group(4).trim())
                                .matched(m.group(5).trim())
                                .build());
                    }
                }
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                log.warn("Nuclei exited with code: {}", exitCode);
            }

            return ScanResult.builder()
                    .assets(new ArrayList<>())
                    .vulnerabilities(vulns)
                    .build();

        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ScanException("Nuclei execution failed", e);
        }
    }

    private List<String> buildCommand(ScanTask task) {
        List<String> cmd = new ArrayList<>();
        cmd.add(nucleiPath);
        // Use proper URL format if the target looks like an IP/domain without scheme
        String target = task.getTargetRange();
        if (target.matches("^\\d+\\.\\d+\\.\\d+\\.\\d+$")) {
            target = "http://" + target;
        } else if (!target.startsWith("http")) {
            target = "http://" + target;
        }
        cmd.add("-target"); cmd.add(target);
        cmd.add("-no-interactsh");
        cmd.add("-silent");
        cmd.add("-severity"); cmd.add("critical,high,medium,low");
        cmd.add("-timeout"); cmd.add("10");
        return cmd;
    }
}