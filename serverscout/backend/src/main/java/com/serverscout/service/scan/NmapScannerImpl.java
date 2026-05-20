package com.serverscout.service.scan;

import com.serverscout.entity.ScanTask;
import com.serverscout.util.ScanException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
public class NmapScannerImpl implements ScannerStrategy {

    @Value("${app.scan.nmap-path}")
    private String nmapPath;

    @Override
    public boolean supports(String scanType) {
        return "quick".equals(scanType) || "full".equals(scanType) || "custom".equals(scanType);
    }

    @Override
    public ScanResult execute(ScanTask task) {
        try {
            List<String> command = buildCommand(task);
            log.info("Executing Nmap: {}", String.join(" ", command));

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new ScanException("Nmap exited with code: " + exitCode);
            }

            return parseXmlOutput(output.toString());

        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ScanException("Nmap execution failed", e);
        }
    }

    private List<String> buildCommand(ScanTask task) {
        List<String> cmd = new ArrayList<>();
        cmd.add(nmapPath);
        cmd.add("-oX"); cmd.add("-");
        cmd.add("--stats-every"); cmd.add("5s");

        switch (task.getScanType()) {
            case "quick" -> {
                cmd.add("-sS");
                cmd.add("-sV");
                cmd.add("--top-ports"); cmd.add("100");
                cmd.add("--version-intensity"); cmd.add("3");
                cmd.add("--script"); cmd.add("banner");
                cmd.add("--script-timeout"); cmd.add("30s");
            }
            case "full" -> {
                cmd.add("-sS");
                cmd.add("-sV");
                cmd.add("-O");
                cmd.add("--version-intensity"); cmd.add("7");
                cmd.add("--script"); cmd.add("banner,http-title,ssl-cert,ssl-enum-ciphers");
                cmd.add("--script-timeout"); cmd.add("60s");
            }
        }

        if (task.getPortRange() != null) {
            cmd.add("-p"); cmd.add(task.getPortRange());
        } else if ("full".equals(task.getScanType())) {
            cmd.add("-p"); cmd.add("1-65535");
        }

        cmd.add(task.getTargetRange());
        return cmd;
    }

    private ScanResult parseXmlOutput(String xml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new org.xml.sax.InputSource(new StringReader(xml)));

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
