<script setup>
import { computed, nextTick, onMounted, ref, watch } from "vue";
import FlowCanvas from "@/components/FlowCanvas.vue";
import ResourcePanel from "@/components/ResourcePanel.vue";
import { useAppState } from "@/composables/useAppState";

const ui = {
  pageTitle: "工作台",
  currentSkills: "当前能力",
  newTask: "新建资料任务",
  task: "任务",
  skillChain: "能力链路",
  status: "状态",
  inspectMode: "审查模式",
  time: "时间",
  emptyRuns: "还没有工作台记录",
  currentWorkspace: "当前工作区",
  ready: "已就绪",
  back: "返回工作台",
  runTitle: "资料任务执行",
  running: "执行中...",
  inspecting: "检查中...",
  execute: "执行",
  resourceEmpty: "从文件树点击 + 添加。关闭全局工作区时，这条资料任务只会处理这些工作文件。",
  pageLabel: "页码",
  promptPlaceholder: "输入问题：例如请帮我整理文件",
  activeTaskLabel: "正在执行的任务：",
  generateExecutionReport: "生成执行报告",
  stats: ["目录数", "文件数", "工作文件", "当前范围"],
  more: "更多",
  pin: "置顶",
  unpin: "取消置顶",
  delete: "删除",
};

const seedRecordMap = {
  "seed-1": {
    name: "资料整理任务",
    skills: "检索 / 整理 / 审查",
    status: "运行中",
    inspectMode: "重点",
    time: "今天",
  },
  "seed-2": {
    name: "工作区结构梳理",
    skills: "目录整理 / 文件操作",
    status: "已完成",
    inspectMode: "关闭",
    time: "昨天",
  },
  "seed-3": {
    name: "引用证据检查",
    skills: "断言检查 / 证据匹配",
    status: "待处理",
    inspectMode: "完整",
    time: "昨天",
  },
};

const {
  state,
  openSkillModal,
  clearSelectedNode,
  selectNode,
  workbenchRecords,
  workbenchWorkspaceStats,
  activeWorkbenchRun,
  loadWorkbenchRecords,
  openWorkbenchDraft,
  openWorkbenchRun,
  markWorkbenchPromptDirty,
  restoreWorkbenchRunView,
  submitWorkbenchRun,
  deleteWorkbenchRun,
  isWorkbenchRunPinned,
  togglePinnedWorkbenchRun,
  backWorkbenchList,
} = useAppState();

const flowRef = ref(null);
const pageInput = ref("1");
const recordMenuId = ref(null);

const labelMap = {
  draft: "草稿",
  prepared: "已准备",
  completed: "已完成",
  running: "运行中",
  queued: "排队中",
  bypassed: "跳过",
  focus: "重点",
  full: "完整",
  off: "关闭",
  executing: "执行中",
  inspecting: "检查中",
  retrieve: "检索",
  organize: "整理",
  inspect: "审查",
  scope: "范围",
  planning: "规划",
  execution: "执行",
  cleanup: "清理",
  review: "复核",
  read: "读取",
  classify: "分类",
  move: "移动",
  audit: "复核",
};

const runTitle = computed(() => activeWorkbenchRun.value?.name ?? ui.runTitle);
const runExecutionActive = computed(() => Boolean(activeWorkbenchRun.value?.activeExecutionNodeId));
const runInspectionActive = computed(() => Boolean(activeWorkbenchRun.value?.activeInspectionNodeId));
const runStatus = computed(() => {
  if (runExecutionActive.value) {
    return labelMap.executing;
  }

  if (runInspectionActive.value) {
    return labelMap.inspecting;
  }

  return translateLabel(activeWorkbenchRun.value?.status ?? "draft");
});
const runSkills = computed(() => translateChain(activeWorkbenchRun.value?.skills ?? "retrieve / organize / inspect"));
const runInspectMode = computed(() => translateLabel(activeWorkbenchRun.value?.inspectMode ?? "focus"));
const runBusy = computed(() => runExecutionActive.value || runInspectionActive.value);
const composerBusy = computed(() => state.workbenchSubmitting || runBusy.value);
const activeTaskText = computed(
  () => state.workbenchActiveTaskLabel || activeWorkbenchRun.value?.prompt || runTitle.value,
);
const runActionLabel = computed(() => {
  if (state.workbenchSubmitting || runExecutionActive.value) {
    return ui.running;
  }

  if (runInspectionActive.value) {
    return ui.inspecting;
  }

  return ui.execute;
});
const currentRunPage = computed(() => activeWorkbenchRun.value?.currentPage ?? 1);
const totalRunPages = computed(() => activeWorkbenchRun.value?.totalPages ?? 1);
const canGoPrevPage = computed(() => Boolean(activeWorkbenchRun.value?.id) && currentRunPage.value > 1);
const canGoNextPage = computed(
  () => Boolean(activeWorkbenchRun.value?.id) && currentRunPage.value < totalRunPages.value,
);
const showRunPager = computed(() => Boolean(activeWorkbenchRun.value?.id) && totalRunPages.value > 1);

