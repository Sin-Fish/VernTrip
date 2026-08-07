<template>
  <div class="marketing-page">
    <!-- 山水装饰背景 -->
    <div class="landscape-bg">
      <div class="mountain m1"></div>
      <div class="mountain m2"></div>
      <div class="mountain m3"></div>
      <div class="cloud c1"></div>
      <div class="cloud c2"></div>
      <div class="cloud c3"></div>
    </div>

    <!-- 顶部栏 -->
    <header class="marketing-topbar">
      <button class="back-btn" @click="goHome">← 返回首页</button>
      <h2 class="marketing-title font-brush">营销服务</h2>
      <span class="marketing-sub">山水灵 · 助力你的文旅品牌成长</span>
    </header>

    <!-- 服务卡片区域 -->
    <div class="marketing-body">
      <div class="service-grid">
        <div
          v-for="svc in services"
          :key="svc.title"
          class="service-card"
          @click="openService(svc)"
        >
          <div class="card-icon-wrap">
            <img class="card-icon" :src="svc.icon" :alt="svc.title" />
          </div>
          <h4 class="card-title">{{ svc.title }}</h4>
          <p class="card-desc">{{ svc.desc }}</p>
          <span class="card-arrow">了解详情 →</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router'

const router = useRouter()

const services = [
  {
    title: '内容创作',
    desc: '专业文旅文案与视觉内容制作，让你的景点故事打动人心',
    icon: '/images/icon-content.jpg',
  },
  {
    title: '社交媒体推广',
    desc: '小红书、抖音、微信全平台投放，精准触达目标游客',
    icon: '/images/icon-social.jpg',
  },
  {
    title: '品牌包装',
    desc: '从LOGO到VI体系，打造独具江南韵味的文旅品牌形象',
    icon: '/images/icon-brand.jpg',
  },
  {
    title: '数据分析',
    desc: '游客画像、流量分析、转化追踪，数据驱动营销决策',
    icon: '/images/icon-data.jpg',
  },
]

function openService(svc) {
  alert(`${svc.title}\n\n${svc.desc}\n\n（后续将跳转至详情页）`)
}

function goHome() {
  router.push('/')
}
</script>

<style scoped>
.marketing-page {
  display: flex;
  flex-direction: column;
  min-height: calc(100vh - 88px);
  background: linear-gradient(180deg, #c8d5c0 0%, #b7c2a9 40%, #a3b59a 100%);
  position: relative;
  overflow: hidden;
}

/* ---- 山水装饰 ---- */
.landscape-bg {
  position: absolute;
  inset: 0;
  pointer-events: none;
  z-index: 0;
}
.mountain {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
}
.mountain.m1 {
  height: 200px;
  background: rgba(127, 160, 173, .18);
  clip-path: polygon(0% 100%, 0% 55%, 8% 38%, 18% 50%, 28% 22%, 40% 48%, 52% 18%, 64% 44%, 76% 25%, 88% 50%, 100% 32%, 100% 100%);
}
.mountain.m2 {
  height: 140px;
  background: rgba(127, 160, 173, .25);
  clip-path: polygon(0% 100%, 0% 62%, 10% 42%, 22% 56%, 34% 30%, 48% 54%, 60% 28%, 74% 52%, 86% 35%, 100% 55%, 100% 100%);
}
.mountain.m3 {
  height: 80px;
  background: rgba(44, 38, 32, .08);
  clip-path: polygon(0% 100%, 0% 72%, 12% 50%, 26% 68%, 40% 44%, 56% 66%, 70% 45%, 84% 66%, 100% 48%, 100% 100%);
}
.cloud {
  position: absolute;
  border-radius: 999px;
  background: rgba(255, 255, 255, .35);
}
.cloud.c1 { width: 140px; height: 30px; top: 60px; right: 8%; }
.cloud.c2 { width: 100px; height: 22px; top: 120px; left: 5%; }
.cloud.c3 { width: 80px; height: 18px; top: 200px; right: 15%; }

/* ---- 顶部栏 ---- */
.marketing-topbar {
  display: flex;
  align-items: center;
  gap: 18px;
  padding: 16px 32px;
  background: rgba(255, 255, 255, .75);
  backdrop-filter: blur(8px);
  border-bottom: 1px solid rgba(44, 38, 32, .06);
  flex-shrink: 0;
  position: relative;
  z-index: 1;
}
.back-btn {
  padding: 8px 18px;
  border-radius: 999px;
  border: 1px solid var(--terracotta);
  background: transparent;
  color: var(--terracotta);
  font-family: 'Noto Sans SC', sans-serif;
  font-size: 13px;
  cursor: pointer;
  letter-spacing: .5px;
  transition: all .2s;
  white-space: nowrap;
}
.back-btn:hover {
  background: var(--terracotta);
  color: #fefaf5;
}
.marketing-title {
  font-size: 26px;
  color: var(--ink);
  letter-spacing: 2px;
}
.marketing-sub {
  font-family: 'Noto Sans SC', sans-serif;
  font-size: 12px;
  color: var(--ink-soft);
  letter-spacing: 1px;
}

/* ---- 卡片区域 ---- */
.marketing-body {
  flex: 1;
  padding: 40px 32px;
  display: flex;
  justify-content: center;
  position: relative;
  z-index: 1;
}
.service-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 24px;
  max-width: 700px;
  width: 100%;
}

/* ---- 服务卡片 ---- */
.service-card {
  background: #fff;
  border-radius: 16px;
  padding: 32px 26px 24px;
  border: 1px solid rgba(44, 38, 32, .06);
  box-shadow: 0 10px 30px -16px rgba(44, 38, 32, .12);
  cursor: pointer;
  transition: transform .2s, box-shadow .2s;
  display: flex;
  flex-direction: column;
}
.service-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 18px 40px -16px rgba(44, 38, 32, .22);
}
.card-icon-wrap {
  width: 52px;
  height: 52px;
  border-radius: 14px;
  background: transparent;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 18px;
}
.card-icon {
  width: 44px;
  height: 44px;
  object-fit: contain;
  mix-blend-mode: multiply;
}
.card-title {
  font-family: 'Noto Serif SC', serif;
  font-size: 16px;
  font-weight: 600;
  color: var(--ink);
  margin-bottom: 10px;
  letter-spacing: .5px;
}
.card-desc {
  font-family: 'Noto Sans SC', sans-serif;
  font-size: 13px;
  color: var(--ink-soft);
  line-height: 1.75;
  flex: 1;
}
.card-arrow {
  margin-top: 16px;
  font-family: 'Noto Sans SC', sans-serif;
  font-size: 12px;
  color: var(--dusty-blue-deep);
  letter-spacing: 1px;
}

@media (max-width: 540px) {
  .service-grid {
    grid-template-columns: 1fr;
  }
  .marketing-topbar {
    padding: 12px 16px;
    gap: 10px;
  }
  .marketing-title { font-size: 22px; }
  .marketing-sub { display: none; }
  .marketing-body { padding: 24px 16px; }
}
</style>
