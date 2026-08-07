<script setup>
import { onMounted, reactive, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { useUserAuth } from "@/composables/useUserAuth";

const route = useRoute();
const router = useRouter();
const { authState, signInUser, clearAuthError } = useUserAuth();

const form = reactive({ username: "", password: "" });
const errorMessage = ref("");

const demoAccounts = [
  { role: "游客端", username: "demo", password: "demo123", note: "规划路线、咨询 AI 向导" },
  { role: "商家端", username: "merchant", password: "merchant123", note: "管理文旅资源与经营内容" },
];

function landingFor(role) {
  if (role === "MERCHANT") return { name: "merchant-home" };
  if (role === "INTERNAL") return { name: "home" };
  return { name: "travel-home" };
}

function useAccount(account) {
  form.username = account.username;
  form.password = account.password;
  errorMessage.value = "";
}

async function submitLogin() {
  errorMessage.value = "";
  try {
    const profile = await signInUser(form.username.trim(), form.password);
    const fallback = landingFor(profile?.role);
    const requested = typeof route.query.redirect === "string" ? route.query.redirect : "";
    if (!requested) {
      await router.replace(fallback);
      return;
    }

    const resolved = router.resolve(requested);
    const requiredRole = resolved.meta.requiredRole;
    if (requiredRole && requiredRole !== profile?.role) {
      await router.replace(fallback);
      return;
    }
    await router.replace(requested);
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : "登录失败，请稍后重试。";
  }
}

onMounted(() => {
  errorMessage.value = "";
  clearAuthError();
});
</script>

<template>
  <main class="role-login-page">
    <RouterLink class="login-brand" :to="{ name: 'travel-home' }">
      <img src="/images/logo.jpg" alt="" />
      <span><strong>山水灵</strong><small>VERNTRIP</small></span>
    </RouterLink>

    <section class="login-story">
      <span class="story-index">01 / 连接旅程</span>
      <h1>一处入口，<br />进入两种文旅视角。</h1>
      <p>游客从兴趣出发寻找旅程，商家从资源出发经营体验。系统会根据账号身份进入对应工作台。</p>
      <div class="ink-landscape" aria-hidden="true"><i></i><i></i><i></i></div>
    </section>

    <section class="login-panel">
      <div class="login-heading">
        <span>WELCOME BACK</span>
        <h2>登录山水灵</h2>
        <p>选择演示身份，或输入已有账号。</p>
      </div>

      <div class="account-options">
        <button v-for="account in demoAccounts" :key="account.username" type="button" @click="useAccount(account)">
          <span>{{ account.role }}</span>
          <strong>{{ account.username }}</strong>
          <small>{{ account.note }}</small>
        </button>
      </div>

      <form class="role-login-form" @submit.prevent="submitLogin">
        <label>
          <span>账号</span>
          <input v-model="form.username" type="text" autocomplete="username" placeholder="请输入用户名" />
        </label>
        <label>
          <span>密码</span>
          <input v-model="form.password" type="password" autocomplete="current-password" placeholder="请输入密码" />
        </label>
        <p v-if="errorMessage" class="login-error">{{ errorMessage }}</p>
        <button class="login-submit" type="submit" :disabled="authState.loading || !form.username || !form.password">
          {{ authState.loading ? "正在登录…" : "进入对应工作台" }} <span>→</span>
        </button>
      </form>

      <p class="account-hint">游客：demo / demo123　商家：merchant / merchant123</p>
    </section>
  </main>
</template>

<style scoped>
.role-login-page {
  --login-ink: #26332e;
  --login-paper: #eee7d8;
  display: grid;
  grid-template-columns: minmax(0, 1.25fr) minmax(430px, .75fr);
  min-height: 100vh;
  overflow: hidden;
  background: var(--login-paper);
  color: var(--login-ink);
  font-family: "Noto Serif SC", "STSong", serif;
}

.login-brand {
  position: fixed;
  top: 28px;
  left: 42px;
  z-index: 3;
  display: flex;
  align-items: center;
  gap: 10px;
  color: inherit;
  text-decoration: none;
}

.login-brand img { width: 38px; height: 38px; object-fit: cover; mix-blend-mode: multiply; }
.login-brand span { display: grid; }
.login-brand strong { font-size: 22px; letter-spacing: 3px; }
.login-brand small { color: #6f776f; font-size: 8px; letter-spacing: 3px; }

.login-story {
  position: relative;
  display: flex;
  justify-content: center;
  flex-direction: column;
  padding: 120px clamp(48px, 8vw, 130px);
  overflow: hidden;
  background:
    linear-gradient(110deg, rgba(238, 231, 216, .76), rgba(238, 231, 216, .12)),
    url('/images/hero-full.jpg') center / cover;
}

.story-index { color: #315b5a; font-size: 10px; letter-spacing: 4px; }
.login-story h1 { margin: 18px 0 24px; font-size: clamp(42px, 5.4vw, 76px); font-weight: 600; letter-spacing: 4px; line-height: 1.28; }
.login-story p { max-width: 510px; color: #5f665f; font-size: 14px; letter-spacing: .7px; line-height: 2; }

.ink-landscape { position: absolute; right: 3vw; bottom: 4vh; width: 260px; height: 130px; opacity: .16; }
.ink-landscape i { position: absolute; bottom: 0; width: 130px; height: 110px; background: #315b5a; clip-path: polygon(50% 0, 100% 100%, 0 100%); }
.ink-landscape i:nth-child(2) { right: 0; height: 80px; }
.ink-landscape i:nth-child(3) { left: 65px; width: 150px; height: 125px; background: #b74736; opacity: .45; }

.login-panel {
  position: relative;
  z-index: 2;
  display: flex;
  justify-content: center;
  flex-direction: column;
  padding: 60px clamp(38px, 5vw, 76px);
  border-left: 1px solid rgba(38, 51, 46, .1);
  background: rgba(250, 247, 239, .9);
  box-shadow: -25px 0 80px rgba(38, 51, 46, .08);
  backdrop-filter: blur(18px);
}

.login-heading > span { color: #b74736; font-size: 9px; letter-spacing: 4px; }
.login-heading h2 { margin: 8px 0 5px; font-size: 30px; letter-spacing: 2px; }
.login-heading p { color: #77736b; font-size: 12px; }

.account-options { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; margin: 24px 0; }
.account-options button { display: grid; gap: 4px; padding: 13px; border: 1px solid rgba(38, 51, 46, .12); border-radius: 12px; background: #f7f2e8; color: inherit; cursor: pointer; text-align: left; transition: .2s; }
.account-options button:hover { border-color: #315b5a; background: #edf1e9; transform: translateY(-2px); }
.account-options span { color: #315b5a; font-size: 10px; letter-spacing: 1px; }
.account-options strong { font-size: 14px; }
.account-options small { color: #77736b; font-size: 9px; line-height: 1.5; }

.role-login-form { display: grid; gap: 15px; }
.role-login-form label { display: grid; gap: 7px; }
.role-login-form label span { color: #646b65; font-size: 11px; letter-spacing: 1px; }
.role-login-form input { width: 100%; padding: 12px 13px; border: 1px solid rgba(38, 51, 46, .14); border-radius: 10px; outline: none; background: rgba(255, 255, 255, .66); color: inherit; font-family: inherit; }
.role-login-form input:focus { border-color: #315b5a; box-shadow: 0 0 0 3px rgba(49, 91, 90, .07); }

.login-error { color: #9e3d32; font-size: 11px; }
.login-submit { display: flex; justify-content: space-between; padding: 13px 17px; border: 0; border-radius: 10px; background: #315b5a; color: #fffaf1; cursor: pointer; font-family: inherit; letter-spacing: 1px; }
.login-submit:disabled { cursor: not-allowed; opacity: .48; }
.account-hint { margin-top: 13px; color: #999287; font-family: "Noto Sans SC", sans-serif; font-size: 9px; text-align: center; }

@media (max-width: 900px) {
  .role-login-page { grid-template-columns: 1fr; overflow: auto; }
  .login-story { min-height: 360px; padding: 110px 28px 50px; }
  .login-story h1 { font-size: 42px; }
  .login-panel { padding: 40px 28px 54px; border-left: 0; }
  .login-brand { top: 22px; left: 24px; }
}

@media (max-width: 520px) {
  .account-options { grid-template-columns: 1fr; }
  .login-story { min-height: 320px; }
  .login-story h1 { font-size: 34px; }
}
</style>
