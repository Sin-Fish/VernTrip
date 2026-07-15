<script setup>
import { onMounted, ref } from "vue";
import { adminRequest } from "@/lib/adminApi";
import { formatDateTime } from "@/lib/format";

const loading = ref(true);
const errorMessage = ref("");
const systemInfo = ref(null);
const logs = ref([]);

async function loadSystemData() {
  loading.value = true;
  errorMessage.value = "";

  try {
    const [systemResponse, logsResponse] = await Promise.all([
      adminRequest("/api/admin/system"),
      adminRequest("/api/admin/logs?page=0&size=20"),
    ]);
    systemInfo.value = systemResponse;
    logs.value = logsResponse.items ?? [];
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : "加载系统治理数据失败。";
  } finally {
    loading.value = false;
  }
}

onMounted(() => {
  loadSystemData();
});
</script>

<template>
  <div class="admin-page-grid">
    <section class="admin-panel">
      <div class="admin-panel-head">
        <div>
          <h2>系统配置</h2>
          <p>查看当前支撑管理后台运行的治理配置，只读展示。</p>
        </div>
        <button
          class="admin-btn ghost"
          type="button"
          @click="loadSystemData"
        >
          刷新
        </button>
      </div>

      <div
        v-if="loading"
        class="admin-empty"
      >
        正在加载系统配置...
      </div>
      <div
        v-else-if="errorMessage"
        class="admin-empty error"
      >
        {{ errorMessage }}
      </div>
      <div
        v-else
        class="admin-config-grid"
      >
        <div><span>管理员账号</span><strong>{{ systemInfo.adminUsername }}</strong></div>
        <div><span>工作区根目录</span><strong>{{ systemInfo.workspaceRootDir }}</strong></div>
        <div><span>论文上传目录</span><strong>{{ systemInfo.paperUploadDir }}</strong></div>
        <div><span>AI 服务地址</span><strong>{{ systemInfo.aiBaseUrl }}</strong></div>
        <div><span>AI 超时时间</span><strong>{{ systemInfo.aiTimeoutSeconds }}s</strong></div>
        <div><span>最大文件大小</span><strong>{{ systemInfo.maxFileSize }}</strong></div>
        <div><span>最大请求大小</span><strong>{{ systemInfo.maxRequestSize }}</strong></div>
      </div>
    </section>

    <section class="admin-panel">
      <div class="admin-panel-head">
        <div>
          <h2>操作审计日志</h2>
          <p>记录后台治理动作和控制台访问轨迹。</p>
        </div>
      </div>

      <div
        v-if="!logs.length && !loading"
        class="admin-empty"
      >
        暂无操作审计日志。
      </div>
      <table
        v-else
        class="admin-table"
      >
        <thead>
          <tr>
            <th>操作者</th>
            <th>动作</th>
            <th>目标</th>
            <th>详情</th>
            <th>时间</th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="log in logs"
            :key="log.id"
          >
            <td>{{ log.actor }}</td>
            <td><span class="admin-tag">{{ log.action }}</span></td>
            <td>{{ log.targetPath || log.targetType }}</td>
            <td>{{ log.detail || "-" }}</td>
            <td>{{ formatDateTime(log.createdAt) }}</td>
          </tr>
        </tbody>
      </table>
    </section>
  </div>
</template>
