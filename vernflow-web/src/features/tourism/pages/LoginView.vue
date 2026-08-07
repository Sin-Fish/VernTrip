<template>
  <div class="login-page">
    <div class="login-card">
      <h2 class="login-title font-brush">欢迎回来</h2>
      <p class="login-sub">登录你的山水灵账号</p>

      <form class="login-form" @submit.prevent="handleLogin">
        <div class="field">
          <label>账号</label>
          <input
            v-model.trim="username"
            type="text"
            placeholder="请输入手机号或用户名"
            autocomplete="username"
          />
        </div>
        <div class="field">
          <label>密码</label>
          <input
            v-model.trim="password"
            type="password"
            placeholder="请输入密码"
            autocomplete="current-password"
          />
        </div>
        <button type="submit" class="login-btn">登 录</button>
      </form>

      <p class="login-extra">
        <a href="#">忘记密码？</a>
        <span>还没有账号？<a href="#">立即注册</a></span>
      </p>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'

const router = useRouter()
const username = ref('')
const password = ref('')

function handleLogin() {
  if (!username.value || !password.value) {
    alert('请输入账号和密码')
    return
  }
  // 占位登录逻辑 — 后续接入后端 API 时替换此处
  alert(`欢迎回来，${username.value}！`)
  router.push('/')
}
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background:
    linear-gradient(180deg, rgba(242, 232, 213, .5) 0%, rgba(242, 232, 213, .3) 100%),
    url('/images/登录页面.jpg') center / cover no-repeat;
}

/* ---- 表单卡片 ---- */
.login-card {
  width: 420px;
  max-width: 92vw;
  padding: 48px 44px 38px;
  border-radius: 18px;
  background: rgba(255, 255, 255, .62);
  backdrop-filter: blur(12px);
  border: 1px solid transparent;
  box-shadow: 0 20px 50px -20px rgba(44, 38, 32, .25);
}

.login-title {
  text-align: center;
  font-size: 38px;
  color: var(--ink);
  letter-spacing: 3px;
  margin-bottom: 6px;
}

.login-sub {
  text-align: center;
  font-family: 'Noto Sans SC', sans-serif;
  font-size: 13px;
  color: var(--ink-soft);
  letter-spacing: 1px;
  margin-bottom: 34px;
}

/* ---- 表单字段 ---- */
.field {
  margin-bottom: 22px;
}
.field label {
  display: block;
  font-family: 'Noto Sans SC', sans-serif;
  font-size: 13px;
  color: var(--ink-soft);
  letter-spacing: 1px;
  margin-bottom: 8px;
}
.field input {
  width: 100%;
  padding: 13px 16px;
  border-radius: 10px;
  border: 1px solid rgba(44, 38, 32, .18);
  background: rgba(255, 255, 255, .7);
  font-family: 'Noto Sans SC', sans-serif;
  font-size: 14px;
  color: var(--ink);
  outline: none;
  transition: border-color .2s;
}
.field input::placeholder {
  color: rgba(44, 38, 32, .35);
}
.field input:focus {
  border-color: var(--terracotta);
}

/* ---- 登录按钮 ---- */
.login-btn {
  width: 100%;
  margin-top: 8px;
  padding: 14px 0;
  border-radius: 10px;
  border: none;
  background: var(--terracotta);
  color: #fefaf5;
  font-family: 'Noto Sans SC', sans-serif;
  font-size: 15px;
  letter-spacing: 3px;
  cursor: pointer;
  transition: background .2s;
}
.login-btn:hover {
  background: var(--terracotta-deep);
}

/* ---- 底部链接 ---- */
.login-extra {
  display: flex;
  justify-content: space-between;
  margin-top: 26px;
  font-family: 'Noto Sans SC', sans-serif;
  font-size: 12px;
}
.login-extra a {
  color: var(--ink-soft);
  text-decoration: none;
  letter-spacing: .5px;
}
.login-extra a:hover {
  color: var(--terracotta);
}
.login-extra span {
  color: var(--ink-soft);
  letter-spacing: .5px;
}

/* 欢迎提示 toast 样式 */
@media (max-width: 500px) {
  .login-card {
    padding: 34px 24px 28px;
  }
}
</style>
