<template>
  <div class="home-stage" @wheel="handleWheel">
    <Transition name="scene-dissolve" mode="out-in">
      <section
        v-if="scene === 'hero'"
        key="hero"
        class="scene-panel hero-scene"
        @click="handleHeroClick"
      >
        <img src="/images/hero-full.jpg" alt="山水云雾" class="hero-bg" />
        <div class="hero-wash"></div>
        <div class="hero-overlay">
          <HeroSection @explore="showExplore" />
        </div>
        <FeatureStrip class="feature-strip" />
      </section>

      <section v-else key="explore" ref="explorePanel" class="scene-panel inspiration-scene">
        <img src="/images/explorenew2.jpg" alt="" class="inspiration-bg" />
        <div class="inspiration-wash"></div>

        <header class="inspiration-heading">
          <button class="return-button" type="button" @click="showHero">← 返回首页</button>
          <div>
            <span class="eyebrow">DISCOVER · 城市精选</span>
            <h2>旅行灵感</h2>
          </div>
          <RouterLink class="planner-link" :to="{ name: 'travel-planner' }">AI 规划行程 <span>→</span></RouterLink>
        </header>

        <div class="explore-body">
          <TravelInspiration />
        </div>
      </section>
    </Transition>
  </div>
</template>

<script setup>
import { onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import FeatureStrip from '../components/FeatureStrip.vue'
import HeroSection from '../components/HeroSection.vue'
import TravelInspiration from '../components/TravelInspiration.vue'

const route = useRoute()
const router = useRouter()
const explorePanel = ref(null)
const scene = ref(route.query.scene === 'explore' ? 'explore' : 'hero')
let wheelLocked = false

function setScene(nextScene) {
  if (scene.value === nextScene) return
  scene.value = nextScene
  const query = { ...route.query }
  if (nextScene === 'explore') query.scene = 'explore'
  else delete query.scene
  router.replace({ name: 'travel-home', query })
}

function showExplore() {
  setScene('explore')
}

function showHero() {
  setScene('hero')
}

function handleHeroClick(event) {
  if (event.target.closest('button, input, a')) return
  showExplore()
}

function handleWheel(event) {
  if (wheelLocked || Math.abs(event.deltaY) < 28) return
  if (scene.value === 'hero' && event.deltaY > 0) {
    wheelLocked = true
    showExplore()
  } else if (
    scene.value === 'explore'
    && event.deltaY < 0
    && (explorePanel.value?.scrollTop || 0) <= 0
  ) {
    wheelLocked = true
    showHero()
  } else {
    return
  }
  window.setTimeout(() => { wheelLocked = false }, 850)
}

watch(
  () => route.query.scene,
  (value) => {
    scene.value = value === 'explore' ? 'explore' : 'hero'
  },
)

onMounted(() => {
  scene.value = route.query.scene === 'explore' ? 'explore' : 'hero'
})
</script>

<style scoped>
.home-stage {
  position: relative;
  isolation: isolate;
  height: calc(100vh - 77px);
  min-height: 620px;
  overflow: hidden;
  background: #e9e2d4;
}

.scene-panel {
  position: absolute;
  inset: 0;
}

.hero-scene {
  display: flex;
  flex-direction: column;
  overflow: hidden;
  cursor: pointer;
}

.hero-bg,
.inspiration-bg {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.hero-bg {
  object-position: top center;
  animation: hero-arrive 1.3s ease-out both;
}

.hero-wash {
  position: absolute;
  inset: 0;
  background:
    linear-gradient(90deg, rgba(239, 233, 218, .8) 0%, rgba(239, 233, 218, .34) 43%, transparent 72%),
    linear-gradient(0deg, rgba(235, 228, 212, .42), transparent 38%);
}

.hero-overlay {
  position: relative;
  z-index: 2;
  width: min(1360px, 100%);
  margin: 0 auto;
  padding: clamp(45px, 7vh, 82px) 56px 30px;
  cursor: default;
}

.feature-strip {
  position: absolute;
  right: 0;
  bottom: 0;
  left: 0;
  z-index: 2;
  cursor: default;
}

.inspiration-scene {
  overflow-x: hidden;
  overflow-y: auto;
  background: #f1eadc;
}

.inspiration-bg {
  position: fixed;
  top: 77px;
  height: calc(100vh - 77px);
  object-position: top center;
}

.inspiration-wash {
  position: fixed;
  inset: 77px 0 0;
  background:
    linear-gradient(90deg, rgba(249, 245, 236, .92), rgba(249, 245, 236, .72) 56%, rgba(239, 232, 217, .88)),
    linear-gradient(180deg, rgba(243, 237, 224, .18), #f3ede2 100%);
  pointer-events: none;
}

.inspiration-heading,
.explore-body,
.inspiration-scene :deep(.travelogue-section) {
  position: relative;
  z-index: 1;
}

.inspiration-heading {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: end;
  gap: 24px;
  width: min(1380px, calc(100% - 72px));
  margin: 0 auto;
  padding: 12px 0 11px;
  border-bottom: 1px solid rgba(44, 36, 27, .14);
}

.inspiration-heading > div {
  grid-column: 1;
  grid-row: 1;
  padding-left: 104px;
  text-align: left;
}

.eyebrow {
  color: var(--terracotta-deep);
  font: 700 9px/1 Georgia, 'Noto Serif SC', serif;
  letter-spacing: 2.5px;
}

.inspiration-heading h2 {
  margin: 4px 0 0;
  color: var(--ink);
  font-family: 'Noto Serif SC', serif;
  font-size: clamp(22px, 1.8vw, 27px);
  font-weight: 600;
  letter-spacing: 1px;
  line-height: 1.15;
}

.return-button,
.planner-link {
  justify-self: start;
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--ink);
  cursor: pointer;
  font-family: 'Noto Serif SC', serif;
  font-size: 12px;
  letter-spacing: 1px;
  text-decoration: none;
}

.return-button {
  grid-column: 1;
  grid-row: 1;
  align-self: center;
  color: var(--ink-soft);
}

.planner-link {
  grid-column: 2;
  grid-row: 1;
  justify-self: end;
  min-width: 132px;
  padding: 11px 17px;
  border: 1px solid var(--teal);
  border-radius: 2px;
  background: var(--teal);
  color: #f8f3e9;
  text-align: center;
}

.planner-link span {
  margin-left: 13px;
}

.explore-body {
  width: min(1380px, calc(100% - 72px));
  margin: 0 auto;
  padding: 10px 0 70px;
}

.scene-dissolve-enter-active,
.scene-dissolve-leave-active {
  transition: opacity .58s ease, filter .58s ease, transform .58s ease;
}

.scene-dissolve-enter-from {
  opacity: 0;
  filter: blur(16px) saturate(.75);
  transform: scale(1.025);
}

.scene-dissolve-leave-to {
  opacity: 0;
  filter: blur(10px) saturate(.8);
  transform: scale(.985);
}

@keyframes hero-arrive {
  from { opacity: .5; transform: scale(1.045); }
  to { opacity: 1; transform: scale(1); }
}

@media (max-width: 900px) {
  .home-stage { height: calc(100vh - 72px); min-height: 680px; }
  .hero-overlay { padding: 34px 24px 24px; }
  .inspiration-bg { top: 72px; height: calc(100vh - 72px); }
  .inspiration-wash { inset: 72px 0 0; }
  .inspiration-heading { width: calc(100% - 32px); }
  .inspiration-heading > div { padding-left: 0; }
  .return-button { display: none; }
  .explore-body { width: calc(100% - 32px); }
}

@media (max-width: 640px) {
  .home-stage { min-height: 720px; }
  .hero-wash { background: rgba(239, 233, 218, .57); }
  .inspiration-heading { padding-top: 22px; }
  .inspiration-heading h2 { font-size: 24px; letter-spacing: 1px; }
}
</style>
