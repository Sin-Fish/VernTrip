package com.vernflow.modules.tourism.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vernflow.common.config.AmapWebServiceProperties;
import com.vernflow.integration.amap.AmapPoiClient;
import com.vernflow.integration.amap.AmapPoiClient.AmapPoi;
import com.vernflow.modules.tourism.domain.TourismPlace;
import com.vernflow.modules.tourism.domain.TourismResource;
import com.vernflow.modules.tourism.repository.TourismPlaceRepository;
import com.vernflow.modules.tourism.repository.TourismResourceRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

@Service
public class DynamicTourismCatalogService {

    private static final Logger log = LoggerFactory.getLogger(DynamicTourismCatalogService.class);
    private static final String AMAP_SOURCE = "高德 POI 2.0（动态检索）";
    private static final List<SearchDefinition> SEARCH_DEFINITIONS = List.of(
            new SearchDefinition("景点", TourismResource.Category.SCENIC),
            new SearchDefinition("博物馆", TourismResource.Category.CULTURE),
            new SearchDefinition("文化体验", TourismResource.Category.EXPERIENCE),
            new SearchDefinition("餐厅", TourismResource.Category.FOOD),
            new SearchDefinition("酒店", TourismResource.Category.LODGING)
    );

    private final TourismResourceRepository tourismResourceRepository;
    private final TourismPlaceRepository tourismPlaceRepository;
    private final AmapPoiClient amapPoiClient;
    private final AmapWebServiceProperties properties;
    private final ObjectMapper objectMapper;

