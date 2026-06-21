package com.serverscout.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class IpGeoService {

    private final OkHttpClient client;
    private final ObjectMapper mapper = new ObjectMapper();

    public IpGeoService() {
        this.client = new OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .build();
    }

    /**
     * Look up geo-location info for an IP address using ip-api.com (free, no key needed).
     */
    public IpGeoInfo lookup(String ip) {
        if (ip == null || ip.isBlank() || isPrivateIp(ip)) {
            return new IpGeoInfo(ip, "内网地址", "", "", "");
        }

        try {
            String url = "http://ip-api.com/json/" + ip + "?lang=zh-CN&fields=country,regionName,city,isp,org";
            Request req = new Request.Builder().url(url).build();
            try (Response resp = client.newCall(req).execute()) {
                if (resp.body() != null) {
                    JsonNode node = mapper.readTree(resp.body().string());
                    String country = node.has("country") ? node.get("country").asText() : "";
                    String region = node.has("regionName") ? node.get("regionName").asText() : "";
                    String city = node.has("city") ? node.get("city").asText() : "";
                    String isp = node.has("isp") ? node.get("isp").asText() : "";
                    return new IpGeoInfo(ip, country, region, city, isp);
                }
            }
        } catch (Exception e) {
            log.debug("IP geo lookup failed for {}: {}", ip, e.getMessage());
        }
        return new IpGeoInfo(ip, "未知", "", "", "");
    }

    private boolean isPrivateIp(String ip) {
        if (ip.equals("127.0.0.1") || ip.equals("0:0:0:0:0:0:0:1") || ip.startsWith("0.")) return true;
        try {
            String[] parts = ip.split("\\.");
            if (parts.length != 4) return false;
            int second = Integer.parseInt(parts[1]);
            if (parts[0].equals("10")) return true;
            if (parts[0].equals("172") && second >= 16 && second <= 31) return true;
            if (parts[0].equals("192") && second == 168) return true;
        } catch (NumberFormatException e) {
            // not IPv4
        }
        return false;
    }

    public record IpGeoInfo(String ip, String country, String region, String city, String isp) {
        public String getLocation() {
            StringBuilder sb = new StringBuilder();
            if (country != null && !country.isBlank()) sb.append(country);
            if (region != null && !region.isBlank()) {
                if (!sb.isEmpty()) sb.append(" ");
                sb.append(region);
            }
            if (city != null && !city.isBlank()) {
                if (!sb.isEmpty()) sb.append(" ");
                sb.append(city);
            }
            return sb.isEmpty() ? "未知" : sb.toString();
        }
    }
}
