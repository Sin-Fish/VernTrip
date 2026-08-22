<script setup>
import { computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useTravelPlanning } from '@/composables/useTravelPlanning'

const router = useRouter()
const {
  state,
  loadSavedPlans,
  removeSavedPlan,
  restorePlan,
} = useTravelPlanning()

const plans = computed(() => state.savedPlans)

function formatDate(value) {
  if (!value) return '日期待定'
  return String(value).replaceAll('-', '.')
}

function spentBudget(plan) {
  return Math.max(0, Number(plan.budget?.total ?? 0) - Number(plan.budget?.reserve ?? 0))
}

function openPlan(plan) {
  restorePlan(plan)
  router.push({ name: 'travel-plan', query: { plan: String(plan.id) } })
}

async function removePlan(plan) {
  if (!window.confirm(`确定从收藏中移除“${plan.title}”吗？`)) return
  await removeSavedPlan(plan.id)
}

onMounted(() => {
  window.scrollTo({ top: 0, behavior: 'auto' })
  loadSavedPlans()
})
</script>

<template>
  <main class="trips-page">
    <section class="trips-hero">
      <div>
        <p class="travel-eyebrow">MY JOURNEYS / 行程册</p>
        <h1>把想走的路，<br /><em>慢慢收进这里。</em></h1>
      </div>
      <div class="trips-intro">
        <span>{{ plans.length.toString().padStart(2, '0') }}</span>
        <p>份已收藏计划</p>
        <button type="button" @click="router.push({ name: 'travel-planner' })">规划新旅程 <b>→</b></button>
      </div>
    </section>

    <section class="trips-content">
      <div class="trips-section-head">
        <div><span>ARCHIVE</span><h2>最近的旅行计划</h2></div>
        <p>打开后仍可交给 OpenClaw 调整，原版本会被保留。</p>
      </div>

      <div v-if="state.isLoadingSavedPlans" class="trips-state">
        <i></i>
        <b>正在翻开你的行程册</b>
        <p>从后端读取已收藏的计划与地图节点。</p>
      </div>

      <div v-else-if="state.error" class="trips-state error">
        <span>!</span>
        <b>行程册暂时没有打开</b>
        <p>{{ state.error }}</p>
        <button type="button" @click="loadSavedPlans">重新读取</button>
      </div>

      <div v-else-if="!plans.length" class="trips-empty">
        <div class="empty-mark"><span>山</span><i></i><span>水</span></div>
        <div>
          <p class="travel-eyebrow">THE FIRST JOURNEY</p>
          <h2>这里还没有收藏的路线</h2>
          <p>先告诉我们目的地、天数和预算，让 OpenClaw 为你整理第一份可以继续修改的旅行计划。</p>
          <button class="travel-button travel-button-primary" type="button" @click="router.push({ name: 'travel-planner' })">开始规划 <span>→</span></button>
        </div>
      </div>

      <div v-else class="trip-grid">
        <article v-for="(plan, index) in plans" :key="plan.id" class="trip-card">
          <div class="trip-card-index">{{ String(index + 1).padStart(2, '0') }}</div>
          <div class="trip-card-copy">
            <div class="trip-card-meta">
              <span>{{ formatDate(plan.request?.date) }}</span>
              <i></i>
              <span>{{ plan.request?.days }} 天</span>
              <i></i>
              <span>V{{ plan.version }}</span>
            </div>
            <h3>{{ plan.title }}</h3>
            <p>{{ plan.summary }}</p>
            <div class="trip-card-route">
              <span>{{ plan.request?.departure || '出发地待定' }}</span>
              <b>→</b>
              <strong>{{ plan.request?.destination }}</strong>
            </div>
          </div>
          <div class="trip-card-stats">
            <div><small>适配度</small><b>{{ plan.match }}%</b></div>
            <div><small>预计使用</small><b>¥ {{ spentBudget(plan).toLocaleString('zh-CN') }}</b></div>
            <div><small>规划引擎</small><b>{{ plan.engine === 'openclaw' ? 'OpenClaw' : '本地规则' }}</b></div>
          </div>
          <div class="trip-card-actions">
            <button class="open-button" type="button" @click="openPlan(plan)">打开行程 <span>↗</span></button>
            <button class="remove-button" type="button" @click="removePlan(plan)">移出收藏</button>
          </div>
        </article>
      </div>
    </section>
  </main>
</template>

