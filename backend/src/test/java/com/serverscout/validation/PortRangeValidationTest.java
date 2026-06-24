package com.serverscout.validation;

import com.serverscout.exception.BadRequestException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link PortRangeValidator}.
 */
class PortRangeValidationTest {

    // --- Valid port ranges ---

    @Test
    void shouldAcceptSinglePort() {
        assertDoesNotThrow(() -> PortRangeValidator.validate("80"));
    }

    @Test
    void shouldAcceptPortRange() {
        assertDoesNotThrow(() -> PortRangeValidator.validate("1-65535"));
    }

    @Test
    void shouldAcceptCommaSeparatedPorts() {
        assertDoesNotThrow(() -> PortRangeValidator.validate("22,80,443"));
    }

    @Test
    void shouldAcceptMixedPorts() {
        assertDoesNotThrow(() -> PortRangeValidator.validate("1-1000,8080,9000-9999"));
    }

    @Test
    void shouldAcceptSingleHighPort() {
        assertDoesNotThrow(() -> PortRangeValidator.validate("65535"));
    }

    // --- Invalid port ranges ---

    @Test
    void shouldRejectNullPortRange() {
        assertThrows(BadRequestException.class, () -> PortRangeValidator.validate(null));
    }

    @Test
    void shouldRejectEmptyPortRange() {
        assertThrows(BadRequestException.class, () -> PortRangeValidator.validate(""));
    }

    @Test
    void shouldRejectPortZero() {
        assertThrows(BadRequestException.class, () -> PortRangeValidator.validate("0-1000"));
    }

    @Test
    void shouldRejectPortOver65535() {
        assertThrows(BadRequestException.class, () -> PortRangeValidator.validate("65536"));
    }

    @Test
    void shouldRejectPortRangeOver65535() {
        assertThrows(BadRequestException.class, () -> PortRangeValidator.validate("1-100000"));
    }

    @Test
    void shouldRejectReversedPortRange() {
        assertThrows(BadRequestException.class, () -> PortRangeValidator.validate("1000-1"));
    }

    @Test
    void shouldRejectNonNumericPort() {
        assertThrows(BadRequestException.class, () -> PortRangeValidator.validate("abc"));
    }

    @Test
    void shouldRejectMixedInvalidToken() {
        assertThrows(BadRequestException.class, () -> PortRangeValidator.validate("80,abc,443"));
    }
}
