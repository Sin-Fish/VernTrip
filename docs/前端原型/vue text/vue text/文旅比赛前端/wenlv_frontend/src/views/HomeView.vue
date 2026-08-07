<template>
  <div class="home-stage">
    <!-- ======== Hero 通栏背景图区域 ======== -->
    <section class="hero-area">
      <img
        src="/images/hero-full.jpg"
        alt=""
        class="hero-bg"
      />

      <div class="hero-overlay">
        <HeroSection />
      </div>
    </section>

    <!-- ======== 功能亮点横条 ======== -->
    <FeatureStrip />
  </div>
</template>

<script setup>
import HeroSection from '../components/HeroSection.vue'
import FeatureStrip from '../components/FeatureStrip.vue'
</script>

<style scoped>
.home-stage{
  background:var(--paper);
  display:flex;
  flex-direction:column;
  height:calc(100vh - 70px);
  overflow:hidden;
  position:relative;
}

/* ======== Hero 通栏背景图 ======== */
.hero-area{
  position:relative;
  flex:1;
  overflow:hidden;
}

/* 通栏背景图 */
.hero-bg{
  position:absolute;
  inset:0;
  width:100%;
  height:100%;
  object-fit:cover;
  object-position:top center;
  display:block;
}

/* 文字叠加层 — 透明底 */
.hero-overlay{
  position:relative;
  z-index:2;
  padding:70px 56px;
  max-width:1360px;
  margin:0 auto;
}

@media (max-width:960px){
  .hero-area{ flex:1; }
  .hero-overlay{ padding:32px 24px; }
  .hero-bg{ object-fit:cover; }
}
</style>