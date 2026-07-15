<script setup>
import { computed, onMounted, ref, watch } from "vue";
import { useRouter } from "vue-router";
import { adminRequest } from "@/lib/adminApi";
import { formatDateTime } from "@/lib/format";

const router = useRouter();
const loading = ref(true);
const errorMessage = ref("");
const dashboard = ref(null);
const users = ref([]);
const searchKeyword = ref("");
const currentPage = ref(1);
const pageSize = 8;

const filteredUsers = computed(() => {
  const keyword = searchKeyword.value.trim().toLowerCase();
  const orderedUsers = [...users.value].sort((left, right) => {
    const rightActivity = right.assistantSessionCount + right.workbenchRunCount;
    const leftActivity = left.assistantSessionCount + left.workbenchRunCount;
    if (rightActivity !== leftActivity) {
      return rightActivity - leftActivity;
    }
    return left.username.localeCompare(right.username);
  });

  if (!keyword) {
    return orderedUsers;
  }

  return orderedUsers.filter((user) =>
    [user.displayName, user.username, String(user.id)]
      .filter(Boolean)
      .some((value) => value.toLowerCase().includes(keyword)),
  );
});

const totalPages = computed(() => Math.max(1, Math.ceil(filteredUsers.value.length / pageSize)));

const pagedUsers = computed(() => {
  const start = (currentPage.value - 1) * pageSize;
  return filteredUsers.value.slice(start, start + pageSize);
});

const pageSummary = computed(() => {
  if (!filteredUsers.value.length) {
    return "0 / 0";
  }
  const start = (currentPage.value - 1) * pageSize + 1;
  const end = Math.min(filteredUsers.value.length, currentPage.value * pageSize);
  return `${start}-${end} / ${filteredUsers.value.length}`;
});

async function loadDashboard() {
  loading.value = true;
  errorMessage.value = "";

  try {
    const [dashboardData, userData] = await Promise.all([
      adminRequest("/api/admin/dashboard"),
      adminRequest("/api/admin/users"),
    ]);
    dashboard.value = dashboardData;
    users.value = userData ?? [];
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : "加载全控台失败。";
  } finally {
    loading.value = false;
  }
}

function openUserConsole(userId) {
  router.push({ name: "admin-user-storage", params: { userId } });
}

function goToPreviousPage() {
  currentPage.value = Math.max(1, currentPage.value - 1);
}

function goToNextPage() {
  currentPage.value = Math.min(totalPages.value, currentPage.value + 1);
}

watch(searchKeyword, () => {
  currentPage.value = 1;
});

watch(totalPages, (value) => {
  if (currentPage.value > value) {
    currentPage.value = value;
  }
});

onMounted(() => {
  loadDashboard();
});
</script>

<template>
  <div class="admin-page-grid">
    <section class="admin-panel">
      <div class="admin-panel-head">
        <div>
          <h2>全控台</h2>
          <p>查看全局治理指标，以及进入各用户控制台的统一入口。</p>
        </div>
        <button
          class="admin-btn ghost"
          type="button"
          @click="loadDashboard"
        >
          刷新
        </button>
      </div>

      <div
        v-if="loading"
        class="admin-empty"
      >
        正在加载全控台...
      </div>
      <div
        v-else-if="errorMessage"
        class="admin-empty error"
      >
        {{ errorMessage }}
      </div>
      <div
        v-else
        class="admin-stat-grid"
      >
        <article class="admin-stat-card">
          <span>工作空间文件</span>
          <strong>{{ dashboard.workspaceFileCount }}</strong>
        </article>
        <article class="admin-stat-card">
          <span>工作区文件夹</span>
          <strong>{{ dashboard.workspaceFolderCount }}</strong>
        </article>
        <article class="admin-stat-card">
          <span>助理会话</span>
          <strong>{{ dashboard.assistantSessionCount }}</strong>
        </article>
        <article class="admin-stat-card">
          <span>工作台运行</span>
          <strong>{{ dashboard.workbenchRunCount }}</strong>
        </article>
        <article class="admin-stat-card">
          <span>解析任务</span>
          <strong>{{ dashboard.parseTaskCount }}</strong>
        </article>
        <article class="admin-stat-card">
          <span>审计日志</span>
          <strong>{{ dashboard.adminLogCount }}</strong>
        </article>
      </div>
    </section>

    <section class="admin-panel">
      <div class="admin-panel-head wrap">
        <div>
          <h2>用户控制台目录</h2>
         
        </div>
        <div class="admin-directory-tools">
          <label class="admin-search-field">
            <span>搜索用户</span>
            <input
              v-model="searchKeyword"
              type="search"
              placeholder="按名称、账号或 ID 搜索"
            >
          </label>
          <span class="admin-directory-summary">{{ filteredUsers.length }} 个用户</span>
        </div>
      </div>

      <div
        v-if="!loading && !users.length"
        class="admin-empty"
      >
        当前还没有可治理的产品用户。
      </div>
      <div
        v-else-if="!filteredUsers.length"
        class="admin-empty"
      >
        当前搜索条件下没有匹配用户。
      </div>
      <div
        v-else
        class="admin-user-directory"
      >
        <table class="admin-table admin-user-table">
          <thead>
            <tr>
              <th>用户</th>
              <th>ID</th>
              <th>文件</th>
              <th>文件夹</th>
              <th>会话</th>
              <th>运行</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="user in pagedUsers"
              :key="user.id"
            >
              <td>
                <div class="admin-user-row">
                  <strong>{{ user.displayName }}</strong>
                  <span>@{{ user.username }}</span>
                </div>
              </td>
              <td><span class="admin-tag">#{{ user.id }}</span></td>
              <td>{{ user.workspaceFileCount }}</td>
              <td>{{ user.workspaceFolderCount }}</td>
              <td>{{ user.assistantSessionCount }}</td>
              <td>{{ user.workbenchRunCount }}</td>
              <td>
                <button
                  class="admin-btn primary compact"
                  type="button"
                  @click="openUserConsole(user.id)"
                >
                  打开
                </button>
              </td>
            </tr>
          </tbody>
        </table>

        <div class="admin-directory-footer">
          <span class="admin-directory-summary">{{ pageSummary }}</span>
          <div class="admin-directory-pagination">
            <button
              class="admin-btn ghost compact"
              type="button"
              :disabled="currentPage === 1"
              @click="goToPreviousPage"
            >
              上一页
            </button>
            <span>第 {{ currentPage }} / {{ totalPages }} 页</span>
            <button
              class="admin-btn ghost compact"
              type="button"
              :disabled="currentPage === totalPages"
              @click="goToNextPage"
            >
              下一页
            </button>
          </div>
        </div>
      </div>
    </section>

    <section class="admin-panel">
      <div class="admin-panel-head">
        <div>
          <h2>近期活动</h2>
          
        </div>
      </div>

      <div
        v-if="!dashboard?.recentActivities?.length"
        class="admin-empty"
      >
        暂无近期活动记录。
      </div>
      <table
        v-else
        class="admin-table"
      >
        <thead>
          <tr>
            <th>类型</th>
            <th>标题</th>
            <th>详情</th>
            <th>时间</th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="item in dashboard.recentActivities"
            :key="`${item.type}-${item.title}-${item.createdAt}`"
          >
            <td><span class="admin-tag">{{ item.type }}</span></td>
            <td>{{ item.title }}</td>
            <td>{{ item.detail || "-" }}</td>
            <td>{{ formatDateTime(item.createdAt) }}</td>
          </tr>
        </tbody>
      </table>
    </section>
  </div>
</template>
