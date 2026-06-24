package com.serverscout.service.scan;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.serverscout.entity.Port;
import com.serverscout.entity.WebFingerprint;
import com.serverscout.repository.WebFingerprintRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
public class HttpProbeService {

    private final HttpClient httpClient;
    private final WebFingerprintRepository webFingerprintRepository;
    private final ObjectMapper objectMapper;
    private List<FingerprintRule> rules;

    @Value("${app.scan.http-probe-timeout:3000}")
    private int probeTimeout;

    public HttpProbeService(WebFingerprintRepository webFingerprintRepository,
                            ObjectMapper objectMapper) {
        this.webFingerprintRepository = webFingerprintRepository;
        this.objectMapper = objectMapper;
        this.httpClient = buildInsecureHttpClient();
        loadRules();
    }

    public record ProbeResult(
            int httpStatus,
            String serverHeader,
            Map<String, String> responseHeaders,
            String title,
            String bodyText,
            String bodyHash,
            String faviconHash,
            String frameworkName,
            String frameworkVersion,
            String cmsName,
            String cmsVersion,
            String wafName,
            List<String> techStack,
            String responseSummary
    ) {}

    public ProbeResult probePort(String ip, Port port) {
        int portNum = port.getPortNumber();
        boolean isSsl = portNum == 443 || portNum == 8443 ||
                        (port.getServiceName() != null &&
                         port.getServiceName().toLowerCase().contains("https"));

        String scheme = isSsl ? "https" : "http";
        String url = scheme + "://" + ip + (portNum != 80 && portNum != 443 ? ":" + portNum : "") + "/";

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofMillis(probeTimeout))
                    .header("User-Agent", "Mozilla/5.0 (compatible; ServerScout/1.0)")
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());

            String body = response.body() != null ? response.body() : "";
            Map<String, String> headers = response.headers().map().entrySet().stream()
                    .collect(Collectors.toMap(Map.Entry::getKey,
                            e -> String.join(", ", e.getValue()),
                            (a, b) -> a, TreeMap::new));

            String serverHeader = headers.getOrDefault("Server",
                    headers.getOrDefault("server", null));
            String title = extractTitle(body);
            String bodyHash = sha256(body.substring(0, Math.min(body.length(), 2048)));
            String faviconHash = null;
            String frameworkName = null;
            String frameworkVersion = null;
            String cmsName = null;
            String cmsVersion = null;
            String wafName = null;
            List<String> techStack = new ArrayList<>();

            // 基于响应头的初步检测
            if (serverHeader != null) {
                if (serverHeader.toLowerCase().contains("nginx")) {
                    techStack.add("Nginx");
                    frameworkName = "nginx";
                    frameworkVersion = extractVersion(serverHeader, "nginx/");
                } else if (serverHeader.toLowerCase().contains("apache")) {
                    techStack.add("Apache");
                    frameworkName = "apache";
                    frameworkVersion = extractVersion(serverHeader, "Apache/");
                } else if (serverHeader.toLowerCase().contains("iis")) {
                    techStack.add("IIS");
                    frameworkName = "iis";
                } else if (serverHeader.toLowerCase().contains("tomcat")) {
                    techStack.add("Tomcat");
                    frameworkName = "tomcat";
                }
            }

            String poweredBy = headers.getOrDefault("X-Powered-By",
                    headers.getOrDefault("x-powered-by", ""));
            if (!poweredBy.isEmpty()) {
                if (poweredBy.toLowerCase().contains("php")) {
                    techStack.add("PHP");
                } else if (poweredBy.toLowerCase().contains("asp.net")) {
                    techStack.add("ASP.NET");
                } else if (poweredBy.toLowerCase().contains("express")) {
                    techStack.add("Express.js");
                }
            }

            // 检测 WAF
            wafName = detectWaf(headers, body);

            // 获取 favicon hash
            faviconHash = fetchFaviconHash(ip, portNum, isSsl);

            // 基于指纹规则检测
            if (rules != null) {
                for (FingerprintRule rule : rules) {
                    if (matchRule(rule, headers, body, faviconHash)) {
                        if ("cms".equals(rule.getCategory())) {
                            cmsName = rule.getName();
                            if (rule.getVersion() != null) cmsVersion = rule.getVersion();
                        } else if ("framework".equals(rule.getCategory())) {
                            if (frameworkName == null) frameworkName = rule.getName();
                        }
                        if (rule.getName() != null && !techStack.contains(rule.getName())) {
                            techStack.add(rule.getName());
                        }
                    }
                }
            }

            // 基于 HTML 特征检测前端框架
            detectFrontendFrameworks(body, techStack);

            String respSummary = "HTTP " + response.statusCode() + " | " +
                    (serverHeader != null ? serverHeader : "Unknown") + " | " +
                    (title != null ? title.substring(0, Math.min(title.length(), 80)) : "No Title");

            return new ProbeResult(
                    response.statusCode(), serverHeader, headers, title,
                    body.substring(0, Math.min(body.length(), 2048)),
                    bodyHash, faviconHash,
                    frameworkName, frameworkVersion,
                    cmsName, cmsVersion,
                    wafName, techStack, respSummary
            );

        } catch (Exception e) {
            log.warn("HTTP probe failed for {}:{} - {}", ip, portNum, e.getMessage());
            return null;
        }
    }

    public void saveProbeResult(Port port, ProbeResult result) {
        if (result == null) return;

        WebFingerprint wf = webFingerprintRepository.findByPortId(port.getId())
                .orElse(WebFingerprint.builder().port(port).build());

        wf.setHttpStatus(result.httpStatus());
        wf.setServerHeader(result.serverHeader());
        wf.setTitle(result.title());
        wf.setBodyHash(result.bodyHash());
        wf.setFaviconHash(result.faviconHash());
        wf.setFrameworkName(result.frameworkName());
        wf.setFrameworkVersion(result.frameworkVersion());
        wf.setCmsName(result.cmsName());
        wf.setCmsVersion(result.cmsVersion());
        wf.setWafName(result.wafName());
        wf.setTechStack(result.techStack() != null ?
                String.join(",", result.techStack()) : null);
        wf.setResponseSummary(result.responseSummary());

        // 只存储前 500 个字符的响应头
        String headerStr = result.responseHeaders() != null ?
                result.responseHeaders().entrySet().stream()
                        .map(e -> e.getKey() + ": " + e.getValue())
                        .limit(20)
                        .collect(Collectors.joining("\n")) : null;
        wf.setResponseHeaders(headerStr != null ?
                headerStr.substring(0, Math.min(headerStr.length(), 2000)) : null);

        webFingerprintRepository.save(wf);
    }

    private String extractTitle(String html) {
        if (html == null) return null;
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(
                "<title[^>]*>([^<]+)</title>",
                java.util.regex.Pattern.CASE_INSENSITIVE | java.util.regex.Pattern.DOTALL);
        java.util.regex.Matcher m = p.matcher(html);
        return m.find() ? m.group(1).trim() : null;
    }

    private String extractVersion(String header, String prefix) {
        int idx = header.toLowerCase().indexOf(prefix.toLowerCase());
        if (idx >= 0) {
            String version = header.substring(idx + prefix.length()).trim();
            // Extract version number
            java.util.regex.Matcher m = Pattern.compile("([\\d.]+)").matcher(version);
            if (m.find()) return m.group(1);
        }
        return null;
    }

    private String detectWaf(Map<String, String> headers, String body) {
        String lowerHeaders = headers.toString().toLowerCase();
        if (lowerHeaders.contains("x-cdn") || lowerHeaders.contains("cf-ray")) return "Cloudflare";
        if (lowerHeaders.contains("x-sucuri-id")) return "Sucuri";
        if (lowerHeaders.contains("akamai")) return "Akamai";
        if (body != null && body.toLowerCase().contains("mod_security")) return "ModSecurity";
        if (body != null && body.toLowerCase().contains("blocked by waf")) return "Generic WAF";
        return null;
    }

    private void detectFrontendFrameworks(String body, List<String> techStack) {
        if (body == null) return;
        String lower = body.toLowerCase();
        if (lower.contains("react") || lower.contains("__react")) techStack.add("React");
        if (lower.contains("vue") || lower.contains("v-bind") || lower.contains("v-model"))
            techStack.add("Vue.js");
        if (lower.contains("ng-version") || lower.contains("_ngcontent")) techStack.add("Angular");
        if (lower.contains("jquery")) techStack.add("jQuery");
        if (lower.contains("bootstrap")) techStack.add("Bootstrap");
    }

    private String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (Exception e) {
            return null;
        }
    }

    private HttpClient buildInsecureHttpClient() {
        try {
            TrustManager[] trustAll = new TrustManager[]{
                    new X509TrustManager() {
                        public java.security.cert.X509Certificate[] getAcceptedIssuers() { return null; }
                        public void checkClientTrusted(java.security.cert.X509Certificate[] c, String a) {}
                        public void checkServerTrusted(java.security.cert.X509Certificate[] c, String a) {}
                    }
            };
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustAll, new java.security.SecureRandom());
            return HttpClient.newBuilder()
                    .sslContext(sslContext)
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .connectTimeout(Duration.ofSeconds(3))
                    .build();
        } catch (Exception e) {
            return HttpClient.newBuilder()
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .connectTimeout(Duration.ofSeconds(3))
                    .build();
        }
    }

    // ========== 指纹规则引擎 ==========

    @SuppressWarnings("unchecked")
    private void loadRules() {
        try {
            InputStream is = new ClassPathResource("data/web-fingerprint-rules.json").getInputStream();
            rules = objectMapper.readValue(is, new TypeReference<List<FingerprintRule>>() {});
            log.info("Loaded {} web fingerprint rules", rules.size());
        } catch (Exception e) {
            log.warn("Failed to load web fingerprint rules: {}", e.getMessage());
            rules = new ArrayList<>();
            loadBuiltinRules();
        }
    }

    private void loadBuiltinRules() {
        // 内建规则作为后备
        rules = new ArrayList<>();

        rules.add(new FingerprintRule("WordPress", "cms", null,
                Map.of(), List.of("<meta name=\"generator\" content=\"WordPress"),
                List.of("wp-content", "wp-includes")));

        rules.add(new FingerprintRule("Drupal", "cms", null,
                Map.of(), List.of("<meta name=\"generator\" content=\"Drupal"),
                List.of("sites/default", "misc/drupal.js")));

        rules.add(new FingerprintRule("Joomla", "cms", null,
                Map.of(), List.of("<meta name=\"generator\" content=\"Joomla"),
                List.of("media/jui/js", "templates/protostar")));

        rules.add(new FingerprintRule("React", "framework", null,
                Map.of(), List.of("<div id=\"root\">", "__reactFiber"),
                List.of("react.production", "react-dom")));

        rules.add(new FingerprintRule("Vue.js", "framework", null,
                Map.of(), List.of("<div id=\"app\">", "v-bind:"),
                List.of("vue.js", "vue.runtime")));

        rules.add(new FingerprintRule("Spring Boot", "framework", null,
                Map.of(), List.of("org.springframework.web.servlet"),
                List.of("/actuator/health")));

        rules.add(new FingerprintRule("Tomcat", "server", null,
                Map.of("Server", "Apache-Coyote"), List.of(),
                List.of("Apache Tomcat")));

        rules.add(new FingerprintRule("Nginx", "server", null,
                Map.of("Server", "nginx"), List.of(),
                List.of("nginx")));

        rules.add(new FingerprintRule("Apache", "server", null,
                Map.of("Server", "Apache"), List.of(),
                List.of("Apache/2")));

        rules.add(new FingerprintRule("IIS", "server", null,
                Map.of("Server", "Microsoft-IIS"), List.of(),
                List.of("iisstart.htm")));
    }

    private boolean matchRule(FingerprintRule rule, Map<String, String> headers, String body, String faviconHash) {
        boolean matched = false;

        // 匹配 favicon hash
        if (faviconHash != null && rule.getFaviconHash() != null) {
            if (faviconHash.equals(rule.getFaviconHash())) {
                matched = true;
            }
        }

        // 匹配响应头
        if (!matched) {
            for (Map.Entry<String, String> header : rule.getHeaders().entrySet()) {
                String actual = headers.getOrDefault(header.getKey(),
                        headers.getOrDefault(header.getKey().toLowerCase(), ""));
                if (actual.toLowerCase().contains(header.getValue().toLowerCase())) {
                    matched = true;
                    break;
                }
            }
        }

        // 匹配 HTML 正则
        if (!matched && body != null) {
            for (String htmlPattern : rule.getHtmlPatterns()) {
                if (body.toLowerCase().contains(htmlPattern.toLowerCase())) {
                    matched = true;
                    break;
                }
            }
        }

        // 匹配 URL 关键词
        if (!matched && body != null) {
            for (String urlKeyword : rule.getUrlKeywords()) {
                if (body.toLowerCase().contains(urlKeyword.toLowerCase())) {
                    matched = true;
                    break;
                }
            }
        }

        return matched;
    }

    public static class FingerprintRule {
        private String name;
        private String category;
        private String version;
        private Map<String, String> headers = new HashMap<>();
        private List<String> htmlPatterns = new ArrayList<>();
        private List<String> urlKeywords = new ArrayList<>();
        private String faviconHash;

        public FingerprintRule() {}
        public FingerprintRule(String name, String category, String version,
                               Map<String, String> headers, List<String> htmlPatterns,
                               List<String> urlKeywords) {
            this.name = name; this.category = category; this.version = version;
            this.headers = headers; this.htmlPatterns = htmlPatterns;
            this.urlKeywords = urlKeywords;
        }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getCategory() { return category; }
        public void setCategory(String category) { this.category = category; }
        public String getVersion() { return version; }
        public void setVersion(String version) { this.version = version; }
        public Map<String, String> getHeaders() { return headers; }
        public void setHeaders(Map<String, String> headers) { this.headers = headers; }
        public List<String> getHtmlPatterns() { return htmlPatterns; }
        public void setHtmlPatterns(List<String> htmlPatterns) { this.htmlPatterns = htmlPatterns; }
        public List<String> getUrlKeywords() { return urlKeywords; }
        public void setUrlKeywords(List<String> urlKeywords) { this.urlKeywords = urlKeywords; }
        public String getFaviconHash() { return faviconHash; }
        public void setFaviconHash(String faviconHash) { this.faviconHash = faviconHash; }
    }

    /**
     * Fetch favicon.ico and compute its mmh3 hash (base64-encoded content).
     */
    private String fetchFaviconHash(String ip, int portNum, boolean isSsl) {
        try {
            String scheme = isSsl ? "https" : "http";
            String faviconUrl = scheme + "://" + ip + (portNum != 80 && portNum != 443 ? ":" + portNum : "") + "/favicon.ico";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(faviconUrl))
                    .timeout(Duration.ofMillis(2000))
                    .GET()
                    .build();
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() == 200 && response.body() != null && response.body().length > 0) {
                String b64 = java.util.Base64.getEncoder().encodeToString(response.body());
                return String.valueOf(murmurHash32(b64.getBytes(StandardCharsets.UTF_8)));
            }
        } catch (Exception e) {
            log.warn("Favicon fetch failed for {}:{} - {}", ip, portNum, e.getMessage());
        }
        return null;
    }

    /**
     * MurmurHash3 32-bit (x86, 32-bit) implementation.
     * Matches the Python mmh3 library output used by FOFA/EHole.
     */
    private static int murmurHash32(byte[] data) {
        int seed = 0;
        int len = data.length;
        int h1 = seed;
        int c1 = 0xcc9e2d51;
        int c2 = 0x1b873593;

        for (int i = 0; i + 4 <= len; i += 4) {
            int k1 = (data[i] & 0xff) | ((data[i + 1] & 0xff) << 8)
                   | ((data[i + 2] & 0xff) << 16) | ((data[i + 3] & 0xff) << 24);
            k1 *= c1;
            k1 = Integer.rotateLeft(k1, 15);
            k1 *= c2;
            h1 ^= k1;
            h1 = Integer.rotateLeft(h1, 13);
            h1 = h1 * 5 + 0xe6546b64;
        }

        int k1 = 0;
        int tail = len & 3;
        if (tail >= 3) k1 ^= (data[len - 1] & 0xff) << 16;
        if (tail >= 2) k1 ^= (data[len - 2 + (tail == 3 ? 1 : 0)] & 0xff) << 8;
        if (tail >= 1) {
            k1 ^= (data[len - tail] & 0xff);
            k1 *= c1;
            k1 = Integer.rotateLeft(k1, 15);
            k1 *= c2;
            h1 ^= k1;
        }

        h1 ^= len;
        h1 ^= h1 >>> 16;
        h1 *= 0x85ebca6b;
        h1 ^= h1 >>> 13;
        h1 *= 0xc2b2ae35;
        h1 ^= h1 >>> 16;

        return h1;
    }
}
