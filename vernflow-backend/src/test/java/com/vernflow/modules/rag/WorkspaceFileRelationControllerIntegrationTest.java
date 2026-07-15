package com.vernflow.modules.rag;

import com.vernflow.modules.user.domain.AppUser;
import com.vernflow.modules.user.repository.AppUserRepository;
import com.vernflow.modules.workspace.service.UserWorkspaceRootService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:file-relation-controller-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.storage.workspace-root-dir=./build/test-storage/file-relation-controller",
        "app.ai.base-url=http://127.0.0.1:65530",
        "app.user.auth-required=false"
})
@AutoConfigureMockMvc
class WorkspaceFileRelationControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private UserWorkspaceRootService userWorkspaceRootService;

    @Test
    void shouldReturnFileRelationGraphForFocusedFile() throws Exception {
        AppUser demoUser = appUserRepository.findByUsernameIgnoreCase("demo").orElseThrow();
        Path workspaceRoot = userWorkspaceRootService.resolveUserWorkspaceRoot(demoUser.getId());
        Files.createDirectories(workspaceRoot.resolve("kg"));
        Files.writeString(
                workspaceRoot.resolve("kg").resolve("agent.md"),
                """
                        Agent skills describe reusable procedural knowledge for an agent.
                        Agent systems often combine skills, tools and planning.
                        """,
                StandardCharsets.UTF_8
        );
        Files.writeString(
                workspaceRoot.resolve("kg").resolve("planner.md"),
                """
                        A planner coordinates tools, tasks and reusable agent skills.
                        Planning and agent skills are closely connected in agent systems.
                        """,
                StandardCharsets.UTF_8
        );

        mockMvc.perform(get("/api/library/relations")
                        .param("path", "workspace://kg/agent.md")
                        .param("limit", "6"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.focusPath").value("workspace://kg/agent.md"))
                .andExpect(jsonPath("$.data.focusLabel").value("agent.md"))
                .andExpect(jsonPath("$.data.nodes[0].role").value("focus"))
                .andExpect(jsonPath("$.data.edges[0].relationType").isNotEmpty())
                .andExpect(jsonPath("$.data.edges[0].reasons[0]").isNotEmpty());
    }
}
