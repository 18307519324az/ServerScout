package com.serverscout.validation;

import com.serverscout.exception.BadRequestException;

import java.util.regex.Pattern;

/**
 * Validates port range strings for scan task creation.
 * <p>
 * Acceptable formats:
 * <ul>
 *   <li>Single port: {@code 80}</li>
 *   <li>Range: {@code 1-65535}</li>
 *   <li>Comma-separated: {@code 22,80,443}</li>
 *   <li>Mixed: {@code 1-1000,8080,9000-9999}</li>
 * </ul>
 * Each port must be in the range [1, 65535].
 */
public final class PortRangeValidator {

    private static final Pattern PORT_TOKEN = Pattern.compile("^\\d{1,5}(?:-\\d{1,5})?$");

    private PortRangeValidator() {
        // utility class
    }

    /**
     * Validates the port range string.
     *
     * @param portRange the port range string to validate
     * @throws BadRequestException if the port range is null, blank, or contains invalid tokens
     */
    public static void validate(String portRange) {
        if (portRange == null || portRange.isBlank()) {
            throw new BadRequestException("端口范围不能为空");
        }
        for (String raw : portRange.split(",")) {
            String token = raw.trim();
            if (!PORT_TOKEN.matcher(token).matches()) {
                throw new BadRequestException("无效的端口范围: " + token);
            }
            String[] bounds = token.split("-");
            int start = Integer.parseInt(bounds[0]);
            int end = bounds.length == 2 ? Integer.parseInt(bounds[1]) : start;
            if (start < 1 || end > 65535 || start > end) {
                throw new BadRequestException("无效的端口范围: " + token);
            }
        }
    }
}
