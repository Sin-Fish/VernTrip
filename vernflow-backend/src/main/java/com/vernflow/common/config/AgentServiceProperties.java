package com.vernflow.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.time.Duration;

/**
 * Dedicated configuration for the agent service endpoint.
 *
 * @param baseUrl agent service base URL
 * @param timeout HTTP timeout for agent calls
 */
@ConfigurationProperties(prefix = "app.agent-service")
public record AgentServiceProperties(URI baseUrl, Duration timeout) {
}
