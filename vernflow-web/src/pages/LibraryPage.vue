<script setup>
import { computed, ref, watch } from "vue";
import { useRouter } from "vue-router";
import { VueFinder } from "vuefinder";
import FileRelationGraph from "@/components/FileRelationGraph.vue";
import { useAppState } from "@/composables/useAppState";
import { getWorkspaceFileRelationGraph } from "@/lib/userApi";
import {
  WORKSPACE_FINDER_ID,
  WORKSPACE_ROOT_PATH,
  formatWorkspaceFileSize,
  formatWorkspaceTimestamp,
  labelForWorkspacePath,
  workspaceFinderConfig,
  workspaceFinderDriver,
  workspaceFinderFeatures,
} from "@/lib/workspaceFinder";

const ui = {
  title: "资料库",
  relationMapTitle: "知识图谱",
  workspace: "工作区",
  currentSelection: "文件详情",
  currentFileInfo: "当前文件信息",
  currentDir: "当前目录",
  folderSummary: "文件夹",
  fileGeneric: "文件 / 工作区资料",
  fileTag: "文件",
  folderTag: "文件夹",
  workspaceTag: "工作区",
  path: "路径",
  type: "类型",
  mime: "格式",
  size: "大小",
  updated: "更新时间",
  previewFile: "预览文件",
  openReading: "送到文档助手",
  addWorkingFile: "添加到工作文件",
  viewRelations: "查看关联",
  relationStatus: "文件关联",
  relationGuide: "选中一个文件后，查看它与其他资料的关联。",
  relationLoading: "正在分析文件关联...",
  relationEmpty: "当前文件暂时没有明显关联。",
  relationUnavailable: "请先选择一个文件，再查看关联。",
  relationType: "关联类型",
  relationScore: "关联分数",
  relationReasons: "关联依据",
  relationConcepts: "相关概念",
  relationSource: "起点文件",
  relationTarget: "关联文件",
  focusRelation: "以此文件为中心",
  relationRole: "角色",
  relationDir: "所在目录",
  relationViewTitle: "文件关联图",
  relationEdgeTitle: "关联说明",
  currentFileRole: "当前文件",
  relatedFileRole: "关联文件",
  backToDetail: "返回文件详情",
  backToLibrary: "返回资料库",
  expandRelation: "展开图谱",
  relationLoadFailed: "加载文件关联失败",
};

const router = useRouter();
const { addWorkingFile } = useAppState();

const currentPath = ref(WORKSPACE_ROOT_PATH);
const selectedItems = ref([]);
const relationMode = ref(false);
const relationExpanded = ref(false);
const relationGraph = ref(null);
const relationLoading = ref(false);
const relationError = ref("");
const selectedRelationNode = ref(null);
const selectedRelationEdge = ref(null);

const selectedItem = computed(() => selectedItems.value[0] ?? null);
const isSelectedFile = computed(() => selectedItem.value?.type === "file");
const pageClasses = computed(() => ({
  "relation-expanded": relationMode.value && relationExpanded.value,
}));

const detailTitle = computed(() => selectedItem.value?.basename ?? labelForWorkspacePath(currentPath.value));
const detailSummary = computed(() => {
  if (!selectedItem.value) {
    return ui.currentDir;
  }

  if (selectedItem.value.type === "dir") {
    return ui.folderSummary;
  }

  return selectedItem.value.mime_type ? `${ui.fileTag} / ${selectedItem.value.mime_type}` : ui.fileGeneric;
});

const detailPath = computed(() => selectedItem.value?.path ?? currentPath.value);
const detailType = computed(() => {
  if (!selectedItem.value) {
    return ui.folderTag;
  }

  return selectedItem.value.type === "file" ? ui.fileTag : ui.folderTag;
});
const detailMimeType = computed(() => selectedItem.value?.mime_type ?? "-");
const detailSize = computed(() => formatWorkspaceFileSize(selectedItem.value?.file_size));
const detailUpdated = computed(() => formatWorkspaceTimestamp(selectedItem.value?.last_modified));

const statusTagLabel = computed(() => {
  if (relationMode.value) {
    return ui.relationStatus;
  }

  if (!selectedItem.value) {
    return ui.workspaceTag;
  }

  return selectedItem.value.type === "file" ? ui.fileTag : ui.folderTag;
});

const statusTagClass = computed(() => {
  if (relationMode.value) {
    return "tag blue";
  }

  if (!selectedItem.value) {
    return "tag blue";
  }

  return selectedItem.value.type === "file" ? "tag ok" : "tag blue";
});

const relationFocusNode = computed(() => {
  const graphNodes = relationGraph.value?.nodes ?? [];
  return graphNodes.find((node) => node.role === "focus") ?? null;
});

