package com.vernflow.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.time.Duration;

/**
 * Server-side AMap POI search and dynamic tourism catalog configuration.
 */
@ConfigurationProperties(prefix = "app.amap.web-service")
public record AmapWebServiceProperties(
        URI baseUrl,
        String key,
        Duration timeout,
        boolean dynamicCatalogEnabled,
        int minimumCatalogSize,
        int pageSize
) {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(8);
    private static final int DEFAULT_MINIMUM_CATALOG_SIZE = 12;
    private static final int DEFAULT_PAGE_SIZE = 8;

    public boolean available() {
        return dynamicCatalogEnabled && StringUtils.hasText(key);
    }

    public Duration effectiveTimeout() {
        return timeout == null || timeout.isZero() || timeout.isNegative()
                ? DEFAULT_TIMEOUT
                : timeout;
    }

    public int effectiveMinimumCatalogSize() {
        return minimumCatalogSize > 0 ? minimumCatalogSize : DEFAULT_MINIMUM_CATALOG_SIZE;
    }

    public int effectivePageSize() {
        return pageSize > 0 ? Math.min(pageSize, 25) : DEFAULT_PAGE_SIZE;
    }
}