<style scoped>
.trips-page {
  min-height: calc(100vh - 77px);
  padding-bottom: 100px;
  color: #20362f;
  background:
    linear-gradient(90deg, rgba(36, 95, 90, .025) 1px, transparent 1px),
    linear-gradient(rgba(36, 95, 90, .02) 1px, transparent 1px),
    radial-gradient(circle at 8% 5%, rgba(186, 114, 74, .15), transparent 25%),
    #f6f0e5;
  background-size: 42px 42px, 42px 42px, auto, auto;
}

.trips-hero {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 250px;
  align-items: end;
  gap: 60px;
  width: min(1160px, calc(100% - 72px));
  margin: 0 auto;
  padding: 76px 0 58px;
  border-bottom: 1px solid rgba(31, 41, 39, .2);
}

.trips-hero h1 {
  margin: 10px 0 0;
  color: #20362f;
  font-family: 'Noto Serif SC', 'Songti SC', serif;
  font-size: clamp(48px, 6vw, 78px);
  font-weight: 500;
  letter-spacing: -4px;
  line-height: 1.13;
}

.trips-hero h1 em { color: #b65c49; font-style: normal; }
.trips-intro { display: grid; padding: 0 0 8px 24px; border-left: 3px solid #276d62; }
.trips-intro > span { color: #276d62; font: 500 54px/1 Georgia, serif; }
.trips-intro p { margin: 6px 0 21px; color: #68766f; font-size: 11px; letter-spacing: 1px; }
.trips-intro button { justify-self: start; padding: 9px 0; border: 0; background: transparent; color: #20362f; cursor: pointer; font-family: inherit; font-size: 12px; font-weight: 800; }
.trips-intro button b { margin-left: 13px; color: #b65c49; }

.trips-content { width: min(1160px, calc(100% - 72px)); margin: 0 auto; padding-top: 48px; }
.trips-section-head { display: flex; align-items: end; justify-content: space-between; gap: 30px; margin-bottom: 27px; }
.trips-section-head span { color: #b65c49; font: 700 9px/1 Georgia, serif; letter-spacing: 2px; }
.trips-section-head h2 { margin: 7px 0 0; font-family: 'Noto Serif SC', 'Songti SC', serif; font-size: 30px; font-weight: 500; }
.trips-section-head > p { max-width: 350px; margin: 0; color: #768078; font-size: 11px; line-height: 1.7; text-align: right; }

.trip-grid { display: grid; gap: 16px; }
.trip-card {
  position: relative;
  display: grid;
  grid-template-columns: 62px minmax(0, 1fr) 220px 126px;
  align-items: stretch;
  min-height: 230px;
  overflow: hidden;
  border-top: 4px solid #276d62;
  background: rgba(255, 253, 248, .84);
  box-shadow: 0 18px 45px rgba(40, 71, 64, .07);
}

.trip-card-index { padding: 29px 0 0 22px; color: rgba(39, 109, 98, .32); font: 500 19px/1 Georgia, serif; }
.trip-card-copy { min-width: 0; padding: 28px 34px 25px 10px; }
.trip-card-meta { display: flex; align-items: center; gap: 9px; color: #a85845; font: 700 9px/1 Georgia, serif; letter-spacing: 1px; }
.trip-card-meta i { width: 3px; height: 3px; border-radius: 50%; background: #c6b999; }
.trip-card h3 { margin: 13px 0 9px; font-family: 'Noto Serif SC', 'Songti SC', serif; font-size: clamp(23px, 2.5vw, 32px); font-weight: 500; letter-spacing: -1px; }
.trip-card-copy > p { display: -webkit-box; max-width: 590px; margin: 0; overflow: hidden; color: #68766f; font-size: 11px; line-height: 1.8; -webkit-box-orient: vertical; -webkit-line-clamp: 2; }
.trip-card-route { display: flex; align-items: center; gap: 12px; margin-top: 22px; font-family: 'Noto Serif SC', 'Songti SC', serif; font-size: 12px; }
.trip-card-route b { color: #b65c49; font-weight: 400; }
.trip-card-route strong { color: #276d62; font-size: 15px; }

.trip-card-stats { display: grid; grid-template-rows: repeat(3, 1fr); border-left: 1px solid rgba(32, 54, 48, .09); }
.trip-card-stats > div { display: grid; align-content: center; gap: 5px; padding: 12px 22px; border-bottom: 1px solid rgba(32, 54, 48, .08); }
.trip-card-stats > div:last-child { border-bottom: 0; }
.trip-card-stats small { color: #8b928b; font-size: 8px; letter-spacing: 1px; }
.trip-card-stats b { color: #20362f; font-family: Georgia, 'Noto Serif SC', serif; font-size: 14px; }

.trip-card-actions { display: grid; grid-template-rows: 1fr auto; background: #e8ddc9; }
.trip-card-actions button { border: 0; cursor: pointer; font-family: inherit; }
.open-button { display: grid; place-content: center; gap: 12px; background: #276d62; color: #fffdf8; font-size: 12px; font-weight: 800; }
.open-button span { color: #d6a47b; font-size: 18px; }
.remove-button { min-height: 45px; border-top: 1px solid rgba(32, 54, 48, .12) !important; background: transparent; color: #8a6f63; font-size: 9px; }

.trips-state,
.trips-empty { min-height: 330px; background: rgba(255, 253, 248, .72); box-shadow: 0 18px 45px rgba(40, 71, 64, .06); }
.trips-state { display: grid; place-content: center; justify-items: center; gap: 8px; color: #276d62; text-align: center; }
.trips-state i { width: 28px; height: 28px; border: 2px solid rgba(39, 109, 98, .18); border-top-color: #276d62; border-radius: 50%; animation: spin .8s linear infinite; }
.trips-state > span { display: grid; width: 34px; height: 34px; place-items: center; border: 1px solid #b65c49; border-radius: 50%; color: #b65c49; font: 700 18px/1 Georgia, serif; }
.trips-state b { font-family: 'Noto Serif SC', 'Songti SC', serif; font-size: 20px; }
.trips-state p { margin: 0; color: #758078; font-size: 11px; }
.trips-state button { margin-top: 12px; padding: 9px 17px; border: 1px solid #276d62; background: transparent; color: #276d62; cursor: pointer; }
.trips-state.error { color: #a8493a; }

.trips-empty { display: grid; grid-template-columns: 240px minmax(0, 500px); place-content: center; align-items: center; gap: 55px; padding: 45px; }
.empty-mark { position: relative; display: flex; align-items: center; justify-content: center; width: 210px; height: 210px; border: 1px solid rgba(39, 109, 98, .22); border-radius: 50%; color: #276d62; font-family: 'Noto Serif SC', serif; font-size: 25px; }
.empty-mark::before { position: absolute; inset: 18px; content: ''; border: 1px dashed rgba(182, 92, 73, .24); border-radius: 50%; }
.empty-mark i { width: 44px; height: 1px; margin: 0 12px; background: #b65c49; transform: rotate(-18deg); }
.trips-empty h2 { margin: 8px 0 14px; font-family: 'Noto Serif SC', 'Songti SC', serif; font-size: 30px; font-weight: 500; }
.trips-empty > div:last-child > p:not(.travel-eyebrow) { margin: 0 0 24px; color: #68766f; font-size: 12px; line-height: 1.9; }

@keyframes spin { to { transform: rotate(360deg); } }

@media (max-width: 900px) {
  .trip-card { grid-template-columns: 48px minmax(0, 1fr) 120px; }
  .trip-card-stats { display: none; }
}

@media (max-width: 680px) {
  .trips-page { min-height: calc(100vh - 72px); padding-bottom: 60px; }
  .trips-hero, .trips-content { width: calc(100% - 28px); }
  .trips-hero { grid-template-columns: 1fr; gap: 30px; padding: 45px 0 35px; }
  .trips-hero h1 { font-size: 43px; letter-spacing: -2px; }
  .trips-intro { grid-template-columns: auto 1fr; align-items: end; gap: 0 12px; }
  .trips-intro p { margin: 0 0 6px; }
  .trips-intro button { grid-column: 1 / -1; margin-top: 12px; }
  .trips-section-head { display: grid; }
  .trips-section-head > p { text-align: left; }
  .trip-card { grid-template-columns: 42px minmax(0, 1fr); }
  .trip-card-copy { padding-right: 18px; }
  .trip-card-actions { grid-column: 1 / -1; grid-template-columns: 1fr auto; grid-template-rows: 48px; }
  .open-button { display: flex; align-items: center; justify-content: center; gap: 10px; }
  .remove-button { min-width: 92px; min-height: 48px; border-top: 0 !important; border-left: 1px solid rgba(32, 54, 48, .12) !important; }
  .trips-empty { grid-template-columns: 1fr; gap: 28px; padding: 35px 24px; }
  .empty-mark { width: 140px; height: 140px; }
}
</style>
