package com.vernflow.modules.workbench;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vernflow.modules.user.domain.AppUser;
import com.vernflow.modules.user.repository.AppUserRepository;
import com.vernflow.modules.workspace.service.UserWorkspaceRootService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:workbench-controller-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.storage.workspace-root-dir=./build/test-storage/workbench-controller",
        "app.user.auth-required=false"
})
@AutoConfigureMockMvc
class WorkbenchControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserWorkspaceRootService userWorkspaceRootService;

    @Autowired
    private AppUserRepository appUserRepository;

    @Test
    void shouldCreateRunExposeListAndBootstrapHistory() throws Exception {
        String responseBody = mockMvc.perform(post("/api/workbench/runs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "prompt":"Organize the selected workspace notes",
                                  "workingFiles":[
                                    {"path":"workspace://notes.md","label":"notes.md","kind":"file"}
                                  ],
                                  "globalScope":false,
                                  "executionMode":"execute"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Organize the selected workspace notes"))
                .andExpect(jsonPath("$.data.status").value("Running"))
                .andExpect(jsonPath("$.data.inspectMode").value("focus"))
                .andExpect(jsonPath("$.data.workingFiles[0].path").value("workspace://notes.md"))
                .andExpect(jsonPath("$.data.graphMainPath[2]").value("selector"))
                .andExpect(jsonPath("$.data.activeExecutionNodeId").value("scope"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode createResponse = objectMapper.readTree(responseBody);
        long runId = createResponse.path("data").path("id").asLong();

        mockMvc.perform(get("/api/workbench/runs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(runId))
                .andExpect(jsonPath("$.data[0].status").value("Running"));

        mockMvc.perform(get("/api/workbench/runs/{runId}", runId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(runId))
                .andExpect(jsonPath("$.data.selectedNodeId").value("scope"));

        String bootstrapBody = mockMvc.perform(get("/api/workspace/bootstrap"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode bootstrap = objectMapper.readTree(bootstrapBody);
        JsonNode bootstrapRecords = bootstrap.path("data").path("workbenchRecords");
        boolean containsRun = false;
        for (JsonNode record : bootstrapRecords) {
            if (record.path("id").asLong() == runId) {
                containsRun = true;
                assertThat(record.path("name").asText()).isEqualTo("Organize the selected workspace notes");
                break;
            }
        }
        assertThat(containsRun).isTrue();
    }

    @Test
    void shouldCreatePreparedRun() throws Exception {
        mockMvc.perform(post("/api/workbench/runs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "prompt":"Prepare a workspace clean-up checklist",
                                  "workingFiles":[],
                                  "globalScope":true,
                                  "executionMode":"prepare"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("Prepared"))
                .andExpect(jsonPath("$.data.inspectMode").value("focus"))
                .andExpect(jsonPath("$.data.globalScope").value(true))
                .andExpect(jsonPath("$.data.selectedNodeId").value("scope"))
                .andExpect(jsonPath("$.data.activeExecutionNodeId").isEmpty())
                .andExpect(jsonPath("$.data.activeInspectionNodeId").isEmpty());
    }

    @Test
    void shouldReuseCurrentRunInsteadOfCreatingDuplicateHistoryRows() throws Exception {
        String beforeListBody = mockMvc.perform(get("/api/workbench/runs"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        int beforeCount = objectMapper.readTree(beforeListBody).path("data").size();

        String preparedBody = mockMvc.perform(post("/api/workbench/runs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "prompt":"Organize the selected workspace notes",
                                  "workingFiles":[
                                    {"path":"workspace://notes.md","label":"notes.md","kind":"file"}
                                  ],
                                  "globalScope":false,
                                  "executionMode":"prepare"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("Prepared"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        long runId = objectMapper.readTree(preparedBody).path("data").path("id").asLong();

        mockMvc.perform(put("/api/workbench/runs/{runId}", runId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "prompt":"Organize the selected workspace notes and write an output draft",
                                  "workingFiles":[
                                    {"path":"workspace://notes.md","label":"notes.md","kind":"file"}
                                  ],
                                  "globalScope":false,
                                  "executionMode":"execute"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(runId))
                .andExpect(jsonPath("$.data.status").value("Running"))
                .andExpect(jsonPath("$.data.activeExecutionNodeId").value("scope"));

        String listBody = mockMvc.perform(get("/api/workbench/runs"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode records = objectMapper.readTree(listBody).path("data");
        assertThat(records).hasSize(beforeCount + 1);
        assertThat(records.get(0).path("id").asLong()).isEqualTo(runId);
        assertThat(records.get(0).path("status").asText()).isEqualTo("Running");
    }

    @Test
    void shouldExposeWorkbenchPagesInsideSingleRun() throws Exception {
        String firstBody = mockMvc.perform(post("/api/workbench/runs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "prompt":"整理第一版资料摘要",
                                  "workingFiles":[],
                                  "globalScope":true,
                                  "executionMode":"prepare"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentPage").value(1))
                .andExpect(jsonPath("$.data.totalPages").value(1))
                .andReturn()
                .getResponse()
                .getContentAsString();

        long runId = objectMapper.readTree(firstBody).path("data").path("id").asLong();

        mockMvc.perform(put("/api/workbench/runs/{runId}", runId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "prompt":"整理第二版资料摘要",
                                  "workingFiles":[],
                                  "globalScope":true,
                                  "executionMode":"prepare"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(runId))
                .andExpect(jsonPath("$.data.currentPage").value(2))
                .andExpect(jsonPath("$.data.totalPages").value(2))
                .andExpect(jsonPath("$.data.prompt").value("整理第二版资料摘要"));

        mockMvc.perform(get("/api/workbench/runs/{runId}?page=1", runId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(runId))
                .andExpect(jsonPath("$.data.currentPage").value(1))
                .andExpect(jsonPath("$.data.totalPages").value(2))
                .andExpect(jsonPath("$.data.prompt").value("整理第一版资料摘要"));
    }

    @Test
    void shouldDeleteWorkbenchRun() throws Exception {
        String body = mockMvc.perform(post("/api/workbench/runs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "prompt":"待删除工作台任务",
                                  "workingFiles":[],
                                  "globalScope":true,
                                  "executionMode":"prepare"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        long runId = objectMapper.readTree(body).path("data").path("id").asLong();

        mockMvc.perform(delete("/api/workbench/runs/{runId}", runId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(true));

        String listBody = mockMvc.perform(get("/api/workbench/runs"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode records = objectMapper.readTree(listBody).path("data");
        boolean stillExists = false;
        for (JsonNode record : records) {
            if (record.path("id").asLong() == runId) {
                stillExists = true;
                break;
            }
        }
        assertThat(stillExists).isFalse();
    }

    @Test
    void shouldOrganizeWorkspaceFilesInsideSingleWorkbenchRun() throws Exception {
        AppUser demoUser = appUserRepository.findByUsernameIgnoreCase("demo").orElseThrow();
        Path workspaceRoot = userWorkspaceRootService.resolveUserWorkspaceRoot(demoUser.getId());
        Path inbox = workspaceRoot.resolve("inbox-organize-" + System.nanoTime());
        Path rawFolder = inbox.resolve("raw");
        Path staleFolder = inbox.resolve("stale");
        Path nestedStaleFolder = staleFolder.resolve("nested");
        Files.createDirectories(rawFolder);
        Files.createDirectories(nestedStaleFolder);
        Files.writeString(inbox.resolve("outline.md"), "# Demo outline", StandardCharsets.UTF_8);
        Files.writeString(rawFolder.resolve("paper.pdf"), "fake pdf content", StandardCharsets.UTF_8);

        String responseBody = mockMvc.perform(post("/api/workbench/runs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "prompt":"请帮我整理资料库，把当前工作文件按类型归档",
                                  "workingFiles":[
                                    {"path":"workspace://%s","label":"%s","kind":"folder"}
                                  ],
                                  "globalScope":false,
                                  "executionMode":"execute",
                                  "taskIntent":"workspace_organize"
                                }
                                """.formatted(inbox.getFileName(), inbox.getFileName())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("Running"))
                .andExpect(jsonPath("$.data.graphMainPath[1]").value("organizer"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        long runId = objectMapper.readTree(responseBody).path("data").path("id").asLong();

        JsonNode latestDetail = awaitRunCompleted(runId);
        assertThat(latestDetail.path("status").asText()).isEqualTo("Completed");

        Path organizerRoot = inbox.resolve("资料整理");
        Path movedMarkdown = organizerRoot.resolve("笔记草稿").resolve("outline.md");
        Path movedPdf = organizerRoot.resolve("研究论文").resolve("paper.pdf");

        assertThat(Files.exists(movedMarkdown)).isTrue();
        assertThat(Files.exists(movedPdf)).isTrue();
        assertThat(Files.notExists(inbox.resolve("outline.md"))).isTrue();
        assertThat(Files.notExists(rawFolder.resolve("paper.pdf"))).isTrue();
        assertThat(Files.notExists(rawFolder)).isTrue();
        assertThat(Files.notExists(nestedStaleFolder)).isTrue();
        assertThat(Files.notExists(staleFolder)).isTrue();

        JsonNode reviewNode = findNode(latestDetail, "review");
        assertThat(reviewNode).isNotNull();
        assertThat(reviewNode.path("executeStatus").asText()).isEqualTo("completed");
        assertThat(reviewNode.path("inspectStatus").asText()).isEqualTo("passed");
        assertThat(reviewNode.path("detail").asText()).isNotBlank();
    }

    @Test
    void shouldTreatColloquialOrganizePromptAsWorkspaceOrganizationFlow() throws Exception {
        AppUser demoUser = appUserRepository.findByUsernameIgnoreCase("demo").orElseThrow();
        Path workspaceRoot = userWorkspaceRootService.resolveUserWorkspaceRoot(demoUser.getId());
        Path inbox = workspaceRoot.resolve("inbox-colloquial-" + System.nanoTime());
        Files.createDirectories(inbox);
        Files.writeString(inbox.resolve("notes.md"), "# Notes", StandardCharsets.UTF_8);

        mockMvc.perform(post("/api/workbench/runs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "prompt":"帮我整理一下文件",
                                  "workingFiles":[
                                    {"path":"workspace://%s","label":"%s","kind":"folder"}
                                  ],
                                  "globalScope":false,
                                  "executionMode":"prepare",
                                  "taskIntent":"workspace_organize"
                                }
                                """.formatted(inbox.getFileName(), inbox.getFileName())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.graphMainPath[1]").value("organizer"))
                .andExpect(jsonPath("$.data.nodes[1].id").value("organizer"))
                .andExpect(jsonPath("$.data.skills").value(containsString("分类规划")));
    }

    @Test
    void shouldInferWorkspaceOrganizationIntentFromTopicCollectionPrompt() throws Exception {
        AppUser demoUser = appUserRepository.findByUsernameIgnoreCase("demo").orElseThrow();
        Path workspaceRoot = userWorkspaceRootService.resolveUserWorkspaceRoot(demoUser.getId());
        Path inbox = workspaceRoot.resolve("topic-collect-" + System.nanoTime());
        Files.createDirectories(inbox);
        Files.writeString(inbox.resolve("prep-notes.md"), "# prep", StandardCharsets.UTF_8);

        mockMvc.perform(post("/api/workbench/runs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "prompt":"帮我把备考相关的资料整理成一个单独的文件夹",
                                  "workingFiles":[
                                    {"path":"workspace://%s","label":"%s","kind":"folder"}
                                  ],
                                  "globalScope":false,
                                  "executionMode":"prepare"
                                }
                                """.formatted(inbox.getFileName(), inbox.getFileName())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.graphMainPath[1]").value("organizer"))
                .andExpect(jsonPath("$.data.nodes[1].id").value("organizer"));
    }

    @Test
    void shouldPlanWorkspaceOrganizationAgainstWholeWorkspaceWhenGlobalScopeEnabled() throws Exception {
        AppUser demoUser = appUserRepository.findByUsernameIgnoreCase("demo").orElseThrow();
        Path workspaceRoot = userWorkspaceRootService.resolveUserWorkspaceRoot(demoUser.getId());
        Path organizeAllRoot = workspaceRoot.resolve("organize-all-" + System.nanoTime());
        Files.createDirectories(organizeAllRoot.resolve("nested"));
        Files.writeString(organizeAllRoot.resolve("root.pdf"), "paper", StandardCharsets.UTF_8);
        Files.writeString(organizeAllRoot.resolve("nested").resolve("notes.md"), "# notes", StandardCharsets.UTF_8);

        mockMvc.perform(post("/api/workbench/runs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "prompt":"帮我整理一下资料",
                                  "workingFiles":[
                                    {"path":"workspace://%s","label":"%s","kind":"folder"}
                                  ],
                                  "globalScope":true,
                                  "executionMode":"prepare",
                                  "taskIntent":"workspace_organize"
                                }
                                """.formatted(organizeAllRoot.getFileName(), organizeAllRoot.getFileName())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.graphMainPath[1]").value("organizer"))
                .andExpect(jsonPath("$.data.nodes[0].detail").value(containsString("全局工作区")))
                .andExpect(jsonPath("$.data.nodes[1].checks[*].result")
                        .value(org.hamcrest.Matchers.hasItem(containsString("个文件"))));
    }

    @Test
    void shouldAppendExecutionReportChainAndWriteReportFile() throws Exception {
        AppUser demoUser = appUserRepository.findByUsernameIgnoreCase("demo").orElseThrow();
        Path workspaceRoot = userWorkspaceRootService.resolveUserWorkspaceRoot(demoUser.getId());
        Path organizeRoot = workspaceRoot.resolve("report-chain-" + System.nanoTime());
        Files.createDirectories(organizeRoot.resolve("nested"));
        Files.writeString(organizeRoot.resolve("agent.pdf"), "paper", StandardCharsets.UTF_8);
        Files.writeString(organizeRoot.resolve("nested").resolve("notes.md"), "# notes", StandardCharsets.UTF_8);

        String responseBody = mockMvc.perform(post("/api/workbench/runs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "prompt":"帮我把这些资料整理出来，并生成一份执行报告",
                                  "workingFiles":[
                                    {"path":"workspace://%s","label":"%s","kind":"folder"}
                                  ],
                                  "globalScope":false,
                                  "executionMode":"execute",
                                  "taskIntent":"workspace_organize",
                                  "generateExecutionReport":true
                                }
                                """.formatted(organizeRoot.getFileName(), organizeRoot.getFileName())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("Running"))
                .andExpect(jsonPath("$.data.graphMainPath[5]").value("report"))
                .andExpect(jsonPath("$.data.nodes[5].id").value("report"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        long runId = objectMapper.readTree(responseBody).path("data").path("id").asLong();
        JsonNode latestDetail = awaitRunCompleted(runId);
        assertThat(latestDetail.path("status").asText()).isEqualTo("Completed");

        Path reportDirectory = organizeRoot.resolve("执行报告");
        assertThat(Files.exists(reportDirectory)).isTrue();

        Path reportFile = Files.list(reportDirectory)
                .filter(Files::isRegularFile)
                .findFirst()
                .orElseThrow();
        String reportContent = Files.readString(reportFile, StandardCharsets.UTF_8);

        assertThat(reportFile.getFileName().toString()).contains("执行报告");
        assertThat(reportContent).contains("资料整理执行报告");
        assertThat(reportContent).contains("帮我把这些资料整理出来，并生成一份执行报告");
        assertThat(reportContent).contains("agent.pdf");
        assertThat(reportContent).contains("notes.md");

        JsonNode reportNode = findNode(latestDetail, "report");
        assertThat(reportNode).isNotNull();
        assertThat(reportNode.path("executeStatus").asText()).isEqualTo("completed");
        assertThat(reportNode.path("inspectStatus").asText()).isEqualTo("passed");
        assertThat(reportNode.path("detail").asText()).isNotBlank();
    }

    @Test
    void shouldReuseExistingOrganizerRootInsteadOfCreatingNestedOrganizerDirectory() throws Exception {
        AppUser demoUser = appUserRepository.findByUsernameIgnoreCase("demo").orElseThrow();
        Path workspaceRoot = userWorkspaceRootService.resolveUserWorkspaceRoot(demoUser.getId());
        Path organizerRoot = workspaceRoot.resolve("资料整理");
        Path sourceFolder = organizerRoot.resolve("待整理资料");
        Files.createDirectories(sourceFolder);
        Files.writeString(sourceFolder.resolve("agent.pdf"), "paper", StandardCharsets.UTF_8);
        Files.writeString(sourceFolder.resolve("outline.md"), "# notes", StandardCharsets.UTF_8);

        String responseBody = mockMvc.perform(post("/api/workbench/runs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "prompt":"帮我把这些资料重新整理一遍",
                                  "workingFiles":[
                                    {"path":"workspace://资料整理/待整理资料","label":"待整理资料","kind":"folder"}
                                  ],
                                  "globalScope":false,
                                  "executionMode":"execute",
                                  "taskIntent":"workspace_organize"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        long runId = objectMapper.readTree(responseBody).path("data").path("id").asLong();
        JsonNode latestDetail = awaitRunCompleted(runId);
        assertThat(latestDetail.path("status").asText()).isEqualTo("Completed");

        assertThat(Files.exists(organizerRoot.resolve("研究论文").resolve("agent.pdf"))).isTrue();
        assertThat(Files.exists(organizerRoot.resolve("笔记草稿").resolve("outline.md"))).isTrue();
        assertThat(Files.notExists(sourceFolder.resolve("资料整理"))).isTrue();
        assertThat(Files.notExists(organizerRoot.resolve("资料整理"))).isTrue();
    }

    @Test
    void shouldSkipExistingOrganizerOutputsWhenOrganizingWholeWorkspace() throws Exception {
        AppUser demoUser = appUserRepository.findByUsernameIgnoreCase("demo").orElseThrow();
        Path workspaceRoot = userWorkspaceRootService.resolveUserWorkspaceRoot(demoUser.getId());
        Path sourceFolder = workspaceRoot.resolve("organize-root-" + System.nanoTime());
        Path organizerRoot = workspaceRoot.resolve("资料整理");
        Files.createDirectories(sourceFolder);
        Files.createDirectories(organizerRoot.resolve("研究论文"));
        Files.writeString(sourceFolder.resolve("fresh-notes.md"), "# fresh", StandardCharsets.UTF_8);
        Files.writeString(organizerRoot.resolve("研究论文").resolve("old-paper.pdf"), "old", StandardCharsets.UTF_8);

        String responseBody = mockMvc.perform(post("/api/workbench/runs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "prompt":"帮我整理整个资料库",
                                  "workingFiles":[],
                                  "globalScope":true,
                                  "executionMode":"execute",
                                  "taskIntent":"workspace_organize"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        long runId = objectMapper.readTree(responseBody).path("data").path("id").asLong();
        JsonNode latestDetail = awaitRunCompleted(runId);
        assertThat(latestDetail.path("status").asText()).isEqualTo("Completed");

        assertThat(Files.exists(organizerRoot.resolve("研究论文").resolve("old-paper.pdf"))).isTrue();
        assertThat(Files.exists(organizerRoot.resolve("笔记草稿").resolve("fresh-notes.md"))).isTrue();
        assertThat(Files.notExists(organizerRoot.resolve("资料整理"))).isTrue();
        assertThat(findNode(latestDetail, "organizer").path("detail").asText()).isNotBlank();
    }

    @Test
    void shouldReorganizeManagedOutputsWhenWholeWorkspaceOnlyContainsOrganizerFiles() throws Exception {
        AppUser demoUser = appUserRepository.findByUsernameIgnoreCase("demo").orElseThrow();
        Path workspaceRoot = userWorkspaceRootService.resolveUserWorkspaceRoot(demoUser.getId());
        Path rerunRoot = workspaceRoot.resolve("rerun-organize-" + System.nanoTime());
        Path organizerRoot = rerunRoot.resolve("资料整理");
        Path nestedOrganizer = organizerRoot.resolve("资料整理");
        Path nestedPaperFolder = nestedOrganizer.resolve("研究论文");
        Path reportFolder = organizerRoot.resolve("执行报告");
        Files.createDirectories(nestedPaperFolder);
        Files.createDirectories(reportFolder);
        Files.writeString(nestedPaperFolder.resolve("agent.pdf"), "paper", StandardCharsets.UTF_8);
        Files.writeString(reportFolder.resolve("旧执行报告.md"), "# old report", StandardCharsets.UTF_8);

        String responseBody = mockMvc.perform(post("/api/workbench/runs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "prompt":"帮我整理一下资料",
                                  "workingFiles":[
                                    {"path":"workspace://%s","label":"%s","kind":"folder"}
                                  ],
                                  "globalScope":true,
                                  "executionMode":"execute",
                                  "taskIntent":"workspace_organize"
                                }
                                """.formatted(rerunRoot.getFileName(), rerunRoot.getFileName())))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        long runId = objectMapper.readTree(responseBody).path("data").path("id").asLong();
        JsonNode latestDetail = awaitRunCompleted(runId);
        assertThat(latestDetail.path("status").asText()).isEqualTo("Completed");

        Path flattenedPaper = organizerRoot.resolve("研究论文").resolve("agent.pdf");
        assertThat(Files.exists(flattenedPaper)).isTrue();
        assertThat(Files.notExists(nestedPaperFolder)).isTrue();
        assertThat(Files.notExists(nestedOrganizer)).isTrue();
        assertThat(Files.exists(reportFolder.resolve("旧执行报告.md"))).isTrue();
        assertThat(findNode(latestDetail, "organizer").path("detail").asText())
                .contains("1 个文件");
    }

    @Test
    void shouldExposeRetrievedEvidenceInsideWorkbenchNodeChecks() throws Exception {
        AppUser demoUser = appUserRepository.findByUsernameIgnoreCase("demo").orElseThrow();
        Path workspaceRoot = userWorkspaceRootService.resolveUserWorkspaceRoot(demoUser.getId());
        Files.createDirectories(workspaceRoot);
        Files.writeString(
                workspaceRoot.resolve("brief.md"),
                """
                        VernFlow 比赛作品报告说明了三条核心链路：资料库、工作台和文档助手。
                        资料库负责聚合用户资料，工作台负责生成执行图并沿图推进，文档助手负责基于范围回答问题。
                        """,
                StandardCharsets.UTF_8
        );

        String responseBody = mockMvc.perform(post("/api/workbench/runs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "prompt":"整理 VernFlow 比赛作品报告中的核心链路",
                                  "workingFiles":[
                                    {"path":"workspace://brief.md","label":"brief.md","kind":"file"}
                                  ],
                                  "globalScope":false,
                                  "executionMode":"execute"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        long runId = objectMapper.readTree(responseBody).path("data").path("id").asLong();

        JsonNode retrieverNode = null;
        for (int attempt = 0; attempt < 6; attempt++) {
            Thread.sleep(700L);
            String detailBody = mockMvc.perform(get("/api/workbench/runs/{runId}", runId))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();

            JsonNode nodes = objectMapper.readTree(detailBody).path("data").path("nodes");
            for (JsonNode node : nodes) {
                if ("retriever".equals(node.path("id").asText())) {
                    retrieverNode = node;
                    break;
                }
            }
            if (retrieverNode != null && retrieverNode.path("detail").asText().contains("已召回")) {
                break;
            }
        }

        assertThat(retrieverNode).isNotNull();
        assertThat(retrieverNode.path("executeStatus").asText()).isEqualTo("completed");
        assertThat(retrieverNode.path("inspectStatus").asText()).isIn("running", "passed");
        assertThat(retrieverNode.path("detail").asText()).isNotBlank();
        assertThat(retrieverNode.path("checks").size()).isGreaterThanOrEqualTo(8);
        assertThat(retrieverNode.path("checks").toString()).contains("brief.md");
    }

    private JsonNode awaitRunCompleted(long runId) throws Exception {
        JsonNode latestDetail = null;
        for (int attempt = 0; attempt < 18; attempt++) {
            Thread.sleep(950L);
            String detailBody = mockMvc.perform(get("/api/workbench/runs/{runId}", runId))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();
            latestDetail = objectMapper.readTree(detailBody).path("data");
            if ("Completed".equals(latestDetail.path("status").asText())) {
                break;
            }
        }
        assertThat(latestDetail).isNotNull();
        return latestDetail;
    }

    private JsonNode findNode(JsonNode detail, String nodeId) {
        for (JsonNode node : detail.path("nodes")) {
            if (nodeId.equals(node.path("id").asText())) {
                return node;
            }
        }
        return null;
    }
}
