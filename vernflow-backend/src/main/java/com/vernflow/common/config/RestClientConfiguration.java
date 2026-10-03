package com.vernflow.common.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Backend-side HTTP clients for rag and agent services.
 */
@Configuration
public class RestClientConfiguration {

    @Bean
    @Qualifier("ragServiceRestClient")
    public RestClient ragServiceRestClient(RagServiceProperties properties) {
        return buildRestClient(properties.baseUrl().toString(), properties.timeout().toMillis());
    }

    @Bean
    @Qualifier("agentServiceRestClient")
    public RestClient agentServiceRestClient(AgentServiceProperties properties) {
        return buildRestClient(properties.baseUrl().toString(), properties.timeout().toMillis());
    }

    @Bean
    @Qualifier("openClawRestClient")
    public RestClient openClawRestClient(OpenClawProperties properties) {
        return buildRestClient(
                properties.baseUrl().toString(),
                properties.effectiveShadowTimeout().toMillis()
        );
    }

    @Bean
    @Qualifier("amapRestClient")
    public RestClient amapRestClient(AmapWebServiceProperties properties) {
        return buildRestClient(
                properties.baseUrl().toString(),
                properties.effectiveTimeout().toMillis()
        );
    }

    @Bean
    @Qualifier("klingRestClient")
    public RestClient klingRestClient(KlingProperties properties) {
        return buildRestClient(
                properties.baseUrl().toString(),
                properties.effectiveTimeout().toMillis()
        );
    }

    private RestClient buildRestClient(String baseUrl, long timeoutMillis) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        int timeout = Math.toIntExact(Math.min(timeoutMillis, Integer.MAX_VALUE));
        requestFactory.setConnectTimeout(timeout);
        requestFactory.setReadTimeout(timeout);
        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }
}
