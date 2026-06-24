package com.serverscout.entity.enums;

/**
 * Scan profile categorizing the scan intent, used to determine
 * which stages should be skipped upfront versus executed.
 */
public enum ScanProfile {
    HOST_DISCOVERY,
    QUICK_SCAN,
    FULL_SCAN
}
