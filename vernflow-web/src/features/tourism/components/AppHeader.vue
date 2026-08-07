<template>
  <header class="site-header">
    <RouterLink class="logo" :to="{ name: 'travel-home' }">
      <img class="logo-mark" src="/images/logo.jpg" alt="" />
      <div>
        <strong class="font-brush">山水灵</strong>
        <small>VERNTRIP</small>
      </div>
    </RouterLink>

    <nav class="nav-actions" aria-label="游客端导航">
      <RouterLink class="nav-btn" :to="{ name: 'travel-home' }">首页</RouterLink>
      <RouterLink class="nav-btn ai-special" :to="{ name: 'travel-planner' }">
        <span class="brush-icon" aria-hidden="true">✦</span>AI 个性规划
      </RouterLink>
      <RouterLink v-if="!loggedIn" class="nav-btn account-link" :to="{ name: 'login' }">登录</RouterLink>
      <RouterLink v-else-if="role === 'MERCHANT'" class="nav-btn account-link" :to="{ name: 'merchant-home' }">商家端</RouterLink>
      <button v-else class="nav-btn account-link" type="button" @click="logout">退出</button>
    </nav>
  </header>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useUserAuth } from '@/composables/useUserAuth'
import { getUserRole, hasUserToken } from '@/lib/userApi'

const router = useRouter()
const { signOutUser } = useUserAuth()
const loggedIn = ref(hasUserToken())
const role = ref(getUserRole())

async function logout() {
  await signOutUser()
  loggedIn.value = false
  role.value = ''
  await router.replace({ name: 'travel-home' })
}
</script>

<style scoped>
.site-header {
  position: relative;
  z-index: 20;
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 77px;
  padding: 12px 56px;
  border-bottom: 1px solid rgba(119, 115, 107, .12);
  background: rgba(245, 240, 230, .88);
  backdrop-filter: blur(12px);
}
.logo { display: flex; align-items: center; gap: 10px; color: inherit; text-decoration: none; }
.logo-mark { width: 42px; height: 42px; object-fit: contain; mix-blend-mode: multiply; }
.logo > div { display: grid; }
.logo strong { color: var(--ink); font-size: 30px; font-weight: 400; letter-spacing: 2px; line-height: 1; }
.logo small { margin-top: 3px; color: var(--ink-soft); font-size: 7px; letter-spacing: 3px; }
.nav-actions { display: flex; align-items: center; gap: 7px; }
.nav-btn { position: relative; padding: 8px 17px; border: 1px solid transparent; border-radius: 999px; background: transparent; color: var(--ink); cursor: pointer; font-family: 'Noto Serif SC', serif; font-size: 13px; font-weight: 500; letter-spacing: 1px; text-decoration: none; transition: all .25s; white-space: nowrap; }
.nav-btn:hover, .nav-btn.router-link-active { border-color: rgba(49, 91, 90, .32); color: var(--teal); }
.ai-special { padding-right: 20px; border-color: rgba(49, 91, 90, .18); }
.brush-icon { margin-right: 5px; color: var(--cinnabar); font-size: 9px; }
.account-link { margin-left: 5px; border-color: var(--teal); background: var(--teal); color: #f7f1e6; }
.account-link:hover { background: var(--teal-deep); color: #fffaf2; }

@media (max-width: 700px) {
  .site-header { height: 72px; padding: 10px 16px; }
  .logo-mark { width: 36px; height: 36px; }
  .logo strong { font-size: 25px; }
  .nav-btn { padding: 7px 10px; font-size: 11px; }
  .nav-actions .nav-btn:first-child { display: none; }
}
</style>
