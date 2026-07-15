package com.vernflow.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "app.rag-store.flyway")
public record RagStoreFlywayProperties(
        boolean enabled,
        List<String> locations,
        String table
) {
}
