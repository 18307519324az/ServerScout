package com.serverscout.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SystemModeResponse {
    private String mode;             // DEMO / REAL / UNKNOWN
    private boolean demoMode;
    private String scannerMode;      // DEMO / REAL
    private boolean nmapAvailable;
    private boolean nucleiAvailable;
    private boolean allowPublicTargets;
    private String configSource;
    private String actualBehavior;
    private String switchGuide;
    private String safetyNotice;
}
