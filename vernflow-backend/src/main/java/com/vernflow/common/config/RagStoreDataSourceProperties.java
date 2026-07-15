package com.vernflow.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.rag-store.datasource")
public record RagStoreDataSourceProperties(
        String url,
        String username,
        String password,
        String driverClassName
) {
}
