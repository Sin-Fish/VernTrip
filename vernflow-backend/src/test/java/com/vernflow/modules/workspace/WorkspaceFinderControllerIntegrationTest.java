package com.vernflow.modules.workspace;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:workspace-finder-controller-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.storage.workspace-root-dir=./build/test-storage/workspace-finder",
        "app.user.auth-required=false"
})
@AutoConfigureMockMvc
class WorkspaceFinderControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private final Path workspaceRoot = Path.of("./build/test-storage/workspace-finder").toAbsolutePath().normalize();

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
    void shouldListWorkspaceRoot() throws Exception {
        mockMvc.perform(get("/api/finder"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.storages[0]").value("workspace"))
                .andExpect(jsonPath("$.dirname").value("workspace://"));
    }

    @Test
    void shouldCreateFolderAndFileAndRenameThem() throws Exception {
        mockMvc.perform(post("/api/finder/create-folder")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"path":"workspace://","name":"planning"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.files[0].basename").value("planning"))
                .andExpect(jsonPath("$.files[0].type").value("dir"));

        mockMvc.perform(post("/api/finder/create-file")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"path":"workspace://planning","name":"notes"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.files[0].basename").value("notes.md"))
                .andExpect(jsonPath("$.files[0].type").value("file"));

        mockMvc.perform(post("/api/finder/rename")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"path":"workspace://planning","item":"workspace://planning/notes.md","name":"todo.md"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.files[0].basename").value("todo.md"));
    }

    @Test
    void shouldUploadAndDeleteCommonFiles() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "brief.txt",
                MediaType.TEXT_PLAIN_VALUE,
                "workspace finder".getBytes()
        );

        mockMvc.perform(multipart("/api/finder/upload")
                        .file(file)
                        .param("path", "workspace://"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.files[0].basename").value("brief.txt"))
                .andExpect(jsonPath("$.files[0].type").value("file"));

        mockMvc.perform(post("/api/finder/delete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"path":"workspace://","items":[{"path":"workspace://brief.txt","type":"file"}]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.files").isEmpty());
    }

    @Test
    void shouldDeleteNonEmptyFolder() throws Exception {
        mockMvc.perform(post("/api/finder/create-folder")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"path":"workspace://","name":"assets"}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/finder/create-folder")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"path":"workspace://assets","name":"images"}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/finder/create-file")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"path":"workspace://assets/images","name":"cover.png"}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/finder/delete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"path":"workspace://","items":[{"path":"workspace://assets","type":"dir"}]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.files").isEmpty());
    }
}
