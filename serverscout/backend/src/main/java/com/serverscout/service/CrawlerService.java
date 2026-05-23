package com.serverscout.service;

import com.serverscout.entity.*;
import com.serverscout.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
@RequiredArgsConstructor
public class CrawlerService {

    private final CrawledUrlRepository crawledUrlRepository;
    private final PortRepository portRepository;
    private final ScreenshotService screenshotService;
    private final ProgressEmitter progressEmitter;

    @Value("${app.crawler.max-pages:50}")
    private int maxPages;

    @Value("${app.crawler.max-depth:3}")
    private int maxDepth;

    @Value("${app.crawler.request-delay-ms:500}")
    private int requestDelayMs;

    @Value("${app.crawler.timeout-seconds:10}")
    private int timeoutSeconds;

    @Value("${app.crawler.max-concurrent:3}")
    private int maxConcurrent;

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "text/html", "text/plain", "application/xhtml+xml",
            "application/xml", "text/xml"
    );

    private static final String[] USER_AGENTS = {
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
            "Mozilla/5.0 (X11; Linux x86_64; rv:120.0) Gecko/20100101 Firefox/120.0"
    };

    private final Set<String> bloomFilter = ConcurrentHashMap.newKeySet();
    private final Random random = new Random();

    /**
     * Run the static crawler against web services discovered for the given scan task.
     */
    public int crawl(ScanTask task, List<Asset> assets) {
        if (assets.isEmpty()) return 0;

        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .readTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .build();

        AtomicInteger totalCrawled = new AtomicInteger(0);
        bloomFilter.clear();

        ExecutorService executor = Executors.newFixedThreadPool(maxConcurrent);
        List<Future<?>> futures = new ArrayList<>();

        for (Asset asset : assets) {
            List<Port> ports = portRepository.findByAssetId(asset.getId());
            for (Port port : ports) {
                if (!Boolean.TRUE.equals(port.getIsWebService())) continue;
                if (totalCrawled.get() >= maxPages) break;

                futures.add(executor.submit(() -> {
                    try {
                        String scheme = port.getPortNumber() == 443 || port.getPortNumber() == 8443
                                ? "https" : "http";
                        String baseUrl = scheme + "://" + asset.getIpAddress() + ":" + port.getPortNumber();
                        crawlUrl(client, baseUrl, "/", asset, port, task, 0, totalCrawled);
                    } catch (Exception e) {
                        log.debug("Crawl failed for {}:{}: {}", asset.getIpAddress(),
                                port.getPortNumber(), e.getMessage());
                    }
                }));
            }
            if (totalCrawled.get() >= maxPages) break;
        }

        // Wait for all tasks
        for (Future<?> f : futures) {
            try { f.get(60, TimeUnit.SECONDS); } catch (Exception ignored) {}
        }
        executor.shutdownNow();

        log.info("Crawler finished for task {}: {} pages crawled", task.getId(), totalCrawled.get());
        return totalCrawled.get();
    }

    private void crawlUrl(OkHttpClient client, String baseUrl, String path,
                          Asset asset, Port port, ScanTask task, int depth,
                          AtomicInteger counter) {
        if (depth > maxDepth || counter.get() >= maxPages) return;

        String fullUrl = normalizeUrl(baseUrl, path);
        String urlKey = normalizeKey(fullUrl);
        if (!bloomFilter.add(urlKey)) return; // Already crawled

        try {
            Thread.sleep(requestDelayMs + random.nextInt(300));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }

        long start = System.currentTimeMillis();
        try {
            Request request = new Request.Builder()
                    .url(fullUrl)
                    .header("User-Agent", USER_AGENTS[random.nextInt(USER_AGENTS.length)])
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                    .build();

            Response response = client.newCall(request).execute();
            long responseTime = System.currentTimeMillis() - start;
            int statusCode = response.code();
            String contentType = response.header("Content-Type", "");

            // Only process HTML content
            if (!isAllowedContentType(contentType)) {
                response.close();
                return;
            }

            byte[] bodyBytes = response.body().bytes();
            response.close();

            if (bodyBytes.length == 0) return;

            String html = new String(bodyBytes, detectCharset(contentType, bodyBytes));
            Document doc = Jsoup.parse(html, fullUrl);

            String title = doc.title();
            String bodyText = doc.body() != null
                    ? doc.body().text().replaceAll("\\s+", " ").trim()
                    : "";

            // Truncate body text
            if (bodyText.length() > 5000) {
                bodyText = bodyText.substring(0, 5000) + "...";
            }

            // Count and extract links
            List<String> discoveredLinks = extractLinks(doc, baseUrl);

            // Save to database
            CrawledUrl crawled = CrawledUrl.builder()
                    .asset(asset).port(port).task(task)
                    .url(fullUrl).path(path)
                    .httpStatus(statusCode)
                    .contentType(contentType)
                    .title(title != null && title.length() > 500
                            ? title.substring(0, 500) : title)
                    .bodyText(bodyText)
                    .linksFound(discoveredLinks.size())
                    .crawlDepth(depth)
                    .responseTimeMs(responseTime)
                    .isDynamic(false)
                    .build();

            crawledUrlRepository.save(crawled);
            counter.incrementAndGet();

            log.debug("Crawled [{}]: {} ({}ms, {} links)", depth, fullUrl, responseTime, discoveredLinks.size());

            // Emit progress
            progressEmitter.sendProgress(task.getId(),
                    45 + Math.min(10, counter.get() * 10 / maxPages),
                    "爬虫发现: " + (title != null ? title : fullUrl),
                    counter.get());

            // Take screenshot of the crawled page (Goby-like)
            if (depth == 0 && counter.get() <= 5) {
                try {
                    String screenshotFile = screenshotService.captureAndSave(fullUrl, 1280, 800);
                    if (screenshotFile != null) {
                        crawled.setScreenshotPath(screenshotFile);
                        crawledUrlRepository.save(crawled);
                    }
                } catch (Exception e) {
                    log.debug("Screenshot failed for {}: {}", fullUrl, e.getMessage());
                }
            }

            // Recurse into discovered links
            if (depth < maxDepth && counter.get() < maxPages) {
                for (String link : discoveredLinks) {
                    if (counter.get() >= maxPages) break;
                    crawlUrl(client, baseUrl, link, asset, port, task, depth + 1, counter);
                }
            }

        } catch (Exception e) {
            log.debug("Crawl error for {}: {}", fullUrl, e.getMessage());
            // Save error entry
            try {
                CrawledUrl errEntry = CrawledUrl.builder()
                        .asset(asset).port(port).task(task)
                        .url(fullUrl).path(path)
                        .httpStatus(0).crawlDepth(depth)
                        .title("Error: " + e.getMessage())
                        .linksFound(0).responseTimeMs(System.currentTimeMillis() - start)
                        .build();
                crawledUrlRepository.save(errEntry);
            } catch (Exception ignored) {}
        }
    }

    private List<String> extractLinks(Document doc, String baseUrl) {
        Set<String> links = new LinkedHashSet<>();
        Elements anchors = doc.select("a[href]");
        for (Element a : anchors) {
            String href = a.attr("abs:href");
            if (isSameOrigin(href, baseUrl) && isAllowedContentTypeByExtension(href)) {
                try {
                    URI uri = new URI(href);
                    String path = uri.getPath();
                    if (path.isEmpty()) path = "/";
                    // Remove fragment
                    int frag = path.indexOf('#');
                    if (frag >= 0) path = path.substring(0, frag);
                    if (path.length() < 500 && !path.matches(".*\\.(png|jpg|jpeg|gif|svg|ico|pdf|zip|gz|tar|mp4|mp3|css|js|woff|ttf|eot)(\\?.*)?$")) {
                        links.add(path);
                    }
                } catch (Exception ignored) {}
            }
        }

        // Also extract from link tags
        Elements links2 = doc.select("link[href]");
        for (Element l : links2) {
            String href = l.attr("abs:href");
            if (isSameOrigin(href, baseUrl)) {
                try {
                    URI uri = new URI(href);
                    String path = uri.getPath();
                    if (path.isEmpty()) path = "/";
                    if (path.length() < 200) links.add(path);
                } catch (Exception ignored) {}
            }
        }

        return new ArrayList<>(links);
    }

    private String normalizeUrl(String baseUrl, String path) {
        if (path.startsWith("http://") || path.startsWith("https://")) return path;
        if (!path.startsWith("/")) path = "/" + path;
        return baseUrl.replaceAll("/$", "") + path;
    }

    private String normalizeKey(String url) {
        try {
            // Remove trailing slash, lowercase scheme+host, sort query params, remove fragment
            URI uri = new URI(url);
            String host = uri.getHost() != null ? uri.getHost().toLowerCase() : "";
            String path = uri.getPath() != null ? uri.getPath().replaceAll("/$", "") : "";
            String query = uri.getQuery();
            if (path.isEmpty()) path = "/";
            String key = host + ":" + (uri.getPort() > 0 ? uri.getPort() : (uri.getScheme().startsWith("https") ? 443 : 80)) + path;
            if (query != null && !query.isEmpty()) {
                String[] params = query.split("&");
                Arrays.sort(params);
                key += "?" + String.join("&", params);
            }
            return key;
        } catch (Exception e) {
            return url;
        }
    }

    private boolean isSameOrigin(String href, String baseUrl) {
        if (href == null || href.isEmpty()) return false;
        if (href.startsWith("javascript:") || href.startsWith("mailto:") || href.startsWith("tel:")) return false;
        if (!href.startsWith("http://") && !href.startsWith("https://")) return true; // relative URL
        try {
            URI hrefUri = new URI(href);
            URI baseUri = new URI(baseUrl);
            String hHost = hrefUri.getHost();
            String bHost = baseUri.getHost();
            if (hHost == null || bHost == null) return false;
            return hHost.equalsIgnoreCase(bHost) && hrefUri.getPort() == baseUri.getPort();
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isAllowedContentType(String contentType) {
        if (contentType == null || contentType.isEmpty()) return true; // assume HTML if not specified
        String lower = contentType.toLowerCase();
        for (String allowed : ALLOWED_CONTENT_TYPES) {
            if (lower.contains(allowed)) return true;
        }
        return false;
    }

    private boolean isAllowedContentTypeByExtension(String url) {
        String lower = url.toLowerCase();
        return !lower.matches(".*\\.(png|jpg|jpeg|gif|svg|ico|webp|pdf|zip|gz|tar|bz2|7z|rar|mp4|mp3|avi|mov|wmv|flv|css|js|json|xml|woff|woff2|ttf|eot)(\\?.*)?$");
    }

    private String detectCharset(String contentType, byte[] body) {
        if (contentType != null && contentType.toLowerCase().contains("charset=")) {
            String[] parts = contentType.toLowerCase().split("charset=");
            if (parts.length > 1) {
                String cs = parts[1].split(";")[0].trim().toUpperCase();
                try {
                    if (java.nio.charset.Charset.isSupported(cs)) return cs;
                } catch (Exception ignored) {}
            }
        }
        // Try to detect from HTML meta tag
        String head = new String(body, 0, Math.min(body.length, 1024), StandardCharsets.ISO_8859_1);
        if (head.toLowerCase().contains("charset=utf-8") || head.toLowerCase().contains("charset=\"utf-8\"")) {
            return "UTF-8";
        }
        return "UTF-8"; // default
    }
}
