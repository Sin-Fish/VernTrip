package com.vernflow.common.config;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class KlingPropertiesTest {

    @Test
    void emptyApiKeyEnablesMockMode() {
        KlingProperties properties = new KlingProperties(
                URI.create("https://api-singapore.klingai.com"),
                "",
                Duration.ofSeconds(60),
                Duration.ofSeconds(8)
        );
        assertThat(properties.mockEnabled()).isTrue();
    }

    @Test
    void configuredApiKeyDisablesMockMode() {
        KlingProperties properties = new KlingProperties(
                URI.create("https://api-singapore.klingai.com"),
                "secret-key",
                Duration.ofSeconds(60),
                Duration.ofSeconds(8)
        );
        assertThat(properties.mockEnabled()).isFalse();
    }

    @Test
    void invalidTimeoutFallsBackToDefault() {
        KlingProperties properties = new KlingProperties(
                URI.create("https://api-singapore.klingai.com"),
                "",
                null,
                Duration.ofSeconds(8)
        );
        assertThat(properties.effectiveTimeout()).isEqualTo(Duration.ofSeconds(60));
        assertThat(properties.effectiveMockDelay()).isEqualTo(Duration.ofSeconds(8));
    }

    @Test
    void zeroMockDelayIsKeptForInstantCompletion() {
        KlingProperties properties = new KlingProperties(
                URI.create("https://api-singapore.klingai.com"),
                "",
                Duration.ofSeconds(60),
                Duration.ZERO
        );
        assertThat(properties.effectiveMockDelay()).isEqualTo(Duration.ZERO);
    }
}
