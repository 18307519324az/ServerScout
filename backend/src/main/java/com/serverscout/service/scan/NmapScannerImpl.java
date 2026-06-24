package com.serverscout.service.scan;

import com.serverscout.entity.ScanTask;
import com.serverscout.service.SystemConfigService;
import com.serverscout.exception.ScanException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class NmapScannerImpl implements ScannerStrategy {

    @Value("${app.scan.nmap-path}")
    private String nmapPath;

    @Value("${app.scan.default-timeout-seconds:300}")
    private int defaultTimeoutSeconds;

    @Value("${app.scan.host-discovery-timeout-seconds:60}")
    private int hostDiscoveryTimeoutSeconds;

    @Value("${app.scan.quick-timeout-seconds:120}")
    private int quickTimeoutSeconds;

    @Value("${app.scan.full-timeout-seconds:900}")
    private int fullTimeoutSeconds;

    @Value("${app.scan.stealth-timeout-seconds:180}")
    private int stealthTimeoutSeconds;

    private final SystemConfigService configService;
    private final ProcessRegistry processRegistry;

    public NmapScannerImpl(SystemConfigService configService, ProcessRegistry processRegistry) {
        this.configService = configService;
        this.processRegistry = processRegistry;
    }

    private String getNmapPath() {
        return configService.getConfig("nmap-path", nmapPath);
    }

    @Override
    public boolean supports(String scanType) {
        if (scanType == null) return false;
        return switch (scanType.toUpperCase(java.util.Locale.ROOT)) {
            case "QUICK", "FULL", "CUSTOM", "HOST_DISCOVERY", "STEALTH", "WEB" -> true;
            default -> false;
        };
    }

    @Override
    public ScanResult execute(ScanTask task) {
        try {
            List<String> command = buildCommand(task);
            int timeout = getTimeoutForScanType(task.getScanType());
            return executeCommand(command, task.getId(), timeout);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ScanException("Nmap execution failed", e);
        } catch (IOException e) {
            throw new ScanException("Nmap execution failed", e);
        }
    }

    private int getTimeoutForScanType(String scanType) {
        return switch (scanType) {
            case "host_discovery" -> hostDiscoveryTimeoutSeconds;
            case "quick", "QUICK", "WEB" -> quickTimeoutSeconds;
            case "STEALTH" -> stealthTimeoutSeconds;
            case "full", "FULL" -> fullTimeoutSeconds;
            default -> defaultTimeoutSeconds;
        };
    }

    private ScanResult executeCommand(List<String> command, Long taskId, int timeoutSeconds) throws IOException, InterruptedException {
        log.info("Executing Nmap: {}", String.join(" ", command));

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(false);
        Process process = pb.start();
        processRegistry.register(taskId, process);

        CompletableFuture<String> stdoutFuture = CompletableFuture.supplyAsync(() ->
                readStream(process.getInputStream()));
        CompletableFuture<String> stderrFuture = CompletableFuture.supplyAsync(() ->
                readStream(process.getErrorStream()));

        boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            log.error("Nmap process timed out after {}s for task {}", timeoutSeconds, taskId);
            throw new ScanException("Nmap execution timed out after " + timeoutSeconds + "s");
        }

        String stdout = stdoutFuture.join();
        String stderr = stderrFuture.join();

        if (stderr != null && !stderr.isBlank()) {
            String compact = stderr.length() > 800 ? stderr.substring(0, 800) + "..." : stderr;
            log.debug("Nmap stderr: {}", compact.replace("\r", " ").replace("\n", " | "));
        }

        int exitCode = process.exitValue();
        if (exitCode != 0) {
            // SYN scan (-sS) and OS detection (-O) both require root/cap_net_raw.
            // Fall back to TCP connect scan (-sT) and remove -O for non-root environments.
            if (command.contains("-sS") || command.contains("-O")) {
                log.warn("Nmap exited with code {} — retrying with TCP connect scan (-sT) without OS detection", exitCode);
                List<String> retryCmd = new ArrayList<>(command);
                for (int i = 0; i < retryCmd.size(); i++) {
                    String arg = retryCmd.get(i);
                    if ("-sS".equals(arg)) {
                        retryCmd.set(i, "-sT");
                    }
                }
                retryCmd.removeIf(arg -> "-O".equals(arg));
                return executeCommand(retryCmd, taskId, timeoutSeconds);
            }
            log.error("Nmap exited with code {} for task {}: {}",
                    exitCode, taskId, stderr != null ? stderr : "(no stderr)");
            throw new ScanException("Nmap execution failed (exit code " + exitCode + ")");
        }

        return parseXmlOutput(stdout);
    }

    private List<String> buildCommand(ScanTask task) {
        List<String> cmd = new ArrayList<>();
        cmd.add(getNmapPath());
        cmd.add("-oX"); cmd.add("-");
        cmd.add("--stats-every"); cmd.add("5s");
        cmd.add("-Pn"); // skip host discovery (cloud servers block ICMP)
        cmd.add("-n"); // no DNS resolution
        cmd.add("-T3");

        switch (task.getScanType()) {
            case "host_discovery" -> {
                cmd.add("-sn");
                cmd.add("--host-timeout"); cmd.add("30s");
            }
            case "quick", "QUICK" -> {
                cmd.add("-sS");
                cmd.add("-sV");
                if (task.getPortRange() == null || task.getPortRange().isBlank()) {
                    cmd.add("--top-ports"); cmd.add("100");
                }
                cmd.add("--host-timeout"); cmd.add("60s");
                cmd.add("--version-intensity"); cmd.add("3");
                cmd.add("--script"); cmd.add("banner");
                cmd.add("--script-timeout"); cmd.add("30s");
                cmd.add("--min-rate"); cmd.add("500");
                cmd.add("--max-retries"); cmd.add("1");
            }
            case "STEALTH" -> {
                cmd.add("-sS");
                cmd.add("-sV");
                if (task.getPortRange() == null || task.getPortRange().isBlank()) {
                    cmd.add("--top-ports"); cmd.add("50");
                }
                cmd.add("--host-timeout"); cmd.add("120s");
                cmd.add("--version-intensity"); cmd.add("2");
                cmd.add("--scan-delay"); cmd.add("200ms");
                cmd.add("--max-retries"); cmd.add("1");
                cmd.add("-T2");
            }
            case "WEB" -> {
                cmd.add("-sS");
                cmd.add("-sV");
                if (task.getPortRange() == null || task.getPortRange().isBlank()) {
                    cmd.add("-p"); cmd.add("80,443,8080,8443,8000,3000,5000");
                }
                cmd.add("--host-timeout"); cmd.add("60s");
                cmd.add("--version-intensity"); cmd.add("5");
                cmd.add("--script"); cmd.add("banner,http-title,ssl-cert");
                cmd.add("--script-timeout"); cmd.add("30s");
                cmd.add("--max-retries"); cmd.add("1");
                cmd.add("-T3");
            }
            case "full", "FULL" -> {
                cmd.add("-sS");
                cmd.add("-sV");
                cmd.add("-O");
                cmd.add("--host-timeout"); cmd.add("600s");
                cmd.add("--version-intensity"); cmd.add("7");
                cmd.add("--script"); cmd.add("banner,http-title,ssl-cert,ssl-enum-ciphers");
                cmd.add("--script-timeout"); cmd.add("60s");
                cmd.add("--min-rate"); cmd.add("300");
                cmd.add("--max-retries"); cmd.add("1");
            }
        }

        if (task.getPortRange() != null && !task.getPortRange().isBlank()) {
            cmd.add("-p"); cmd.add(task.getPortRange());
        } else if ("FULL".equalsIgnoreCase(task.getScanType())) {
            cmd.add("-p"); cmd.add("1-65535");
        }

        cmd.add(task.getTargetRange());
        return cmd;
    }

    private ScanResult parseXmlOutput(String xml) {
        try {
            String xmlBody = extractXmlBody(xml);
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new org.xml.sax.InputSource(new StringReader(xmlBody)));

            NodeList hostNodes = doc.getElementsByTagName("host");
            List<ScanResult.AssetEntry> assets = new ArrayList<>();

            for (int i = 0; i < hostNodes.getLength(); i++) {
                Element host = (Element) hostNodes.item(i);

                if (!"up".equals(getChildText(host, "status", "state"))) continue;

                String ip = null;
                String macAddress = null;

                NodeList addrNodes = host.getElementsByTagName("address");
                for (int k = 0; k < addrNodes.getLength(); k++) {
                    Element addrEl = (Element) addrNodes.item(k);
                    String addrType = addrEl.getAttribute("addrtype");
                    String addrValue = addrEl.getAttribute("addr");
                    if ("ipv4".equals(addrType) && ip == null) {
                        ip = addrValue;
                    } else if ("mac".equals(addrType) || "macaddress".equals(addrType)) {
                        macAddress = addrValue;
                    }
                }
                if (ip == null && addrNodes.getLength() > 0) {
                    ip = ((Element) addrNodes.item(0)).getAttribute("addr");
                }

                String hostname = getNestedText(host, "hostname", "name");

                ScanResult.AssetEntry.AssetEntryBuilder assetBuilder = ScanResult.AssetEntry.builder()
                        .ipAddress(ip).hostname(hostname).macAddress(macAddress);

                Element os = getFirstChild(host, "os");
                if (os != null) {
                    Element osmatch = getFirstChild(os, "osmatch");
                    if (osmatch != null) {
                        assetBuilder.osFingerprint(osmatch.getAttribute("name"));
                    }
                }

                List<ScanResult.PortEntry> ports = new ArrayList<>();
                Element portsEl = getFirstChild(host, "ports");
                if (portsEl != null) {
                    NodeList portNodes = portsEl.getElementsByTagName("port");
                    for (int j = 0; j < portNodes.getLength(); j++) {
                        Element portEl = (Element) portNodes.item(j);
                        Element stateEl = getFirstChild(portEl, "state");
                        if (stateEl != null && "open".equals(stateEl.getAttribute("state"))) {
                            Element svc = getFirstChild(portEl, "service");
                            String banner = null;
                            // Extract banner from port-level NSE script output
                            Element scriptEl = getFirstChild(portEl, "script");
                            if (scriptEl != null) {
                                String scriptId = scriptEl.getAttribute("id");
                                String scriptOutput = scriptEl.getAttribute("output");
                                if (scriptOutput != null && !scriptOutput.isEmpty()) {
                                    banner = "[" + scriptId + "] " + scriptOutput;
                                }
                            }
                            ports.add(ScanResult.PortEntry.builder()
                                    .portNumber(Integer.parseInt(portEl.getAttribute("portid")))
                                    .protocol(portEl.getAttribute("protocol"))
                                    .state(stateEl.getAttribute("state"))
                                    .serviceName(svc != null ? svc.getAttribute("name") : null)
                                    .serviceVersion(svc != null ? svc.getAttribute("version") : null)
                                    .serviceProduct(svc != null ? svc.getAttribute("product") : null)
                                    .banner(banner)
                                    .build());
                        }
                    }
                }

                assetBuilder.ports(ports);
                assets.add(assetBuilder.build());
            }

            return ScanResult.builder().assets(assets).build();

        } catch (Exception e) {
            throw new ScanException("Failed to parse Nmap XML output", e);
        }
    }

    private String extractXmlBody(String raw) {
        if (raw == null) return "";
        String text = raw.trim();

        int start = text.indexOf("<?xml");
        if (start < 0) start = text.indexOf("<nmaprun");

        int end = text.lastIndexOf("</nmaprun>");
        if (start >= 0 && end >= 0 && end > start) {
            return text.substring(start, end + "</nmaprun>".length());
        }
        return text;
    }

    private String readStream(InputStream stream) {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
        } catch (IOException e) {
            log.debug("Failed reading process stream: {}", e.getMessage());
        }
        return sb.toString();
    }

    private String getChildText(Element parent, String tag, String attr) {
        Element el = getFirstChild(parent, tag);
        return el != null ? el.getAttribute(attr) : null;
    }

    private String getElementAttr(Element parent, String tag, String attr) {
        NodeList list = parent.getElementsByTagName(tag);
        return list.getLength() > 0 ? ((Element) list.item(0)).getAttribute(attr) : null;
    }

    private String getNestedText(Element parent, String tag, String attr) {
        NodeList list = parent.getElementsByTagName(tag);
        return list.getLength() > 0 ? ((Element) list.item(0)).getAttribute(attr) : null;
    }

    private Element getFirstChild(Element parent, String tag) {
        NodeList list = parent.getElementsByTagName(tag);
        return list.getLength() > 0 ? (Element) list.item(0) : null;
    }
}
