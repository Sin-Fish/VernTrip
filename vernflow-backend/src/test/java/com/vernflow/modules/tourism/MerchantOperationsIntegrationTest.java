package com.vernflow.modules.tourism;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:merchant-operations-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.rag-store.datasource.url=jdbc:h2:mem:merchant-operations-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "app.rag-store.datasource.username=sa",
        "app.rag-store.datasource.password=",
        "app.rag-store.datasource.driver-class-name=org.h2.Driver",
        "app.rag-store.flyway.enabled=false",
        "app.storage.workspace-root-dir=./build/test-storage/merchant-operations",
        "app.ai.base-url=http://127.0.0.1:65530",
        "app.user.auth-required=true",
        "app.openclaw.engine=legacy"
})
@AutoConfigureMockMvc
class MerchantOperationsIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void shouldManageProductsAndKeepResourceStatusConsistent() throws Exception {
        String merchantToken = login("merchant", "merchant123");

        mockMvc.perform(get("/api/merchant/dashboard")
                        .header("Authorization", "Bearer " + merchantToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.resourceCount").value(12))
                .andExpect(jsonPath("$.data.productCount").value(3))
                .andExpect(jsonPath("$.data.publishedProductCount").value(2))
                .andExpect(jsonPath("$.data.averageQualityScore", greaterThanOrEqualTo(80)));

        String resourcesBody = mockMvc.perform(get("/api/merchant/resources")
                        .header("Authorization", "Bearer " + merchantToken))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode resources = objectMapper.readTree(resourcesBody).path("data");
        long firstResourceId = resources.get(0).path("id").asLong();
        long secondResourceId = resources.get(1).path("id").asLong();

        String productBody = mockMvc.perform(post("/api/merchant/products")
                        .header("Authorization", "Bearer " + merchantToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"测试亲子文化产品",
                                  "targetAudience":"亲子家庭",
                                  "durationDays":2,
                                  "price":688,
                                  "description":"用于验证真实资源组合和发布状态联动。",
                                  "highlights":["亲子友好","文化体验"],
                                  "resourceIds":[%d,%d]
                                }
                                """.formatted(firstResourceId, secondResourceId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.resources.length()").value(2))
                .andReturn()
                .getResponse()
                .getContentAsString();
        long productId = objectMapper.readTree(productBody).path("data").path("id").asLong();

        mockMvc.perform(patch("/api/merchant/products/{productId}/status", productId)
                        .header("Authorization", "Bearer " + merchantToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"PUBLISHED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"));

        mockMvc.perform(patch("/api/merchant/resources/{resourceId}/status", firstResourceId)
                        .header("Authorization", "Bearer " + merchantToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"INACTIVE\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/merchant/products")
                        .header("Authorization", "Bearer " + merchantToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id == %d)].status".formatted(productId), hasItem("INACTIVE")));

        mockMvc.perform(post("/api/merchant/content/generate")
                        .header("Authorization", "Bearer " + merchantToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sourceType":"RESOURCE",
                                  "sourceId":%d,
                                  "channel":"小红书",
                                  "tone":"温暖故事感"
                                }
                                """.formatted(secondResourceId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("当前未启用 OpenClaw 主引擎，无法生成真实营销内容。"));
    }

    @Test
    void shouldRejectTouristFromMerchantOperations() throws Exception {
        String touristToken = login("demo", "demo123");
        mockMvc.perform(get("/api/merchant/dashboard")
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
        return objectMapper.readTree(response).path("data").path("token").asText();
    }
}
