package com.vernflow.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.time.Duration;

/**
 * 可灵 Kling 视频生成 API 配置。
 * apiKey 为空时启用 mock 模式：任务在模拟延迟后直接成功，返回内置样例视频。
 */
@ConfigurationProperties(prefix = "app.kling")
public record KlingProperties(
        URI baseUrl,
        String apiKey,
        Duration timeout,
        Duration mockDelay
) {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(60);
    private static final Duration DEFAULT_MOCK_DELAY = Duration.ofSeconds(8);

    /** 无 API Key 时进入 Mock 模式。 */
    public boolean mockEnabled() {
        return !StringUtils.hasText(apiKey);
    }

    public Duration effectiveTimeout() {
        return timeout == null || timeout.isZero() || timeout.isNegative()
                ? DEFAULT_TIMEOUT
                : timeout;
    }

    public Duration effectiveMockDelay() {
        return mockDelay == null || mockDelay.isNegative()
                ? DEFAULT_MOCK_DELAY
                : mockDelay;
    }
}
