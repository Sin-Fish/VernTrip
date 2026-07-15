package com.vernflow.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Lightweight admin console credentials and session policy.
 *
 * @param username admin login username
 * @param password admin login password
 * @param sessionTtl admin session time-to-live
 */
@ConfigurationProperties(prefix = "app.admin")
public record AdminProperties(String username, String password, Duration sessionTtl) {
}
