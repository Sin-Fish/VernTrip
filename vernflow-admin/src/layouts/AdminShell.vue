<script setup>
import { computed, onMounted, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { adminRequest, getAdminProfile, logoutAdmin } from "@/lib/adminApi";

const route = useRoute();
const router = useRouter();
const profile = ref(null);
const selectedUser = ref(null);

const selectedUserId = computed(() => {
  const rawValue = route.params.userId;
  const normalized = Array.isArray(rawValue) ? rawValue[0] : rawValue;
  const numeric = Number(normalized);
  return Number.isFinite(numeric) && numeric > 0 ? numeric : null;
});

const inUserConsole = computed(() => selectedUserId.value !== null);

const menus = computed(() => {
  if (!inUserConsole.value) {
    return [
      { label: "用户控制台", to: { name: "admin-dashboard" }, icon: "U" },
      { label: "系统治理", to: { name: "admin-system" }, icon: "G" },
    ];
  }

  return [
    { label: "返回用户目录", to: { name: "admin-dashboard" }, icon: "<" },
    { label: "内部存储", to: { name: "admin-user-storage", params: { userId: selectedUserId.value } }, icon: "S" },
    { label: "助理会话", to: { name: "admin-user-sessions", params: { userId: selectedUserId.value } }, icon: "A" },
    { label: "运行与任务", to: { name: "admin-user-runs", params: { userId: selectedUserId.value } }, icon: "R" },
  ];
});

const pageTitle = computed(() => {
  if (inUserConsole.value && selectedUser.value) {
    return `${selectedUser.value.displayName} 控制台`;
  }
  return route.meta.title ?? "VernFlow 管理员";
});

const pageDescription = computed(() => {
  if (inUserConsole.value && selectedUser.value) {
    return `当前正在治理 @${selectedUser.value.username} 的用户空间。`;
  }
  return " ";
});

async function loadProfile() {
  try {
    profile.value = await getAdminProfile();
  } catch (error) {
    console.error("加载管理员信息失败", error);
  }
}

async function loadSelectedUser() {
  if (!selectedUserId.value) {
    selectedUser.value = null;
    return;
  }

  try {
    selectedUser.value = await adminRequest(`/api/admin/users/${selectedUserId.value}`);
  } catch (error) {
    console.error("加载当前用户失败", error);
    selectedUser.value = null;
    router.replace({ name: "admin-dashboard" });
  }
}

async function handleLogout() {
  await logoutAdmin();
  router.replace({ name: "admin-login" });
}

onMounted(() => {
  loadProfile();
});

watch(
  () => route.params.userId,
  () => {
    loadSelectedUser();
  },
  { immediate: true },
);
</script>

<template>
  <div class="admin-app">
    <aside class="admin-sidebar">
      <div class="admin-brand">
        <div class="admin-brand-mark">V</div>
        <div>
          <strong>VernFlow 管理员</strong>
          <span>{{ "用户治理控制台"  }}</span>
        </div>
      </div>

      <div
        v-if="inUserConsole && selectedUser"
        class="admin-user-context"
      >
        <span class="admin-user-context-label">当前用户</span>
        <strong>{{ selectedUser.displayName }}</strong>
        <small>@{{ selectedUser.username }}</small>
      </div>

      <nav class="admin-menu">
        <RouterLink
          v-for="item in menus"
          :key="`${item.label}-${item.to.name}`"
          :to="item.to"
          class="admin-menu-item"
          active-class="active"
        >
          <span class="admin-menu-icon">{{ item.icon }}</span>
          <span>{{ item.label }}</span>
        </RouterLink>
      </nav>
    </aside>

    <main class="admin-main">
      <header class="admin-topbar">
        <div>
          <h1>{{ pageTitle }}</h1>
          <p>{{ pageDescription }}</p>
        </div>
        <div class="admin-topbar-actions">
          <div
            v-if="inUserConsole && selectedUser"
            class="admin-profile admin-profile-context"
          >
            <strong>{{ selectedUser.workspaceFileCount }} 文件 / {{ selectedUser.workspaceFolderCount }} 文件夹</strong>
            <span>{{ selectedUser.assistantSessionCount }} 会话 / {{ selectedUser.workbenchRunCount }} 运行</span>
          </div>
          <div class="admin-profile">
            <strong>{{ profile?.username ?? "admin" }}</strong>
            <span>{{ profile?.expiresAt ? "会话活跃" : "加载会话中" }}</span>
          </div>
          <button
            class="admin-btn ghost"
            type="button"
            @click="handleLogout"
          >
            退出登录
          </button>
        </div>
      </header>

      <section class="admin-content">
        <RouterView />
      </section>
    </main>
  </div>
</template>
