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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
                                  "summary":"在老师指导下完成一件传统扇艺作品，适合亲子家庭。",
                                  "tags":["非遗体验","亲子友好","需预约"]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.statusLabel").value("待完善"))
                .andExpect(jsonPath("$.data.qualityScore").value(80))
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
