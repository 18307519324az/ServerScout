package com.serverscout.service;

import com.serverscout.entity.Asset;
import com.serverscout.entity.Port;
import com.serverscout.exception.ResourceNotFoundException;
import com.serverscout.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AssetDeleteCascadeTest {

    @Mock
    private AssetRepository assetRepository;

    @Mock
    private PortRepository portRepository;

    @Mock
    private WebFingerprintRepository webFingerprintRepository;

    @Mock
    private AssetVulnerabilityRepository assetVulnerabilityRepository;

    @Mock
    private SslCertificateRepository sslCertificateRepository;

    @Mock
    private ScanAssetMappingRepository scanAssetMappingRepository;

    @Mock
    private SubdomainRepository subdomainRepository;

    @Mock
    private CrawledUrlRepository crawledUrlRepository;

    @Mock
    private RiskScoreRepository riskScoreRepository;

    @Mock
    private HoneypotDetectionRepository honeypotDetectionRepository;

    @Mock
    private HoneypotDetectionService honeypotDetectionService;

    @Mock
    private com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    @InjectMocks
    private AssetService assetService;

    @Test
    void shouldDeleteAssetWithPorts() {
        Asset asset = Asset.builder().id(1L).ipAddress("192.168.1.1").build();
        Port port = Port.builder().id(10L).portNumber(80).build();
        List<Port> ports = List.of(port);

        when(assetRepository.findById(1L)).thenReturn(Optional.of(asset));
        when(portRepository.findByAssetId(1L)).thenReturn(ports);

        assetService.deleteAsset(1L);

        verify(crawledUrlRepository).deleteByPortIdIn(List.of(10L));
        verify(crawledUrlRepository).deleteByAssetId(1L);
        verify(webFingerprintRepository).deleteByPortId(10L);
        verify(sslCertificateRepository).deleteByPortId(10L);
        verify(assetVulnerabilityRepository).deleteByPortId(10L);
        verify(portRepository).deleteByAssetId(1L);
        verify(assetVulnerabilityRepository).deleteByAssetId(1L);
        verify(subdomainRepository).deleteAllByAssetId(1L);
        verify(scanAssetMappingRepository).deleteByAssetId(1L);
        verify(riskScoreRepository).deleteByAssetId(1L);
        verify(honeypotDetectionRepository).deleteByAssetId(1L);
        verify(assetRepository).delete(asset);
    }

    @Test
    void shouldDeleteAssetWithoutPorts() {
        Asset asset = Asset.builder().id(2L).ipAddress("192.168.1.2").build();

        when(assetRepository.findById(2L)).thenReturn(Optional.of(asset));
        when(portRepository.findByAssetId(2L)).thenReturn(List.of());

        assetService.deleteAsset(2L);

        verify(crawledUrlRepository, never()).deleteByPortIdIn(any());
        verify(crawledUrlRepository).deleteByAssetId(2L);
        verify(portRepository).deleteByAssetId(2L);
        verify(riskScoreRepository).deleteByAssetId(2L);
        verify(honeypotDetectionRepository).deleteByAssetId(2L);
        verify(assetRepository).delete(asset);
    }

    @Test
    void shouldThrow404WhenAssetNotFound() {
        when(assetRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> assetService.deleteAsset(999L));

        verify(crawledUrlRepository, never()).deleteByAssetId(any());
        verify(portRepository, never()).deleteByAssetId(any());
        verify(assetRepository, never()).delete(any());
    }
}
