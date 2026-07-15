<script setup>
import { useAppState } from "@/composables/useAppState";

const tabs = [
  { id: "basic", label: "\u57fa\u7840\u8bbe\u7f6e" },
  { id: "agent", label: "\u667a\u80fd\u4f53\u8bbe\u7f6e" },
  { id: "skills", label: "\u80fd\u529b\u5e93" },
  { id: "advanced", label: "\u9ad8\u7ea7\u8bbe\u7f6e" },
];

const skillRows = [
  {
    name: "\u8bc1\u636e\u5316\u6587\u6863\u751f\u6210",
    summary: "\u7cfb\u7edf\u5185\u7f6e\uff1a\u68c0\u7d22\u8d44\u6599\u3001\u9009\u62e9\u8bc1\u636e\u3001\u751f\u6210\u6587\u6863\u5e76\u6267\u884c\u68c0\u67e5\u3002",
    actions: ["\u8be6\u60c5", "\u590d\u5236", "\u505c\u7528"],
  },
  {
    name: "\u8d44\u6599\u6574\u7406",
    summary: "\u7cfb\u7edf\u5185\u7f6e\uff1a\u6839\u636e\u76ee\u5f55\u3001\u7c7b\u578b\u548c\u6458\u8981\u751f\u6210\u6574\u7406\u5efa\u8bae\u3002",
    actions: ["\u8be6\u60c5", "\u590d\u5236", "\u505c\u7528"],
  },
  {
    name: "\u7f51\u9875\u8d44\u6599\u8865\u5145",
    summary: "\u7cfb\u7edf\u5185\u7f6e\uff1a\u516c\u5f00\u7f51\u9875\u641c\u7d22\u3001\u6b63\u6587\u8bfb\u53d6\u548c\u5916\u90e8\u8d44\u6599\u4fdd\u5b58\u3002",
    actions: ["\u8be6\u60c5", "\u590d\u5236", "\u505c\u7528"],
  },
  {
    name: "\u9879\u76ee\u6587\u6863\u751f\u6210",
    summary: "\u6211\u7684\u80fd\u529b\uff1a\u56fa\u5b9a\u6574\u7406\u7ed3\u6784\uff0c\u5bfc\u51fa .docx \u6587\u6863\uff0c\u5e76\u4fdd\u5b58\u5ba1\u67e5\u8bb0\u5f55\u3002",
    actions: ["\u7f16\u8f91", "\u590d\u5236", "\u5220\u9664"],
  },
];

const ui = {
  title: "\u8bbe\u7f6e",
  system: "\u7cfb\u7edf",
  basicTitle: "\u57fa\u7840\u8bbe\u7f6e",
  defaultWorkspace: "\u9ed8\u8ba4\u5de5\u4f5c\u533a",
  defaultEntry: "\u9ed8\u8ba4\u5165\u53e3",
  projectMaterials: "\u9879\u76ee\u8d44\u6599",
  systemDesign: "\u7cfb\u7edf\u8bbe\u8ba1\u6750\u6599",
  home: "\u4e3b\u9875",
  library: "\u8d44\u6599\u5e93",
  workbench: "\u5de5\u4f5c\u53f0",
  indexingTitle: "\u7d22\u5f15\u7b56\u7565",
  autoIndex: "\u4e0a\u4f20\u540e\u81ea\u52a8\u7d22\u5f15",
  keepIndex: "\u6587\u4ef6\u79fb\u52a8\u540e\u4e0d\u91cd\u5efa\u7d22\u5f15",
  batchIndex: "\u4f4e\u5cf0\u671f\u6279\u91cf\u91cd\u5efa\u7d22\u5f15",
  decision: "\u51b3\u7b56\u667a\u80fd\u4f53",
  model: "\u6a21\u578b",
  planComplexity: "\u89c4\u5212\u56fe\u590d\u6742\u5ea6",
  planNormal: "\u4e3b\u8def\u5f84 + \u5355\u5c42\u5206\u652f",
  planSimple: "\u53ea\u5141\u8bb8\u4e3b\u8def\u5f84",
  execution: "\u6267\u884c\u667a\u80fd\u4f53",
  toolStrategy: "\u5de5\u5177\u8c03\u7528\u7b56\u7565",
  allowWhitelist: "\u5141\u8bb8\u767d\u540d\u5355\u5de5\u5177",
  localOnly: "\u4ec5\u672c\u5730\u5de5\u5177",
  allowWeb: "\u5141\u8bb8\u7f51\u9875\u8865\u5145\u5206\u652f",
  toolLog: "\u5de5\u5177\u8c03\u7528\u8bb0\u5f55\u65e5\u5fd7",
  inspectAgent: "\u5ba1\u67e5\u667a\u80fd\u4f53",
  inspectMode: "\u9ed8\u8ba4\u5ba1\u67e5\u6a21\u5f0f",
  off: "\u5173\u95ed",
  focus: "\u91cd\u70b9",
  full: "\u5b8c\u6574",
  inspectClaims: "\u68c0\u67e5\u65e0\u4f9d\u636e\u65ad\u8a00",
  inspectCitations: "\u68c0\u67e5\u5f15\u7528\u4e00\u81f4\u6027",
  skillLibrary: "\u80fd\u529b\u5e93",
  skillLibraryNote: "\u8fd9\u91cc\u7ba1\u7406\u80fd\u529b\u672c\u4f53\uff1a\u589e\u5220\u6539\u67e5\u3001\u590d\u5236\u3001\u9ed8\u8ba4\u5de5\u5177\u3001\u8f93\u5165\u9879\u548c\u8ba1\u5212\u6a21\u677f\u3002",
  createSkill: "\u65b0\u5efa\u80fd\u529b",
  advancedRetriever: "\u68c0\u7d22\u5668",
  evidenceRecall: "\u8bc1\u636e\u53ec\u56de",
  selector: "\u8bc1\u636e\u9009\u62e9\u5668",
  selectorStrategy: "\u9ed8\u8ba4\u7b56\u7565",
  inspector: "\u5ba1\u67e5\u5668",
  inspectStrategy: "\u68c0\u67e5\u7b56\u7565",
};