const relationDetailKind = computed(() => {
  if (relationError.value) {
    return "error";
  }
  if (selectedRelationEdge.value) {
    return "edge";
  }
  if (selectedRelationNode.value) {
    return "node";
  }
  if (relationFocusNode.value) {
    return "focus";
  }
  return "empty";
});

const relationDetailTitle = computed(() => {
  if (relationDetailKind.value === "error") {
    return ui.relationViewTitle;
  }
  if (relationDetailKind.value === "edge") {
    return ui.relationEdgeTitle;
  }
  if (relationDetailKind.value === "node") {
    return selectedRelationNode.value?.label ?? ui.relationStatus;
  }
  if (relationDetailKind.value === "focus") {
    return relationFocusNode.value?.label ?? ui.relationStatus;
  }
  return ui.relationStatus;
});

const relationDetailSummary = computed(() => {
  if (relationError.value) {
    return relationError.value;
  }
  if (relationLoading.value) {
    return ui.relationLoading;
  }
  if (relationDetailKind.value === "edge") {
    return relationTypeLabel(selectedRelationEdge.value?.relationType);
  }
  if (relationDetailKind.value === "node" || relationDetailKind.value === "focus") {
    return selectedRelationNode.value?.directoryLabel
      ?? relationFocusNode.value?.directoryLabel
      ?? ui.relationGuide;
  }
  return relationGraph.value?.statusMessage ?? ui.relationGuide;
});

const relationDetailRows = computed(() => {
  if (relationDetailKind.value === "edge") {
    const edge = selectedRelationEdge.value;
    if (!edge) {
      return [];
    }

    return [
      { label: ui.relationType, value: relationTypeLabel(edge.relationType) },
      { label: ui.relationScore, value: formatRelationScore(edge.score) },
      { label: ui.relationSource, value: fileLabelFromPath(edge.source) },
      { label: ui.relationTarget, value: fileLabelFromPath(edge.target) },
    ];
  }

  if (relationDetailKind.value === "node" || relationDetailKind.value === "focus") {
    const node = selectedRelationNode.value ?? relationFocusNode.value;
    if (!node) {
      return [];
    }

    return [
      { label: ui.path, value: node.path },
      { label: ui.relationRole, value: node.role === "focus" ? ui.currentFileRole : ui.relatedFileRole },
      { label: ui.relationScore, value: formatRelationScore(node.score) },
      { label: ui.relationDir, value: node.directoryLabel ?? "-" },
    ];
  }

  return [];
});

const relationReasons = computed(() => selectedRelationEdge.value?.reasons ?? []);
const relationConcepts = computed(() => {
  const node = selectedRelationNode.value ?? relationFocusNode.value;
  return node?.concepts ?? [];
});
const relationHasSingleNodeGraph = computed(() => {
  return (relationGraph.value?.nodes?.length ?? 0) <= 1 && (relationGraph.value?.edges?.length ?? 0) === 0;
});
const relationSingleNode = computed(() => {
  if ((relationGraph.value?.nodes?.length ?? 0) > 0) {
    return relationGraph.value?.nodes?.[0] ?? null;
  }
  if (!selectedItem.value || selectedItem.value.type !== "file") {
    return null;
  }
  return {
    id: selectedItem.value.path,
    path: selectedItem.value.path,
    label: selectedItem.value.basename,
    directoryLabel: labelForWorkspacePath(currentPath.value),
    role: "focus",
    score: 1,
    concepts: [],
  };
});
const relationGraphKey = computed(() => {
  const focus = relationGraph.value?.focusPath ?? selectedItem.value?.path ?? "empty";
  const nodeCount = relationGraph.value?.nodes?.length ?? 0;
  const edgeCount = relationGraph.value?.edges?.length ?? 0;
  return `${focus}:${nodeCount}:${edgeCount}:${relationExpanded.value ? "expanded" : "side"}`;
});

function relationTypeLabel(type) {
  if (type === "semantic_similar") {
    return "语义相近";
  }
  if (type === "shared_concepts") {
    return "共享概念";
  }
  if (type === "co_hit") {
    return "共同命中";
  }
  return "相关";
}

function formatRelationScore(score) {
  return typeof score === "number" && !Number.isNaN(score) ? score.toFixed(2) : "-";
}

function fileLabelFromPath(path) {
  if (!path) {
    return "-";
  }
  const normalized = path.endsWith("/") ? path.slice(0, -1) : path;
  return normalized.split("/").pop() ?? normalized;
}

function addSelectedFileToWorkingSet() {
  if (!isSelectedFile.value) {
    return;
  }

  addWorkingFile({
    path: selectedItem.value.path,
    label: selectedItem.value.basename,
    kind: "file",
  });
}

