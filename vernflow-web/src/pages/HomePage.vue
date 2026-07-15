<script setup>
import { computed, onMounted } from "vue";
import { useRouter } from "vue-router";
import { useAppState } from "@/composables/useAppState";

const ui = {
  eyebrow: "VernFlow \u4e2a\u4eba\u5de5\u4f5c\u533a\u52a9\u624b",
  title: "\u628a\u8d44\u6599\u653e\u8fdb\u6765\uff0c\u4e5f\u628a\u7ebf\u7d22\u7406\u51fa\u6765",
  start: "\u5f00\u59cb\u6574\u7406",
  import: "\u5bfc\u5165\u8d44\u6599",
  ask: "\u95ee\u8d44\u6599",
  organize: "\u6574\u7406\u8d44\u6599",
  checkConfig: "\u68c0\u67e5\u914d\u7f6e",
  flowPreview: "\u8d44\u6599\u5904\u7406\u94fe\u8def\u9884\u89c8",
  localWorkspace: "\u672c\u5730\u5de5\u4f5c\u533a",
  scope: "\u8d44\u6599\u8303\u56f4",
  workingFiles: "\u5de5\u4f5c\u6587\u4ef6",
  localRetrieve: "\u672c\u5730\u68c0\u7d22",
  webFill: "\u7f51\u9875\u8865\u5145",
  fallbackBranch: "\u56de\u9000\u5206\u652f",
  evidenceSelector: "\u8bc1\u636e\u9009\u62e9",
  coverage: "\u8986\u76d6\u4f18\u5148",
  organizeResult: "\u6574\u7406\u7ed3\u679c",
  draftOutput: "\u8349\u7a3f\u8f93\u51fa",
  saveOutput: "\u4fdd\u5b58\u8f93\u51fa",
  reviewResult: "\u7ed3\u679c\u5ba1\u67e5",
  claimsCheck: "\u65ad\u8a00\u6838\u5bf9",
  currentWorkspace: "\u5f53\u524d\u5de5\u4f5c\u533a",
  ready: "\u5df2\u5c31\u7eea",
  generalWorkspace: "\u901a\u7528\u5de5\u4f5c\u533a",
  folderUnit: "\u4e2a\u76ee\u5f55",
  fileUnit: "\u4e2a\u6587\u4ef6",
  openLibrary: "\u6253\u5f00\u8d44\u6599\u5e93",
  currentScope: "\u5f53\u524d\u4f5c\u7528\u8303\u56f4",
  workingFileUnit: "\u4e2a\u5de5\u4f5c\u6587\u4ef6",
  taskEntry: "\u4efb\u52a1\u5165\u53e3",
  skillTemplate: "\u80fd\u529b\u6a21\u677f",
  libraryTitle: "\u8d44\u6599\u5e93",
  enter: "\u8fdb\u5165",
  workbenchTitle: "\u5de5\u4f5c\u53f0",
  newTask: "\u65b0\u5efa\u4efb\u52a1",
  assistantTitle: "\u6587\u6863\u52a9\u624b",
  startQa: "\u5f00\u59cb\u95ee\u7b54",
  systemState: "\u7cfb\u7edf\u72b6\u6001",
  workspaceFiles: "\u5de5\u4f5c\u533a\u6587\u4ef6",
  workspaceFolders: "\u5de5\u4f5c\u533a\u76ee\u5f55",
  currentRange: "\u5f53\u524d\u8303\u56f4",
  globalWorkspace: "\u5168\u5c40\u5de5\u4f5c\u533a",
};

const router = useRouter();
const { homeSummary, state, resetWorkbenchView, openWorkbenchRun } = useAppState();

const scopeModeLabel = computed(() => {
  return homeSummary.value.scopeMode === "global_workspace" ? ui.globalWorkspace : ui.workingFiles;
});

function goTo(page) {
  if (page === "workbench") {
    resetWorkbenchView();
  }

  router.push({ name: page });
}

async function startWorkbenchTask() {
  await openWorkbenchRun();
  router.push({ name: "workbench" });
}

onMounted(() => {
  if (/锟|浼|鍏|鏁|妫|璧|甯/.test(state.homeTaskDraft)) {
    state.homeTaskDraft = "\u6574\u7406\u5f53\u524d\u5de5\u4f5c\u533a\u8d44\u6599\uff0c\u5e76\u7ed9\u51fa\u76ee\u5f55\u5efa\u8bae\u3002";
  }
});
</script>

