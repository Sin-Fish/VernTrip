package com.vernflow.modules.tourism;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:merchant-resource-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.rag-store.datasource.url=jdbc:h2:mem:merchant-resource-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "app.rag-store.datasource.username=sa",
        "app.rag-store.datasource.password=",
        "app.rag-store.datasource.driver-class-name=org.h2.Driver",
        "app.rag-store.flyway.enabled=false",
        "app.storage.workspace-root-dir=./build/test-storage/merchant-resource",
        "app.ai.base-url=http://127.0.0.1:65530",
        "app.user.auth-required=true",
        "app.openclaw.engine=legacy"
})
@AutoConfigureMockMvc
class MerchantResourceIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void shouldPersistAndPublishMerchantResource() throws Exception {
        String merchantToken = login("merchant", "merchant123");

        mockMvc.perform(get("/api/merchant/resources")
                        .header("Authorization", "Bearer " + merchantToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(12));

        String createdBody = mockMvc.perform(post("/api/merchant/resources")
                        .header("Authorization", "Bearer " + merchantToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "city":"杭州",
                                  "name":"杭州扇艺手作体验",
                                  "category":"EXPERIENCE",
                                  "district":"拱墅区",
                                  "placeName":"杭州工艺美术博物馆",
                                  "amapPoiId":"B0FFH8X1DE",
                                  "address":"拱墅区小河路450号",
                                  "longitude":120.150114,
                                  "latitude":30.320335,
                                  "summary":"在老师指导下完成一件传统扇艺作品，适合亲子家庭。",
                                  "tags":["非遗体验","亲子友好","需预约"],
                                  "openHours":"09:00-17:00",
                                  "priceMin":128,
                                  "priceMax":228,
                                  "reservationRequired":true,
                                  "dailyCapacity":24,
                                  "availableCapacity":8
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.statusLabel").value("待完善"))
                .andExpect(jsonPath("$.data.placeName").value("杭州工艺美术博物馆"))
                .andExpect(jsonPath("$.data.locationStatus").value("VERIFIED"))
                .andExpect(jsonPath("$.data.amapPoiId").value("B0FFH8X1DE"))
                .andExpect(jsonPath("$.data.longitude").value(120.150114))
                .andExpect(jsonPath("$.data.qualityScore").value(100))
                .andExpect(jsonPath("$.data.availabilityStatus").value("AVAILABLE"))
                .andExpect(jsonPath("$.data.availableCapacity").value(8))
                .andReturn()
                .getResponse()
                .getContentAsString();

        long resourceId = objectMapper.readTree(createdBody).path("data").path("id").asLong();

        mockMvc.perform(patch("/api/merchant/resources/{resourceId}/status", resourceId)
                        .header("Authorization", "Bearer " + merchantToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"PUBLISHED"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.statusLabel").value("已上线"));

        mockMvc.perform(get("/api/tourism/resources").param("city", "杭州"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].name", hasItem("杭州扇艺手作体验")));

        mockMvc.perform(put("/api/merchant/resources/{resourceId}", resourceId)
                        .header("Authorization", "Bearer " + merchantToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"杭州扇艺手作体验",
                                  "category":"EXPERIENCE",
                                  "summary":"传统扇艺手作体验，当前场次已经约满。",
                                  "tags":["非遗体验","亲子友好","已约满"],
                                  "openHours":"09:30-17:30",
                                  "priceMin":138,
                                  "priceMax":238,
                                  "reservationRequired":true,
                                  "dailyCapacity":24,
                                  "availableCapacity":0
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.data.availabilityStatus").value("SOLD_OUT"))
                .andExpect(jsonPath("$.data.availabilityLabel").value("已约满"));

        mockMvc.perform(get("/api/tourism/resources").param("city", "杭州"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].name", not(hasItem("杭州扇艺手作体验"))));

        mockMvc.perform(put("/api/merchant/resources/{resourceId}", resourceId)
                        .header("Authorization", "Bearer " + merchantToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"杭州扇艺手作体验",
                                  "category":"EXPERIENCE",
                                  "summary":"传统扇艺手作体验，新场次已经开放预约。",
                                  "tags":["非遗体验","亲子友好","需预约"],
                                  "openHours":"09:30-17:30",
                                  "priceMin":138,
                                  "priceMax":238,
                                  "reservationRequired":true,
                                  "dailyCapacity":24,
                                  "availableCapacity":6
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.availabilityStatus").value("LIMITED"));

        mockMvc.perform(get("/api/tourism/resources").param("city", "杭州"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].name", hasItem("杭州扇艺手作体验")))
                .andExpect(jsonPath("$.data[?(@.name == '杭州扇艺手作体验')].openHours")
                        .value(hasItem("09:30-17:30")));
    }

    @Test
    void shouldRequireAmapPoiWhenCreatingMerchantResource() throws Exception {
        String merchantToken = login("merchant", "merchant123");

        mockMvc.perform(post("/api/merchant/resources")
                        .header("Authorization", "Bearer " + merchantToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "city":"杭州",
                                  "name":"未定位的体验资源",
                                  "category":"EXPERIENCE",
                                  "district":"拱墅区",
                                  "summary":"缺少真实地点绑定。",
                                  "tags":["体验"]
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldAuditResourceDraftWithoutPretendingFallbackIsAi() throws Exception {
        String merchantToken = login("merchant", "merchant123");

        mockMvc.perform(post("/api/merchant/resources/audit")
                        .header("Authorization", "Bearer " + merchantToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "city":"苏州",
                                  "name":"园林拓印体验",
                                  "category":"EXPERIENCE",
                                  "district":"姑苏区",
                                  "placeName":"苏州博物馆",
                                  "amapPoiId":"B0TESTPOI",
                                  "address":"姑苏区东北街204号",
                                  "longitude":120.627929,
                                  "latitude":31.324097,
                                  "summary":"",
                                  "tags":[],
                                  "openHours":"09:00-17:00",
                                  "priceMin":68,
                                  "priceMax":128,
                                  "reservationRequired":true,
                                  "dailyCapacity":20,
                                  "availableCapacity":8
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.engine").value("LOCAL_RULES"))
                .andExpect(jsonPath("$.data.currentScore").value(78))
                .andExpect(jsonPath("$.data.projectedScore", greaterThanOrEqualTo(90)))
                .andExpect(jsonPath("$.data.recommendable").value(true))
                .andExpect(jsonPath("$.data.generatedSummary").isNotEmpty())
                .andExpect(jsonPath("$.data.generatedTags.length()", greaterThanOrEqualTo(3)))
                .andExpect(jsonPath("$.data.checks.length()").value(4));
    }

    @Test
    void shouldRejectTouristAccountFromMerchantResources() throws Exception {
        String touristToken = login("demo", "demo123");

        mockMvc.perform(get("/api/merchant/resources")
                        .header("Authorization", "Bearer " + touristToken))
                .andExpect(status().isForbidden());
    }

    private String login(String username, String password) throws Exception {
        String response = mockMvc.perform(post("/api/user/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"%s"}
                                """.formatted(username, password)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode data = objectMapper.readTree(response).path("data");
        return data.path("token").asText();
    }
}
