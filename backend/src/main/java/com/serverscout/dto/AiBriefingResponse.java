package com.serverscout.dto;

import java.util.List;
import java.util.Map;

public record AiBriefingResponse(
        String mode,
        String language,
        String inputSummary,
        Map<String, List<String>> detectedSignals,
        List<Section> sections,
        List<String> warnings) {

    public record Section(String key, String title, String body, List<String> items) {
    }
}
