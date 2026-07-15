<script setup>
import { computed } from "vue";
import { useAppState } from "@/composables/useAppState";

const ui = {
  prefix: "\u8282\u70b9\u4fe1\u606f\uff1a",
  inspect: "\u8ba9\u5ba1\u67e5\u667a\u80fd\u4f53\u68c0\u67e5\u8fd9\u4e00\u6b65",
  basic: "\u57fa\u672c\u4fe1\u606f",
  nodeId: "\u8282\u70b9 ID",
  type: "\u7c7b\u578b",
  status: "\u72b6\u6001",
  scope: "\u8f93\u5165\u8303\u56f4",
  checks: "\u68c0\u67e5\u9879",
  summary: "\u8282\u70b9 I/O \u6458\u8981",
  result: "\u5ba1\u67e5\u667a\u80fd\u4f53\u5df2\u5b8c\u6210\u68c0\u67e5\uff1a\u5f53\u524d\u8282\u70b9\u8f93\u5165\u8303\u56f4\u660e\u786e\uff0c\u8f93\u51fa\u6709\u8bc1\u636e\u7ed1\u5b9a\uff1b\u5efa\u8bae\u8865\u5145\u201c\u5b9e\u9a8c\u7ed3\u679c\u4e0e\u5bf9\u6bd4\u6570\u636e\u201d\u7684\u6765\u6e90\u3002",
  global: "\u5168\u5c40\u5de5\u4f5c\u533a",
  working: "\u5de5\u4f5c\u6587\u4ef6",
};

const {
  state,
  closeNodeModal,
  selectedNode,
  nodeLog,
  runInspect,
} = useAppState();

const scopeText = computed(() => (state.globalScope ? ui.global : ui.working));
const nodeTitle = computed(() => selectedNode.value.title);
const nodeType = computed(() => selectedNode.value.type);
const nodeStatus = computed(() => selectedNode.value.status);
const checks = computed(() => selectedNode.value.checks ?? []);

function closeFromMask(event) {
  if (event.target === event.currentTarget) {
    closeNodeModal();
  }
}
</script>

<template>
  <div
    :class="['mask', { open: state.nodeModalOpen }]"
    @click="closeFromMask"
  >
    <section class="modal">
      <div class="mh">
        <b>{{ ui.prefix }}{{ nodeTitle }}</b>
        <div class="actions">
          <button class="btn primary" type="button" @click="runInspect">{{ ui.inspect }}</button>
          <button class="close" type="button" @click="closeNodeModal">x</button>
        </div>
      </div>
      <div class="mb">
        <div class="node-info">
          <div class="block">
            <h4>{{ ui.basic }}</h4>
            <div class="line">
              <span>{{ ui.nodeId }}</span>
              <b>{{ `${selectedNode.type}_${nodeTitle}` }}</b>
            </div>
            <div class="line">
              <span>{{ ui.type }}</span>
              <b>{{ nodeType }}</b>
            </div>
            <div class="line">
              <span>{{ ui.status }}</span>
              <b>{{ nodeStatus }}</b>
            </div>
            <div class="line">
              <span>{{ ui.scope }}</span>
              <b>{{ scopeText }}</b>
            </div>
          </div>
          <div class="block">
            <h4>{{ ui.checks }}</h4>
            <div
              v-for="item in checks"
              :key="item.label"
              class="row"
            >
              <span :class="['check', item.tone]"></span>
              <span>{{ item.label }}</span>
              <span>{{ item.result }}</span>
            </div>
          </div>
        </div>
        <div style="height: 12px"></div>
        <div class="block">
          <h4>{{ ui.summary }}</h4>
          <pre class="log">{{ nodeLog }}</pre>
          <div :class="['result', { show: state.inspectResultVisible }]">
            {{ ui.result }}
          </div>
        </div>
      </div>
    </section>
  </div>
</template>
