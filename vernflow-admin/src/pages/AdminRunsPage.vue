<script setup>
import { computed, onMounted, ref, watch } from "vue";
import { useRoute } from "vue-router";
import { adminRequest } from "@/lib/adminApi";
import { formatDateTime } from "@/lib/format";

const route = useRoute();
const loading = ref(true);
const errorMessage = ref("");
const workbenchRuns = ref([]);
const selectedRunId = ref(null);
const selectedRun = ref(null);
const runDetailLoading = ref(false);

const userId = computed(() => Number(route.params.userId));

async function loadRuns() {
  loading.value = true;
  errorMessage.value = "";

  try {
    const runsPage = await adminRequest(`/api/admin/users/${userId.value}/workbench/runs?page=0&size=20`);
    workbenchRuns.value = runsPage.items ?? [];

    if (!selectedRunId.value && workbenchRuns.value.length) {
      await selectRun(workbenchRuns.value[0].id);
    }
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : "加载用户运行记录失败。";
  } finally {
    loading.value = false;
  }
}

async function selectRun(runId) {
  if (!runId) {
    return;
  }

  selectedRunId.value = runId;
  runDetailLoading.value = true;
  try {
    selectedRun.value = await adminRequest(`/api/admin/users/${userId.value}/workbench/runs/${runId}`);
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : "加载运行详情失败。";
  } finally {
    runDetailLoading.value = false;
  }
}

onMounted(() => {
  loadRuns();
});

watch(
  () => route.params.userId,
  () => {
    selectedRunId.value = null;
    selectedRun.value = null;
    workbenchRuns.value = [];
    loadRuns();
  },
);
</script>

<template>
  <div class="admin-page-grid two-columns">
    <section class="admin-panel">
      <div class="admin-panel-head">
        <div>
          <h2>运行与任务</h2>
          <p>查看当前用户的工作台运行历史与任务上下文。</p>
        </div>
        <button
          class="admin-btn ghost"
          type="button"
          @click="loadRuns"
        >
          刷新
        </button>
      </div>

      <div
        v-if="loading"
        class="admin-empty"
      >
        正在加载运行记录...
      </div>
      <div
        v-else-if="errorMessage && !workbenchRuns.length"
        class="admin-empty error"
      >
        {{ errorMessage }}
      </div>
      <div
        v-else-if="!workbenchRuns.length"
        class="admin-empty"
      >
        当前还没有用户运行记录。
      </div>
      <table
        v-else
        class="admin-table"
      >
        <thead>
          <tr>
            <th>名称</th>
            <th>状态</th>
            <th>范围</th>
            <th>检查模式</th>
            <th>创建时间</th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="item in workbenchRuns"
            :key="item.id"
            class="clickable-row"
            @click="selectRun(item.id)"
          >
            <td>{{ item.name }}</td>
            <td><span class="admin-tag">{{ item.status }}</span></td>
            <td>{{ item.globalScope ? "全局" : `${item.workingFileCount} 个文件` }}</td>
            <td>{{ item.inspectMode }}</td>
            <td>{{ formatDateTime(item.createdAt) }}</td>
          </tr>
        </tbody>
      </table>
    </section>

    <section class="admin-panel">
      <div class="admin-panel-head">
        <div>
          <h2>运行详情</h2>
          <p>查看当前运行的提示词、状态与绑定文件上下文。</p>
        </div>
      </div>

      <div
        v-if="runDetailLoading"
        class="admin-empty"
      >
        正在加载运行详情...
      </div>
      <div
        v-else-if="selectedRun"
        class="admin-detail-stack"
      >
        <div class="admin-meta-grid">
          <div><span>名称</span><strong>{{ selectedRun.name }}</strong></div>
          <div><span>状态</span><strong>{{ selectedRun.status }}</strong></div>
          <div><span>检查模式</span><strong>{{ selectedRun.inspectMode }}</strong></div>
          <div><span>时间</span><strong>{{ selectedRun.time }}</strong></div>
        </div>
        <div>
          <h3>任务提示</h3>
          <p class="admin-text-block">{{ selectedRun.prompt }}</p>
        </div>
        <div>
          <h3>绑定工作文件</h3>
          <div class="admin-chip-row">
            <span
              v-for="file in selectedRun.workingFiles"
              :key="file.path"
              class="admin-chip"
            >
              {{ file.label }}
            </span>
            <span
              v-if="!selectedRun.workingFiles?.length"
              class="admin-empty-inline"
            >
              当前没有绑定工作文件。
            </span>
          </div>
        </div>
      </div>
      <div
        v-else
        class="admin-empty"
      >
        请选择一条运行记录查看详情。
      </div>
    </section>
  </div>
</template>
