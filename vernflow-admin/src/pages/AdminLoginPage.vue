<script setup>
import { reactive, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { loginAdmin } from "@/lib/adminApi";

const route = useRoute();
const router = useRouter();

const form = reactive({
  username: "admin",
  password: "admin123",
});
const loading = ref(false);
const errorMessage = ref("");

async function submit() {
  if (!form.username.trim() || !form.password.trim() || loading.value) {
    return;
  }

  loading.value = true;
  errorMessage.value = "";

  try {
    await loginAdmin(form.username.trim(), form.password);
    router.replace(typeof route.query.redirect === "string" ? route.query.redirect : "/dashboard");
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : "登录失败。";
  } finally {
    loading.value = false;
  }
}
</script>

<template>
  <div class="admin-login">
    <div class="admin-login-card">
      <div class="admin-login-head">
        <div class="admin-brand-mark large">V</div>
        <div>
          <h1>VernFlow 管理员</h1>
          <p>面向治理场景的独立后台控制台。</p>
        </div>
      </div>

      <form
        class="admin-login-form"
        @submit.prevent="submit"
      >
        <label>
          <span>用户名</span>
          <input
            v-model="form.username"
            type="text"
            autocomplete="username"
          />
        </label>

        <label>
          <span>密码</span>
          <input
            v-model="form.password"
            type="password"
            autocomplete="current-password"
          />
        </label>

        <p
          v-if="errorMessage"
          class="admin-error"
        >
          {{ errorMessage }}
        </p>

        <button
          class="admin-btn primary full"
          type="submit"
          :disabled="loading"
        >
          {{ loading ? "登录中..." : "登录" }}
        </button>
      </form>

      <div class="admin-login-tip">
        默认演示账号来自后端环境变量：<code>ADMIN_USERNAME</code> / <code>ADMIN_PASSWORD</code>。
      </div>
    </div>
  </div>
</template>
