package com.serverscout.dto;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data @Builder
public class TopologyResponse {
    private List<Node> nodes;
    private List<Link> links;

    @Data @Builder
    public static class Node {
        private long id;
        private String ipAddress;
        private String hostname;
        private int openPortCount;
        private int criticalVulnCount;
        private String subnet;
        private String group;
        private List<String> serviceLabels;
    }

    @Data @Builder
    public static class Link {
        private long source;
        private long target;
        private String type;
        private List<Integer> ports;
    }
}
