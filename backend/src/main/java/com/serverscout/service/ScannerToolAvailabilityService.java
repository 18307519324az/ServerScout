package com.serverscout.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class ScannerToolAvailabilityService {

    private static final long TIMEOUT_SECONDS = 2;

    public boolean isNmapAvailable() {
        return checkToolAvailable("nmap");
    }

    public boolean isNucleiAvailable() {
        return checkToolAvailable("nuclei");
    }

    private boolean checkToolAvailable(String toolName) {
        try {
            String os = System.getProperty("os.name").toLowerCase();
            ProcessBuilder pb;
            if (os.contains("win")) {
                pb = new ProcessBuilder("where.exe", toolName);
            } else {
                pb = new ProcessBuilder("which", toolName);
            }
            pb.redirectErrorStream(false);
            pb.redirectOutput(new File(os.contains("win") ? "NUL" : "/dev/null"));
            pb.redirectError(new File(os.contains("win") ? "NUL" : "/dev/null"));

            Process process = pb.start();
            boolean finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                log.debug("{} detection timed out after {}s", toolName, TIMEOUT_SECONDS);
                return false;
            }
            return process.exitValue() == 0;
        } catch (Exception e) {
            log.debug("Failed to detect {}: {}", toolName, e.getMessage());
            return false;
        }
    }
}
