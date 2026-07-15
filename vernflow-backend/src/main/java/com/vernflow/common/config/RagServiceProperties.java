package com.vernflow.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.time.Duration;

/**
 * Dedicated configuration for the RAG service endpoint.
 *
 * @param baseUrl RAG service base URL
 * @param timeout HTTP timeout for RAG calls
 */
@ConfigurationProperties(prefix = "app.rag-service")
public record RagServiceProperties(URI baseUrl, Duration timeout) {
}
