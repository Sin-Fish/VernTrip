<script setup>
import { computed, onMounted, ref, watch } from "vue";
import { useRoute } from "vue-router";
import { adminRequest } from "@/lib/adminApi";
import { formatDateTime } from "@/lib/format";

const route = useRoute();
const loading = ref(true);
const detailLoading = ref(false);
const errorMessage = ref("");
const sessions = ref([]);
const selectedSessionId = ref(null);
const selectedSession = ref(null);

const userId = computed(() => Number(route.params.userId));

async function loadSessions() {
  loading.value = true;
  errorMessage.value = "";

  try {
    const data = await adminRequest(`/api/admin/users/${userId.value}/sessions?page=0&size=20`);
    sessions.value = data.items ?? [];
    if (!selectedSessionId.value && sessions.value.length) {
      await selectSession(sessions.value[0].id);
    }
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : "加载会话列表失败。";
  } finally {
    loading.value = false;
  }
}

async function selectSession(sessionId) {
  if (!sessionId) {
    return;
  }

  selectedSessionId.value = sessionId;
  detailLoading.value = true;
  try {
    selectedSession.value = await adminRequest(`/api/admin/users/${userId.value}/sessions/${sessionId}`);
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : "加载会话详情失败。";
  } finally {
    detailLoading.value = false;
  }
}

onMounted(() => {
  loadSessions();
});

watch(
  () => route.params.userId,
  () => {
    selectedSessionId.value = null;
    selectedSession.value = null;
    sessions.value = [];
    loadSessions();
  },
);
</script>

<template>
  <div class="admin-split-grid">
    <section class="admin-panel">
      <div class="admin-panel-head">
        <div>
          <h2>助理会话</h2>
          <p>查看当前用户保存的提问、范围绑定和引用回答。</p>
        </div>
        <button
          class="admin-btn ghost"
          type="button"
          @click="loadSessions"
        >
          刷新
        </button>
      </div>

      <div
        v-if="loading"
        class="admin-empty"
      >
        正在加载会话记录...
      </div>
      <div
        v-else-if="errorMessage && !sessions.length"
        class="admin-empty error"
      >
        {{ errorMessage }}
      </div>
      <div
        v-else-if="!sessions.length"
        class="admin-empty"
      >
        当前还没有保存的助理会话。
      </div>
      <div
        v-else
        class="admin-list"
      >
        <button
          v-for="item in sessions"
          :key="item.id"
          :class="['admin-list-item', { active: selectedSessionId === item.id }]"
          type="button"
          @click="selectSession(item.id)"
        >
          <strong>{{ item.title }}</strong>
          <span>{{ item.summary }}</span>
          <small>{{ item.globalScope ? "全局工作区" : `${item.workingFileCount} 个工作文件` }}</small>
        </button>
      </div>
    </section>

    <section class="admin-panel">
      <div class="admin-panel-head">
        <div>
          <h2>会话详情</h2>
          <p>查看当前会话的范围、工作文件和消息时间线。</p>
        </div>
      </div>

      <div
        v-if="detailLoading"
        class="admin-empty"
      >
        正在加载会话详情...
      </div>
      <div
        v-else-if="selectedSession"
        class="admin-detail-stack"
      >
        <div class="admin-meta-grid">
          <div><span>标题</span><strong>{{ selectedSession.title }}</strong></div>
          <div><span>摘要</span><strong>{{ selectedSession.summary }}</strong></div>
          <div><span>范围</span><strong>{{ selectedSession.globalScope ? "全局工作区" : "仅工作文件" }}</strong></div>
          <div><span>更新时间</span><strong>{{ formatDateTime(selectedSession.updatedAt) }}</strong></div>
        </div>

        <div>
          <h3>工作文件</h3>
          <div class="admin-chip-row">
            <span
              v-for="file in selectedSession.workingFiles"
              :key="file.path"
              class="admin-chip"
            >
              {{ file.label }}
            </span>
            <span
              v-if="!selectedSession.workingFiles?.length"
              class="admin-empty-inline"
            >
              当前没有绑定工作文件。
            </span>
          </div>
        </div>

        <div>
          <h3>消息记录</h3>
          <div class="admin-message-list">
            <article
              v-for="message in selectedSession.messages"
              :key="message.id"
              class="admin-message-card"
            >
              <div class="admin-message-head">
                <strong>{{ message.role }}</strong>
                <span>{{ formatDateTime(message.createdAt) }}</span>
              </div>
              <p>{{ message.text }}</p>
              <div
                v-if="message.cites?.length"
                class="admin-chip-row"
              >
                <span
                  v-for="cite in message.cites"
                  :key="cite"
                  class="admin-chip"
                >
                  {{ cite }}
                </span>
              </div>
            </article>
          </div>
        </div>
      </div>
      <div
        v-else
        class="admin-empty"
      >
        请选择一条会话查看详情。
      </div>
    </section>
  </div>
</template>