function translateLabel(value) {
  if (typeof value !== "string" || !value.trim()) {
    return "";
  }

  const normalized = value.trim().toLowerCase();
  return labelMap[normalized] ?? value;
}

function translateChain(value) {
  if (typeof value !== "string" || !value.trim()) {
    return "";
  }

  return value
    .split("/")
    .map((item) => translateLabel(item.trim()))
    .join(" / ");
}

function displayRecordName(record) {
  return seedRecordMap[record.id]?.name ?? record.name;
}

function displayRecordSkills(record) {
  return seedRecordMap[record.id]?.skills ?? translateChain(record.skills);
}

function displayRecordStatus(record) {
  return seedRecordMap[record.id]?.status ?? translateLabel(record.status);
}

function displayRecordInspectMode(record) {
  return seedRecordMap[record.id]?.inspectMode ?? translateLabel(record.inspectMode);
}

function displayRecordTime(record) {
  return seedRecordMap[record.id]?.time ?? record.time;
}

function syncFlow() {
  nextTick(() => {
    flowRef.value?.redraw?.();
    window.setTimeout(() => flowRef.value?.redraw?.(), 80);
  });
}

function startRun() {
  openWorkbenchDraft();
  syncFlow();
}

async function openRecord(record) {
  await openWorkbenchRun(typeof record?.id === "number" ? record.id : null);
  syncFlow();
}

async function triggerRun(mode) {
  await submitWorkbenchRun(mode);
  syncFlow();
}

function returnList() {
  backWorkbenchList();
}

function closeRecordMenu() {
  recordMenuId.value = null;
}

function toggleRecordMenu(runId) {
  recordMenuId.value = recordMenuId.value === runId ? null : runId;
}

function togglePinnedRecord(runId) {
  togglePinnedWorkbenchRun(runId);
  closeRecordMenu();
}

async function removeRecord(runId) {
  closeRecordMenu();
  await deleteWorkbenchRun(runId);
}

async function jumpToRunPage(targetPage) {
  const runId = activeWorkbenchRun.value?.id;

  if (typeof runId !== "number") {
    return;
  }

  const normalizedPage = Math.max(1, Math.min(Number(targetPage) || 1, totalRunPages.value));
  pageInput.value = String(normalizedPage);
  await openWorkbenchRun(runId, normalizedPage);
  syncFlow();
}

function handlePageInputCommit() {
  jumpToRunPage(pageInput.value);
}

function handlePromptInput() {
  markWorkbenchPromptDirty();
}

onMounted(() => {
  loadWorkbenchRecords();
  restoreWorkbenchRunView().then(() => {
    syncFlow();
  });
});

watch(
  () => [state.workbenchView, state.resourceCollapsed.workbench],
  ([view]) => {
    if (view === "run") {
      syncFlow();
    }
  },
);

watch(
  () => [currentRunPage.value, totalRunPages.value],
  ([currentPage]) => {
    pageInput.value = String(currentPage);
  },
  { immediate: true },
);
</script>