function openReading() {
  if (!isSelectedFile.value) {
    return;
  }

  addSelectedFileToWorkingSet();
  router.push({ name: "assistant" });
}

function openPreview(item = selectedItem.value) {
  if (!item || item.type !== "file") {
    return;
  }

  const previewUrl = workspaceFinderDriver.getPreviewUrl({ path: item.path });
  window.open(previewUrl, "_blank", "noopener,noreferrer");
}

function handleSelect(items) {
  selectedItems.value = items;
}

function handlePathChange(path) {
  currentPath.value = path;
  selectedItems.value = [];
  clearRelationSelection();
  relationMode.value = false;
  relationExpanded.value = false;
}

function handleFileDclick(event) {
  event.preventDefault();
  openPreview(event.item);
}

function clearRelationSelection() {
  selectedRelationNode.value = null;
  selectedRelationEdge.value = null;
}

function handleRelationNodeSelect(node) {
  selectedRelationNode.value = node;
  selectedRelationEdge.value = null;
}

function handleRelationEdgeSelect(edge) {
  selectedRelationEdge.value = edge;
  selectedRelationNode.value = null;
}

async function loadRelationGraphFor(path) {
  relationLoading.value = true;
  relationError.value = "";
  clearRelationSelection();

  try {
    relationGraph.value = await getWorkspaceFileRelationGraph(path, 8);
    selectedRelationNode.value = relationGraph.value?.nodes?.find((node) => node.role === "focus") ?? null;
  } catch (error) {
    relationGraph.value = null;
    relationError.value = error instanceof Error ? error.message : ui.relationLoadFailed;
  } finally {
    relationLoading.value = false;
  }
}

async function openRelationView() {
  if (!isSelectedFile.value) {
    return;
  }

  relationMode.value = true;
  relationExpanded.value = false;
}

function returnToDetail() {
  relationMode.value = false;
  relationExpanded.value = false;
  relationError.value = "";
  clearRelationSelection();
}

function expandRelationView() {
  if (!relationMode.value) {
    return;
  }
  relationExpanded.value = true;
}

async function focusSelectedRelationNode() {
  const path = selectedRelationNode.value?.path;
  if (!path || path === relationGraph.value?.focusPath) {
    return;
  }

  await loadRelationGraphFor(path);
}

watch(
  () => [relationMode.value, selectedItem.value?.path, selectedItem.value?.type],
  ([active, path, type]) => {
    if (!active) {
      return;
    }

    if (type !== "file" || !path) {
      relationGraph.value = null;
      relationError.value = "";
      clearRelationSelection();
      relationMode.value = false;
      return;
    }

    loadRelationGraphFor(path);
  }
);
</script>

