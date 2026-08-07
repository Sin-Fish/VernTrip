<template>
  <div class="hero-content">
    <span class="hero-tag">山水灵 · AI 文旅智能向导</span>

    <h1 class="hero-title">
      让 <em>AI</em>，<br>读懂你的山水。
    </h1>

    <p class="hero-sub">
      告诉我你此刻的心情，AI 为你寻找一段值得抵达的旅程。
    </p>

    <div class="ai-entry">
      <div class="ai-input-wrap">
        <span class="ai-dot"></span>
        <input
          v-model="searchQuery"
          type="text"
          placeholder="告诉我，你想怎样抵达一段山水。"
          @keydown.enter="handleSearch"
        />
        <button class="ai-btn" @click="handleSearch">
          <span class="seal-text">寻一程</span>
        </button>
      </div>
      <p class="ai-hint">AI 会理解你的心情、时间与偏好，为你生成一段专属旅程。</p>

      <a class="ai-explore-link" @click="goExplore">
        还没灵感？去探索 <span class="explore-arrow">→</span>
      </a>

      <div class="ai-quick-tags">
        <button
          v-for="tag in quickTags"
          :key="tag"
          class="quick-tag"
          @click="setQuery(tag)"
        >{{ tag }}</button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'

const router = useRouter()
const searchQuery = ref('')

const quickTags = [
  '我想一个人静静',
  '我只有两天时间',
  '我想逃离城市',
  '我想吃真正的地方味',
]

function setQuery(tag) { searchQuery.value = tag }

function goExplore() {
  router.push('/explore')
}

function handleSearch() {
  const q = searchQuery.value.trim()
  if (!q) return
  router.push({ path: '/ai-assistant', query: { q } })
}
</script>

<style scoped>
.hero-content{
  padding:28px 0 28px;
  width:100%;
}

.hero-tag{
  display:inline-block;
  font-family:'Noto Serif SC', serif;
  font-size:12px; font-weight:600; color:#2f4f42;
  letter-spacing:3px; margin-bottom:26px;
  padding-bottom:9px;
  border-bottom:1px solid rgba(47,79,66,.25);
}

.hero-title{
  font-family:'Noto Serif SC', serif;
  font-weight:700; font-size:clamp(40px,4.5vw,58px);
  line-height:1.3; letter-spacing:2px;
  color:var(--ink); margin-bottom:18px;
}
.hero-title em{ font-style:normal; color:var(--cinnabar); }

.hero-sub{
  font-family:'Noto Serif SC', serif;
  font-weight:300; font-size:15px;
  color:var(--ink); letter-spacing:.5px;
  line-height:1.75; margin-bottom:30px; max-width:440px;
}

.ai-entry{ max-width:520px; }
.ai-input-wrap{
  display:flex; align-items:center;
  background:rgba(255,255,255,.15);
  backdrop-filter:blur(2px);
  border:1px solid rgba(44,36,27,.06);
  padding:4px 4px 4px 16px;
  border-radius:10px;
  overflow:hidden;
}
.ai-input-wrap:focus-within{
  border-color:rgba(44,36,27,.2);
  box-shadow:0 0 0 3px rgba(44,36,27,.03);
}
.ai-dot{
  width:8px; height:8px; border-radius:50%;
  background:var(--ink); opacity:.2; flex-shrink:0; margin-right:12px;
}
.ai-input-wrap input{
  flex:1; border:none; outline:none; background:transparent;
  font-family:'Noto Serif SC', serif;
  font-size:15px; color:var(--ink); padding:12px 0; letter-spacing:.5px;
}
.ai-input-wrap input::placeholder{ color:rgba(44,36,27,.25); }
.ai-btn{ padding:0; border:none; background:transparent; cursor:pointer; flex-shrink:0; }
.seal-text{
  display:flex; align-items:center; justify-content:center;
  height:38px; padding:0 24px;
  background:var(--cinnabar); color:var(--paper);
  font-family:'Noto Serif SC', serif;
  font-size:15px; letter-spacing:3px;
  border-radius:7px;
  transition:background .3s, box-shadow .3s;
}
.ai-btn:hover .seal-text{
  background:#C85A40;
  box-shadow:
    0 0 14px 3px rgba(212,162,78,.5),
    0 0 28px 8px rgba(212,162,78,.25);
}

.ai-hint{
  font-family:'Noto Serif SC', serif; font-weight:300;
  font-size:13px; color:var(--ink-soft);
  letter-spacing:.5px; margin-top:10px; padding-left:2px;
}

.ai-explore-link{
  display:inline-block;
  font-family:'Noto Serif SC', serif;
  font-size:12px; color:var(--teal);
  text-decoration:none;
  border-bottom:1px solid var(--teal);
  text-underline-offset:4px;
  cursor:pointer;
  margin-top:10px; margin-bottom:14px;
  transition:color .2s;
}
.ai-explore-link:hover{ color:var(--teal-deep); }
.explore-arrow{ display:inline-block; transition:transform .2s; }
.ai-explore-link:hover .explore-arrow{ transform:translateX(3px); }

.ai-quick-tags{ display:flex; flex-wrap:wrap; gap:12px; margin-top:18px; }
.quick-tag{
  padding:6px 18px;
  border:1px solid rgba(44,36,27,.1);
  border-radius:999px;
  background:rgba(255,255,255,.15);
  backdrop-filter:blur(2px);
  color:var(--ink);
  font-family:'Noto Serif SC', serif;
  font-size:13px; letter-spacing:1px; cursor:pointer; transition:all .3s;
}
.quick-tag:hover{
  border-color:var(--ink);
  background:rgba(44,36,27,.04);
}
</style>
