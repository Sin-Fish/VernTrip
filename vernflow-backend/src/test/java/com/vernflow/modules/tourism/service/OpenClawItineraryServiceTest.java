package com.vernflow.modules.tourism.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vernflow.common.config.OpenClawProperties;
import com.vernflow.modules.openclaw.service.OpenClawGatewayClient;
import com.vernflow.modules.openclaw.service.OpenClawSessionContextService;
import com.vernflow.modules.tourism.domain.TourismPlace;
import com.vernflow.modules.tourism.domain.TourismResource;
import com.vernflow.modules.tourism.domain.TripRequest;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpenClawItineraryServiceTest {

    private final OpenClawSessionContextService sessionContextService = mock(OpenClawSessionContextService.class);
    private final OpenClawGatewayClient gatewayClient = mock(OpenClawGatewayClient.class);
    private final OpenClawItineraryService service = new OpenClawItineraryService(
            properties(),
            sessionContextService,
            gatewayClient,
            new ObjectMapper()
    );

    @Test
    void shouldAcceptStructuredItineraryUsingOnlyTrustedResources() {
        when(sessionContextService.bind(eq(7L), eq(null), eq(false), eq(List.of())))
                .thenReturn(new OpenClawSessionContextService.BoundSession(
                        "verntrip:itinerary-test",
                        LocalDateTime.now().plusMinutes(30)
                ));
        when(gatewayClient.createResponse(eq("verntrip:itinerary-test"), anyString(), anyString()))
                .thenReturn(new OpenClawGatewayClient.ShadowResponse(
                        "resp_itinerary_1",
                        """
                                {
                                  "title":"杭州亲子手作慢游",
                                  "summary":"沿运河和西湖安排手作、杭帮菜与轻松漫步，为亲子家庭保留休息时间。",
                                  "matchScore":91,
                                  "days":[
                                    {"day":1,"items":[
                                      {"time":"09:30","label":"认识杭州手艺","resourceName":"工艺博物馆","description":"以室内文化体验开启行程。","cost":0},
                                      {"time":"12:00","label":"午餐","resourceName":"运河杭帮菜","description":"选择适合家庭分享的本地菜。","cost":240},
                                      {"time":"14:30","label":"亲手做一件作品","resourceName":"桥畔手作课堂","description":"预留九十分钟完成亲子手作。","cost":180},
                                      {"time":"17:00","label":"运河边散步","resourceName":"小河直街散步","description":"手作结束后在运河街区自由停留。","cost":0},
                                      {"time":"18:30","label":"就近住下","resourceName":"湖滨亲子酒店","description":"减少第二天跨区移动。","cost":900}
                                    ]},
                                    {"day":2,"items":[
                                      {"time":"09:00","label":"湖边慢行","resourceName":"西湖慢行段","description":"按亲子节奏安排短距离步行。","cost":0},
                                      {"time":"12:00","label":"时令午餐","resourceName":"湖滨时令小馆","description":"午餐后就近前往下一站。","cost":280},
                                      {"time":"15:00","label":"宋韵收尾","resourceName":"清河坊夜游","description":"自由探索街区并选择旅行纪念。","cost":120}
                                    ]}
                                  ]
                                }
                                """,
                        500L,
                        900L
                ));

        OpenClawItineraryService.AgentItinerary itinerary = service.generate(
                7L,
                request(),
                resources()
        ).orElseThrow();

        assertThat(itinerary.title()).isEqualTo("杭州亲子手作慢游");
        assertThat(itinerary.matchScore()).isEqualTo(91);
        assertThat(itinerary.stops()).hasSize(8);
        assertThat(itinerary.stops().get(2).tag()).isEqualTo("非遗体验");
        assertThat(itinerary.stops()).extracting(OpenClawItineraryService.AgentStop::resourceName)
                .doesNotHaveDuplicates();
    }

    @Test
    void shouldRejectAnItineraryThatInventsAResource() {
        when(sessionContextService.bind(eq(7L), eq(null), eq(false), eq(List.of())))
                .thenReturn(new OpenClawSessionContextService.BoundSession(
                        "verntrip:invalid-test",
                        LocalDateTime.now().plusMinutes(30)
                ));
        when(gatewayClient.createResponse(eq("verntrip:invalid-test"), anyString(), anyString()))
                .thenReturn(new OpenClawGatewayClient.ShadowResponse(
                        "resp_invalid",
                        """
                                {
                                  "title":"无效行程",
                                  "summary":"包含目录外资源的行程不能被采用。",
                                  "matchScore":88,
                                  "days":[
                                    {"day":1,"items":[
                                      {"time":"09:00","label":"上午","resourceName":"不存在的景点","description":"无效资源。","cost":0},
                                      {"time":"12:00","label":"午餐","resourceName":"运河杭帮菜","description":"午餐。","cost":200},
                                      {"time":"14:00","label":"手作","resourceName":"桥畔手作课堂","description":"手作。","cost":180}
                                    ]},
                                    {"day":2,"items":[
                                      {"time":"09:00","label":"散步","resourceName":"西湖慢行段","description":"散步。","cost":0},
                                      {"time":"12:00","label":"午餐","resourceName":"湖滨时令小馆","description":"午餐。","cost":200},
                                      {"time":"15:00","label":"夜游","resourceName":"清河坊夜游","description":"夜游。","cost":100}
                                    ]}
                                  ]
                                }
                                """,
                        300L,
                        400L
                ));

        assertThat(service.generate(7L, request(), resources())).isEmpty();
        verify(gatewayClient, times(2))
                .createResponse(eq("verntrip:invalid-test"), anyString(), anyString());
    }

    @Test
    void shouldMoveASingleTrustedLodgingNodeToDayOne() {
        when(sessionContextService.bind(eq(7L), eq(null), eq(false), eq(List.of())))
                .thenReturn(new OpenClawSessionContextService.BoundSession(
                        "verntrip:lodging-normalization-test",
                        LocalDateTime.now().plusMinutes(30)
                ));
        when(gatewayClient.createResponse(
                eq("verntrip:lodging-normalization-test"),
                anyString(),
                anyString()
        )).thenReturn(new OpenClawGatewayClient.ShadowResponse(
                "resp_lodging_normalization",
                """
                        {
                          "title":"杭州两日慢游",
                          "summary":"可信节点完整，住宿节点将在后端移动到第一天末尾。",
                          "matchScore":88,
                          "days":[
                            {"day":1,"items":[
                              {"time":"09:30","label":"参观","resourceName":"工艺博物馆","description":"室内参观。","cost":400},
                              {"time":"12:00","label":"午餐","resourceName":"运河杭帮菜","description":"本地午餐。","cost":400},
                              {"time":"14:30","label":"手作","resourceName":"桥畔手作课堂","description":"下午体验。","cost":400}
                            ]},
                            {"day":2,"items":[
                              {"time":"09:00","label":"办理入住","resourceName":"湖滨亲子酒店","description":"全程唯一住宿。","cost":1800},
                              {"time":"10:00","label":"湖边慢行","resourceName":"西湖慢行段","description":"轻松步行。","cost":200},
                              {"time":"12:00","label":"午餐","resourceName":"湖滨时令小馆","description":"就近午餐。","cost":400},
                              {"time":"15:00","label":"夜游","resourceName":"清河坊夜游","description":"街区漫步。","cost":200}
                            ]}
                          ]
                        }
                        """,
                400L,
                700L
        ));

        OpenClawItineraryService.AgentItinerary itinerary = service.generate(
                7L,
                request(),
                resources()
        ).orElseThrow();

        List<OpenClawItineraryService.AgentStop> lodgingStops = itinerary.stops().stream()
                .filter(stop -> stop.resourceName().equals("湖滨亲子酒店"))
                .toList();
        assertThat(lodgingStops).singleElement().satisfies(stop -> {
            assertThat(stop.day()).isEqualTo(1);
            assertThat(stop.sequence()).isEqualTo(4);
            assertThat(stop.time()).isEqualTo("18:00");
        });
        assertThat(itinerary.stops()).filteredOn(stop -> stop.day() == 2).hasSize(3);
        BigDecimal normalizedTotal = itinerary.stops().stream()
                .map(OpenClawItineraryService.AgentStop::cost)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(normalizedTotal.compareTo(new BigDecimal("2400"))).isLessThanOrEqualTo(0);
    }

    @Test
    void shouldPreserveAnUnsatisfiedReplacementDecisionForBackendValidation() {
        when(sessionContextService.bind(eq(7L), eq(null), eq(false), eq(List.of())))
                .thenReturn(new OpenClawSessionContextService.BoundSession(
                        "verntrip:replacement-test",
                        LocalDateTime.now().plusMinutes(30)
                ));
        when(gatewayClient.createResponse(eq("verntrip:replacement-test"), anyString(), anyString()))
                .thenReturn(new OpenClawGatewayClient.ShadowResponse(
                        "resp_replacement_1",
                        """
                                {
                                  "resourceName":"丝绸博物馆",
                                  "satisfiesRequest":false,
                                  "reason":"候选资源中没有体育公园或户外运动场地。"
                                }
                                """,
                        180L,
                        260L
                ));

        OpenClawItineraryService.AgentReplacement replacement = service.selectReplacement(
                7L,
                request(),
                resource("工艺博物馆", TourismResource.Category.CULTURE),
                List.of(resource("丝绸博物馆", TourismResource.Category.CULTURE)),
                List.of("桥畔手作课堂", "运河杭帮菜"),
                "想换成体育公园"
        ).orElseThrow();

        assertThat(replacement.resourceName()).isEqualTo("丝绸博物馆");
        assertThat(replacement.satisfiesRequest()).isFalse();
        assertThat(replacement.reason()).contains("没有体育公园");
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

    private TripRequest request() {
        return TripRequest.create(
                7L,
                "杭州",
                "上海",
                LocalDate.of(2026, 8, 8),
                2,
                "亲子出行",
                BigDecimal.valueOf(3000),
                "轻松一些",
                "[\"非遗体验\",\"本地美食\"]",
                "孩子七岁"
        );
    }

    private List<TourismResource> resources() {
        return List.of(
                resource("工艺博物馆", TourismResource.Category.CULTURE),
                resource("运河杭帮菜", TourismResource.Category.FOOD),
                resource("桥畔手作课堂", TourismResource.Category.EXPERIENCE),
                resource("小河直街散步", TourismResource.Category.LEISURE),
                resource("湖滨亲子酒店", TourismResource.Category.LODGING),
                resource("西湖慢行段", TourismResource.Category.SCENIC),
                resource("湖滨时令小馆", TourismResource.Category.FOOD),
                resource("清河坊夜游", TourismResource.Category.LEISURE)
        );
    }

    private TourismResource resource(String name, TourismResource.Category category) {
        TourismPlace place = TourismPlace.approximate(
                "杭州",
                name,
                "演示区域",
                "测试地址",
                BigDecimal.valueOf(120.1),
                BigDecimal.valueOf(30.2),
                "测试地点"
        );
        return TourismResource.published(
                place,
                name,
                category,
                "用于结构化行程测试的可信资源。",
                "[]",
                "[]",
                "测试资源库",
                null,
                null,
                BigDecimal.ZERO,
                BigDecimal.valueOf(1000)
        );
    }
}
