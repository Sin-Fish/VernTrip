<script setup>
import { computed, onMounted, ref, watch } from "vue";
import { useRoute } from "vue-router";
import { adminRequest } from "@/lib/adminApi";
import { formatBytes, formatDateTime } from "@/lib/format";

const route = useRoute();
const currentPath = ref("/");
const parentPath = ref("/");
const items = ref([]);
const loading = ref(true);
const errorMessage = ref("");
const creating = ref(false);
const deletingPath = ref("");
const newFolderName = ref("");

const userId = computed(() => Number(route.params.userId));

const breadcrumbs = computed(() => {
  if (currentPath.value === "/") {
    return [{ label: "工作区", path: "/" }];
  }

  const parts = currentPath.value.split("/").filter(Boolean);
  return [
    { label: "工作区", path: "/" },
    ...parts.map((part, index) => ({
      label: part,
      path: `/${parts.slice(0, index + 1).join("/")}`,
    })),
  ];
});

async function loadStorage(path = currentPath.value) {
  loading.value = true;
  errorMessage.value = "";

  try {
    const data = await adminRequest(`/api/admin/users/${userId.value}/storage?path=${encodeURIComponent(path)}`);
    currentPath.value = data.currentPath;
    parentPath.value = data.parentPath;
    items.value = data.items ?? [];
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : "加载内部存储失败。";
  } finally {
    loading.value = false;
  }
}

async function createFolder() {
  if (!newFolderName.value.trim() || creating.value) {
    return;
  }

  creating.value = true;
  try {
    const data = await adminRequest(`/api/admin/users/${userId.value}/storage/folders`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        parentPath: currentPath.value,
        name: newFolderName.value.trim(),
      }),
    });
    currentPath.value = data.currentPath;
    parentPath.value = data.parentPath;
    items.value = data.items ?? [];
    newFolderName.value = "";
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : "创建文件夹失败。";
  } finally {
    creating.value = false;
  }
}

async function deleteItem(path) {
  if (!path || deletingPath.value) {
    return;
  }

  deletingPath.value = path;
  try {
    const data = await adminRequest(`/api/admin/users/${userId.value}/storage`, {
      method: "DELETE",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        paths: [path],
      }),
    });
    currentPath.value = data.currentPath;
    parentPath.value = data.parentPath;
    items.value = data.items ?? [];
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : "删除存储项失败。";
  } finally {
    deletingPath.value = "";
  }
}

onMounted(() => {
  loadStorage("/");
});

watch(
  () => route.params.userId,
  () => {
    currentPath.value = "/";
    parentPath.value = "/";
    items.value = [];
    loadStorage("/");
  },
);
</script>

<template>
  <section class="admin-panel">
    <div class="admin-panel-head wrap">
      <div>
        <h2>内部存储</h2>
        <p>以隔离治理视角管理当前用户的工作区存储内容。</p>
      </div>
      <div class="admin-inline-actions">
        <button
          class="admin-btn ghost"
          type="button"
          :disabled="currentPath === '/'"
          @click="loadStorage(parentPath)"
        >
          返回上级
        </button>
        <button
          class="admin-btn ghost"
          type="button"
          @click="loadStorage(currentPath)"
        >
          刷新
        </button>
      </div>
    </div>

    <div class="admin-breadcrumbs">
      <button
        v-for="crumb in breadcrumbs"
        :key="crumb.path"
        class="admin-breadcrumb"
        type="button"
        @click="loadStorage(crumb.path)"
      >
        {{ crumb.label }}
      </button>
    </div>

    <div class="admin-create-row">
      <input
        v-model="newFolderName"
        type="text"
        placeholder="新建治理目录"
      />
      <button
        class="admin-btn primary"
        type="button"
        :disabled="creating"
        @click="createFolder"
      >
        {{ creating ? "创建中..." : "新建文件夹" }}
      </button>
    </div>

    <div
      v-if="loading"
      class="admin-empty"
    >
      正在加载存储项...
    </div>
    <div
      v-else-if="errorMessage"
      class="admin-empty error"
    >
      {{ errorMessage }}
    </div>
    <div
      v-else-if="!items.length"
      class="admin-empty"
    >
      当前目录为空。
    </div>
    <table
      v-else
      class="admin-table"
    >
      <thead>
        <tr>
          <th>名称</th>
          <th>类型</th>
          <th>大小</th>
          <th>更新时间</th>
          <th>操作</th>
        </tr>
      </thead>
      <tbody>
        <tr
          v-for="item in items"
          :key="item.path"
        >
          <td>
            <button
              v-if="item.kind === 'folder'"
              class="admin-link-button"
              type="button"
              @click="loadStorage(item.path)"
            >
              {{ item.name }}
            </button>
            <span v-else>{{ item.name }}</span>
          </td>
          <td>{{ item.kind }}</td>
          <td>{{ formatBytes(item.size) }}</td>
          <td>{{ formatDateTime(item.modifiedAt) }}</td>
          <td>
            <button
              class="admin-btn danger small"
              type="button"
              :disabled="deletingPath === item.path"
              @click="deleteItem(item.path)"
            >
              {{ deletingPath === item.path ? "删除中..." : "删除" }}
            </button>
          </td>
        </tr>
      </tbody>
    </table>
  </section>
</template>
