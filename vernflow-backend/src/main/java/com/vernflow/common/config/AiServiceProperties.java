package com.vernflow.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.time.Duration;

/**
 * AI 服务连接配置。
 *
 * @param baseUrl AI 服务地址
 * @param timeout HTTP 超时时间
 */
@ConfigurationProperties(prefix = "app.ai")
public record AiServiceProperties(URI baseUrl, Duration timeout) {
}
