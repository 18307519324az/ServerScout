package com.serverscout.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class ScreenshotService {

    private final Path screenshotDir;

    public ScreenshotService(@Value("${app.screenshot.dir:./screenshots}") String dir) {
        this.screenshotDir = Paths.get(dir);
        try {
            Files.createDirectories(screenshotDir);
        } catch (IOException e) {
            log.warn("Could not create screenshot dir: {}", e.getMessage());
        }
    }

    /**
     * Capture a screenshot of a web URL.
     * Tries wkhtmltoimage first, then cutycapt, falls back gracefully.
     * @return base64-encoded PNG data, or null if capture failed
     */
    public String captureScreenshot(String url, int width, int height) {
        // Try wkhtmltoimage
        String base64 = tryCapture("wkhtmltoimage",
                "--width", String.valueOf(width),
                "--height", String.valueOf(height),
                "--quality", "80",
                "--format", "png",
                url, "-");

        if (base64 == null) {
            // Try cutycapt
            base64 = tryCapture("cutycapt",
                    "--url=" + url,
                    "--min-width=" + width,
                    "--min-height=" + height,
                    "--out=-");
        }

        return base64;
    }

    public String captureAndSave(String url, int width, int height) {
        String base64 = captureScreenshot(url, width, height);
        if (base64 == null) return null;

        try {
            String filename = "screenshot_" + UUID.randomUUID().toString().substring(0, 8) + ".png";
            Path filePath = screenshotDir.resolve(filename);
            byte[] data = Base64.getDecoder().decode(base64);
            Files.write(filePath, data);
            return filename;
        } catch (IOException e) {
            log.error("Failed to save screenshot: {}", e.getMessage());
            return null;
        }
    }

    private String tryCapture(String tool, String... args) {
        if (!isToolAvailable(tool)) return null;

        try {
            ProcessBuilder pb = new ProcessBuilder();
            pb.command(buildCommand(tool, args));
            pb.redirectErrorStream(true);

            Process process = pb.start();
            ByteArrayOutputStream output = new ByteArrayOutputStream();

            try (InputStream is = process.getInputStream()) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = is.read(buf)) != -1) {
                    output.write(buf, 0, n);
                }
            }

            boolean finished = process.waitFor(30, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                log.warn("{} timed out", tool);
                return null;
            }

            byte[] pngData = output.toByteArray();
            if (pngData.length > 100 && pngData[0] == (byte) 0x89 && pngData[1] == 'P') {
                log.info("{} captured screenshot: {} bytes", tool, pngData.length);
                return Base64.getEncoder().encodeToString(pngData);
            }

            log.debug("{} output is not valid PNG ({} bytes)", tool, pngData.length);
            return null;
        } catch (Exception e) {
            log.debug("{} capture failed: {}", tool, e.getMessage());
            return null;
        }
    }

    private boolean isToolAvailable(String tool) {
        try {
            boolean isWin = System.getProperty("os.name", "").toLowerCase().contains("win");
            String[] cmd = isWin ? new String[]{"where", tool} : new String[]{"which", tool};
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            return p.waitFor(5, TimeUnit.SECONDS) && p.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private String[] buildCommand(String tool, String... args) {
        String[] cmd = new String[args.length + 1];
        cmd[0] = tool;
        System.arraycopy(args, 0, cmd, 1, args.length);
        return cmd;
    }
}
