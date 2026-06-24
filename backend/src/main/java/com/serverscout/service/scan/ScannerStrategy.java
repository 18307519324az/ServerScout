package com.serverscout.service.scan;

import com.serverscout.entity.ScanTask;

public interface ScannerStrategy {
    ScanResult execute(ScanTask task);
    boolean supports(String scanType);
}
