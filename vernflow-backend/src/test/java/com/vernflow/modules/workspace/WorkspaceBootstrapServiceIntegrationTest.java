package com.vernflow.modules.workspace;

import com.vernflow.modules.workspace.dto.WorkspaceBootstrapResponse;
import com.vernflow.modules.workspace.dto.WorkspaceItemRef;
import com.vernflow.modules.workspace.dto.WorkspaceStateUpdateRequest;
import com.vernflow.modules.workspace.service.WorkspaceBootstrapService;
import com.vernflow.modules.workspace.service.WorkspaceStateService;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:workspace-bootstrap-service-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.storage.workspace-root-dir=./build/test-storage/workspace-bootstrap",
        "app.user.auth-required=false"
})
class WorkspaceBootstrapServiceIntegrationTest {

    @Autowired
    private WorkspaceBootstrapService workspaceBootstrapService;

    @Autowired
    private WorkspaceStateService workspaceStateService;

    private final Path workspaceRoot = Path.of("./build/test-storage/workspace-bootstrap").toAbsolutePath().normalize();

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
    void shouldExposeBootstrapFromGenericWorkspaceState() throws Exception {
        Files.createDirectories(workspaceRoot.resolve("docs"));
        Files.writeString(workspaceRoot.resolve("docs").resolve("plan.md"), "# plan");
        Files.writeString(workspaceRoot.resolve("briefing.md"), "# briefing");

        workspaceStateService.updateState(1L, new WorkspaceStateUpdateRequest(
                List.of(
                        new WorkspaceItemRef("workspace://docs/plan.md", "plan.md", "file"),
                        new WorkspaceItemRef("workspace://missing.md", "missing.md", "file")
                ),
                true,
                "workspace://docs",
                "整理 docs",
                "围绕 docs 输出"
        ));

        WorkspaceBootstrapResponse response = workspaceBootstrapService.loadBootstrap(1L);

        assertThat(response.homeSummary().totalFileCount()).isEqualTo(2);
        assertThat(response.homeSummary().totalFolderCount()).isEqualTo(1);
        assertThat(response.homeSummary().workingFileCount()).isEqualTo(1);
        assertThat(response.homeSummary().scopeMode()).isEqualTo("global_workspace");
        assertThat(response.treeItems())
                .extracting(WorkspaceItemRef::path)
                .containsExactly("workspace://docs", "workspace://briefing.md");
        assertThat(response.workingFiles())
                .extracting(WorkspaceItemRef::path)
                .containsExactly("workspace://docs/plan.md");
        assertThat(response.selectedTreePath()).isEqualTo("workspace://docs");
        assertThat(response.homeTaskDraft()).isEqualTo("整理 docs");
        assertThat(response.workbenchPrompt()).isEqualTo("围绕 docs 输出");
    }

    @Test
    void shouldRespectEmptyWorkingFileSelectionAfterUserClearsIt() throws Exception {
        Files.createDirectories(workspaceRoot);
        Files.writeString(workspaceRoot.resolve("briefing.md"), "# briefing");
        Files.writeString(workspaceRoot.resolve("notes.md"), "# notes");

        workspaceStateService.updateState(1L, new WorkspaceStateUpdateRequest(
                List.of(),
                false,
                "workspace://briefing.md",
                "清空工作文件",
                "重新选择范围"
        ));

        WorkspaceBootstrapResponse response = workspaceBootstrapService.loadBootstrap(1L);

        assertThat(response.workingFiles()).isEmpty();
        assertThat(response.homeSummary().workingFileCount()).isZero();
    }
}
