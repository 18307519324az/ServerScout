package com.serverscout.service;

import com.serverscout.entity.CveDatabase;
import com.serverscout.repository.CveDatabaseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Version-aware CVE matching engine.
 * Compares detected software versions against known vulnerable version ranges.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CveMatchingService {

    private final CveDatabaseRepository cveDatabaseRepository;

    /**
     * Match detected software versions against CVE version ranges.
     * Returns CVEs where the detected version falls within the vulnerable range.
     */
    public List<CveMatchResult> matchVersions(String productName, String detectedVersion) {
        List<CveMatchResult> matches = new ArrayList<>();
        if (productName == null || productName.length() < 2) return matches;

        List<CveDatabase> cves = cveDatabaseRepository.findByAffectedSoftwareContaining(productName.toLowerCase());
        for (CveDatabase cve : cves) {
            VersionMatchResult versionMatch = isVersionVulnerable(detectedVersion, cve.getAffectedVersionRange());
            if (versionMatch.matches()) {
                matches.add(new CveMatchResult(
                        cve.getCveId(), cve.getDescription(), cve.getSeverity(),
                        cve.getCvssScore() != null ? cve.getCvssScore().doubleValue() : 0.0,
                        cve.getAffectedSoftware(), cve.getAffectedVersionRange(),
                        detectedVersion, cve.getFixSuggestion(),
                        versionMatch.confidence()
                ));
            }
        }
        return matches;
    }

    /**
     * Version-aware matching result.
     */
    public record CveMatchResult(
            String cveId, String description, String severity, double cvssScore,
            String affectedSoftware, String affectedVersionRange,
            String detectedVersion, String fixSuggestion,
            MatchConfidence confidence
    ) {}

    public enum MatchConfidence {
        EXACT,      // Version comparison confirmed vulnerable
        APPROXIMATE, // Partial version match
        SOFTWARE_ONLY, // Software matched, version couldn't be compared
        UNKNOWN     // Fallback match
    }

    /**
     * Check if a given version falls within a vulnerable range.
     */
    public record VersionMatchResult(boolean matches, MatchConfidence confidence) {}

    public VersionMatchResult isVersionVulnerable(String detectedVersion, String versionRange) {
        if (detectedVersion == null || detectedVersion.isBlank()) {
            return new VersionMatchResult(true, MatchConfidence.SOFTWARE_ONLY);
        }

        if (versionRange == null || versionRange.isBlank()) {
            return new VersionMatchResult(true, MatchConfidence.SOFTWARE_ONLY);
        }

        try {
            // Normalize version: extract numeric version
            String normalized = normalizeVersion(detectedVersion);
            if (normalized == null) return new VersionMatchResult(true, MatchConfidence.SOFTWARE_ONLY);

            Version ver = parseVersion(normalized);
            if (ver == null) return new VersionMatchResult(true, MatchConfidence.SOFTWARE_ONLY);

            // Parse version range
            String range = versionRange.toLowerCase().trim();

            // Handle various range formats
            // Format: "6.0 - 6.5.1" or "6.0 - 6.5.1, 7.0.0 - 7.0.5"
            String[] parts = range.split("[,;，；]");
            for (String part : parts) {
                part = part.trim();

                // Format: "< 6.5.1"
                if (part.startsWith("<")) {
                    String max = part.substring(1).trim();
                    if (part.startsWith("<=")) max = part.substring(2).trim();
                    Version maxVer = parseVersion(max);
                    boolean inclusive = part.startsWith("<=");
                    if (maxVer != null) {
                        int cmp = ver.compareTo(maxVer);
                        if (cmp < 0 || (inclusive && cmp == 0)) {
                            return new VersionMatchResult(true, MatchConfidence.EXACT);
                        }
                    }
                }
                // Format: ">= 6.0"
                else if (part.startsWith(">=")) {
                    String min = part.substring(2).trim();
                    Version minVer = parseVersion(min);
                    if (minVer != null && ver.compareTo(minVer) >= 0) {
                        return new VersionMatchResult(true, MatchConfidence.EXACT);
                    }
                } else if (part.startsWith(">")) {
                    String min = part.substring(1).trim();
                    Version minVer = parseVersion(min);
                    if (minVer != null && ver.compareTo(minVer) > 0) {
                        return new VersionMatchResult(true, MatchConfidence.EXACT);
                    }
                }
                // Format: "6.0 - 6.5.1" or "6.0 through 6.5.1" or "6.0 to 6.5.1"
                else if (part.contains(" - ") || part.contains("～") || part.contains(" through ") || part.contains(" to ")) {
                    String[] rangeParts = part.split("\\s*[-～]\\s*|\\s+through\\s+|\\s+to\\s+");
                    if (rangeParts.length >= 2) {
                        Version minVer = parseVersion(rangeParts[0].trim());
                        Version maxVer = parseVersion(rangeParts[1].trim());
                        if (minVer != null && maxVer != null) {
                            if (ver.compareTo(minVer) >= 0 && ver.compareTo(maxVer) <= 0) {
                                return new VersionMatchResult(true, MatchConfidence.EXACT);
                            }
                        }
                    }
                }
                // Format: "5.3.0 - 5.3.17, 5.2.0 - 5.2.19"
                // Already handled by the above
            }

            // If we parsed versions but no range matched, check for single version equality
            if (parts.length == 1) {
                String single = parts[0].trim();
                Version singleVer = parseVersion(single);
                if (singleVer != null && ver.compareTo(singleVer) == 0) {
                    return new VersionMatchResult(true, MatchConfidence.EXACT);
                }
            }

            // Version parsed but not in any range - check approximate match
            for (String part : parts) {
                Version partVer = parseVersion(part.trim().replaceAll("^[<>=]+\\s*", ""));
                if (partVer != null && ver.major == partVer.major && ver.minor == partVer.minor) {
                    return new VersionMatchResult(true, MatchConfidence.APPROXIMATE);
                }
            }

            return new VersionMatchResult(false, MatchConfidence.EXACT);

        } catch (Exception e) {
            log.debug("Version comparison error: {} vs {} - {}", detectedVersion, versionRange, e.getMessage());
            return new VersionMatchResult(true, MatchConfidence.SOFTWARE_ONLY);
        }
    }

    /**
     * Parse a version string into a comparable Version object.
     */
    static Version parseVersion(String version) {
        if (version == null || version.isBlank()) return null;
        // Extract numeric version: e.g., "Apache/2.4.49" -> "2.4.49", "9.0.0.M1" -> "9.0.0"
        Matcher m = Pattern.compile("(\\d+)(?:\\.(\\d+))?(?:\\.(\\d+))?(?:\\.(\\d+))?").matcher(version);
        if (m.find()) {
            int major = Integer.parseInt(m.group(1));
            int minor = m.group(2) != null ? Integer.parseInt(m.group(2)) : 0;
            int patch = m.group(3) != null ? Integer.parseInt(m.group(3)) : 0;
            int build = m.group(4) != null ? Integer.parseInt(m.group(4)) : 0;
            return new Version(major, minor, patch, build);
        }
        return null;
    }

    /**
     * Normalize version string by extracting the numeric portion.
     */
    static String normalizeVersion(String version) {
        if (version == null) return null;
        Matcher m = Pattern.compile("(\\d+\\.\\d+(?:\\.\\d+)?(?:\\.\\d+)?)").matcher(version);
        return m.find() ? m.group(1) : null;
    }

    record Version(int major, int minor, int patch, int build) implements Comparable<Version> {
        @Override
        public int compareTo(Version o) {
            int cmp = Integer.compare(major, o.major);
            if (cmp != 0) return cmp;
            cmp = Integer.compare(minor, o.minor);
            if (cmp != 0) return cmp;
            cmp = Integer.compare(patch, o.patch);
            if (cmp != 0) return cmp;
            return Integer.compare(build, o.build);
        }

        @Override
        public String toString() {
            return major + "." + minor + "." + patch + (build > 0 ? "." + build : "");
        }
    }
}
