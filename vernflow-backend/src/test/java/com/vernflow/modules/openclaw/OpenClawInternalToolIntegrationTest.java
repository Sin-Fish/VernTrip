package com.vernflow.modules.openclaw;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vernflow.modules.openclaw.service.OpenClawSessionContextService;
import com.vernflow.modules.user.domain.AppUser;
import com.vernflow.modules.user.repository.AppUserRepository;
import com.vernflow.modules.workspace.dto.WorkspaceItemRef;
import com.vernflow.modules.workspace.service.UserWorkspaceRootService;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:openclaw-tool-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.rag-store.datasource.url=jdbc:h2:mem:openclaw-tool-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "app.rag-store.datasource.username=sa",
        "app.rag-store.datasource.password=",
        "app.rag-store.datasource.driver-class-name=org.h2.Driver",
        "app.rag-store.flyway.enabled=false",
        "app.storage.workspace-root-dir=./build/test-storage/openclaw-tool",
        "app.ai.base-url=http://127.0.0.1:65530",
        "app.user.auth-required=true",
        "app.openclaw.service-token=test-openclaw-service-token",
        "app.openclaw.session-context-ttl=30m"
})
@AutoConfigureMockMvc
class OpenClawInternalToolIntegrationTest {

    private static final String ENDPOINT = "/api/internal/openclaw/v1/tools/workspace-scope-read";
    private static final String TOURISM_ENDPOINT = "/api/internal/openclaw/v1/tools/tourism-catalog-read";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private UserWorkspaceRootService userWorkspaceRootService;

    @Autowired
    private OpenClawSessionContextService contextService;

    private final Path storageRoot = Path.of("./build/test-storage/openclaw-tool")
            .toAbsolutePath()
            .normalize();

    @AfterEach
    void tearDown() throws Exception {
        if (!Files.exists(storageRoot)) {
            return;
        }
        try (var paths = Files.walk(storageRoot)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (Exception ignored) {
                }
            });
        }
    }

    @Test
    void shouldRequireServiceTokenAndReturnOnlyBoundWorkspaceScope() throws Exception {
        AppUser demo = appUserRepository.findByUsernameIgnoreCase("demo").orElseThrow();
        Path userRoot = userWorkspaceRootService.resolveUserWorkspaceRoot(demo.getId());
        Files.createDirectories(userRoot.resolve("travel"));
        Files.writeString(
                userRoot.resolve("travel").resolve("guide.md"),
                "West Lake cultural route. ".repeat(800)
        );

        OpenClawSessionContextService.BoundSession boundSession = contextService.bind(
                demo.getId(),
                null,
                false,
                List.of(new WorkspaceItemRef(
                        "workspace://travel/guide.md",
                        "guide.md",
                        "file"
                ))
        );

        mockMvc.perform(post(ENDPOINT)
                        .header("X-OpenClaw-Session-Key", boundSession.sessionKey()))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post(ENDPOINT)
                        .header("Authorization", "Bearer wrong-token")
                        .header("X-OpenClaw-Session-Key", boundSession.sessionKey()))
                .andExpect(status().isUnauthorized());

        String responseBody = mockMvc.perform(post(ENDPOINT)
                        .header("Authorization", "Bearer test-openclaw-service-token")
                        .header("X-OpenClaw-Session-Key", boundSession.sessionKey()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.globalScope").value(false))
                .andExpect(jsonPath("$.data.totalScopeFileCount").value(1))
                .andExpect(jsonPath("$.data.documents[0].path").value("workspace://travel/guide.md"))
                .andExpect(jsonPath("$.data.documents[0].citeLabel").value("travel/guide.md"))
                .andExpect(jsonPath("$.data.documents[0].chunks.length()").value(4))
                .andExpect(jsonPath("$.data.documents[0].sourceSignature").doesNotExist())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode firstChunk = objectMapper.readTree(responseBody)
                .path("data")
                .path("documents")
                .get(0)
                .path("chunks")
                .get(0);
        assertThat(firstChunk.asText()).hasSize(700);
    }

    @Test
    void shouldReadTourismCatalogThroughTrustedSession() throws Exception {
        AppUser demo = appUserRepository.findByUsernameIgnoreCase("demo").orElseThrow();
        OpenClawSessionContextService.BoundSession boundSession = contextService.bind(
                demo.getId(),
                null,
                false,
                List.of()
        );

        mockMvc.perform(post(TOURISM_ENDPOINT)
                        .header("Authorization", "Bearer test-openclaw-service-token")
                        .header("X-OpenClaw-Session-Key", boundSession.sessionKey())
                        .contentType("application/json")
                        .content("{\"city\":\"杭州\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.city").value("杭州"))
                .andExpect(jsonPath("$.data.supported").value(true))
                .andExpect(jsonPath("$.data.total").value(12))
                .andExpect(jsonPath("$.data.resources[0].name").value("中国杭州工艺美术博物馆"));
    }
}