<template>
  <div :class="['page', 'library', 'active', pageClasses]">
    <section class="panel vf-shell">
      <div class="ph">
        <h3>{{ relationMode && relationExpanded ? ui.relationMapTitle : ui.title }}</h3>
        <span class="tag blue">{{ relationMode && relationExpanded ? ui.relationStatus : ui.workspace }}</span>
      </div>
      <div class="vf-host">
        <template v-if="relationMode && relationExpanded">
          <div class="relation-expanded-host">
            <div v-if="relationHasSingleNodeGraph" class="relation-graph-single">
              <div class="relation-graph-single-card relation-graph-single-card--large">
                <span class="relation-graph-single-tag">当前文件</span>
                <b>{{ relationSingleNode?.label }}</b>
                <span>{{ relationSingleNode?.directoryLabel || "当前目录" }}</span>
              </div>
            </div>
            <FileRelationGraph
              v-else
              :key="relationGraphKey"
              :graph="relationGraph"
              :loading="relationLoading"
              @select-node="handleRelationNodeSelect"
              @select-edge="handleRelationEdgeSelect"
              @clear-selection="clearRelationSelection"
            />
          </div>
        </template>
        <VueFinder
          v-else
          :id="WORKSPACE_FINDER_ID"
          :driver="workspaceFinderDriver"
          :config="workspaceFinderConfig"
          :features="workspaceFinderFeatures"
          locale="zhCN"
          selection-mode="single"
          @select="handleSelect"
          @path-change="handlePathChange"
          @file-dclick="handleFileDclick"
        />
      </div>
    </section>

    <aside class="panel right">
      <div class="ph">
        <h3>{{ relationMode && relationExpanded ? ui.currentFileInfo : relationMode ? ui.relationStatus : ui.currentSelection }}</h3>
        <div class="ph-actions">
          <button v-if="relationMode && relationExpanded" class="ph-link-button" type="button" @click="returnToDetail">
            {{ ui.backToLibrary }}
          </button>
          <span v-else :class="statusTagClass">{{ statusTagLabel }}</span>
        </div>
      </div>
      <div :class="['pb', relationMode ? 'library-relation-panel' : '']">
        <template v-if="!relationMode">
          <div class="detail">
            <b>{{ detailTitle }}</b>
            <span class="muted">{{ detailSummary }}</span>
            <div class="kv">
              <span>{{ ui.path }}</span>
              <span>{{ detailPath }}</span>
              <span>{{ ui.type }}</span>
              <span>{{ detailType }}</span>
              <span>{{ ui.mime }}</span>
              <span>{{ detailMimeType }}</span>
              <span>{{ ui.size }}</span>
              <span>{{ detailSize }}</span>
              <span>{{ ui.updated }}</span>
              <span>{{ detailUpdated }}</span>
            </div>
          </div>

          <button class="op op-button" type="button" :disabled="!isSelectedFile" @click="openPreview()">
            <b>{{ ui.previewFile }}</b>
          </button>
          <button class="op op-button" type="button" :disabled="!isSelectedFile" @click="openReading">
            <b>{{ ui.openReading }}</b>
          </button>
          <button class="op op-button" type="button" :disabled="!isSelectedFile" @click="addSelectedFileToWorkingSet">
            <b>{{ ui.addWorkingFile }}</b>
          </button>
          <button class="op op-button" type="button" :disabled="!isSelectedFile" @click="openRelationView">
            <b>{{ ui.viewRelations }}</b>
          </button>
        </template>

        <template v-else>
          <div v-if="!relationExpanded" class="relation-toolbar">
            <button class="op op-button relation-back" type="button" @click="returnToDetail">
              <b>{{ ui.backToDetail }}</b>
            </button>
            <button class="op op-button relation-expand" type="button" @click="expandRelationView">
              <b>{{ ui.expandRelation }}</b>
            </button>
          </div>

          <div v-if="relationExpanded && selectedItem" class="detail">
            <b>{{ selectedItem.basename }}</b>
            <span class="muted">{{ selectedItem.mime_type ? `${ui.fileTag} / ${selectedItem.mime_type}` : ui.fileGeneric }}</span>
            <div class="kv">
              <span>{{ ui.path }}</span>
              <span>{{ selectedItem.path }}</span>
              <span>{{ ui.relationRole }}</span>
              <span>{{ ui.currentFileRole }}</span>
              <span>{{ ui.relationDir }}</span>
              <span>{{ labelForWorkspacePath(currentPath) }}</span>
            </div>
          </div>

          <div class="detail">
            <b>{{ relationDetailTitle }}</b>
            <span class="muted">{{ relationDetailSummary }}</span>
          </div>

          <div v-if="!relationExpanded" class="relation-graph-side-host">
            <div v-if="relationHasSingleNodeGraph" class="relation-graph-single">
              <div class="relation-graph-single-card">
                <span class="relation-graph-single-tag">当前文件</span>
                <b>{{ relationSingleNode?.label }}</b>
                <span>{{ relationSingleNode?.directoryLabel || '当前目录' }}</span>
              </div>
            </div>
            <FileRelationGraph
              v-else
              :key="relationGraphKey"
              :graph="relationGraph"
              :loading="relationLoading"
              @select-node="handleRelationNodeSelect"
              @select-edge="handleRelationEdgeSelect"
              @clear-selection="clearRelationSelection"
            />
          </div>

          <div v-if="relationDetailRows.length" class="detail">
            <div class="kv">
              <template v-for="row in relationDetailRows" :key="row.label">
                <span>{{ row.label }}</span>
                <span>{{ row.value }}</span>
              </template>
            </div>
          </div>

          <div v-if="relationReasons.length" class="detail">
            <b>{{ ui.relationReasons }}</b>
            <div class="relation-badges">
              <span v-for="reason in relationReasons" :key="reason" class="relation-badge">
                {{ reason }}
              </span>
            </div>
          </div>

          <div v-if="relationConcepts.length" class="detail">
            <b>{{ ui.relationConcepts }}</b>
            <div class="relation-badges">
              <span v-for="concept in relationConcepts" :key="concept" class="relation-badge ghost">
                {{ concept }}
              </span>
            </div>
          </div>

          <button
            v-if="selectedRelationNode && selectedRelationNode.path !== relationGraph?.focusPath"
            class="op op-button"
            type="button"
            @click="focusSelectedRelationNode"
          >
            <b>{{ ui.focusRelation }}</b>
          </button>
        </template>
      </div>
    </aside>
  </div>
</template>
