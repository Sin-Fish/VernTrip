package com.vernflow.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.time.Duration;

/**
 * OpenClaw runtime and VernTrip bridge configuration.
 */
@ConfigurationProperties(prefix = "app.openclaw")
public record OpenClawProperties(
        String engine,
        URI baseUrl,
        String gatewayToken,
        String agentId,
        Duration timeout,
        String serviceToken,
        Duration sessionContextTtl,
        double shadowSampleRate,
        Duration shadowTimeout,
        int shadowMaxOutputTokens,
        int shadowMaxStoredChars
) {

    private static final Duration DEFAULT_SHADOW_TIMEOUT = Duration.ofSeconds(60);
    private static final int DEFAULT_SHADOW_MAX_OUTPUT_TOKENS = 1200;
    private static final int DEFAULT_SHADOW_MAX_STORED_CHARS = 12000;

    public boolean shadowEnabled() {
        return "shadow".equalsIgnoreCase(engine);
    }

    public boolean primaryEnabled() {
        return "openclaw".equalsIgnoreCase(engine);
    }

    public double effectiveShadowSampleRate() {
        return Math.max(0.0, Math.min(1.0, shadowSampleRate));
    }

    public Duration effectiveShadowTimeout() {
        return shadowTimeout == null || shadowTimeout.isZero() || shadowTimeout.isNegative()
                ? DEFAULT_SHADOW_TIMEOUT
                : shadowTimeout;
    }

    public int effectiveShadowMaxOutputTokens() {
        return shadowMaxOutputTokens > 0 ? shadowMaxOutputTokens : DEFAULT_SHADOW_MAX_OUTPUT_TOKENS;
    }

    public int effectiveShadowMaxStoredChars() {
        return shadowMaxStoredChars > 0 ? shadowMaxStoredChars : DEFAULT_SHADOW_MAX_STORED_CHARS;
    }
}
