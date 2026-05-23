package com.serverscout.service.scan;

import com.serverscout.entity.*;
import com.serverscout.repository.*;
import com.serverscout.service.ProgressEmitter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.*;
import java.util.regex.*;

/**
 * OWASP Top 10 web vulnerability detection — SQL Injection, XSS, CSRF.
 * Runs after HTTP probing to analyze web forms and parameters for common weaknesses.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WebVulnDetectorService {

    private final CveDatabaseRepository cveRepository;
    private final AssetVulnerabilityRepository avRepository;
    private final ProgressEmitter progressEmitter;

    private static final int FETCH_TIMEOUT_MS = 8000;
    private static final String UA = "Mozilla/5.0 ServerScout/2.0";

    // SQL injection patterns in URL params / form inputs
    private static final Pattern SQLI_PARAM_PATTERN = Pattern.compile(
            "[?&](\\w+)=([^&\\s\"'<>]*)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern SQLI_FORM_PATTERN = Pattern.compile(
            "<form\\b[^>]*?action\\s*=\\s*[\"']([^\"']+)[\"'][^>]*>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern INPUT_PATTERN = Pattern.compile(
            "<input\\b[^>]*?name\\s*=\\s*[\"']([^\"']+)[\"'][^>]*>",
            Pattern.CASE_INSENSITIVE);

    // XSS reflection patterns
    private static final Pattern XSS_FORM_PATTERN = Pattern.compile(
            "<form\\b[^>]*>.*?</form>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern SCRIPT_TAG = Pattern.compile(
            "<script\\b[^>]*>",
            Pattern.CASE_INSENSITIVE);

    // CSRF detection — check for missing CSRF token in forms
    private static final Pattern CSRF_TOKEN_PATTERN = Pattern.compile(
            "(csrf|_token|authenticity_token|xsrf|__RequestVerificationToken)",
            Pattern.CASE_INSENSITIVE);

    // Known SQL injection error patterns to check in responses
    private static final Pattern[] SQLI_ERROR_PATTERNS = {
        Pattern.compile("SQL syntax.*?MySQL", Pattern.CASE_INSENSITIVE),
        Pattern.compile("Warning.*?mysql_fetch", Pattern.CASE_INSENSITIVE),
        Pattern.compile("ORA-\\d{5}", Pattern.CASE_INSENSITIVE),
        Pattern.compile("PostgreSQL.*?ERROR", Pattern.CASE_INSENSITIVE),
        Pattern.compile("SQLite.*?error", Pattern.CASE_INSENSITIVE),
        Pattern.compile("Microsoft OLE DB.*?SQL Server", Pattern.CASE_INSENSITIVE),
        Pattern.compile("unclosed quotation mark", Pattern.CASE_INSENSITIVE),
        Pattern.compile("You have an error in your SQL syntax", Pattern.CASE_INSENSITIVE),
    };

    /**
     * Run web vulnerability detection on discovered web services for a scan task.
     */
    public int detect(ScanTask task, List<Asset> assets,
                       PortRepository portRepository,
                       WebFingerprintRepository webFingerprintRepository) {
        int totalFindings = 0;

        for (Asset asset : assets) {
            String ip = asset.getIpAddress();
            List<Port> ports = portRepository.findByAssetId(asset.getId());
            for (Port port : ports) {
                if (!Boolean.TRUE.equals(port.getIsWebService())) continue;

                String scheme = port.getPortNumber() == 443 || port.getPortNumber() == 8443 ? "https" : "http";
                String baseUrl = scheme + "://" + ip + ":" + port.getPortNumber();

                try {
                    String html = fetchPage(baseUrl + "/", 1);
                    if (html == null || html.length() < 50) continue;

                    int findings = 0;
                    findings += detectSqli(asset, port, baseUrl, html);
                    findings += detectXss(asset, port, baseUrl, html);
                    findings += detectCsrf(asset, port, baseUrl, html);
                    totalFindings += findings;

                    if (findings > 0) {
                        progressEmitter.sendProgress(task.getId(), task.getProgress(),
                                "Web漏洞检测: " + ip + ":" + port.getPortNumber()
                                        + " 发现 " + findings + " 个风险点",
                                task.getTotalAssets());
                    }
                } catch (Exception e) {
                    log.debug("Web vuln detection failed for {}:{}: {}", ip, port.getPortNumber(), e.getMessage());
                }
            }
        }

        log.info("Task {}: web vuln detection found {} findings", task.getId(), totalFindings);
        return totalFindings;
    }

    /**
     * SQL Injection detection:
     * 1. Find URL query parameters and form input names
     * 2. Send test payloads (') to detect error-based SQLi
     * 3. Check for SQL error messages in responses
     */
    private int detectSqli(Asset asset, Port port, String baseUrl, String html) {
        int found = 0;
        Set<String> testedEndpoints = new HashSet<>();

        // Extract forms with action URLs
        Matcher formMatcher = SQLI_FORM_PATTERN.matcher(html);
        while (formMatcher.find()) {
            String action = formMatcher.group(1);
            String formSnippet = html.substring(Math.max(0, formMatcher.start() - 50),
                    Math.min(html.length(), formMatcher.end() + 500));

            // Find input fields in this form
            Matcher inputMatcher = INPUT_PATTERN.matcher(formSnippet);
            List<String> paramNames = new ArrayList<>();
            while (inputMatcher.find()) {
                String name = inputMatcher.group(1);
                if (!name.isEmpty() && !isIgnoredParam(name)) {
                    paramNames.add(name);
                }
            }

            if (!paramNames.isEmpty()) {
                String fullAction = resolveUrl(baseUrl, action);
                if (testedEndpoints.add(fullAction)) {
                    if (testSqliEndpoint(asset, port, fullAction, paramNames)) found++;
                }
            }
        }

        // Check URL query parameters in links
        Matcher paramMatcher = SQLI_PARAM_PATTERN.matcher(html);
        Set<String> seenParams = new HashSet<>();
        while (paramMatcher.find()) {
            String paramName = paramMatcher.group(1);
            if (!isIgnoredParam(paramName) && seenParams.add(paramName)) {
                String testUrl = baseUrl + "/?" + paramName + "='";
                if (testSqliErrorResponse(testUrl)) {
                    createVulnFinding(asset, port, "CVE-WEB-SQLI-001",
                            "SQL注入漏洞 (SQL Injection)", "critical", 9.0f,
                            "参数 " + paramName + " 疑似存在SQL注入漏洞",
                            "1) 使用参数化查询(PreparedStatement)替代字符串拼接SQL; 2) 对所有用户输入进行严格的类型校验和白名单过滤; 3) 部署WAF规则拦截SQL注入特征");
                    found++;
                }
            }
        }

        return found;
    }

    /**
     * XSS detection:
     * 1. Find URL parameters that are reflected in page content
     * 2. Check if HTML encoding is applied to reflected values
     * 3. Look for forms that post without input sanitization
     */
    private int detectXss(Asset asset, Port port, String baseUrl, String html) {
        int found = 0;

        // Find URL query parameters
        Matcher paramMatcher = SQLI_PARAM_PATTERN.matcher(html);
        Set<String> testedParams = new HashSet<>();

        while (paramMatcher.find()) {
            String paramName = paramMatcher.group(1);
            String paramValue = paramMatcher.group(2);
            if (isIgnoredParam(paramName) || paramValue.length() < 3) continue;
            if (!testedParams.add(paramName)) continue;

            // Test if parameter value is reflected in the HTML without encoding
            if (html.contains(paramValue) && !html.contains(htmlEncode(paramValue))) {
                // Try injecting a benign XSS probe
                String probe = paramValue + "<i>xsstest</i>";
                String testUrl = baseUrl + "/?" + paramName + "=" + urlEncode(probe);
                String reflected = fetchPage(testUrl, 1);
                if (reflected != null && reflected.contains("<i>xsstest</i>")) {
                    createVulnFinding(asset, port, "CVE-WEB-XSS-001",
                            "跨站脚本漏洞 (Cross-Site Scripting)", "high", 7.5f,
                            "参数 " + paramName + " 存在反射型XSS漏洞，用户输入被原样回显到页面",
                            "1) 对所有输出到HTML的变量进行HTML实体编码(如&lt;&gt;&quot;); 2) 使用Content-Security-Policy头限制内联脚本; 3) 设置HttpOnly和Secure Cookie标记; 4) 使用X-XSS-Protection响应头");
                    found++;
                }
            }
        }

        // Check forms for potential stored XSS entry points
        Matcher formMatcher = XSS_FORM_PATTERN.matcher(html);
        while (formMatcher.find()) {
            String formHtml = formMatcher.group();
            Matcher inputMatcher = INPUT_PATTERN.matcher(formHtml);
            boolean hasTextarea = formHtml.toLowerCase().contains("<textarea");
            int inputCount = 0;
            while (inputMatcher.find()) inputCount++;

            // Forms with many text inputs are potential XSS vectors
            if ((hasTextarea || inputCount >= 2) && !formHtml.contains("sanitize")) {
                // Check if there's already an XSS finding for this asset
                // Only flag if we have textarea + no CSP header detected
                if (hasTextarea && html.contains("<textarea")) {
                    createVulnFinding(asset, port, "CVE-WEB-XSS-002",
                            "潜在存储型XSS入口 (Stored XSS Entry Point)", "medium", 5.4f,
                            "页面存在富文本输入区域，可能成为存储型XSS攻击入口",
                            "1) 对富文本内容使用DOMPurify等服务端/客户端HTML清洗库; 2) 实施严格的标签白名单(a,p,img等); 3) 禁止事件处理器(onerror,onload等)和javascript:协议");
                    found++;
                    break;
                }
            }
        }

        return found;
    }

    /**
     * CSRF detection:
     * 1. Check forms for missing CSRF tokens
     * 2. Check if state-changing requests lack Referer/Origin validation
     */
    private int detectCsrf(Asset asset, Port port, String baseUrl, String html) {
        int found = 0;

        // Find all forms
        Matcher formMatcher = XSS_FORM_PATTERN.matcher(html);
        boolean hasUnprotectedForm = false;
        int formCount = 0;

        while (formMatcher.find()) {
            formCount++;
            String formHtml = formMatcher.group();
            String formLower = formHtml.toLowerCase();

            // Check method - only POST/PUT/DELETE are relevant for CSRF
            boolean isStateChanging = formLower.contains("method=\"post\"")
                    || formLower.contains("method='post'")
                    || formLower.contains("method=post")
                    || !formLower.contains("method=get"); // default is GET

            if (!isStateChanging) continue;

            // Check for CSRF token
            Matcher csrfMatcher = CSRF_TOKEN_PATTERN.matcher(formHtml);
            boolean hasCsrfToken = csrfMatcher.find();

            // Check for hidden input with token-like name
            Matcher inputMatcher = INPUT_PATTERN.matcher(formHtml);
            while (inputMatcher.find()) {
                String name = inputMatcher.group(1).toLowerCase();
                if (name.contains("csrf") || name.contains("token") || name.contains("xsrf")
                        || name.contains("nonce") || name.equals("_token")) {
                    hasCsrfToken = true;
                    break;
                }
            }

            if (!hasCsrfToken) {
                hasUnprotectedForm = true;
            }
        }

        if (hasUnprotectedForm && formCount > 0) {
            // Double-check by examining response headers
            boolean hasCsrfHeader = html.contains("X-CSRF") || html.contains("csrf-token")
                    || html.contains("xsrf-token");

            if (!hasCsrfHeader) {
                createVulnFinding(asset, port, "CVE-WEB-CSRF-001",
                        "跨站请求伪造漏洞 (CSRF)", "high", 8.0f,
                        "页面共 " + formCount + " 个表单缺少CSRF防护令牌，可能遭受CSRF攻击",
                        "1) 为所有状态变更请求(POST/PUT/DELETE)添加CSRF Token; 2) 验证Referer/Origin请求头; 3) 使用SameSite Cookie属性(Strict/Lax); 4) 关键操作要求二次认证(密码/验证码)");
                found++;
            }
        }

        return found;
    }

    /**
     * Test a form endpoint for SQL injection by sending a single-quote payload.
     */
    private boolean testSqliEndpoint(Asset asset, Port port, String actionUrl, List<String> paramNames) {
        try {
            // Build POST body with test payload for first parameter
            StringBuilder body = new StringBuilder();
            for (int i = 0; i < paramNames.size(); i++) {
                if (i > 0) body.append("&");
                body.append(urlEncode(paramNames.get(i)));
                body.append("=");
                body.append(i == 0 ? urlEncode("'") : "test");
            }

            String response = postForm(actionUrl, body.toString());
            if (response == null) return false;

            for (Pattern errorPattern : SQLI_ERROR_PATTERNS) {
                if (errorPattern.matcher(response).find()) {
                    createVulnFinding(asset, port, "CVE-WEB-SQLI-002",
                            "SQL注入漏洞 (Error-Based SQL Injection)", "critical", 9.5f,
                            "端点 " + actionUrl + " 返回SQL错误信息，确认存在SQL注入漏洞",
                            "1) 立即下线该接口或临时限制访问; 2) 使用参数化查询重写SQL逻辑; 3) 禁止将数据库错误信息返回给客户端; 4) 部署WAF规则防护");
                    return true;
                }
            }
        } catch (Exception e) {
            log.debug("SQLi test failed for {}: {}", actionUrl, e.getMessage());
        }
        return false;
    }

    private boolean testSqliErrorResponse(String url) {
        String response = fetchPage(url, 1);
        if (response == null) return false;
        for (Pattern p : SQLI_ERROR_PATTERNS) {
            if (p.matcher(response).find()) return true;
        }
        return false;
    }

    private String fetchPage(String urlString, int retries) {
        for (int i = 0; i < retries; i++) {
            try {
                URL url = URI.create(urlString).toURL();
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(FETCH_TIMEOUT_MS);
                conn.setReadTimeout(FETCH_TIMEOUT_MS);
                conn.setRequestProperty("User-Agent", UA);
                conn.setInstanceFollowRedirects(true);

                StringBuilder response = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(conn.getInputStream()))) {
                    String line;
                    int lines = 0;
                    while ((line = reader.readLine()) != null && lines++ < 500) {
                        response.append(line).append("\n");
                    }
                }
                conn.disconnect();
                return response.toString();
            } catch (Exception e) {
                if (i == retries - 1) log.debug("Fetch failed for {}: {}", urlString, e.getMessage());
            }
        }
        return null;
    }

    private String postForm(String urlString, String body) {
        try {
            URL url = URI.create(urlString).toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(FETCH_TIMEOUT_MS);
            conn.setReadTimeout(FETCH_TIMEOUT_MS);
            conn.setDoOutput(true);
            conn.setRequestProperty("User-Agent", UA);
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

            conn.getOutputStream().write(body.getBytes());
            conn.getOutputStream().flush();

            StringBuilder response = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream()))) {
                String line;
                int lines = 0;
                while ((line = reader.readLine()) != null && lines++ < 200) {
                    response.append(line).append("\n");
                }
            } catch (Exception e) {
                // Read error stream for error-based SQLi detection
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(conn.getErrorStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line).append("\n");
                    }
                } catch (Exception ignored) {}
            }
            conn.disconnect();
            return response.toString();
        } catch (Exception e) {
            log.debug("POST failed for {}: {}", urlString, e.getMessage());
            return null;
        }
    }

    private void createVulnFinding(Asset asset, Port port, String cveId,
                                    String description, String severity, float cvss,
                                    String detail, String fix) {
        // Find or create CVE entry
        CveDatabase cve = cveRepository.findByCveId(cveId).orElse(null);
        if (cve == null) {
            cve = CveDatabase.builder()
                    .cveId(cveId)
                    .description(description)
                    .severity(severity)
                    .cvssScore(java.math.BigDecimal.valueOf(cvss))
                    .affectedSoftware("Web Application")
                    .fixSuggestion(fix)
                    .build();
            cve = cveRepository.save(cve);
        }

        // Check if vulnerability already exists for this asset
        boolean exists = avRepository.findByAssetId(asset.getId()).stream()
                .anyMatch(av -> av.getCveDatabase() != null
                        && cveId.equals(av.getCveDatabase().getCveId()));
        if (!exists) {
            AssetVulnerability av = AssetVulnerability.builder()
                    .asset(asset).cveDatabase(cve).port(port)
                    .status("open")
                    .reproductionSteps(detail)
                    .build();
            avRepository.save(av);

            if ("critical".equalsIgnoreCase(severity)) {
                asset.setCriticalVulnCount((asset.getCriticalVulnCount() == null ? 0 : asset.getCriticalVulnCount()) + 1);
            }
        }

        log.info("WebVuln found: {} on {}:{}", cveId, asset.getIpAddress(), port.getPortNumber());
    }

    private String resolveUrl(String baseUrl, String action) {
        if (action.startsWith("http")) return action;
        if (action.startsWith("/")) {
            try {
                URI uri = URI.create(baseUrl);
                return uri.getScheme() + "://" + uri.getHost() + ":" + uri.getPort() + action;
            } catch (Exception e) {
                return baseUrl + action;
            }
        }
        return baseUrl + "/" + action;
    }

    private boolean isIgnoredParam(String name) {
        String lower = name.toLowerCase();
        return lower.startsWith("utm_") || lower.equals("page") || lower.equals("size")
                || lower.equals("sort") || lower.equals("order") || lower.equals("limit")
                || lower.equals("offset") || lower.equals("lang") || lower.equals("locale");
    }

    private String urlEncode(String value) {
        try {
            return java.net.URLEncoder.encode(value, "UTF-8");
        } catch (Exception e) {
            return value;
        }
    }

    private String htmlEncode(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#x27;");
    }
}