    public DynamicTourismCatalogService(
            TourismResourceRepository tourismResourceRepository,
            TourismPlaceRepository tourismPlaceRepository,
            AmapPoiClient amapPoiClient,
            AmapWebServiceProperties properties,
            ObjectMapper objectMapper
    ) {
        this.tourismResourceRepository = tourismResourceRepository;
        this.tourismPlaceRepository = tourismPlaceRepository;
        this.amapPoiClient = amapPoiClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public List<TourismResource> resourcesFor(String city) {
        String queryCity = requireCity(city);
        String catalogCity = normalizeCity(queryCity);
        List<TourismResource> resources = publishedResources(catalogCity);
        if (catalogReady(resources) || !amapPoiClient.available()) {
            return resources;
        }

        int imported = importAmapResources(queryCity, catalogCity);
        if (imported > 0) {
            log.info("Imported {} AMap tourism resources for {}", imported, catalogCity);
        }
        return publishedResources(catalogCity);
    }

    private int importAmapResources(String queryCity, String catalogCity) {
        int imported = 0;
        LinkedHashSet<String> seenPoiIds = new LinkedHashSet<>();
        for (SearchDefinition definition : SEARCH_DEFINITIONS) {
            List<AmapPoi> pois;
            try {
                pois = amapPoiClient.search(queryCity, definition.keyword());
            } catch (AmapPoiClient.AmapPoiException exception) {
                log.warn(
                        "AMap catalog search failed for city={} keyword={}: {}",
                        catalogCity,
                        definition.keyword(),
                        exception.getMessage()
                );
                continue;
            }
            for (AmapPoi poi : pois) {
                if (!seenPoiIds.add(poi.id()) || !validPoi(poi)) {
                    continue;
                }
                if (importPoi(catalogCity, definition.category(), poi)) {
                    imported++;
                }
            }
        }
        return imported;
    }

    private boolean importPoi(String city, TourismResource.Category category, AmapPoi poi) {
        TourismPlace place = tourismPlaceRepository.findByAmapPoiId(poi.id())
                .orElseGet(() -> tourismPlaceRepository.save(TourismPlace.verified(
                        limit(city, 80),
                        limit(poi.name(), 160),
                        nullableLimit(poi.district(), 80),
                        address(city, poi),
                        poi.id(),
                        poi.longitude().setScale(6, RoundingMode.HALF_UP),
                        poi.latitude().setScale(6, RoundingMode.HALF_UP),
                        AMAP_SOURCE
                )));
        if (tourismResourceRepository.existsByPlaceId(place.getId())
                || tourismResourceRepository.existsByCityIgnoreCaseAndNameIgnoreCase(city, poi.name())) {
            return false;
        }

        PriceRange prices = priceRange(category, poi.cost());
        TourismResource resource = TourismResource.published(
                place,
                limit(poi.name(), 160),
                category,
                summary(city, category, poi),
                writeList(tags(city, category, poi)),
                writeList(suitablePeople(category)),
                AMAP_SOURCE,
                null,
                nullableLimit(poi.openHours(), 160),
                prices.minimum(),
                prices.maximum()
        );
        tourismResourceRepository.save(resource);
        return true;
    }

    private List<TourismResource> publishedResources(String city) {
        return tourismResourceRepository.findAllByCityIgnoreCaseAndStatusOrderByIdAsc(
                city,
                TourismResource.PublicationStatus.PUBLISHED
        ).stream().filter(TourismResource::isAvailableForPlanning).toList();
    }

    private boolean catalogReady(List<TourismResource> resources) {
        long activities = resources.stream()
                .filter(resource -> resource.getCategory() != TourismResource.Category.FOOD)
                .filter(resource -> resource.getCategory() != TourismResource.Category.LODGING)
                .count();
        boolean hasFood = resources.stream()
                .anyMatch(resource -> resource.getCategory() == TourismResource.Category.FOOD);
        boolean hasLodging = resources.stream()
                .anyMatch(resource -> resource.getCategory() == TourismResource.Category.LODGING);
        return resources.size() >= properties.effectiveMinimumCatalogSize()
                && activities >= 4
                && hasFood
                && hasLodging;
    }

    private boolean validPoi(AmapPoi poi) {
        return StringUtils.hasText(poi.id())
                && poi.id().length() <= 64
                && StringUtils.hasText(poi.name())
                && poi.longitude() != null
                && poi.latitude() != null
                && poi.longitude().compareTo(BigDecimal.valueOf(73)) >= 0
                && poi.longitude().compareTo(BigDecimal.valueOf(136)) <= 0
                && poi.latitude().compareTo(BigDecimal.valueOf(3)) >= 0
                && poi.latitude().compareTo(BigDecimal.valueOf(54)) <= 0;
    }

    private String summary(String city, TourismResource.Category category, AmapPoi poi) {
        String location = StringUtils.hasText(poi.district()) ? poi.district() : city;
        String rating = poi.rating() == null ? "" : "，高德参考评分 " + poi.rating().stripTrailingZeros();
        return poi.name() + "位于" + location + "，属于" + categoryLabel(category)
                + rating + "。地点名称和坐标来自高德 POI，开放信息与价格请在出发前再次确认。";
    }

    private List<String> tags(String city, TourismResource.Category category, AmapPoi poi) {
        LinkedHashSet<String> tags = new LinkedHashSet<>();
        tags.add(categoryLabel(category));
        tags.add(city);
        if (StringUtils.hasText(poi.district())) {
            tags.add(poi.district());
        }
        if (StringUtils.hasText(poi.tag())) {
            tags.add(limit(poi.tag(), 80));
        }
        if (StringUtils.hasText(poi.type())) {
            tags.add(limit(poi.type(), 80));
        }
        tags.add("高德真实地点");
        return List.copyOf(tags);
    }

    private List<String> suitablePeople(TourismResource.Category category) {
        return switch (category) {
            case SCENIC, LEISURE -> List.of("独自出行", "情侣出行", "朋友结伴", "亲子出行");
            case CULTURE, EXPERIENCE -> List.of("独自出行", "朋友结伴", "亲子出行", "携长辈出行");
            case FOOD, LODGING -> List.of("通用");
        };
    }

    private PriceRange priceRange(TourismResource.Category category, BigDecimal amapCost) {
        if (amapCost != null && amapCost.signum() >= 0) {
            BigDecimal cost = amapCost.setScale(2, RoundingMode.HALF_UP);
            return new PriceRange(cost, cost);
        }
        return switch (category) {
            case SCENIC -> new PriceRange(money(0), money(160));
            case CULTURE -> new PriceRange(money(0), money(80));
            case EXPERIENCE -> new PriceRange(money(80), money(260));
            case FOOD -> new PriceRange(money(50), money(160));
            case LODGING -> new PriceRange(money(300), money(800));
            case LEISURE -> new PriceRange(money(0), money(120));
        };
    }

    private String categoryLabel(TourismResource.Category category) {
        return switch (category) {
            case SCENIC -> "自然风景";
            case CULTURE -> "人文历史";
            case EXPERIENCE -> "文化体验";
            case FOOD -> "本地美食";
            case LODGING -> "住宿";
            case LEISURE -> "休闲游览";
        };
    }

    private String address(String city, AmapPoi poi) {
        List<String> parts = new ArrayList<>();
        if (StringUtils.hasText(poi.district())) {
            parts.add(poi.district());
        }
        if (StringUtils.hasText(poi.address())) {
            parts.add(poi.address());
        }
        return limit(parts.isEmpty() ? city : String.join(" ", parts), 255);
    }

    private String writeList(List<String> values) {
        try {
            return objectMapper.writeValueAsString(values);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize dynamic tourism resource metadata", exception);
        }
    }

    private BigDecimal money(long value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }

    private String requireCity(String city) {
        if (!StringUtils.hasText(city)) {
            return "杭州";
        }
        return limit(city.trim(), 80);
    }

    public String normalizeCity(String city) {
        String value = requireCity(city);
        return value.length() > 2 && value.endsWith("市")
                ? value.substring(0, value.length() - 1)
                : value;
    }

    private String nullableLimit(String value, int maxLength) {
        return StringUtils.hasText(value) ? limit(value.trim(), maxLength) : null;
    }

    private String limit(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private record SearchDefinition(String keyword, TourismResource.Category category) {
    }

    private record PriceRange(BigDecimal minimum, BigDecimal maximum) {
    }
}
