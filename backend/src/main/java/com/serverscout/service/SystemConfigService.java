package com.serverscout.service;

import com.serverscout.entity.SystemConfig;
import com.serverscout.repository.SystemConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SystemConfigService {

    private final SystemConfigRepository configRepository;

    public String getConfig(String key, String defaultValue) {
        return configRepository.findByConfigKey(key)
                .map(SystemConfig::getConfigValue)
                .orElse(defaultValue);
    }

    public Map<String, String> getAllConfigs() {
        return configRepository.findAll().stream()
                .collect(Collectors.toMap(SystemConfig::getConfigKey, SystemConfig::getConfigValue));
    }

    @Transactional
    public void setConfig(String key, String value, String description) {
        SystemConfig config = configRepository.findByConfigKey(key)
                .orElse(SystemConfig.builder().configKey(key).build());
        config.setConfigValue(value);
        if (description != null) config.setDescription(description);
        configRepository.save(config);
    }

    @Transactional
    public void setConfigs(Map<String, String> configs) {
        for (var entry : configs.entrySet()) {
            setConfig(entry.getKey(), entry.getValue(), null);
        }
    }
}
