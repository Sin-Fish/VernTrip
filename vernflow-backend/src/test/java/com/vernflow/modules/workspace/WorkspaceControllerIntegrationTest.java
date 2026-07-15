package com.vernflow.modules.workspace;

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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:workspace-controller-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.storage.workspace-root-dir=./build/test-storage/workspace-controller",
        "app.user.auth-required=false"
})
@AutoConfigureMockMvc
class WorkspaceControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private final Path workspaceRoot = Path.of("./build/test-storage/workspace-controller").toAbsolutePath().normalize();

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
    void shouldExposeWorkspaceBootstrapPayloadFromFilesystem() throws Exception {
        Files.createDirectories(workspaceRoot.resolve("project-materials"));
        Files.writeString(workspaceRoot.resolve("briefing.md"), "# briefing");

        mockMvc.perform(get("/api/workspace/bootstrap"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.homeSummary.totalFileCount").value(1))
                .andExpect(jsonPath("$.data.homeSummary.totalFolderCount").value(1))
                .andExpect(jsonPath("$.data.treeItems[0].path").value("workspace://project-materials"))
                .andExpect(jsonPath("$.data.treeItems[1].path").value("workspace://briefing.md"))
                .andExpect(jsonPath("$.data.workbenchNodes[0].id").value("scope"))
                .andExpect(jsonPath("$.data.chatSessions").isArray())
                .andExpect(jsonPath("$.data.chatMessages").isArray());
    }

    @Test
    void shouldPersistWorkspaceState() throws Exception {
        Files.createDirectories(workspaceRoot);
        Files.writeString(workspaceRoot.resolve("briefing.md"), "# briefing");

        mockMvc.perform(put("/api/workspace/state")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "workingFiles":[
                                    {"path":"workspace://briefing.md","label":"briefing.md","kind":"file"}
                                  ],
                                  "globalScope":true,
                                  "selectedTreePath":"workspace://briefing.md",
                                  "homeTaskDraft":"整理 briefing",
                                  "workbenchPrompt":"围绕 briefing 生成输出"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.workingFiles[0].path").value("workspace://briefing.md"))
                .andExpect(jsonPath("$.data.globalScope").value(true))
                .andExpect(jsonPath("$.data.selectedTreePath").value("workspace://briefing.md"));

        mockMvc.perform(get("/api/workspace/bootstrap"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.workingFiles[0].path").value("workspace://briefing.md"))
                .andExpect(jsonPath("$.data.globalScope").value(true))
                .andExpect(jsonPath("$.data.homeTaskDraft").value("整理 briefing"))
                .andExpect(jsonPath("$.data.workbenchPrompt").value("围绕 briefing 生成输出"));
    }
}