<template>
  <div class="page home active">
    <section class="home-hero">
      <div class="hero-copy">
        <span class="eyebrow">{{ ui.eyebrow }}</span>
        <h1>{{ ui.title }}</h1>
        <div class="taskbar">
          <input
            v-model="state.homeTaskDraft"
            type="text"
          />
          <button
            class="btn primary"
            type="button"
            @click="startWorkbenchTask"
          >
            {{ ui.start }}
          </button>
        </div>
        <div class="quick-actions">
          <button class="btn" type="button" @click="goTo('library')">{{ ui.import }}</button>
          <button class="btn" type="button" @click="goTo('assistant')">{{ ui.ask }}</button>
          <button class="btn" type="button" @click="startWorkbenchTask">{{ ui.organize }}</button>
          <button class="btn" type="button" @click="goTo('settings')">{{ ui.checkConfig }}</button>
        </div>
      </div>
      <div class="hero-flow">
        <div class="hf-head">
          <b>{{ ui.flowPreview }}</b>
          <span class="tag blue">{{ ui.localWorkspace }}</span>
        </div>
        <div class="mini-flow">
          <svg viewBox="0 0 480 190" preserveAspectRatio="none">
            <path class="mf-line" d="M 62 86 L 170 86 L 278 86 L 386 86"></path>
            <path class="mf-line dash" d="M 170 72 L 170 30 L 278 30 L 278 72"></path>
            <path class="mf-line" d="M 386 108 L 386 150 L 278 150"></path>
            <path class="mf-main" d="M 62 86 L 170 86 L 278 86 L 386 86 L 386 150 L 278 150"></path>
          </svg>
          <div class="mf-node mf-a">{{ ui.scope }}<span>{{ ui.workingFiles }}</span></div>
          <div class="mf-node mf-b">{{ ui.localRetrieve }}<span>前 24 条</span></div>
          <div class="mf-node mf-e">{{ ui.webFill }}<span>{{ ui.fallbackBranch }}</span></div>
          <div class="mf-node mf-c">{{ ui.evidenceSelector }}<span>{{ ui.coverage }}</span></div>
          <div class="mf-node mf-d">{{ ui.organizeResult }}<span>{{ ui.draftOutput }}</span></div>
          <div class="mf-node mf-f">{{ ui.saveOutput }}<span>办公文档（.docx）/ 标记文本（.md）</span></div>
          <div class="mf-node mf-g">{{ ui.reviewResult }}<span>{{ ui.claimsCheck }}</span></div>
        </div>
      </div>
    </section>

    <section class="home-grid">
      <div class="home-card">
        <div class="hc-head">
          <h3>{{ ui.currentWorkspace }}</h3>
          <span class="tag ok">{{ ui.ready }}</span>
        </div>
        <div class="hc-body">
          <div class="workspace-card">
            <b>{{ ui.generalWorkspace }}</b>
            <span class="muted">
              {{ homeSummary.totalFolderCount }} {{ ui.folderUnit }} · {{ homeSummary.totalFileCount }} {{ ui.fileUnit }}
            </span>
            <div class="progress"><i></i></div>
            <button class="btn" type="button" @click="goTo('library')">{{ ui.openLibrary }}</button>
          </div>
          <div class="workspace-card">
            <b>{{ ui.currentScope }}</b>
            <span class="muted">
              {{ scopeModeLabel }} · {{ homeSummary.workingFileCount }} {{ ui.workingFileUnit }}
            </span>
          </div>
        </div>
      </div>

      <div class="home-card">
        <div class="hc-head">
          <h3>{{ ui.taskEntry }}</h3>
          <span class="tag">{{ ui.skillTemplate }}</span>
        </div>
        <div class="hc-body">
          <div class="mode-cards">
            <div class="mode-card">
              <div class="mode-ico">F</div>
              <h4>{{ ui.libraryTitle }}</h4>
              <button class="btn" type="button" @click="goTo('library')">{{ ui.enter }}</button>
            </div>
            <div class="mode-card">
              <div class="mode-ico">G</div>
              <h4>{{ ui.workbenchTitle }}</h4>
              <button class="btn primary" type="button" @click="startWorkbenchTask">{{ ui.newTask }}</button>
            </div>
            <div class="mode-card">
              <div class="mode-ico">Q</div>
              <h4>{{ ui.assistantTitle }}</h4>
              <button class="btn" type="button" @click="goTo('assistant')">{{ ui.startQa }}</button>
            </div>
          </div>
        </div>
      </div>

      <div class="home-card">
        <div class="hc-head">
          <h3>{{ ui.systemState }}</h3>
          <span class="tag warn">{{ ui.workspaceFiles }}</span>
        </div>
        <div class="hc-body">
          <div class="stat"><span>{{ ui.workspaceFiles }}</span><b>{{ homeSummary.totalFileCount }}</b></div>
          <div class="stat"><span>{{ ui.workspaceFolders }}</span><b>{{ homeSummary.totalFolderCount }}</b></div>
          <div class="stat"><span>{{ ui.workingFiles }}</span><b>{{ homeSummary.workingFileCount }}</b></div>
          <div class="stat"><span>{{ ui.currentRange }}</span><b>{{ scopeModeLabel }}</b></div>
        </div>
      </div>
    </section>
  </div>
</template>
