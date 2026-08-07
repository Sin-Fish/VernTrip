<template>
  <div class="plan-page">
    <!-- 顶部栏 -->
    <header class="plan-topbar">
      <button class="back-btn" @click="goHome">← 返回首页</button>
      <h2 class="plan-title font-brush">个性规划</h2>
      <span class="plan-sub">山水灵 · 为你定制专属旅程</span>
    </header>

    <!-- 表单区域 -->
    <div class="plan-body">
      <div class="plan-card">
        <!-- 旅游地点 -->
        <section class="plan-section">
          <h3 class="section-label"><img class="label-icon" src="/images/icon-destination.jpg" alt="" /> 旅游地点</h3>
          <input
            v-model.trim="form.destination"
            type="text"
            class="dest-input"
            placeholder="例如：杭州、苏州、乌镇……"
          />
        </section>

        <!-- 预算 -->
        <section class="plan-section">
          <h3 class="section-label"><img class="label-icon" src="/images/icon-budget.jpg" alt="" /> 预算范围</h3>
          <div class="option-row">
            <button
              v-for="opt in budgetOptions"
              :key="opt"
              class="option-chip"
              :class="{ active: form.budget === opt }"
              @click="form.budget = opt"
            >{{ opt }}</button>
          </div>
        </section>

        <!-- 天数 -->
        <section class="plan-section">
          <h3 class="section-label"><img class="label-icon" src="/images/icon-days.jpg" alt="" /> 出行天数</h3>
          <div class="days-row">
            <input
              v-model="form.days"
              type="text"
              class="days-input"
              placeholder="例如：3天2夜"
              maxlength="10"
            />
            <span class="days-hint">请按 "n天n夜" 格式填写</span>
          </div>
        </section>

        <!-- 同行人 -->
        <section class="plan-section">
          <h3 class="section-label"><img class="label-icon" src="/images/icon-companion.jpg" alt="" /> 同行伙伴</h3>
          <div class="option-row">
            <button
              v-for="opt in companionOptions"
              :key="opt"
              class="option-chip"
              :class="{ active: form.companion === opt }"
              @click="form.companion = opt"
            >{{ opt }}</button>
          </div>
        </section>

        <!-- 偏好标签 -->
        <section class="plan-section">
          <h3 class="section-label"><img class="label-icon" src="/images/icon-preference.jpg" alt="" /> 偏好标签（可多选）</h3>
          <div class="option-row">
            <button
              v-for="tag in preferenceTags"
              :key="tag"
              class="option-chip tag-chip"
              :class="{ active: form.preferences.includes(tag) }"
              @click="togglePreference(tag)"
            >{{ tag }}</button>
          </div>
        </section>

        <!-- 生成按钮 -->
        <button class="plan-submit" @click="handleSubmit">
          ✨ 生成我的专属行程
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { reactive } from 'vue'
import { useRouter } from 'vue-router'

const router = useRouter()

const budgetOptions = ['800-1500', '1500-3000', '3000元以上']
const companionOptions = ['情侣', '家庭亲子', '朋友结伴', '独自出行']
const preferenceTags = ['山水湖景', '本地风味', '非遗手作', '休闲慢逛', '文艺打卡']

const form = reactive({
  destination: '',
  budget: '',
  days: '',
  companion: '',
  preferences: [],
})

function togglePreference(tag) {
  const idx = form.preferences.indexOf(tag)
  if (idx > -1) {
    form.preferences.splice(idx, 1)
  } else {
    form.preferences.push(tag)
  }
}

function handleSubmit() {
  if (!form.destination) return alert('请填写旅游地点')
  if (!form.budget) return alert('请选择预算范围')
  if (!form.days.trim()) return alert('请填写出行天数')
  if (!form.companion) return alert('请选择同行伙伴')
  if (form.preferences.length === 0) return alert('请至少选择一个偏好标签')

  // 后续接入后端生成逻辑，此处暂作提示
  alert(
    `已为你生成专属行程！\n\n` +
    `地点：${form.destination}\n` +
    `预算：${form.budget}\n` +
    `天数：${form.days}\n` +
    `同行：${form.companion}\n` +
    `偏好：${form.preferences.join('、')}`
  )
}

function goHome() {
  router.push('/')
}
</script>

<style scoped>
.plan-page {
  display: flex;
  flex-direction: column;
  min-height: calc(100vh - 88px);
  background: #f5efe3;
}

