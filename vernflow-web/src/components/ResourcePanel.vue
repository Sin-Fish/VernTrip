<script setup>
import { computed, reactive, ref, watch } from "vue";
import { useAppState } from "@/composables/useAppState";
import { userRequest } from "@/lib/userApi";

const ui = {
  fileTree: "\u6587\u4ef6\u6811",
  workspace: "\u5de5\u4f5c\u533a",
  explorer: "\u8d44\u6e90\u6d4f\u89c8",
  workingFiles: "\u5de5\u4f5c\u6587\u4ef6",
  globalScope: "\u5168\u5c40\u5de5\u4f5c\u533a",
};

const props = defineProps({
  pageKey: {
    type: String,
    required: true,
  },
  emptyText: {
    type: String,
    required: true,
  },
});

const {
  state,
  treeItems,
  addWorkingFile,
  removeWorkingFile,
  selectTreeItem,
  toggleGlobalScope,
  toggleResource,
} = useAppState();

const rootTreeItems = ref([]);
const expandedFolders = reactive({});
const folderLoading = reactive({});
const directoryChildren = reactive({});

function normalizeTreeItem(item) {
  if (!item) {
    return null;
  }

  if (typeof item === "string") {
    const label = item.split("/").pop() ?? item;
    return {
      path: item,
      label,
      kind: label.includes(".") ? "file" : "folder",
    };
  }

  const path = typeof item.path === "string" ? item.path : "";
  const label = typeof item.label === "string" ? item.label : "";
  const kind = item.kind === "file" ? "file" : "folder";

  if (!path || !label) {
    return null;
  }

  return { path, label, kind };
}

function normalizeFinderEntry(entry) {
  if (!entry || typeof entry !== "object") {
    return null;
  }

  const path =
    typeof entry.path === "string"
      ? entry.path
      : typeof entry.uri === "string"
        ? entry.uri
        : "";
  const label =
    typeof entry.basename === "string" && entry.basename
      ? entry.basename
      : typeof entry.name === "string" && entry.name
        ? entry.name
        : typeof entry.label === "string"
          ? entry.label
          : "";
  const rawType = typeof entry.type === "string" ? entry.type.toLowerCase() : "";
  const kind =
    rawType === "file" || rawType === "document"
      ? "file"
      : rawType === "dir" || rawType === "directory" || rawType === "folder"
        ? "folder"
        : "";

  if (!path || !label || !kind) {
    return null;
  }

  return { path, label, kind };
}

function flattenTree(items, depth = 0) {
  const rows = [];

  for (const item of items) {
    if (!item) {
      continue;
    }

    rows.push({
      ...item,
      depth,
      expanded: Boolean(expandedFolders[item.path]),
      loading: Boolean(folderLoading[item.path]),
    });

    if (item.kind === "folder" && expandedFolders[item.path]) {
      rows.push(...flattenTree(directoryChildren[item.path] ?? [], depth + 1));
    }
  }

  return rows;
}

const visibleTreeItems = computed(() => flattenTree(rootTreeItems.value));

watch(
  treeItems,
  (items) => {
    rootTreeItems.value = (items ?? []).map((item) => normalizeTreeItem(item)).filter(Boolean);
  },
  { immediate: true },
);

async function loadFolderChildren(folderPath) {
  if (directoryChildren[folderPath] || folderLoading[folderPath]) {
    return;
  }

  folderLoading[folderPath] = true;

  try {
    const payload = await userRequest(`/api/finder?path=${encodeURIComponent(folderPath)}`);
    const entries = Array.isArray(payload?.files)
      ? payload.files
      : Array.isArray(payload?.entries)
        ? payload.entries
        : Array.isArray(payload?.items)
          ? payload.items
          : [];
    directoryChildren[folderPath] = entries.map((entry) => normalizeFinderEntry(entry)).filter(Boolean);
  } finally {
    folderLoading[folderPath] = false;
  }
}

async function toggleFolder(item) {
  if (!item || item.kind !== "folder") {
    return;
  }

  if (expandedFolders[item.path]) {
    expandedFolders[item.path] = false;
    return;
  }

  await loadFolderChildren(item.path);
  expandedFolders[item.path] = true;
}

async function handleTreeClick(item) {
  selectTreeItem(item);

  if (item?.kind === "folder") {
    await toggleFolder(item);
  }
}

function handleScopeChange(event) {
  toggleGlobalScope(event.target.checked);
}

function togglePanel() {
  toggleResource(props.pageKey);
}
</script>

<template>
  <aside class="resource">
    <button
      class="handle"
      type="button"
      @click="togglePanel"
    >
      <span>&gt;</span>
    </button>
    <div class="resource-main">
      <div class="ph">
        <h3>{{ ui.fileTree }}</h3>
        <span class="tag">{{ ui.workspace }}</span>
      </div>
      <div class="pb">
        <div class="tree-cap">{{ ui.explorer }}</div>
        <div
          v-for="item in visibleTreeItems"
          :key="item.path"
          :class="['tree-row', { active: state.selectedTreePath === item.path }]"
          :style="{ marginLeft: `${item.depth * 16}px` }"
        >
          <button
            v-if="item.kind === 'folder'"
            :class="['tree-branch', { expanded: item.expanded }]"
            type="button"
            @click.stop="toggleFolder(item)"
          >
            <span class="tree-arrow">{{ item.loading ? "…" : ">" }}</span>
          </button>
          <span
            v-else
            class="tree-spacer"
          ></span>
          <button
            class="tree-button"
            type="button"
            @click="handleTreeClick(item)"
          >
            <span class="name">
              <span :class="['ico', item.kind === 'file' ? 'file' : 'folder']"></span>
              {{ item.label }}
            </span>
          </button>
          <button
            class="add"
            type="button"
            @click="addWorkingFile(item)"
          >
            +
          </button>
        </div>
      </div>
    </div>
    <div class="work-files">
      <div class="wf-head">
        <b>{{ ui.workingFiles }}</b>
        <label class="scope">
          <input
            class="globalScope"
            type="checkbox"
            :checked="state.globalScope"
            @change="handleScopeChange"
          />
          {{ ui.globalScope }}
        </label>
      </div>
      <div
        v-if="!state.workingFiles.length"
        class="wf-empty"
      >
        {{ emptyText }}
      </div>
      <div class="wf-list">
        <div
          v-for="item in state.workingFiles"
          :key="item.path"
          class="wf-item"
        >
          <span :class="['ico', item.kind === 'file' ? 'file' : 'folder']"></span>
          <span class="name">{{ item.label }}</span>
          <button
            class="rm"
            type="button"
            @click="removeWorkingFile(item)"
          >
            x
          </button>
        </div>
      </div>
    </div>
  </aside>
</template>
