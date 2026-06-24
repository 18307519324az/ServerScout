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

import org.springframework.core.io.ClassPathResource;

@Slf4j
@Service
public class ScreenshotService {

    private final Path screenshotDir;
    private final Path scriptPath;

    public ScreenshotService(@Value("${app.screenshot.dir:./screenshots}") String dir) {
        this.screenshotDir = Paths.get(dir);
        try {
            Files.createDirectories(screenshotDir);
        } catch (IOException e) {
            log.warn("Could not create screenshot dir: {}", e.getMessage());
        }

        // Resolve the Playwright script: always extract from classpath to ensure latest version
        Path resolved = null;
        try (InputStream is = new ClassPathResource("scripts/screenshot.mjs").getInputStream()) {
            Files.createDirectories(Paths.get("scripts"));
            Files.copy(is, Paths.get("scripts", "screenshot.mjs"),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            resolved = Paths.get("scripts", "screenshot.mjs").toAbsolutePath();
            log.info("Screenshot script extracted from classpath to: {}", resolved);
        } catch (IOException e) {
            // Try filesystem fallback
            resolved = Paths.get("scripts", "screenshot.mjs").toAbsolutePath();
            if (!Files.exists(resolved)) {
                resolved = Paths.get("backend", "scripts", "screenshot.mjs").toAbsolutePath();
            }
            if (!Files.exists(resolved)) {
                resolved = null;
            }
            log.warn("Screenshot: using fallback path: {} (classpath error: {})", resolved, e.getMessage());
        }
        this.scriptPath = resolved != null && Files.exists(resolved) ? resolved : null;
        if (this.scriptPath != null) {
            log.info("Screenshot script found: {}", this.scriptPath);
        } else {
            log.warn("Screenshot script not found, falling back to legacy tools");
        }
    }

    /**
     * Capture a screenshot of a web URL.
     * Tries Playwright first, then wkhtmltoimage, then cutycapt.
     * @return base64-encoded PNG data, or null if all methods failed
     */
    public String captureScreenshot(String url, int width, int height) {
        // Method 1: Playwright (Node.js)
        if (scriptPath != null) {
            String base64 = captureWithPlaywright(url, width, height);
            if (base64 != null) return base64;
        }

        // Method 2: wkhtmltoimage
        String base64 = tryCapture("wkhtmltoimage",
                "--width", String.valueOf(width),
                "--height", String.valueOf(height),
                "--quality", "80",
                "--format", "png",
                "--javascript-delay", "2000",
                url, "-");

        if (base64 == null) {
            // Method 3: cutycapt
            base64 = tryCapture("cutycapt",
                    "--url=" + url,
                    "--min-width=" + width,
                    "--min-height=" + height,
                    "--delay=2000",
                    "--out=-");
        }

        return base64;
    }

    /** Find a screenshot file by filename */
    public Path findScreenshot(String filename) {
        Path filePath = screenshotDir.resolve(filename);
        if (Files.exists(filePath)) return filePath;
        // Also check relative to working dir
        Path alt = Paths.get(filename);
        if (Files.exists(alt)) return alt;
        return null;
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

    /**
     * Capture screenshot using the Playwright Node.js script.
     */
    private String captureWithPlaywright(String url, int width, int height) {
        if (!isNodeAvailable()) {
            log.warn("Screenshot: Node.js not available (which node failed)");
            return null;
        }

        try {
            ProcessBuilder pb = new ProcessBuilder(
                    nodeCommand,
                    "--experimental-specifier-resolution=node",
                    scriptPath.toString(), url,
                    String.valueOf(width), String.valueOf(height));
            pb.redirectErrorStream(false);
            // Ensure node can find globally installed modules (playwright)
            String nodePath = System.getenv("NODE_PATH");
            if (nodePath == null || nodePath.isBlank()) {
                // Fallback to common global node_modules paths
                for (String p : new String[]{"/usr/lib/node_modules", "/usr/local/lib/node_modules"}) {
                    if (Files.exists(Paths.get(p, "playwright"))) { nodePath = p; break; }
                }
            }
            if (nodePath != null && !nodePath.isBlank()) {
                pb.environment().put("NODE_PATH", nodePath);
            }
            String chromiumPath = System.getenv("PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH");
            if (chromiumPath == null || chromiumPath.isBlank()) {
                chromiumPath = "/usr/lib64/chromium-browser/headless_shell";
            }
            pb.environment().put("PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH", chromiumPath);

            Process process = pb.start();
            ByteArrayOutputStream stdout = new ByteArrayOutputStream();
            ByteArrayOutputStream stderr = new ByteArrayOutputStream();

            Thread outThread = transferAsync(process.getInputStream(), stdout);
            Thread errThread = transferAsync(process.getErrorStream(), stderr);

            boolean finished = process.waitFor(35, TimeUnit.SECONDS);
            outThread.join(5000);
            errThread.join(1000);

            if (!finished) {
                process.destroyForcibly();
                log.warn("Playwright screenshot timed out for {}", url);
                return null;
            }

            if (process.exitValue() != 0) {
                String errMsg = stderr.toString().trim();
                log.warn("Playwright exited with {}: {}", process.exitValue(),
                        errMsg.length() > 300 ? errMsg.substring(0, 300) : errMsg);
                return null;
            }

            byte[] pngData = stdout.toByteArray();
            if (pngData.length > 100 && pngData[0] == (byte) 0x89 && pngData[1] == 'P') {
                log.info("Playwright captured screenshot: {} bytes", pngData.length);
                return Base64.getEncoder().encodeToString(pngData);
            }

            log.debug("Playwright output is not valid PNG ({} bytes)", pngData.length);
            return null;
        } catch (Exception e) {
            log.debug("Playwright capture failed: {}", e.getMessage());
            return null;
        }
    }

    private boolean isNodeAvailable() {
        // Try which/node first, then direct paths
        String[] nodePaths = {"node", "/usr/bin/node", "/usr/local/bin/node", "/bin/node"};
        for (String nodePath : nodePaths) {
            try {
                ProcessBuilder pb = new ProcessBuilder(nodePath, "--version");
                pb.redirectErrorStream(true);
                Process p = pb.start();
                if (p.waitFor(5, TimeUnit.SECONDS) && p.exitValue() == 0) {
                    // Found working node — if it's a custom path, set it for later use
                    if (!"node".equals(nodePath)) {
                        this.nodeCommand = nodePath;
                    }
                    return true;
                }
            } catch (Exception e) {
                // try next
            }
        }
        return false;
    }

    private String nodeCommand = "node";

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

    private Thread transferAsync(InputStream in, OutputStream out) {
        Thread t = new Thread(() -> {
            try {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) != -1) {
                    out.write(buf, 0, n);
                }
            } catch (IOException ignored) {}
        });
        t.start();
        return t;
    }
}
