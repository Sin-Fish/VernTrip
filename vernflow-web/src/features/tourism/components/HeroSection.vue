<template>
  <div class="hero-content">
    <span class="hero-tag">山水灵 · AI 文旅智能向导</span>

    <h1 class="hero-title">
      让 <em>AI</em><br />读懂你的山水。
    </h1>

    <p class="hero-sub">
      告诉我们此刻的心情、时间与偏好，先从一句话开始规划一段值得抵达的旅程。
    </p>

    <div class="ai-entry">
      <div class="ai-input-wrap">
        <span class="ai-dot"></span>
        <input
          v-model="searchQuery"
          type="text"
          placeholder="例如：周末两天，想去安静、有烟火气的地方"
          aria-label="描述你的旅行期待"
          @keydown.enter="handleSearch"
        />
        <span class="enter-hint">按 Enter 开始</span>
      </div>
      <p class="ai-hint">AI 会结合你的描述完善出行条件，并生成可调整的旅行方案。</p>

      <button class="demo-cta" type="button" @click="startDemo">
        <span>一键体验完整演示</span>
        <small>OpenClaw 识别需求 · 生成两日行程</small>
        <b>→</b>
      </button>

      <button class="ai-explore-link" type="button" @click="emit('explore')">
        还没想好？先看旅行灵感 <span class="explore-arrow">→</span>
      </button>

      <div class="ai-quick-tags">
        <button
          v-for="tag in quickTags"
          :key="tag"
          class="quick-tag"
          type="button"
          @click="setQuery(tag)"
        >{{ tag }}</button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'

const emit = defineEmits(['explore'])
const router = useRouter()
const searchQuery = ref('')

const quickTags = [
  '一个人安静走走',
  '周末两天慢游',
  '想体验地方非遗',
  '想吃地道风味',
]

function setQuery(tag) {
  searchQuery.value = tag
}

function handleSearch() {
  const q = searchQuery.value.trim()
  if (!q) return
  router.push({ name: 'travel-planner', query: { q } })
}

function startDemo() {
  router.push({ name: 'travel-planner', query: { demo: '1' } })
}
</script>

<style scoped>
.hero-content {
  width: 100%;
  position: relative;
  padding: 28px 0;
}

.hero-tag {
  display: inline-block;
  margin-bottom: 24px;
  padding-bottom: 9px;
  border-bottom: 1px solid rgba(47, 79, 66, .25);
  color: #2f4f42;
  font-family: 'Noto Serif SC', serif;
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 3px;
}

.hero-title {
  margin-bottom: 18px;
  color: var(--ink);
  font-family: 'Noto Serif SC', serif;
  font-size: clamp(42px, 5vw, 68px);
  font-weight: 700;
  letter-spacing: 2px;
  line-height: 1.22;
}

.hero-title em {
  color: var(--cinnabar);
  font-style: normal;
}

.hero-sub {
  max-width: 480px;
  margin-bottom: 28px;
  color: var(--ink);
  font-family: 'Noto Serif SC', serif;
  font-size: 15px;
  font-weight: 300;
  letter-spacing: .5px;
  line-height: 1.8;
}

.ai-entry {
  max-width: 560px;
}

.ai-input-wrap {
  display: flex;
  align-items: center;
  padding: 5px 14px 5px 17px;
  overflow: hidden;
  border: 1px solid rgba(44, 36, 27, .1);
  border-radius: 12px;
  background: rgba(255, 255, 255, .32);
  backdrop-filter: blur(8px);
  transition: border-color .25s, box-shadow .25s, background .25s;
}

.ai-input-wrap:focus-within {
  border-color: rgba(49, 91, 90, .45);
  background: rgba(255, 255, 255, .52);
  box-shadow: 0 12px 32px rgba(44, 36, 27, .08);
}

.ai-dot {
  width: 8px;
  height: 8px;
  margin-right: 12px;
  flex-shrink: 0;
  border-radius: 50%;
  background: var(--teal);
  box-shadow: 0 0 0 5px rgba(49, 91, 90, .1);
}

.ai-input-wrap input {
  min-width: 0;
  flex: 1;
  padding: 12px 0;
  border: 0;
  outline: 0;
  background: transparent;
  color: var(--ink);
  font-family: 'Noto Serif SC', serif;
  font-size: 15px;
  letter-spacing: .4px;
}

.ai-input-wrap input::placeholder {
  color: rgba(44, 36, 27, .42);
}

.enter-hint {
  flex-shrink: 0;
  margin-left: 14px;
  color: rgba(44, 36, 27, .5);
  font-size: 11px;
  letter-spacing: 1px;
}

.ai-hint {
  margin-top: 10px;
  padding-left: 2px;
  color: var(--ink-soft);
  font-family: 'Noto Serif SC', serif;
  font-size: 12px;
  font-weight: 300;
  letter-spacing: .5px;
}

.demo-cta {
  display: grid;
  grid-template-columns: 1fr auto;
  align-items: center;
  width: 100%;
  margin-top: 15px;
  padding: 13px 16px;
  border: 1px solid rgba(49, 91, 90, .22);
  border-radius: 12px;
  background: linear-gradient(110deg, rgba(49, 91, 90, .92), rgba(62, 104, 91, .84));
  color: #fffaf0;
  cursor: pointer;
  font-family: 'Noto Serif SC', serif;
  text-align: left;
  box-shadow: 0 12px 28px rgba(49, 91, 90, .15);
  transition: transform .25s ease, box-shadow .25s ease;
}

.demo-cta:hover { transform: translateY(-2px); box-shadow: 0 16px 34px rgba(49, 91, 90, .22); }
.demo-cta span { font-size: 14px; letter-spacing: 1px; }
.demo-cta small { grid-column: 1; margin-top: 3px; color: rgba(255, 250, 240, .65); font-size: 9px; letter-spacing: .6px; }
.demo-cta b { grid-column: 2; grid-row: 1 / 3; font-size: 20px; font-weight: 400; }

.ai-explore-link {
  display: inline-block;
  margin: 13px 0 2px;
  padding: 0 0 4px;
  border: 0;
  border-bottom: 1px solid currentColor;
  background: transparent;
  color: var(--teal);
  cursor: pointer;
  font-family: 'Noto Serif SC', serif;
  font-size: 12px;
}

.explore-arrow {
  display: inline-block;
  transition: transform .2s;
}

.ai-explore-link:hover .explore-arrow {
  transform: translateX(4px);
}

.ai-quick-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 17px;
}

.quick-tag {
  padding: 7px 16px;
  border: 1px solid rgba(44, 36, 27, .12);
  border-radius: 999px;
  background: rgba(255, 255, 255, .2);
  color: var(--ink);
  cursor: pointer;
  font-family: 'Noto Serif SC', serif;
  font-size: 12px;
  letter-spacing: .8px;
  transition: all .25s;
}

.quick-tag:hover {
  border-color: var(--teal);
  background: rgba(49, 91, 90, .08);
  color: var(--teal-deep);
}

@media (max-width: 640px) {
  .hero-content { padding: 8px 0; }
  .hero-tag { margin-bottom: 18px; }
  .hero-title { font-size: clamp(38px, 12vw, 52px); }
  .hero-sub { margin-bottom: 22px; font-size: 14px; }
  .enter-hint { display: none; }
  .quick-tag { padding: 6px 12px; }
}
</style>
