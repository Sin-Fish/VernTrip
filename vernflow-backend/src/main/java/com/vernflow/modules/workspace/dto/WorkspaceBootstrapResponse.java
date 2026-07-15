package com.vernflow.modules.workspace.dto;

import java.util.List;
import java.util.Map;

/**
 * Initial workspace payload consumed by the VernFlow frontend shell.
 *
 * @param homeSummary homepage workspace summary
 * @param pageMeta route metadata shown in the top bar
 * @param treeItems root-level workspace explorer entries
 * @param workingFiles current working-file scope
 * @param globalScope whether the workspace starts in global mode
 * @param selectedTreePath selected item in the right-side explorer
 * @param selectedNodeId default selected workbench node
 * @param selectedSessionId default selected assistant session
 * @param homeTaskDraft default home quick-task prompt
 * @param workbenchPrompt default workbench prompt
 * @param libraryItems library page cards reserved for future shell sections
 * @param workbenchRecords workbench task list
 * @param workbenchNodes workbench graph nodes
 * @param graphLinks graph edges
 * @param graphMainPath current highlighted execution path
 * @param workbenchWorkspaceStats right-side workbench summary stats
 * @param chatSessions assistant session list
 * @param chatMessages assistant message timeline
 * @param settingsTabs settings menu tabs
 * @param skillLibraryRows settings skill-library rows
 * @param skillPermissions current workspace skill permission rows
 */
public record WorkspaceBootstrapResponse(
        HomeSummary homeSummary,
        Map<String, PageMeta> pageMeta,
        List<WorkspaceItemRef> treeItems,
        List<WorkspaceItemRef> workingFiles,
        boolean globalScope,
        String selectedTreePath,
        String selectedNodeId,
        String selectedSessionId,
        String homeTaskDraft,
        String workbenchPrompt,
        List<LibraryItem> libraryItems,
        List<WorkbenchRecord> workbenchRecords,
        List<WorkbenchNode> workbenchNodes,
        List<WorkbenchLink> graphLinks,
        List<String> graphMainPath,
        List<KeyValueStat> workbenchWorkspaceStats,
        List<AssistantSessionCard> chatSessions,
        List<AssistantMessage> chatMessages,
        List<SettingsTab> settingsTabs,
        List<SkillLibraryRow> skillLibraryRows,
        List<SkillPermission> skillPermissions
) {

    public record HomeSummary(
            long totalFileCount,
            long totalFolderCount,
            long workingFileCount,
            String scopeMode
    ) {
    }

    public record PageMeta(String title, String description) {
    }

    public record LibraryItem(
            String name,
            String kind,
            String ext,
            String summary,
            String path
    ) {
    }

    public record WorkbenchRecord(
            Long id,
            String name,
            String skills,
            String status,
            String statusTone,
            String inspectMode,
            String time
    ) {
    }

    public record WorkbenchNode(
            String id,
            String type,
            String title,
            String status,
            String tone,
            String detail,
            String positionClass,
            String executeStatus,
            String executeTone,
            String inspectStatus,
            String inspectTone,
            boolean executionActive,
            boolean inspectionActive,
            List<NodeCheck> checks
    ) {
    }

    public record NodeCheck(String tone, String label, String result) {
    }

    public record WorkbenchLink(String from, String to, boolean dashed) {
    }

    public record KeyValueStat(String label, String value) {
    }

    public record AssistantSessionCard(
            String id,
            String title,
            String summary
    ) {
    }

    public record AssistantMessage(
            String id,
            String role,
            String text,
            List<String> cites
    ) {
    }

    public record SettingsTab(String id, String label) {
    }

    public record SkillLibraryRow(String name, String summary, List<String> actions) {
    }

    public record SkillPermission(
            String id,
            String name,
            String summary,
            boolean open,
            List<SkillPermissionAgent> agents
    ) {
    }

    public record SkillPermissionAgent(
            String id,
            String label,
            String description,
            boolean enabled
    ) {
    }
}
