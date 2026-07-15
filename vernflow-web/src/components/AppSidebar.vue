<script setup>
import { computed } from "vue";

const ui = {
  main: "\u4e3b\u529f\u80fd",
  output: "\u8f93\u51fa",
  system: "\u7cfb\u7edf",
  home: "\u4e3b\u9875",
  library: "\u8d44\u6599\u5e93",
  workbench: "\u5de5\u4f5c\u53f0",
  assistant: "\u6587\u6863\u52a9\u624b",
  results: "\u751f\u6210\u7ed3\u679c",
  review: "\u5ba1\u67e5\u8bb0\u5f55",
  settings: "\u8bbe\u7f6e",
  subtitle: "\u4e2a\u4eba\u5de5\u4f5c\u533a\u52a9\u624b",
  signOut: "\u9000\u51fa\u767b\u5f55",
};

const navigationGroups = [
  {
    label: ui.main,
    items: [
      { label: ui.home, page: "home" },
      { label: ui.library, page: "library" },
      { label: ui.workbench, page: "workbench" },
      { label: ui.assistant, page: "assistant" },
    ],
  },
  {
    label: ui.output,
    items: [
      { label: ui.results },
      { label: ui.review },
    ],
  },
  {
    label: ui.system,
    items: [
      { label: ui.settings, page: "settings" },
    ],
  },
];

const props = defineProps({
  activePage: {
    type: String,
    default: "home",
  },
  currentUser: {
    type: Object,
    default: null,
  },
});

const emit = defineEmits(["navigate", "logout"]);

const userInitial = computed(() => {
  const source = props.currentUser?.displayName || props.currentUser?.username || "U";
  return source.slice(0, 1).toUpperCase();
});

function navigate(page) {
  if (page) {
    emit("navigate", page);
  }
}

function logout() {
  emit("logout");
}
</script>

<template>
  <aside class="nav">
    <div class="brand">
      <div class="mark">V</div>
      <div>
        <b>VernFlow</b>
        <span>{{ ui.subtitle }}</span>
      </div>
    </div>
    <div class="nav-body">
      <template
        v-for="group in navigationGroups"
        :key="group.label"
      >
        <div class="nav-label">{{ group.label }}</div>
        <button
          v-for="item in group.items"
          :key="`${group.label}-${item.label}`"
          :class="['nav-item', { active: item.page === activePage, inert: !item.page }]"
          type="button"
          @click="navigate(item.page)"
        >
          {{ item.label }}
        </button>
      </template>
    </div>
    <div class="nav-bottom">
      <div
        v-if="currentUser"
        class="user-badge"
      >
        <div class="user-avatar">{{ userInitial }}</div>
        <div class="user-meta">
          <b>{{ currentUser.displayName }}</b>
          <span>@{{ currentUser.username }}</span>
        </div>
      </div>
      <div class="nav-bottom-actions">
        <button
          class="agent-btn"
          type="button"
          @click="navigate('settings')"
        >
          {{ ui.settings }}
        </button>
        <button
          class="agent-btn ghost"
          type="button"
          @click="logout"
        >
          {{ ui.signOut }}
        </button>
      </div>
    </div>
  </aside>
</template>
