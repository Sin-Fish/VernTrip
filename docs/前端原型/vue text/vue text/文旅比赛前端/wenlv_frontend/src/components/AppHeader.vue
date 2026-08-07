<template>
  <header class="site-header">
    <div class="logo">
      <img class="logo-mark" src="/images/logo.jpg" alt="山水灵" />
      <div class="logo-text font-brush">山水灵</div>
    </div>

    <nav class="nav-actions">
      <template v-for="item in navItems" :key="item.label">
        <router-link
          v-if="item.to"
          :to="item.to"
          class="nav-btn"
          :class="{ 'ai-special': item.icon }"
        >
          <span v-if="item.icon" class="brush-icon" aria-hidden="true">
            <svg viewBox="0 0 18 18" class="brush-svg"><path d="M3 15 Q2 9 7 5 L15 2 L14 4 Q9 7 8 13 Q8.5 9 12 7 L14 6 Q11 11 5 15 Z" fill="currentColor"/></svg>
          </span>
          {{ item.label }}
        </router-link>
        <a
          v-else
          class="nav-btn"
          :class="{ 'ai-special': item.icon }"
          href="#"
        >
          <span v-if="item.icon" class="brush-icon" aria-hidden="true">
            <svg viewBox="0 0 18 18" class="brush-svg"><path d="M3 15 Q2 9 7 5 L15 2 L14 4 Q9 7 8 13 Q8.5 9 12 7 L14 6 Q11 11 5 15 Z" fill="currentColor"/></svg>
          </span>
          {{ item.label }}
        </a>
      </template>
    </nav>
  </header>
</template>

<script setup>
const navItems = [
  { label: '登录', to: '/login' },
  { label: 'AI助手', to: '/ai-assistant', icon: true },
  { label: '个性规划', to: '/plan' },
  { label: '营销服务', to: '/marketing' },
]
</script>

<style scoped>
.site-header{
  display:flex;
  align-items:center;
  justify-content:space-between;
  padding:18px 56px;
  border-bottom:1px solid rgba(119,115,107,.12);
  background:rgba(245,240,230,.85);
  backdrop-filter:blur(8px);
}

.logo{
  display:flex;
  align-items:center;
  gap:12px;
}
.logo-mark{
  width:40px; height:40px;
  object-fit:contain;
}
.logo-text{
  font-size:34px;
  color:var(--ink);
  letter-spacing:2px;
}

.nav-actions{
  display:flex;
  align-items:center;
  gap:6px;
}

/* ---- 导航按钮 ---- */
.nav-btn{
  position:relative;
  padding:7px 18px;
  border-radius:3px;
  font-family:'Noto Serif SC', serif;
  font-size:14.5px;
  font-weight:600;
  letter-spacing:1.5px;
  text-decoration:none;
  cursor:pointer;
  border:1px solid rgba(119,115,107,.22);
  color:var(--ink);
  background:transparent;
  transition:all .3s;
  white-space:nowrap;
}
.nav-btn:hover{
  background:rgba(49,91,90,.06);
  border-color:var(--teal);
  color:var(--teal);
}

/* ---- AI 按钮：黛青重点 ---- */
.ai-special {
  border-color:rgba(119,115,107,.22);
  color:var(--ink);
  padding-right:24px;
}
.ai-special:hover{
  background:var(--teal);
  border-color:var(--teal);
  color:#F5F0E6;
}
.brush-icon {
  position:absolute;
  top:-3px;
  right:-1px;
  width:13px;
  height:13px;
  display:flex;
  color:var(--ink);
  transition:color .3s;
}
.ai-special:hover .brush-icon {
  color:#F5F0E6;
}
.brush-svg {
  width:100%;
  height:100%;
}

@media (max-width:760px){
  .site-header{ padding:14px 20px; flex-wrap:wrap; gap:12px; }
  .nav-actions{ flex-wrap:wrap; gap:6px; }
  .nav-btn{ padding:6px 14px; font-size:13px; letter-spacing:1px; }
  .ai-special { padding-right:20px; }
}
</style>
