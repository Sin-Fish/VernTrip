package com.vernflow.modules.tourism.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vernflow.common.config.OpenClawProperties;
import com.vernflow.modules.openclaw.service.OpenClawGatewayClient;
import com.vernflow.modules.openclaw.service.OpenClawSessionContextService;
import com.vernflow.modules.tourism.domain.TourismPlace;
import com.vernflow.modules.tourism.domain.TourismResource;
import com.vernflow.modules.tourism.dto.TourismRecommendationRequest;
import com.vernflow.modules.tourism.dto.TourismRecommendationResponse;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TourismRecommendationServiceTest {

    private final OpenClawSessionContextService sessionContextService = mock(OpenClawSessionContextService.class);
    private final OpenClawGatewayClient gatewayClient = mock(OpenClawGatewayClient.class);
    private final DynamicTourismCatalogService catalogService = mock(DynamicTourismCatalogService.class);
    private final TourismRecommendationService service = new TourismRecommendationService(
            properties(),
            sessionContextService,
            gatewayClient,
            catalogService,
            new ObjectMapper()
    );

    @Test
    void shouldUseOnlyTrustedCandidateIdsAndFillMissingSlotsLocally() {
        when(catalogService.resourcesFor("杭州")).thenReturn(resources());
        when(sessionContextService.bind(eq(7L), eq(null), eq(false), eq(List.of())))
                .thenReturn(new OpenClawSessionContextService.BoundSession(
                        "verntrip:recommendation-test",
                        LocalDateTime.now().plusMinutes(30)
                ));
        when(gatewayClient.createResponse(eq("verntrip:recommendation-test"), anyString(), anyString()))
                .thenReturn(new OpenClawGatewayClient.ShadowResponse(
                        "resp_recommendation_1",
                        """
                                {"recommendations":[
                                  {"resourceId":3,"matchScore":96,"reason":"亲子手作体验与同行人高度匹配","caution":"建议提前预约"},
                                  {"resourceId":999,"matchScore":99,"reason":"不存在的候选资源","caution":""}
                                ]}
                                """,
                        200L,
                        160L
                ));

        TourismRecommendationResponse result = service.recommend(7L, request());

        assertThat(result.engine()).isEqualTo("openclaw");
        assertThat(result.recommendations()).hasSize(4);
        assertThat(result.recommendations().getFirst().resourceId()).isEqualTo(3L);
        assertThat(result.recommendations()).extracting(
                TourismRecommendationResponse.RecommendationItem::resourceId
        ).doesNotContain(999L).doesNotHaveDuplicates();
    }

    @Test
    void shouldReturnDeterministicFallbackWhenGatewayResponseIsInvalid() {
        when(catalogService.resourcesFor("杭州")).thenReturn(resources());
        when(sessionContextService.bind(eq(7L), eq(null), eq(false), eq(List.of())))
                .thenReturn(new OpenClawSessionContextService.BoundSession(
                        "verntrip:recommendation-fallback",
                        LocalDateTime.now().plusMinutes(30)
                ));
        when(gatewayClient.createResponse(eq("verntrip:recommendation-fallback"), anyString(), anyString()))
                .thenReturn(new OpenClawGatewayClient.ShadowResponse(
                        "resp_invalid",
                        "not-json",
                        100L,
                        20L
                ));

        TourismRecommendationResponse result = service.recommend(7L, request());

        assertThat(result.engine()).isEqualTo("local-fallback");
        assertThat(result.recommendations()).hasSize(4);
        assertThat(result.recommendations()).allSatisfy(item -> {
            assertThat(item.matchScore()).isBetween(60, 98);
            assertThat(item.reason()).isNotBlank();
        });
    }

    private TourismRecommendationRequest request() {
        return new TourismRecommendationRequest(
                "杭州",
                "family",
                "亲子出行",
                "舒缓慢游",
                BigDecimal.valueOf(3000),
                List.of(1L, 2L, 3L, 4L, 5L)
        );
    }

    private List<TourismResource> resources() {
        return List.of(
                resource(1L, "西湖慢行段", TourismResource.Category.SCENIC, "自然湖景与亲子漫步"),
                resource(2L, "工艺博物馆", TourismResource.Category.CULTURE, "室内文化研学"),
                resource(3L, "桥畔手作课堂", TourismResource.Category.EXPERIENCE, "亲子非遗手作体验"),
                resource(4L, "运河街区", TourismResource.Category.LEISURE, "适合轻松漫步"),
                resource(5L, "城市美术馆", TourismResource.Category.CULTURE, "艺术展览与室内参观")
        );
    }

    private TourismResource resource(
            Long id,
            String name,
            TourismResource.Category category,
            String summary
    ) {
        TourismPlace place = TourismPlace.approximate(
                "杭州",
                name,
                "演示区域",
                "测试地址",
                BigDecimal.valueOf(120.1),
                BigDecimal.valueOf(30.2),
                "测试地点"
        );
        TourismResource resource = TourismResource.published(
                place,
                name,
                category,
                summary,
                "[\"亲子\",\"体验\"]",
                "[\"亲子出行\",\"朋友结伴\"]",
                "测试资源库",
                null,
                "09:00-17:00",
                BigDecimal.ZERO,
                BigDecimal.valueOf(200)
        );
        ReflectionTestUtils.setField(resource, "id", id);
        return resource;
    }

    private OpenClawProperties properties() {
        return new OpenClawProperties(
                "openclaw",
                URI.create("http://127.0.0.1:18789"),
                "gateway-secret",
                "verntrip-main",
                Duration.ofMinutes(3),
                "service-secret",
                Duration.ofMinutes(30),
                1.0,
                Duration.ofSeconds(60),
                2400,
                12000
        );
    }
}