const { state } = useAppState();

function activateTab(tabId) {
  state.settingTab = tabId;
}
</script>

<template>
  <div class="page settings-page active">
    <aside class="settings-menu">
      <button
        v-for="tab in tabs"
        :key="tab.id"
        :class="{ active: state.settingTab === tab.id }"
        type="button"
        @click="activateTab(tab.id)"
      >
        {{ tab.label }}
      </button>
    </aside>

    <section class="settings-content">
      <div class="ph">
        <h3>{{ ui.title }}</h3>
        <span class="tag">{{ ui.system }}</span>
      </div>
      <div class="pb">
        <div :class="['settings-section', { active: state.settingTab === 'basic' }]">
          <div class="settings">
            <div class="card">
              <h4>{{ ui.basicTitle }}</h4>
              <div class="field">
                <label>{{ ui.defaultWorkspace }}</label>
                <select>
                  <option>{{ ui.projectMaterials }}</option>
                  <option>{{ ui.systemDesign }}</option>
                </select>
              </div>
              <div class="field">
                <label>{{ ui.defaultEntry }}</label>
                <select>
                  <option>{{ ui.home }}</option>
                  <option>{{ ui.library }}</option>
                  <option>{{ ui.workbench }}</option>
                </select>
              </div>
            </div>
            <div class="card">
              <h4>{{ ui.indexingTitle }}</h4>
              <div class="checks">
                <label><input type="checkbox" checked /> {{ ui.autoIndex }}</label>
                <label><input type="checkbox" checked /> {{ ui.keepIndex }}</label>
                <label><input type="checkbox" /> {{ ui.batchIndex }}</label>
              </div>
            </div>
          </div>
        </div>

        <div :class="['settings-section', { active: state.settingTab === 'agent' }]">
          <div class="settings">
            <div class="card">
              <h4>{{ ui.decision }}</h4>
              <div class="field">
                <label>{{ ui.model }}</label>
                <select>
                  <option>GPT-5.5（深度思考）</option>
                  <option>GPT-5.5（快速响应）</option>
                </select>
              </div>
              <div class="field">
                <label>{{ ui.planComplexity }}</label>
                <select>
                  <option>{{ ui.planNormal }}</option>
                  <option>{{ ui.planSimple }}</option>
                </select>
              </div>
            </div>
            <div class="card">
              <h4>{{ ui.execution }}</h4>
              <div class="field">
                <label>{{ ui.toolStrategy }}</label>
                <select>
                  <option>{{ ui.allowWhitelist }}</option>
                  <option>{{ ui.localOnly }}</option>
                </select>
              </div>
              <div class="checks">
                <label><input type="checkbox" checked /> {{ ui.allowWeb }}</label>
                <label><input type="checkbox" checked /> {{ ui.toolLog }}</label>
              </div>
            </div>
            <div class="card">
              <h4>{{ ui.inspectAgent }}</h4>
              <div class="field">
                <label>{{ ui.inspectMode }}</label>
                <select>
                  <option>{{ ui.off }}</option>
                  <option selected>{{ ui.focus }}</option>
                  <option>{{ ui.full }}</option>
                </select>
              </div>
              <div class="checks">
                <label><input type="checkbox" checked /> {{ ui.inspectClaims }}</label>
                <label><input type="checkbox" checked /> {{ ui.inspectCitations }}</label>
              </div>
            </div>
          </div>
        </div>

        <div :class="['settings-section', { active: state.settingTab === 'skills' }]">
          <div class="skill-lib-toolbar">
            <div>
              <b>{{ ui.skillLibrary }}</b>
              <div class="muted">{{ ui.skillLibraryNote }}</div>
            </div>
            <button class="btn primary" type="button">{{ ui.createSkill }}</button>
          </div>

          <div class="skill-lib-list">
            <div
              v-for="skill in skillRows"
              :key="skill.name"
              class="lib-row"
            >
              <div>
                <b>{{ skill.name }}</b>
                <span>{{ skill.summary }}</span>
              </div>
              <div class="lib-actions">
                <button
                  v-for="action in skill.actions"
                  :key="action"
                  :class="['btn', { 'muted-btn': action === '\u505c\u7528' || action === '\u5220\u9664' }]"
                  type="button"
                >
                  {{ action }}
                </button>
              </div>
            </div>
          </div>
        </div>

        <div :class="['settings-section', { active: state.settingTab === 'advanced' }]">
          <div class="settings">
            <div class="card">
              <h4>{{ ui.advancedRetriever }}</h4>
              <div class="field">
                <label>{{ ui.evidenceRecall }}</label>
                <select>
                  <option>稠密召回（TopK）</option>
                  <option>混合检索</option>
                </select>
              </div>
            </div>
            <div class="card">
              <h4>{{ ui.selector }}</h4>
              <div class="field">
                <label>{{ ui.selectorStrategy }}</label>
                <select>
                  <option>覆盖优先</option>
                  <option>多样性重排（MMR）</option>
                  <option>按得分截断（TopK）</option>
                </select>
              </div>
            </div>
            <div class="card">
              <h4>{{ ui.inspector }}</h4>
              <div class="field">
                <label>{{ ui.inspectStrategy }}</label>
                <select>
                  <option>断言 - 证据校验</option>
                  <option>格式 + 断言校验</option>
                </select>
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>
  </div>
</template>
