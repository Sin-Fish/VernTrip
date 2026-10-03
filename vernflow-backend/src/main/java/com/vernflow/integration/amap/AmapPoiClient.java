package com.vernflow.integration.amap;

import com.fasterxml.jackson.databind.JsonNode;
import com.vernflow.common.config.AmapWebServiceProperties;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriBuilder;

import java.math.BigDecimal;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

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

    /**
     * 拉取 POI 实景照片（最多 3 张），优先按 POI id 精确查询，失败时按名称+城市搜索。
     * 返回空列表表示无照片可用（调用方应走兜底素材）。
     */
    public List<String> fetchPhotos(String amapPoiId, String name, String city) {
        if (!available() || !StringUtils.hasText(name)) {
            return List.of();
        }
        try {
            List<String> photos = queryPhotos(
                    uriBuilder -> uriBuilder
                            .path("/v5/place/detail")
                            .queryParam("key", properties.key())
                            .queryParam("id", amapPoiId)
                            .queryParam("show_fields", "photos")
                            .build(),
                    amapPoiId
            );
            if (photos.isEmpty() && StringUtils.hasText(city)) {
                photos = queryPhotos(
                        uriBuilder -> uriBuilder
                                .path("/v5/place/text")
                                .queryParam("key", properties.key())
                                .queryParam("keywords", name.trim())
                                .queryParam("region", city.trim())
                                .queryParam("city_limit", true)
                                .queryParam("show_fields", "photos")
                                .queryParam("page_size", 1)
                                .build(),
                        null
                );
            }
            return photos;
        } catch (RestClientException exception) {
            throw new AmapPoiException("AMap POI photo fetch failed", exception);
        }
    }

    /** 下载照片字节（store.is.autonavi.com 直链）。 */
    public byte[] downloadPhoto(String url) {
        try {
            byte[] body = restClient.get()
                    .uri(url)
                    .header("User-Agent", "VernFlow/1.0")
                    .retrieve()
                    .body(byte[].class);
            return body == null || body.length == 0 ? null : body;
        } catch (RestClientException exception) {
            return null;
        }
    }

    private List<String> queryPhotos(Function<UriBuilder, URI> uri, String expectedPoiId) {
        JsonNode response = restClient.get().uri(uri).retrieve().body(JsonNode.class);
        if (response == null || !"1".equals(text(response.path("status")))) {
            return List.of();
        }
        JsonNode source = response.path("pois");
        if (!source.isArray() || source.isEmpty()) {
            return List.of();
        }
        JsonNode poi = source.get(0);
        if (StringUtils.hasText(expectedPoiId) && source.size() > 1) {
            for (JsonNode node : source) {
                if (expectedPoiId.equals(text(node.path("id")))) {
                    poi = node;
                    break;
                }
            }
        }
        List<String> urls = new ArrayList<>();
        for (JsonNode photo : poi.path("photos")) {
            String url = text(photo.path("url"));
            if (StringUtils.hasText(url)) {
                urls.add(url);
            }
            if (urls.size() >= 3) {
                break;
            }
        }
        return List.copyOf(urls);
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