/* ---- 顶部栏 ---- */
.plan-topbar {
  display: flex;
  align-items: center;
  gap: 18px;
  padding: 16px 32px;
  background: #fff;
  border-bottom: 1px solid rgba(44, 38, 32, .07);
  flex-shrink: 0;
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
.plan-title {
  font-size: 26px;
  color: var(--ink);
  letter-spacing: 2px;
}
.plan-sub {
  font-family: 'Noto Sans SC', sans-serif;
  font-size: 12px;
  color: var(--ink-soft);
  letter-spacing: 1px;
}

/* ---- 表单区域 ---- */
.plan-body {
  flex: 1;
  padding: 32px;
  display: flex;
  justify-content: center;
}
.plan-card {
  width: 100%;
  max-width: 620px;
  background: #fff;
  border-radius: 18px;
  padding: 40px 44px;
  box-shadow: 0 10px 40px -20px rgba(44, 38, 32, .15);
  align-self: flex-start;
}

/* ---- 分区 ---- */
.plan-section {
  margin-bottom: 32px;
}
.plan-section:last-of-type {
  margin-bottom: 28px;
}
.section-label {
  font-family: 'Noto Sans SC', sans-serif;
  font-size: 14px;
  font-weight: 500;
  color: var(--ink);
  letter-spacing: .5px;
  margin-bottom: 14px;
  display: flex;
  align-items: center;
  gap: 8px;
}
.label-icon {
  width: 22px;
  height: 22px;
  object-fit: contain;
  mix-blend-mode: multiply;
  flex-shrink: 0;
}

/* 选项按钮行 */
.option-row {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}
.option-chip {
  padding: 10px 22px;
  border-radius: 999px;
  border: 1px solid rgba(44, 38, 32, .15);
  background: #faf7f0;
  color: var(--ink-soft);
  font-family: 'Noto Sans SC', sans-serif;
  font-size: 13.5px;
  cursor: pointer;
  letter-spacing: .5px;
  transition: all .2s;
}
.option-chip:hover {
  border-color: var(--terracotta);
  color: var(--terracotta);
}
.option-chip.active {
  background: var(--terracotta);
  border-color: var(--terracotta);
  color: #fefaf5;
}

/* 地点输入 */
.dest-input {
  width: 100%;
  padding: 11px 16px;
  border-radius: 10px;
  border: 1px solid rgba(44, 38, 32, .18);
  background: #faf7f0;
  font-family: 'Noto Sans SC', sans-serif;
  font-size: 14px;
  color: var(--ink);
  outline: none;
  transition: border-color .2s;
}
.dest-input::placeholder {
  color: rgba(44, 38, 32, .35);
}
.dest-input:focus {
  border-color: var(--terracotta);
}

/* 天数输入 */
.days-row {
  display: flex;
  align-items: center;
  gap: 14px;
  flex-wrap: wrap;
}
.days-input {
  width: 180px;
  padding: 11px 16px;
  border-radius: 10px;
  border: 1px solid rgba(44, 38, 32, .18);
  background: #faf7f0;
  font-family: 'Noto Sans SC', sans-serif;
  font-size: 14px;
  color: var(--ink);
  outline: none;
  transition: border-color .2s;
}
.days-input::placeholder {
  color: rgba(44, 38, 32, .35);
}
.days-input:focus {
  border-color: var(--terracotta);
}
.days-hint {
  font-family: 'Noto Sans SC', sans-serif;
  font-size: 12px;
  color: var(--ink-soft);
  opacity: .7;
}

/* 提交按钮 */
.plan-submit {
  width: 100%;
  margin-top: 10px;
  padding: 15px 0;
  border-radius: 12px;
  border: none;
  background: var(--terracotta);
  color: #fefaf5;
  font-family: 'Noto Sans SC', sans-serif;
  font-size: 15px;
  letter-spacing: 2px;
  cursor: pointer;
  transition: background .2s;
}
.plan-submit:hover {
  background: var(--terracotta-deep);
}

@media (max-width: 640px) {
  .plan-topbar {
    padding: 12px 16px;
    gap: 10px;
  }
  .plan-title { font-size: 22px; }
  .plan-sub { display: none; }
  .plan-body { padding: 20px 16px; }
  .plan-card { padding: 28px 22px; }
}
</style>
