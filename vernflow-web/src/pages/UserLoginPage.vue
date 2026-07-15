<script setup>
import { onMounted, reactive, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { useUserAuth } from "@/composables/useUserAuth";

const ui = {
  username: "\u7528\u6237\u540d",
  password: "\u5bc6\u7801",
  loginFailed: "\u767b\u5f55\u5931\u8d25\uff0c\u8bf7\u7a0d\u540e\u91cd\u8bd5\u3002",
  signingIn: "\u767b\u5f55\u4e2d...",
  enterWorkspace: "\u8fdb\u5165\u5de5\u4f5c\u533a",
};

const route = useRoute();
const router = useRouter();
const { authState, signInUser, clearAuthError } = useUserAuth();

const form = reactive({
  username: "",
  password: "",
});

const errorMessage = ref("");

async function submitLogin() {
  errorMessage.value = "";

  try {
    await signInUser(form.username.trim(), form.password);
    const redirect = typeof route.query.redirect === "string" && route.query.redirect
      ? route.query.redirect
      : "/";
    router.replace(redirect);
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : ui.loginFailed;
  }
}

onMounted(() => {
  errorMessage.value = "";
  clearAuthError();
});
</script>

<template>
  <div class="user-login">
    <div class="user-login-card">
      <div class="user-login-head">
        <div class="user-login-mark">V</div>
        <div>
          <h1>VernFlow</h1>
        </div>
      </div>

      <form
        class="user-login-form"
        @submit.prevent="submitLogin"
      >
        <label>
          <span>{{ ui.username }}</span>
          <input
            v-model="form.username"
            type="text"
            autocomplete="username"
            placeholder="请输入用户名"
          >
        </label>

        <label>
          <span>{{ ui.password }}</span>
          <input
            v-model="form.password"
            type="password"
            autocomplete="current-password"
            placeholder="请输入密码"
          >
        </label>

        <div v-if="errorMessage" class="user-login-error">
          {{ errorMessage }}
        </div>

        <button
          class="user-login-submit"
          type="submit"
          :disabled="authState.loading"
        >
          {{ authState.loading ? ui.signingIn : ui.enterWorkspace }}
        </button>
      </form>
    </div>
  </div>
</template>
