package com.serverscout.service;

import com.serverscout.entity.SystemConfig;
import com.serverscout.repository.SystemConfigRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SystemConfigServiceTest {

    @Mock private SystemConfigRepository configRepository;

    @InjectMocks
    private SystemConfigService configService;

    @Test
    void shouldReturnDefaultWhenConfigNotFound() {
        when(configRepository.findByConfigKey("missing-key")).thenReturn(Optional.empty());

        String value = configService.getConfig("missing-key", "defaultVal");
        assertEquals("defaultVal", value);
    }

    @Test
    void shouldReturnStoredConfig() {
        SystemConfig config = SystemConfig.builder()
                .configKey("nmap-path").configValue("/usr/bin/nmap").build();
        when(configRepository.findByConfigKey("nmap-path")).thenReturn(Optional.of(config));

        String value = configService.getConfig("nmap-path", "nmap");
        assertEquals("/usr/bin/nmap", value);
    }

    @Test
    void shouldCreateNewConfig() {
        when(configRepository.findByConfigKey("new-key")).thenReturn(Optional.empty());
        when(configRepository.save(any(SystemConfig.class))).thenAnswer(inv -> inv.getArgument(0));

        assertDoesNotThrow(() ->
                configService.setConfig("new-key", "new-value", "description"));

        verify(configRepository).save(any(SystemConfig.class));
    }

    @Test
    void shouldUpdateExistingConfig() {
        SystemConfig existing = SystemConfig.builder()
                .id(1L).configKey("key").configValue("old").build();
        when(configRepository.findByConfigKey("key")).thenReturn(Optional.of(existing));
        when(configRepository.save(any(SystemConfig.class))).thenReturn(existing);

        configService.setConfig("key", "updated", null);

        assertEquals("updated", existing.getConfigValue());
        verify(configRepository).save(existing);
    }

    @Test
    void shouldSetMultipleConfigs() {
        when(configRepository.findByConfigKey("a")).thenReturn(Optional.empty());
        when(configRepository.findByConfigKey("b")).thenReturn(Optional.empty());
        when(configRepository.save(any(SystemConfig.class))).thenAnswer(inv -> inv.getArgument(0));

        assertDoesNotThrow(() -> configService.setConfigs(
                java.util.Map.of("a", "1", "b", "2")));

        verify(configRepository, times(2)).save(any(SystemConfig.class));
    }
}
