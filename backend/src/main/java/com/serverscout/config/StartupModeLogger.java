package com.serverscout.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class StartupModeLogger {

    @Value("${app.scan.demo-mode:true}")
    private boolean demoMode;

    @EventListener(ApplicationReadyEvent.class)
    public void logMode() {
        if (demoMode) {
            log.info("========================================");
            log.info("  ServerScout 运行模式: DEMO MODE");
            log.info("  使用模拟数据扫描，不执行真实 Nmap / Nuclei");
            log.info("  如需切换: 设置 SCANNER_DEMO_MODE=false 后重启");
            log.info("========================================");
        } else {
            log.info("========================================");
            log.info("  ServerScout 运行模式: REAL MODE");
            log.info("  使用真实扫描工具扫描目标资产");
            log.info("  请确保扫描目标已获得授权");
            log.info("========================================");
        }
    }
}
