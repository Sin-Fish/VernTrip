<script setup>
import { computed, nextTick, onMounted, ref, watch } from "vue";
import ResourcePanel from "@/components/ResourcePanel.vue";
import { renderMarkdownToHtml } from "@/lib/markdown";
import { useAppState } from "@/composables/useAppState";

const ui = {
  sessionHead: "会话管理",
  newDialog: "新对话",
  title: "文档助手",
  loading: "正在加载会话内容...",
  placeholder: "你想知道什么？",
  resourceEmpty: "从文件树点击 + 添加。关闭全局工作区时，助手只会围绕这些工作文件回答。",
  globalScope: "当前范围：全局工作区 · 工作文件仍会保留",
  workingScopePrefix: "当前范围：右侧工作文件 · ",
  workingScopeSuffix: " 份资料",
  thinking: "正在思考...",
  sending: "发送中...",
  send: "发送",
  more: "更多",
  pin: "置顶",
  unpin: "取消置顶",
  delete: "删除",
  emptySessions: "还没有会话",
  emptyMessages: "开始提问",
};

const {
  state,
  chatMessages,
  chatSessions,
  selectSession,
  toggleSessions,
  openSkillModal,
  openAssistantDraft,
  loadAssistantSessions,
  submitAssistantPrompt,
  deleteAssistantSession,
  isAssistantSessionPinned,
  togglePinnedAssistantSession,
} = useAppState();

const canSend = computed(() => state.assistantPrompt.trim().length > 0 && !state.assistantSubmitting);
const sendButtonLabel = computed(() => (state.assistantSubmitting ? ui.sending : ui.send));
const scopeText = computed(() => {
  if (state.globalScope) {
    return ui.globalScope;
  }
  return `${ui.workingScopePrefix}${state.workingFiles.length}${ui.workingScopeSuffix}`;
});

const messagesRef = ref(null);
const composerInputRef = ref(null);
const sessionMenuId = ref(null);

async function handleSubmit() {
  await submitAssistantPrompt();
}

function syncComposerHeight(target = composerInputRef.value) {
  if (!(target instanceof HTMLTextAreaElement)) {
    return;
  }
  target.style.height = "0px";
  target.style.height = `${Math.min(target.scrollHeight, 180)}px`;
}

function scrollMessagesToBottom() {
  const container = messagesRef.value;
  if (!container) {
    return;
  }
  container.scrollTop = container.scrollHeight;
}

function handleComposerKeydown(event) {
  if (event.isComposing || event.key !== "Enter" || event.shiftKey) {
    return;
  }
  event.preventDefault();
  handleSubmit();
}

function displayMessageText(message) {
  if (message.pending) {
    return ui.thinking;
  }
  return message.text;
}

function renderAssistantMessage(message) {
  return renderMarkdownToHtml(displayMessageText(message));
}

function closeSessionMenu() {
  sessionMenuId.value = null;
}

function toggleSessionMenu(sessionId) {
  sessionMenuId.value = sessionMenuId.value === sessionId ? null : sessionId;
}

function togglePinnedSession(sessionId) {
  togglePinnedAssistantSession(sessionId);
  closeSessionMenu();
}

function removeSession(sessionId) {
  closeSessionMenu();
  deleteAssistantSession(sessionId);
}

onMounted(() => {
  loadAssistantSessions({ autoOpen: true });
  nextTick(() => {
    syncComposerHeight();
    scrollMessagesToBottom();
  });
});

watch(
  () => state.assistantPrompt,
  () => {
    nextTick(() => {
      syncComposerHeight();
    });
  },
);

watch(
  () => chatMessages.value.map((message) => `${message.id}:${message.text}:${message.pending ? 1 : 0}:${message.error ? 1 : 0}`).join("|"),
  () => {
    nextTick(() => {
      scrollMessagesToBottom();
    });
  },
);
</script>

