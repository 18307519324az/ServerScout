package com.serverscout.entity.enums;

import lombok.Getter;

@Getter
public enum ScanStageCode {
    TARGET_VALIDATION("目标校验"),
    PORT_SCAN("端口扫描"),
    SERVICE_FINGERPRINT("服务识别"),
    WEB_PROBE("Web 探测"),
    VULNERABILITY_SCAN("漏洞检测"),
    CVE_MATCH("CVE 匹配"),
    RISK_ANALYSIS("风险分析"),
    RESULT_SAVE("结果保存"),
    NOTIFICATION("通知回调");

    private final String displayName;

    ScanStageCode(String displayName) {
        this.displayName = displayName;
    }
}
