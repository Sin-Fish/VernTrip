package com.vernflow.modules.tourism;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vernflow.integration.amap.AmapPoiClient;
import com.vernflow.integration.amap.AmapPoiClient.AmapPoi;
import com.vernflow.modules.tourism.service.TravelFortuneService;
import com.vernflow.modules.tourism.dto.FortuneProfileRequest;
import com.vernflow.modules.user.repository.AppUserRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:tourism-planning-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.rag-store.datasource.url=jdbc:h2:mem:tourism-planning-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "app.rag-store.datasource.username=sa",
        "app.rag-store.datasource.password=",
        "app.rag-store.datasource.driver-class-name=org.h2.Driver",
        "app.rag-store.flyway.enabled=false",
        "app.storage.workspace-root-dir=./build/test-storage/tourism-planning",
        "app.ai.base-url=http://127.0.0.1:65530",
        "app.user.auth-required=false",
        "app.openclaw.engine=legacy",
        "app.amap.web-service.key=test-amap-key",
        "app.amap.web-service.dynamic-catalog-enabled=true",
        "app.amap.web-service.minimum-catalog-size=8"
})
@AutoConfigureMockMvc
class TourismPlanningIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AmapPoiClient amapPoiClient;

    @Autowired
    private TravelFortuneService fortuneService;

    @Autowired
    private AppUserRepository users;

    @Test
    void shouldKeepFortunePreferencesSeparateForEachAccount() {
        Long touristId = users.findByUsernameIgnoreCase("demo").orElseThrow().getId();
        Long merchantId = users.findByUsernameIgnoreCase("merchant").orElseThrow().getId();
        fortuneService.saveProfile(touristId, new FortuneProfileRequest(List.of("摄影"), List.of("北京")));
        fortuneService.saveProfile(merchantId, new FortuneProfileRequest(List.of("徒步"), List.of("杭州")));
        assertThat(fortuneService.profile(touristId).interests()).containsExactly("摄影");
        assertThat(fortuneService.profile(touristId).visitedCities()).containsExactly("北京");
        assertThat(fortuneService.profile(merchantId).interests()).containsExactly("徒步");
        assertThat(fortuneService.profile(merchantId).visitedCities()).containsExactly("杭州");
    }

    @Test
    void shouldRejectFortuneWithoutInterestsOrWithPastDate() throws Exception {
        mockMvc.perform(post("/api/tourism/fortune").contentType(MediaType.APPLICATION_JSON).content("""
                {"preferences":{"interests":[],"visitedCities":[]},"date":"2000-01-01","days":2,
                 "travelers":"朋友结伴","budget":3000,"pace":"轻松一些","exploreNew":true}
                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldExtractConversationIntoAReadyTravelDraft() throws Exception {
        mockMvc.perform(post("/api/tourism/conversations/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "prompt":"从上海出发，周末两天带孩子慢游杭州，想体验非遗和本地美食，预算3000元",
                                  "current":{
                                    "destination":"杭州",
                                    "departure":"",
                                    "date":"",
                                    "days":2,
                                    "travelers":"",
                                    "budget":3000,
                                    "pace":"轻松一些",
                                    "interests":[],
                                    "notes":""
                                  },
                                  "history":[]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.draft.destination").value("杭州"))
                .andExpect(jsonPath("$.data.draft.departure").value("上海"))
                .andExpect(jsonPath("$.data.draft.days").value(2))
                .andExpect(jsonPath("$.data.draft.travelers").value("亲子出行"))
                .andExpect(jsonPath("$.data.draft.date").isNotEmpty())
                .andExpect(jsonPath("$.data.draft.interests").isNotEmpty())
                .andExpect(jsonPath("$.data.changes").isNotEmpty())
                .andExpect(jsonPath("$.data.missingFields").isEmpty())
                .andExpect(jsonPath("$.data.readyToGenerate").value(true))
                .andExpect(jsonPath("$.data.engine").value("local"));
    }

    @Test
    void shouldGenerateFourDayPlanWithCustomBudget() throws Exception {
        mockMvc.perform(post("/api/tourism/trips/plan")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "destination":"杭州",
                                  "departure":"苏州",
                                  "date":"2026-08-22",
                                  "days":4,
                                  "travelers":"两位朋友和一位长辈",
                                  "budget":4800,
                                  "pace":"上午充实，下午尽量轻松",
                                  "interests":["摄影","本地美食"],
                                  "notes":"每天留出午休时间。"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.engine").value("local"))
                .andExpect(jsonPath("$.data.request.days").value(4))
                .andExpect(jsonPath("$.data.request.budget").value(4800))
                .andExpect(jsonPath("$.data.request.travelers").value("两位朋友和一位长辈"))
                .andExpect(jsonPath("$.data.days.length()").value(4))
                .andExpect(jsonPath("$.data.days[2].items.length()").value(3))
                .andExpect(jsonPath("$.data.days[3].title").value("龙井茶事与运河日常"))
                .andExpect(jsonPath("$.data.budget.reserve").isNumber());
    }

    @Test
    void shouldImportAndPlanWithAnotherCityCatalog() throws Exception {
        when(amapPoiClient.available()).thenReturn(true);
        when(amapPoiClient.search(eq("苏州"), anyString())).thenAnswer(invocation -> switch (
                invocation.getArgument(1, String.class)
        ) {
            case "景点" -> List.of(
                    poi("SZ001", "拙政园", "姑苏区", "东北街178号", "120.629120", "31.326510", "80"),
                    poi("SZ002", "金鸡湖景区", "苏州工业园区", "现代大道", "120.704930", "31.316420", "0")
            );
            case "博物馆" -> List.of(
                    poi("SZ003", "苏州博物馆", "姑苏区", "东北街204号", "120.627910", "31.324320", "0")
            );
            case "文化体验" -> List.of(
                    poi("SZ004", "苏州丝绸博物馆", "姑苏区", "人民路2001号", "120.615280", "31.330930", "0")
            );
            case "餐厅" -> List.of(
                    poi("SZ005", "苏州本帮菜馆", "姑苏区", "临顿路", "120.630010", "31.311920", "100"),
                    poi("SZ006", "平江路苏式面馆", "姑苏区", "平江路", "120.638120", "31.314530", "45")
            );
            case "酒店" -> List.of(
                    poi("SZ007", "姑苏文化酒店", "姑苏区", "十全街", "120.627620", "31.298740", "420"),
                    poi("SZ008", "金鸡湖景观酒店", "苏州工业园区", "星港街", "120.684010", "31.311250", "560")
            );
            default -> List.of();
        });

        String responseBody = mockMvc.perform(post("/api/tourism/trips/plan")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "destination":"苏州",
                                  "departure":"南京",
                                  "date":"2026-09-05",
                                  "days":3,
                                  "travelers":"朋友结伴",
                                  "budget":4200,
                                  "pace":"轻松一些",
                                  "interests":["园林","人文历史","本地美食"],
                                  "notes":"下午保留休息时间。"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.engine").value("local"))
                .andExpect(jsonPath("$.data.request.destination").value("苏州"))
                .andExpect(jsonPath("$.data.days.length()").value(3))
                .andExpect(jsonPath("$.data.days[0].items.length()").value(4))
                .andExpect(jsonPath("$.data.days[0].items[0].placeName").value("拙政园"))
                .andExpect(jsonPath("$.data.days[0].items[0].longitude").value(120.629120))
                .andExpect(jsonPath("$.data.days[0].title").value("苏州探索第 1 日"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode plan = objectMapper.readTree(responseBody).path("data");
        assertThat(plan.toString()).doesNotContain("杭州");
        assertThat(plan.path("sources")).hasSizeGreaterThanOrEqualTo(6);

        mockMvc.perform(get("/api/tourism/resources").param("city", "苏州市"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(8));
        verify(amapPoiClient, times(5)).search(eq("苏州"), anyString());
    }

    private AmapPoi poi(
            String id,
            String name,
            String district,
            String address,
            String longitude,
            String latitude,
            String cost
    ) {
        return new AmapPoi(
                id,
                name,
                "旅游资源",
                "110000",
                "苏州市",
                district,
                address,
                new BigDecimal(longitude),
                new BigDecimal(latitude),
                "周一至周日 09:00-18:00",
                new BigDecimal(cost),
                new BigDecimal("4.6"),
                "苏州特色"
        );
    }

    @Test
    void shouldCompleteTouristPlanningAndCollectionFlow() throws Exception {
        mockMvc.perform(get("/api/tourism/resources").param("city", "杭州"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(12))
                .andExpect(jsonPath("$.data[0].name").value("中国杭州工艺美术博物馆"))
                .andExpect(jsonPath("$.data[0].placeName").value("杭州工艺美术博物馆"))
                .andExpect(jsonPath("$.data[0].locationStatus").value("VERIFIED"))
                .andExpect(jsonPath("$.data[0].amapPoiId").value("B023B0I02U"))
                .andExpect(jsonPath("$.data[0].longitude").value(120.136930))
                .andExpect(jsonPath("$.data[0].latitude").value(30.315645));

        String createdBody = mockMvc.perform(post("/api/tourism/trips/plan")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "destination":"杭州",
                                  "departure":"上海",
                                  "date":"2026-08-08",
                                  "days":2,
                                  "travelers":"亲子出行",
                                  "budget":3000,
                                  "pace":"轻松一些",
                                  "interests":["非遗体验","本地美食"],
                                  "notes":"孩子 7 岁，下午希望安排轻松一些。"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value(1))
                .andExpect(jsonPath("$.data.engine").value("local"))
                .andExpect(jsonPath("$.data.status").value("待决定"))
                .andExpect(jsonPath("$.data.days.length()").value(2))
                .andExpect(jsonPath("$.data.days[0].items.length()").value(4))
                .andExpect(jsonPath("$.data.days[0].items[0].placeName").value("杭州工艺美术博物馆"))
                .andExpect(jsonPath("$.data.days[0].items[0].locationStatus").value("VERIFIED"))
                .andExpect(jsonPath("$.data.days[0].items[0].longitude").value(120.136930))
                .andExpect(jsonPath("$.data.days[0].items[0].latitude").value(30.315645))
                .andExpect(jsonPath("$.data.sources.length()").value(7))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode created = objectMapper.readTree(createdBody).path("data");
        long firstPlanId = created.path("id").asLong();
        assertThat(firstPlanId).isPositive();
        assertThat(created.path("budget").path("reserve").decimalValue()).isPositive();
        long firstItemId = created.path("days").get(0).path("items").get(0).path("itemId").asLong();
        assertThat(firstItemId).isPositive();

        String replacedBody = mockMvc.perform(post("/api/tourism/plans/{planId}/adjust", firstPlanId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "type":"node",
                                  "action":"replace",
                                  "itemId":%d,
                                  "note":"换成适合雨天的室内场馆。"
                                }
                                """.formatted(firstItemId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.parentPlanId").value(firstPlanId))
                .andExpect(jsonPath("$.data.version").value(2))
                .andExpect(jsonPath("$.data.days[0].items.length()").value(4))
                .andExpect(jsonPath("$.data.days[0].items[0].title").value("中国丝绸博物馆"))
                .andExpect(jsonPath("$.data.days[0].items[1].title").value("运河家常杭帮菜"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode replaced = objectMapper.readTree(replacedBody).path("data");
        long replacedPlanId = replaced.path("id").asLong();
        long replacedItemId = replaced.path("days").get(0).path("items").get(0).path("itemId").asLong();

        String retimedBody = mockMvc.perform(post("/api/tourism/plans/{planId}/adjust", replacedPlanId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "type":"node",
                                  "action":"time",
                                  "itemId":%d,
                                  "time":"10:15"
                                }
                                """.formatted(replacedItemId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.parentPlanId").value(replacedPlanId))
                .andExpect(jsonPath("$.data.version").value(3))
                .andExpect(jsonPath("$.data.days[0].items[0].time").value("10:15"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode retimed = objectMapper.readTree(retimedBody).path("data");
        long retimedPlanId = retimed.path("id").asLong();
        long retimedItemId = retimed.path("days").get(0).path("items").get(0).path("itemId").asLong();
        mockMvc.perform(post("/api/tourism/plans/{planId}/adjust", retimedPlanId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "type":"node",
                                  "action":"delete",
                                  "itemId":%d
                                }
                                """.formatted(retimedItemId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.parentPlanId").value(retimedPlanId))
                .andExpect(jsonPath("$.data.version").value(4))
                .andExpect(jsonPath("$.data.days[0].items.length()").value(3))
                .andExpect(jsonPath("$.data.days[0].items[0].title").value("运河家常杭帮菜"));

        String adjustedBody = mockMvc.perform(post("/api/tourism/plans/{planId}/adjust", firstPlanId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "type":"budget",
                                  "note":"优先保留手作体验。"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.parentPlanId").value(firstPlanId))
                .andExpect(jsonPath("$.data.version").value(2))
                .andExpect(jsonPath("$.data.request.budget").value(2500))
                .andReturn()
                .getResponse()
                .getContentAsString();

        long adjustedPlanId = objectMapper.readTree(adjustedBody).path("data").path("id").asLong();
        assertThat(adjustedPlanId).isNotEqualTo(firstPlanId);

        mockMvc.perform(post("/api/tourism/plans/{planId}/save", adjustedPlanId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("已收藏"));

        mockMvc.perform(get("/api/tourism/plans"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(adjustedPlanId))
                .andExpect(jsonPath("$.data[0].version").value(2));

        mockMvc.perform(delete("/api/tourism/plans/{planId}/save", adjustedPlanId))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/tourism/plans"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());
    }
}
