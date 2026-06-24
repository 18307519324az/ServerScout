package com.serverscout.controller;

import com.serverscout.common.R;
import com.serverscout.dto.SystemModeResponse;
import com.serverscout.service.ScannerToolAvailabilityService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/system")
@RequiredArgsConstructor
public class SystemModeController {

    @Value("${app.scan.demo-mode:true}")
    private boolean demoMode;

    private final ScannerToolAvailabilityService toolAvailabilityService;

    @GetMapping("/mode")
    public R<SystemModeResponse> getSystemMode() {
        boolean nmapAvailable = toolAvailabilityService.isNmapAvailable();
        boolean nucleiAvailable = toolAvailabilityService.isNucleiAvailable();

        String mode = demoMode ? "DEMO" : "REAL";
        String scannerMode = demoMode ? "DEMO" : "REAL";
        String configSource = demoMode
                ? "SCANNER_DEMO_MODE=true"
                : "SCANNER_DEMO_MODE=false";

        String actualBehavior;
        String switchGuide;
        String safetyNotice;

        if (demoMode) {
            actualBehavior = "当前为 Demo Mode，系统使用模拟数据，不会执行真实 Nmap / Nuclei，也不会访问真实目标。";
            switchGuide = "如需真实扫描，请将 .env 中 SCANNER_DEMO_MODE=false 后重启服务。";
            safetyNotice = "Demo Mode 仅用于安全演示，扫描结果为模拟数据。";
        } else {
            actualBehavior = "当前为 Real Mode，系统将调用真实扫描工具，请仅扫描已授权目标。";
            switchGuide = "如需切换回演示模式，请将 .env 中 SCANNER_DEMO_MODE=true 后重启服务。";
            safetyNotice = "真实扫描仅限授权资产，默认禁止未授权公网扫描。";
        }

        SystemModeResponse response = SystemModeResponse.builder()
                .mode(mode)
                .demoMode(demoMode)
                .scannerMode(scannerMode)
                .nmapAvailable(nmapAvailable)
                .nucleiAvailable(nucleiAvailable)
                .allowPublicTargets(false)
                .configSource(configSource)
                .actualBehavior(actualBehavior)
                .switchGuide(switchGuide)
                .safetyNotice(safetyNotice)
                .build();

        return R.ok(response);
    }
}
