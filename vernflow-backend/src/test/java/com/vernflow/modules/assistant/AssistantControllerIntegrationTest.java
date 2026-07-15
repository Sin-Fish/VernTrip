package com.vernflow.modules.assistant;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:assistant-controller-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.storage.workspace-root-dir=./build/test-storage/assistant-controller",
        "app.ai.base-url=http://127.0.0.1:65530",
        "app.user.auth-required=false"
})
@AutoConfigureMockMvc
class AssistantControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private final Path workspaceRoot = Path.of("./build/test-storage/assistant-controller").toAbsolutePath().normalize();

    @AfterEach
    void tearDown() throws Exception {
        if (!Files.exists(workspaceRoot)) {
            return;
        }

        try (var paths = Files.walk(workspaceRoot)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (Exception ignored) {
                }
            });
        }
    }

    @Test
    void shouldCreateAssistantSessionFromWorkspaceFiles() throws Exception {
        Files.createDirectories(workspaceRoot.resolve("docs"));
        Files.writeString(
                workspaceRoot.resolve("docs").resolve("brief.md"),
                """
                VernFlow keeps the workspace as the primary source of truth.
                The assistant should stay bound to working files before expanding to the full workspace.
                """
        );

        String body = mockMvc.perform(post("/api/assistant/sessions/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "prompt":"助手应该如何描述工作文件优先这件事？",
                                  "globalScope":false,
                                  "workingFiles":[
                                    {"path":"workspace://docs/brief.md","label":"brief.md","kind":"file"}
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").isNumber())
                .andExpect(jsonPath("$.data.messages[0].role").value("user"))
                .andExpect(jsonPath("$.data.messages[1].role").value("assistant"))
                .andExpect(jsonPath("$.data.workingFiles[0].path").value("workspace://docs/brief.md"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode response = objectMapper.readTree(body);
        long sessionId = response.path("data").path("id").asLong();
        String expectedSummary = response.path("data").path("summary").asText();
        assertThat(response.path("data").path("messages").get(1).path("text").asText())
                .isNotBlank();
        assertThat(expectedSummary)
                .isEqualTo(response.path("data").path("messages").get(0).path("text").asText());

        String listBody = mockMvc.perform(get("/api/assistant/sessions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(sessionId))
                .andExpect(jsonPath("$.data[0].title").isNotEmpty())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode listResponse = objectMapper.readTree(listBody);
        assertThat(listResponse.path("data").get(0).path("summary").asText())
                .isEqualTo(expectedSummary);

        String detailBody = mockMvc.perform(get("/api/assistant/sessions/{sessionId}", sessionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(sessionId))
                .andExpect(jsonPath("$.data.messages[1].cites[0]").value("docs/brief.md"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode detailResponse = objectMapper.readTree(detailBody);
        assertThat(detailResponse.path("data").path("summary").asText())
                .isEqualTo(expectedSummary);
    }

    @Test
    void shouldDeleteAssistantSession() throws Exception {
        String body = mockMvc.perform(post("/api/assistant/sessions/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "prompt":"请总结 brief 文档",
                                  "globalScope":false,
                                  "workingFiles":[]
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        long sessionId = objectMapper.readTree(body).path("data").path("id").asLong();

        mockMvc.perform(delete("/api/assistant/sessions/{sessionId}", sessionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(true));

        String listBody = mockMvc.perform(get("/api/assistant/sessions"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode sessions = objectMapper.readTree(listBody).path("data");
        boolean stillExists = false;
        for (JsonNode session : sessions) {
            if (session.path("id").asLong() == sessionId) {
                stillExists = true;
                break;
            }
        }
        assertThat(stillExists).isFalse();
    }
}
