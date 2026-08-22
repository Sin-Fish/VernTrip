package com.vernflow.integration.amap;

import com.fasterxml.jackson.databind.JsonNode;
import com.vernflow.common.config.AmapWebServiceProperties;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Component
public class AmapPoiClient {

    private final RestClient restClient;
    private final AmapWebServiceProperties properties;

    public AmapPoiClient(
            @Qualifier("amapRestClient") RestClient restClient,
            AmapWebServiceProperties properties
    ) {
        this.restClient = restClient;
        this.properties = properties;
    }

    public boolean available() {
        return properties.available();
    }

    public List<AmapPoi> search(String city, String keyword) {
        if (!available() || !StringUtils.hasText(city) || !StringUtils.hasText(keyword)) {
            return List.of();
        }

        try {
            JsonNode response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v5/place/text")
                            .queryParam("key", properties.key())
                            .queryParam("keywords", keyword.trim())
                            .queryParam("region", city.trim())
                            .queryParam("city_limit", true)
                            .queryParam("show_fields", "business")
                            .queryParam("page_size", properties.effectivePageSize())
                            .queryParam("page_num", 1)
                            .build())
                    .retrieve()
                    .body(JsonNode.class);
            return parseResponse(response);
        } catch (RestClientException exception) {
            throw new AmapPoiException("AMap POI search failed", exception);
        }
    }

    private List<AmapPoi> parseResponse(JsonNode response) {
        if (response == null || !"1".equals(text(response.path("status")))) {
            String info = response == null ? "empty_response" : text(response.path("info"));
            throw new AmapPoiException("AMap POI search rejected: " + info);
        }

        List<AmapPoi> pois = new ArrayList<>();
        JsonNode source = response.path("pois");
        if (!source.isArray()) {
            return pois;
        }
        for (JsonNode node : source) {
            AmapPoi poi = parsePoi(node);
            if (poi != null) {
                pois.add(poi);
            }
        }
        return List.copyOf(pois);
    }

    private AmapPoi parsePoi(JsonNode node) {
        String id = text(node.path("id"));
        String name = text(node.path("name"));
        String location = text(node.path("location"));
        if (!StringUtils.hasText(id) || !StringUtils.hasText(name) || !StringUtils.hasText(location)) {
            return null;
        }

        String[] coordinates = location.split(",", 2);
        if (coordinates.length != 2) {
            return null;
        }
        try {
            JsonNode business = node.path("business");
            return new AmapPoi(
                    id,
                    name,
                    text(node.path("type")),
                    text(node.path("typecode")),
                    text(node.path("cityname")),
                    text(node.path("adname")),
                    text(node.path("address")),
                    new BigDecimal(coordinates[0].trim()),
                    new BigDecimal(coordinates[1].trim()),
                    text(business.path("opentime_week")),
                    decimal(business.path("cost")),
                    decimal(business.path("rating")),
                    text(business.path("tag"))
            );
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private String text(JsonNode node) {
        return node != null && node.isTextual() ? node.asText().trim() : "";
    }

    private BigDecimal decimal(JsonNode node) {
        String value = text(node);
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    public record AmapPoi(
            String id,
            String name,
            String type,
            String typeCode,
            String city,
            String district,
            String address,
            BigDecimal longitude,
            BigDecimal latitude,
            String openHours,
            BigDecimal cost,
            BigDecimal rating,
            String tag
    ) {
    }

    public static class AmapPoiException extends RuntimeException {

        public AmapPoiException(String message) {
            super(message);
        }

        public AmapPoiException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