<template>
  <div
    :class="[
      'page',
      'assistant',
      'active',
      { 'sessions-collapsed': state.sessionsCollapsed, 'resource-collapsed': state.resourceCollapsed.assistant },
    ]"
    @click="closeSessionMenu"
  >
    <aside class="chat-side">
      <button class="sess-handle" type="button" @click="toggleSessions">
        <span>&lt;</span>
      </button>
      <div class="sess-head">{{ ui.sessionHead }}</div>
      <button class="new" type="button" @click="openAssistantDraft">
        + {{ ui.newDialog }}
      </button>
      <div class="chat-list">
        <div
          v-for="session in chatSessions"
          :key="session.id"
          :class="['chat-card-row', { active: state.selectedSessionId === session.id }]"
        >
          <button :class="['chat-card', { active: state.selectedSessionId === session.id }]" type="button" @click="selectSession(session.id)">
            <b>{{ session.title }}</b>
            <span>{{ session.summary }}</span>
          </button>
          <div class="row-actions" @click.stop>
            <button class="row-actions-trigger" type="button" :title="ui.more" @click.stop="toggleSessionMenu(session.id)">
              …
            </button>
            <div v-if="sessionMenuId === session.id" class="row-actions-menu">
              <button type="button" @click.stop="togglePinnedSession(session.id)">
                {{ isAssistantSessionPinned(session.id) ? ui.unpin : ui.pin }}
              </button>
              <button class="danger" type="button" @click.stop="removeSession(session.id)">
                {{ ui.delete }}
              </button>
            </div>
          </div>
        </div>
        <div v-if="!chatSessions.length" class="wf-empty">
          {{ ui.emptySessions }}
        </div>
      </div>
    </aside>

    <section class="chat-page">
      <div class="chat-top">
        <b>{{ ui.title }}</b>
        <div class="scope-pill">{{ scopeText }}</div>
      </div>

      <div ref="messagesRef" class="msgs">
        <div v-if="state.assistantLoading" class="msg-wrap">
          <div class="wf-empty">{{ ui.loading }}</div>
        </div>

        <div v-else-if="state.assistantError && !chatMessages.length" class="msg-wrap">
          <div class="wf-empty">{{ state.assistantError }}</div>
        </div>

        <div v-else-if="!chatMessages.length" class="msg-wrap">
          <div class="wf-empty">
            {{ ui.emptyMessages }}
          </div>
        </div>

        <div v-for="message in chatMessages" v-else :key="message.id" class="msg-wrap">
          <div :class="['msg', message.role]">
            <div v-if="message.role === 'assistant'" class="avatar">V</div>
            <div>
              <div :class="['bubble', { pending: message.pending, error: message.error }]">
                <div
                  v-if="message.role === 'assistant'"
                  class="md-content"
                  v-html="renderAssistantMessage(message)"
                ></div>
                <template v-else>
                  {{ displayMessageText(message) }}
                </template>
              </div>
              <div v-if="message.cites?.length" class="cites">
                <span v-for="cite in message.cites" :key="cite" class="cite">
                  {{ cite }}
                </span>
              </div>
            </div>
          </div>
        </div>
      </div>

      <div class="assistant-composer">
        <div class="composer assistant-composer-card">
          <div class="composer-main assistant-composer-main">
            <button class="assistant-plus" type="button" @click="openSkillModal">
              +
            </button>
            <textarea
              ref="composerInputRef"
              v-model="state.assistantPrompt"
              class="input assistant-input"
              rows="1"
              :placeholder="ui.placeholder"
              @input="syncComposerHeight($event.target)"
              @keydown="handleComposerKeydown"
            ></textarea>
            <button class="btn primary assistant-send" type="button" :disabled="!canSend" @click="handleSubmit">
              {{ sendButtonLabel }}
            </button>
          </div>
        </div>
      </div>
    </section>

    <ResourcePanel page-key="assistant" :empty-text="ui.resourceEmpty" />
  </div>
</template>