<template>
  <div class="page workbench-page active" @click="closeRecordMenu">
    <div v-if="state.workbenchView === 'list'" class="wb-list">
      <section class="wb-main">
        <div class="wb-header">
          <h3>{{ ui.pageTitle }}</h3>
          <div class="actions">
            <button class="btn" type="button" @click="openSkillModal">
              {{ ui.currentSkills }}
            </button>
            <button class="btn primary" type="button" @click="startRun">
              {{ ui.newTask }}
            </button>
          </div>
        </div>
        <div class="wb-body">
          <div class="record-table">
            <div class="record-row head">
              <span>{{ ui.task }}</span>
              <span>{{ ui.skillChain }}</span>
              <span>{{ ui.status }}</span>
              <span>{{ ui.inspectMode }}</span>
              <span>{{ ui.time }}</span>
            </div>

            <div
              v-for="record in workbenchRecords"
              :key="record.id ?? record.name"
              :class="['record-entry', { 'menu-open': recordMenuId === record.id }]"
            >
              <button class="record-row record-button" type="button" @click="openRecord(record)">
                <b>{{ displayRecordName(record) }}</b>
                <span>{{ displayRecordSkills(record) }}</span>
                <span :class="['status-dot', record.statusTone]">{{ displayRecordStatus(record) }}</span>
                <span>{{ displayRecordInspectMode(record) }}</span>
                <span>{{ displayRecordTime(record) }}</span>
              </button>
              <div class="row-actions record-actions" @click.stop>
                <button
                  class="row-actions-trigger"
                  type="button"
                  :title="ui.more"
                  @click.stop="toggleRecordMenu(record.id)"
                >
                  ⋯
                </button>
                <div v-if="recordMenuId === record.id" class="row-actions-menu">
                  <button type="button" @click.stop="togglePinnedRecord(record.id)">
                    {{ isWorkbenchRunPinned(record.id) ? ui.unpin : ui.pin }}
                  </button>
                  <button class="danger" type="button" @click.stop="removeRecord(record.id)">
                    {{ ui.delete }}
                  </button>
                </div>
              </div>
            </div>

            <div v-if="!workbenchRecords.length" class="record-row">
              <b>{{ ui.emptyRuns }}</b>
              <span>-</span>
              <span>-</span>
              <span>-</span>
            </div>
          </div>
        </div>
      </section>

      <aside class="wb-aside">
        <div class="wb-header">
          <h3>{{ ui.currentWorkspace }}</h3>
          <span class="tag ok">{{ ui.ready }}</span>
        </div>
        <div class="wb-body">
          <div v-for="(item, index) in workbenchWorkspaceStats" :key="item.label" class="mini-stat">
            <span>{{ ui.stats[index] ?? item.label }}</span>
            <b>{{ item.value }}</b>
          </div>
        </div>
      </aside>
    </div>

    <div
      v-else
      :class="['wb-run', 'workbench', { 'resource-collapsed': state.resourceCollapsed.workbench }]"
    >
      <section class="panel graph">
        <div class="ph">
          <h3>
            <button class="btn back-run" type="button" @click="returnList">
              {{ ui.back }}
            </button>
            {{ runTitle }}
          </h3>
          <span class="tag blue">{{ runStatus }} - {{ runSkills }} - {{ runInspectMode }}</span>
        </div>

        <div v-if="showRunPager" class="run-pager">
          <button
            class="run-pager-arrow"
            type="button"
            :disabled="!canGoPrevPage"
            @click="jumpToRunPage(currentRunPage - 1)"
          >
            &lt;
          </button>
          <label class="run-page-box">
            {{ ui.pageLabel }}
            <input
              v-model="pageInput"
              type="text"
              inputmode="numeric"
              @keydown.enter.prevent="handlePageInputCommit"
              @blur="handlePageInputCommit"
            />
            / {{ totalRunPages }}
          </label>
          <button
            class="run-pager-arrow"
            type="button"
            :disabled="!canGoNextPage"
            @click="jumpToRunPage(currentRunPage + 1)"
          >
            &gt;
          </button>
        </div>

        <div class="graph-canvas">
          <FlowCanvas
            ref="flowRef"
            :collapsed="state.resourceCollapsed.workbench"
            @select-node="selectNode"
            @clear-selection="clearSelectedNode"
          />
        </div>
      </section>

      <section class="composer-box">
        <div class="composer">
          <div :class="['composer-main', { 'is-busy': composerBusy }]">
            <template v-if="!composerBusy">
              <textarea
                v-model="state.workbenchPrompt"
                class="input"
                :placeholder="ui.promptPlaceholder"
                @input="handlePromptInput"
              ></textarea>
              <div class="composer-actions">
                <button
                  class="btn primary"
                  type="button"
                  :disabled="state.workbenchSubmitting || runBusy"
                  @click="triggerRun('execute')"
                >
                  {{ runActionLabel }}
                </button>
              </div>
            </template>
            <div v-else class="composer-status" role="status" aria-live="polite">
              <span class="composer-status-label">{{ ui.activeTaskLabel }}</span>
              <b class="composer-status-text">{{ activeTaskText }}</b>
            </div>
          </div>
          <div v-if="!composerBusy" class="composer-footer">
            <div class="composer-footer-left">
              <button class="skill skill-inline" type="button" :title="ui.currentSkills" @click="openSkillModal">
                +
              </button>
              <label class="composer-option">
                <input v-model="state.workbenchGenerateExecutionReport" type="checkbox" />
                <span>{{ ui.generateExecutionReport }}</span>
              </label>
            </div>
          </div>
        </div>
      </section>

      <ResourcePanel page-key="workbench" :empty-text="ui.resourceEmpty" />
    </div>
  </div>
</template>
