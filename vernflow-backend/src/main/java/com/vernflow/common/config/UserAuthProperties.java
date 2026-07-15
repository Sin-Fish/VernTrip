package com.vernflow.common.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.user")
public record UserAuthProperties(Duration sessionTtl, boolean authRequired) {
}
