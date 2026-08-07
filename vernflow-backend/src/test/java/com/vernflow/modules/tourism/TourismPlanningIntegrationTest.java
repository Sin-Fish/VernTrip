package com.vernflow.modules.tourism;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
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
        "app.openclaw.engine=legacy"
})
@AutoConfigureMockMvc
class TourismPlanningIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

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
    void shouldCompleteTouristPlanningAndCollectionFlow() throws Exception {
        mockMvc.perform(get("/api/tourism/resources").param("city", "杭州"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(12))
                .andExpect(jsonPath("$.data[0].name").value("中国杭州工艺美术博物馆"))
                .andExpect(jsonPath("$.data[0].longitude").value(120.150114))
                .andExpect(jsonPath("$.data[0].latitude").value(30.320335));

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
                .andExpect(jsonPath("$.data.days[0].items[0].longitude").value(120.150114))
                .andExpect(jsonPath("$.data.days[0].items[0].latitude").value(30.320335))
                .andExpect(jsonPath("$.data.sources.length()").value(7))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode created = objectMapper.readTree(createdBody).path("data");
        long firstPlanId = created.path("id").asLong();
        assertThat(firstPlanId).isPositive();
        assertThat(created.path("budget").path("reserve").decimalValue()).isPositive();

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
