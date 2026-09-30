<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { drawTravelFortune, getFortuneProfile, saveFortuneProfile, saveTravelPlan } from '@/lib/travelApi'
import { useTravelPlanning } from '@/composables/useTravelPlanning'
import { buildFortunePlanRequest, fortunePlanError, fortuneProfileError, interestOptions, splitCities, localDate } from '../data/fortunePlanning'
import FortuneStick from './FortuneStick.vue'

const router = useRouter()
const { restorePlan } = useTravelPlanning()
const interests = ref([])
const visitedCitiesText = ref('')
const customInterest = ref('')
const savedDestinations = ref([])
const profileLoading = ref(true)
const profileSaving = ref(false)
const profileError = ref('')
const profileSaved = ref(false)
const preferencesOpen = ref(true)
const exploreNew = ref(true)
const destinationReason = ref('')
const previousDestination = ref('')
let disposed = false
const travelers = ref('朋友结伴')
const pace = ref('随意一些')
const budget = ref(3000)
const days = ref(2)
const date = ref(localDate())
const departure = ref('')
const engine = ref('')
const loading = ref(false)
const saving = ref(false)
const errorMessage = ref('')
const phase = ref('ready')
const drawnPlan = ref(null)
let requestSequence = 0
let pendingRequest = null
let openingTimer = null
let revealTimer = null

const engineLabel = computed(() => {
  if (loading.value) return '正在选目的地与编排行程'
  if (engine.value === 'openclaw') return 'AI 行程已生成'
  if (engine.value) return '基础行程 · AI 暂不可用'
  return '等待抽签'
})
const fortuneNumber = computed(() => {
  const source = Number(drawnPlan.value?.id) || 1
  return String((source % 99) || 99).padStart(2, '0')
})
const fortuneGrade = computed(() => {
  const score = drawnPlan.value?.match ?? 0
  if (score >= 93) return '上上签'
  if (score >= 88) return '上签'
  return '中吉签'
})
const isAnimating = computed(() => ['drawing', 'lifted', 'opening'].includes(phase.value))
const isBusy = computed(() => loading.value || isAnimating.value || saving.value || profileLoading.value || profileSaving.value)
const destination = computed(() => drawnPlan.value?.request.destination ?? '')
const planSaved = computed(() => ['SAVED', '已收藏'].includes(drawnPlan.value?.status))
const estimatedCost = computed(() => Math.max(0, Number(drawnPlan.value?.budget.total ?? 0) - Number(drawnPlan.value?.budget.reserve ?? 0)))
const preferences = computed(() => ({ interests: interests.value, visitedCities: splitCities(visitedCitiesText.value) }))
const visibleInterests = computed(() => [...new Set([...interestOptions, ...interests.value])])
const minimumBudget = computed(() => Math.max(1, Number(days.value) || 1) * 600)

async function loadProfile() {
  profileLoading.value = true
  profileError.value = ''
  try {
    const profile = await getFortuneProfile()
    if (disposed) return
    interests.value = profile.interests ?? []
    visitedCitiesText.value = (profile.visitedCities ?? []).join('、')
    savedDestinations.value = profile.savedDestinations ?? []
    preferencesOpen.value = !interests.value.length
  } catch (error) {
    if (!disposed) profileError.value = error.message || '旅行偏好读取失败。'
  } finally {
    if (!disposed) profileLoading.value = false
  }
}

function toggleInterest(interest) {
  interests.value = interests.value.includes(interest) ? interests.value.filter((item) => item !== interest) : [...interests.value, interest]
}

function addInterest() {
  const value = customInterest.value.trim()
  if (value && !interests.value.includes(value) && interests.value.length < 12) interests.value = [...interests.value, value]
  customInterest.value = ''
}

async function keepPreferences() {
  errorMessage.value = fortuneProfileError(preferences.value)
  if (errorMessage.value) return
  profileSaving.value = true
  try {
    await saveFortuneProfile(preferences.value)
    profileSaved.value = true
  } catch (error) {
    errorMessage.value = error.message || '偏好保存失败。'
  } finally {
    profileSaving.value = false
  }
}

function money(value) {
  return Number(value || 0).toLocaleString('zh-CN', { maximumFractionDigits: 0 })
}

function dayRoute(day) {
  return (day.items ?? []).map((item) => item.title || item.placeName).join(' → ')
}

function wait(milliseconds) {
  return new Promise((resolve) => window.setTimeout(resolve, milliseconds))
}

function clearAnimationTimers() {
  window.clearTimeout(openingTimer)
  window.clearTimeout(revealTimer)
}

async function drawFortune() {
  if (isBusy.value) return
  if (profileError.value) return
  const input = { preferences: preferences.value, exploreNew: exploreNew.value, previousDestination: previousDestination.value, date: date.value, days: days.value, departure: departure.value, budget: budget.value, travelers: travelers.value, pace: pace.value }
  errorMessage.value = fortunePlanError(input)
  if (errorMessage.value) return
  clearAnimationTimers()
  const sequence = ++requestSequence
  pendingRequest?.abort()
  const controller = new AbortController()
  pendingRequest = controller
  drawnPlan.value = null
  engine.value = ''
  phase.value = 'drawing'
  loading.value = true
  try {
    const [result] = await Promise.all([
      drawTravelFortune(buildFortunePlanRequest(input), { signal: controller.signal }),
      wait(1250),
    ])
    if (sequence !== requestSequence) return
    const plan = result.plan
    if (!plan?.id || plan.days?.length !== Number(input.days)) throw new Error('行程未完整生成，请重新抽签。')
    drawnPlan.value = plan
    destinationReason.value = result.destinationReason
    previousDestination.value = plan.request.destination
    profileSaved.value = true
    preferencesOpen.value = false
    engine.value = plan.engine
    phase.value = 'lifted'
    openingTimer = window.setTimeout(() => { phase.value = 'opening' }, 1100)
    revealTimer = window.setTimeout(() => { phase.value = 'revealed' }, 2060)
  } catch (error) {
    if (sequence !== requestSequence) return
    errorMessage.value = error.message || '旅行计划暂时未能生成，请重试。'
    phase.value = 'ready'
  } finally {
    if (sequence === requestSequence) {
      loading.value = false
      pendingRequest = null
    }
  }
}

function openPlan() {
  if (!drawnPlan.value) return
  restorePlan(drawnPlan.value)
  router.push({ name: 'travel-plan', query: { plan: String(drawnPlan.value.id) } })
}

async function keepPlan() {
  if (!drawnPlan.value || saving.value) return
  const sequence = requestSequence
  saving.value = true
  errorMessage.value = ''
  try {
    const saved = await saveTravelPlan(drawnPlan.value.id)
    if (sequence === requestSequence) drawnPlan.value = saved
  } catch (error) {
    if (sequence === requestSequence) errorMessage.value = error.message || '收藏失败，请重试。'
  } finally {
    saving.value = false
  }
}

function resetFortune() {
  requestSequence += 1
  pendingRequest?.abort()
  pendingRequest = null
  clearAnimationTimers()
  loading.value = false
  engine.value = ''
  phase.value = 'ready'
  drawnPlan.value = null
  destinationReason.value = ''
  profileSaved.value = false
  errorMessage.value = ''
}

watch(
  () => [
    interests.value.join('|'),
    visitedCitiesText.value,
    exploreNew.value,
    travelers.value,
    pace.value,
    budget.value,
    days.value,
    date.value,
    departure.value,
  ],
  resetFortune,
)

onBeforeUnmount(() => {
  disposed = true
  requestSequence += 1
  pendingRequest?.abort()
  clearAnimationTimers()
})

onMounted(loadProfile)
</script>

<template>
  <section class="fortune-recommendation">
    <header class="fortune-head">
      <div><span>AI TRAVEL FORTUNE</span><h3>下一程，交给山水</h3></div>
      <button class="preferences-toggle" type="button" :aria-expanded="preferencesOpen" @click="preferencesOpen = !preferencesOpen">我的旅行偏好 · {{ interests.length }} 项 {{ preferencesOpen ? '−' : '+' }}</button>
    </header>

    <p v-if="profileError" class="fortune-error" role="alert">{{ profileError }} <button type="button" @click="loadProfile">重新读取</button></p>
    <fieldset v-if="preferencesOpen" class="fortune-preferences" :disabled="isBusy || !!profileError">
      <div class="interest-picker"><span>喜欢怎样的旅行</span><div class="interest-chips"><button v-for="interest in visibleInterests" :key="interest" type="button" :class="{ selected: interests.includes(interest) }" :aria-pressed="interests.includes(interest)" @click="toggleInterest(interest)">{{ interest }}</button></div><div class="custom-interest"><input v-model="customInterest" maxlength="30" placeholder="自定义兴趣" aria-label="自定义兴趣" @keydown.enter.prevent="addInterest" /><button type="button" :disabled="!customInterest.trim() || interests.length >= 12" @click="addInterest">添加</button></div></div>
      <div class="visited-picker"><label for="visited-cities">去过的城市</label><input id="visited-cities" v-model="visitedCitiesText" placeholder="例如：杭州、上海，用顿号分隔" maxlength="1600" /><label class="explore-new"><input v-model="exploreNew" type="checkbox" />优先探索新地方</label><button class="save-preferences" type="button" @click="keepPreferences">{{ profileSaving ? '保存中…' : profileSaved ? '已保存' : '保存偏好' }}</button></div>
      <p v-if="savedDestinations.length" class="saved-intentions">参考收藏意向：{{ savedDestinations.join('、') }}</p>
    </fieldset>

    <fieldset class="fortune-controls" :disabled="isBusy" aria-label="旅行条件">
      <span :class="{ thinking: loading }"><i></i>{{ engineLabel }}</span>
      <label><small>出发</small><input v-model="date" type="date" :min="localDate()" aria-label="出发日期" /></label>
      <label class="days-control"><small>天数</small><input v-model.number="days" type="number" min="1" max="7" step="1" aria-label="出行天数" /><small>天</small></label>
      <label><small>出发地</small><input v-model.trim="departure" class="departure-input" placeholder="选填" maxlength="40" aria-label="出发地" /></label>
      <label><small>同行</small><select v-model="travelers" aria-label="同行人"><option>独自出行</option><option>情侣出行</option><option>朋友结伴</option><option>亲子出行</option><option>携长辈出行</option></select></label>
      <label><small>节奏</small><select v-model="pace" aria-label="旅行节奏"><option value="轻松一些">舒缓慢游</option><option value="随意一些">均衡体验</option><option value="丰富一点">紧凑打卡</option></select></label>
      <label class="budget-control"><small>总预算</small><b>￥</b><input v-model.number="budget" :min="minimumBudget" max="200000" step="100" type="number" aria-label="旅行预算" /></label>
    </fieldset>

    <p v-if="errorMessage" class="fortune-error" role="alert">{{ errorMessage }}</p>

    <div class="fortune-stage" :class="`phase-${phase}`">
      <section class="draw-scene">
        <span class="scene-kicker">ASK THE MOUNTAINS · 问山水</span>
        <div class="fortune-cylinder" :class="{ shaking: phase === 'drawing' }">
          <div class="rising-stick" :class="{ raised: phase !== 'ready' && phase !== 'drawing', fading: phase === 'opening' || phase === 'revealed' }"><FortuneStick :number="fortuneNumber" /></div>
          <img src="/images/fortune-cylinder-flat-v1.png" alt="旅行抽签筒" />
        </div>
        <button class="draw-fortune-button" type="button" :disabled="isBusy || !!profileError" @click="drawFortune">
          <span>{{ phase === 'revealed' ? '再抽一签' : loading ? '正在问签' : '抽取旅行签' }}</span><b>↗</b>
        </button>
        <p>一签一程 · {{ days || 1 }} 天未知的风景</p>
      </section>

      <section class="fortune-result" :class="{ 'has-plan': drawnPlan && ['opening', 'revealed'].includes(phase) }" aria-live="polite">
        <div v-if="phase === 'ready'" class="fortune-ready">
          <div class="ready-cards" aria-hidden="true"><img v-for="index in 3" :key="index" src="/images/sign-card.png" alt="" /></div>
          <span>DESTINATION LOT</span>
          <h4>静心一问，山水作答</h4>
          <p>循着你的足迹与喜好，寻找下一程。</p>
        </div>

        <div v-else-if="phase === 'drawing'" class="fortune-reading">
          <i></i><span>TRAVEL FORTUNE</span><h4>正在寻找适合你的下一程</h4><p>选定目的地后，编排 {{ days }} 天路线与预算</p>
        </div>

        <div v-else-if="phase === 'lifted'" class="lifted-lot">
          <div class="lifted-stick"><FortuneStick :number="fortuneNumber" /></div><b>第 {{ fortuneNumber }} 签</b><p>正在展开签文</p>
        </div>

        <div v-else-if="drawnPlan" class="fortune-plan-result">
        <article class="fortune-paper" :class="{ opening: phase === 'opening', revealed: phase === 'revealed' }">
          <img class="fortune-paper-bg" src="/images/fortune-lot.png" alt="" />
          <div class="fortune-paper-content">
            <span class="fortune-grade">{{ fortuneGrade }} · 第 {{ fortuneNumber }} 签</span>
            <div class="fortune-place-image"><small>下一程</small><strong>{{ destination }}</strong></div>
            <div class="fortune-copy">
              <small>{{ drawnPlan.request.days }} DAYS · {{ destination }}</small>
              <h4>{{ drawnPlan.title }}</h4>
            </div>
            <p class="fortune-reason">{{ drawnPlan.request.travelers }}<br />预计 ￥{{ money(estimatedCost) }}</p>
            <div class="fortune-actions">
              <button type="button" @click="openPlan">展开完整行程 →</button>
            </div>
          </div>
        </article>
        <section class="plan-summary" aria-label="抽签旅行计划">
          <header><span>YOUR JOURNEY</span><h4>{{ destination }} · {{ drawnPlan.request.days }} 日旅行</h4><p :title="destinationReason">{{ destinationReason }}</p></header>
          <ol class="fortune-day-list">
            <li v-for="day in drawnPlan.days" :key="day.day">
              <span>DAY {{ String(day.day).padStart(2, '0') }}</span>
              <h5>{{ day.title }}</h5>
              <p :title="dayRoute(day)">{{ dayRoute(day) }}</p>
            </li>
          </ol>
          <div class="plan-total"><span>行程预估</span><strong>￥{{ money(estimatedCost) }}</strong><small>总预算 ￥{{ money(drawnPlan.request.budget) }}</small></div>
          <p v-if="drawnPlan.engine !== 'openclaw'" class="plan-engine-note">AI 暂不可用，本次为基础路线，可稍后重抽。</p>
          <div class="plan-result-actions">
            <button type="button" @click="openPlan">查看与调整</button>
            <button type="button" :disabled="saving || planSaved" @click="keepPlan">{{ saving ? '收藏中…' : planSaved ? '已收藏' : '收藏行程' }}</button>
          </div>
        </section>
        </div>
      </section>
    </div>
  </section>
</template>

<style scoped>
.fortune-recommendation { display: grid; gap: 12px; }
.fortune-head { display: flex; align-items: end; justify-content: space-between; gap: 24px; padding: 2px 0 10px; border-bottom: 1px solid rgba(44,36,27,.14); }
.fortune-head > div > span { color: #a6533f; font: 700 8px/1 Georgia, serif; letter-spacing: 2px; }
.fortune-head h3 { margin: 5px 0 0; color: #263c35; font-family: 'Noto Serif SC', serif; font-size: 20px; font-weight: 600; letter-spacing: 1px; }
.fortune-head nav { display: flex; gap: 6px; }
.fortune-head button { padding: 8px 13px; border: 1px solid rgba(49,91,90,.16); border-radius: 999px; background: rgba(255,255,255,.28); color: #68746e; cursor: pointer; font-family: 'Noto Serif SC', serif; font-size: 9px; }
.fortune-head button.active { border-color: #315b5a; background: #315b5a; color: #fffaf1; }
.fortune-preferences { display: grid; grid-template-columns: 1.5fr 1fr; gap: 14px 28px; margin: 0; padding: 18px 22px; border: 1px solid rgba(49,91,90,.16); background: rgba(255,252,245,.7); color: #315b5a; }
.interest-picker, .visited-picker { display: flex; align-items: start; flex-wrap: wrap; gap: 10px; }
.interest-picker > span, .visited-picker > label:first-child { width: 100%; font-size: 12px; }
.interest-chips { display: flex; flex-wrap: wrap; gap: 6px; }
.fortune-preferences button { padding: 6px 11px; border: 1px solid rgba(49,91,90,.2); border-radius: 4px; background: transparent; color: #315b5a; cursor: pointer; font-family: inherit; font-size: 11px; }
.interest-chips button.selected, .fortune-preferences .save-preferences { background: #315b5a; color: #fffaf1; }
.fortune-preferences input:not([type="checkbox"]) { min-width: 0; padding: 7px 9px; border: 0; border-bottom: 1px solid rgba(49,91,90,.24); background: rgba(255,255,255,.5); color: #293f38; font: inherit; font-size: 12px; }
.custom-interest { display: flex; gap: 6px; }
.visited-picker > input { width: 100%; box-sizing: border-box; }
.explore-new { display: flex; align-items: center; gap: 5px; margin-right: auto; font-size: 11px; }
.explore-new input { accent-color: #315b5a; }
.saved-intentions { grid-column: 1 / -1; margin: 0; color: #7b7e70; font-size: 10px; }
.fortune-preferences:disabled, .fortune-preferences button:disabled { opacity: .6; }
.fortune-preferences button:focus-visible, .fortune-preferences input:focus-visible { outline: 2px solid #a6533f; outline-offset: 2px; }
.fortune-controls { display: flex; min-width: 0; min-height: 38px; flex-wrap: wrap; align-items: center; gap: 6px; margin: 0; padding: 7px 10px; border: 1px solid rgba(49,91,90,.12); background: rgba(255,252,245,.58); }
.fortune-controls > span { display: flex; min-width: 130px; align-items: center; gap: 7px; color: #315b5a; font: 700 8px/1 Georgia, 'Noto Serif SC', serif; letter-spacing: .7px; }
.fortune-controls > span i { width: 6px; height: 6px; border-radius: 50%; background: #5a8d75; box-shadow: 0 0 0 4px rgba(90,141,117,.1); }
.fortune-controls > span.thinking i { animation: status-pulse 1s ease-in-out infinite; }
.fortune-controls label { display: flex; height: 27px; align-items: center; gap: 6px; padding: 0 8px; border-left: 1px solid rgba(44,36,27,.12); }
.fortune-controls small { color: #a6533f; font-size: 8px; white-space: nowrap; }
.fortune-controls select, .fortune-controls input { border: 0; outline: 0; background: transparent; color: #2b4039; font: 500 9px/1 'Noto Serif SC', serif; }
.fortune-controls select { padding-right: 5px; cursor: pointer; }
.fortune-controls input { width: 66px; }
.fortune-controls input[type="date"] { width: 105px; }
.fortune-controls .days-control input { width: 33px; }
.fortune-controls input.departure-input { width: 65px; }
.fortune-controls:disabled { opacity: .7; }
.fortune-error { margin: 0; padding: 10px 14px; border-left: 2px solid #a6533f; background: #f5e8dc; color: #8d3e2d; font-size: 12px; }
.budget-control b { color: #263c35; font: 600 10px/1 Georgia, serif; }
.fortune-stage { display: grid; height: min(520px, calc(100vh - 355px)); min-height: 410px; grid-template-columns: minmax(260px,.75fr) minmax(500px,1.25fr); overflow: hidden; border-top: 2px solid #315b5a; background: rgba(244,239,227,.68); box-shadow: 0 18px 40px rgba(37,49,44,.1); }
.draw-scene { position: relative; display: flex; overflow: hidden; align-items: center; flex-direction: column; justify-content: flex-end; padding: 18px 32px; background: linear-gradient(180deg, rgba(25,62,53,.12), rgba(20,48,41,.5)), url('/images/mountain-card-bg.png') center / cover; }
.draw-scene::after { position: absolute; inset: 0; background: radial-gradient(circle at 50% 20%, rgba(246,226,167,.22), transparent 28%), linear-gradient(90deg, rgba(20,48,41,.12), transparent 45%, rgba(20,48,41,.18)); content: ''; pointer-events: none; }
.scene-kicker { position: absolute; z-index: 3; top: 22px; left: 25px; color: #f0b38c; font: 700 8px/1 Georgia, serif; letter-spacing: 1.6px; }
.fortune-cylinder { position: relative; z-index: 2; display: grid; width: min(235px, 66%); height: min(285px, 64%); place-items: end center; transform-origin: 50% 86%; }
.fortune-cylinder img { position: relative; z-index: 2; display: block; width: 100%; height: 100%; object-fit: contain; filter: drop-shadow(0 15px 15px rgba(12,30,25,.26)); }
.fortune-cylinder.shaking { animation: cylinder-shake .16s ease-in-out infinite alternate; }
.rising-stick { position: absolute; z-index: 1; top: 17%; left: 50.5%; width: 17px; height: 96px; filter: drop-shadow(0 3px 4px rgba(15,28,24,.16)); opacity: 0; transform: translate(-50%,50px); transition: transform .8s cubic-bezier(.2,.85,.2,1), opacity .25s ease; }
.rising-stick.raised { opacity: 1; transform: translate(-50%,-83px); }
.rising-stick.fading { opacity: 0; transform: translate(-50%,-122px); }
.draw-fortune-button { position: relative; z-index: 4; display: flex; min-width: 150px; min-height: 39px; align-items: center; justify-content: space-between; gap: 20px; padding: 0 17px; border: 1px solid rgba(255,250,237,.45); background: #a9503d; color: #fffaf1; cursor: pointer; font-family: 'Noto Serif SC', serif; font-size: 10px; letter-spacing: 1px; box-shadow: 0 10px 24px rgba(43,31,23,.18); }
.draw-fortune-button:disabled { cursor: wait; opacity: .68; }
.draw-fortune-button b { color: #f0bd92; }
.draw-scene > p { position: relative; z-index: 3; margin: 10px 0 0; color: rgba(255,255,255,.62); font-size: 8px; }
.fortune-result { position: relative; display: grid; overflow: hidden; place-items: center; background: linear-gradient(rgba(247,243,232,.88), rgba(240,234,218,.9)), url('/images/ranking-paper-v1.png') center / cover; }
.fortune-result::before { position: absolute; inset: 18px; border: 1px solid rgba(110,78,43,.13); content: ''; pointer-events: none; }
.fortune-ready { display: grid; width: 100%; place-items: center; padding: 30px; text-align: center; }
.ready-cards { position: relative; width: 230px; height: 225px; margin-bottom: 4px; }
.ready-cards img { position: absolute; top: 4px; left: 50%; width: 112px; height: 192px; object-fit: cover; border-radius: 6px; box-shadow: 0 15px 28px rgba(49,55,49,.2); transform-origin: 50% 90%; }
.ready-cards img:nth-child(1) { transform: translateX(-50%) rotate(-13deg) translateX(-42px); }
.ready-cards img:nth-child(2) { z-index: 2; transform: translateX(-50%); }
.ready-cards img:nth-child(3) { transform: translateX(-50%) rotate(13deg) translateX(42px); }
.fortune-ready > span { color: #a6533f; font: 700 8px/1 Georgia, serif; letter-spacing: 2px; }
.fortune-ready h4 { margin: 8px 0 7px; color: #263c35; font-family: 'Noto Serif SC', serif; font-size: 22px; font-weight: 600; }
.fortune-ready p { margin: 0; color: #85847b; font-size: 9px; }
.fortune-reading { display: grid; place-items: center; color: #315b5a; text-align: center; }
.fortune-reading i { width: 58px; height: 58px; margin-bottom: 18px; border: 1px solid rgba(49,91,90,.18); border-top-color: #a6533f; border-radius: 50%; animation: reading-spin 1.35s linear infinite; }
.fortune-reading span { color: #a6533f; font: 700 8px/1 Georgia, serif; letter-spacing: 2px; }
.fortune-reading h4 { margin: 9px 0 5px; font-family: 'Noto Serif SC', serif; font-size: 20px; font-weight: 600; }
.fortune-reading p, .lifted-lot p { margin: 0; color: #8a887d; font-size: 8px; }
.lifted-lot { display: grid; place-items: center; color: #315b5a; text-align: center; animation: lifted-arrive .55s ease both; }
.lifted-stick { width: 40px; height: 225px; filter: drop-shadow(0 10px 12px rgba(52,39,27,.14)); }
.lifted-lot b { margin: 15px 0 5px; color: #a6533f; font-family: 'Noto Serif SC', serif; font-size: 14px; }
.fortune-paper { position: relative; width: min(100%, 240px); height: auto; max-height: 100%; aspect-ratio: 1442 / 2464; filter: drop-shadow(0 16px 22px rgba(54,45,32,.22)); --seal-x: 55.83%; --seal-y: 47.89%; }
.fortune-paper.opening { animation: unfold-fortune .95s cubic-bezier(.2,.75,.2,1) both; }
.fortune-paper.revealed { animation: settle-fortune .35s ease-out both; }
.fortune-paper-bg { position: absolute; inset: 0; width: 100%; height: 100%; object-fit: fill; }
.fortune-paper-content { position: absolute; inset: 0; display: flex; align-items: center; flex-direction: column; color: #293f38; text-align: center; }
.fortune-grade { position: absolute; top: 12%; left: var(--seal-x); width: 70%; color: #9e4939; font: 700 8px/1 'Noto Serif SC', serif; letter-spacing: 1.5px; transform: translateX(-50%); }
/* The printed seal is centred at (805, 1180) in the 1442 x 2464 source, including transparent margins. */
.fortune-place-image { position: absolute; top: var(--seal-y); left: var(--seal-x); display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 6px; width: 40.5%; aspect-ratio: 1; margin: 0; overflow: hidden; border: 0; border-radius: 50%; background: #aa4834; color: #fff1d1; transform: translate(-50%, -50%); }
.fortune-place-image small { font-size: 8px; letter-spacing: 2px; }
.fortune-place-image strong { max-width: 90%; font-family: 'Noto Serif SC', serif; font-size: clamp(15px, 2vw, 23px); line-height: 1.25; }
.fortune-copy { position: absolute; top: 19%; left: var(--seal-x); width: 69%; margin: 0; color: #293f38; transform: translateX(-50%); }
.fortune-copy small { color: #97563d; font: 700 7px/1 Georgia, serif; letter-spacing: 1px; }
.fortune-copy h4 { display: -webkit-box; margin: 6px 0 0; overflow: hidden; font-family: 'Noto Serif SC', serif; font-size: 13px; font-weight: 700; line-height: 1.35; -webkit-box-orient: vertical; -webkit-line-clamp: 2; }
.fortune-reason { position: absolute; top: 63%; left: var(--seal-x); display: -webkit-box; width: 66%; margin: 0; padding: 5px 4px; overflow: hidden; background: rgba(250,240,213,.9); color: #4d5849; font-size: 9px; line-height: 1.7; transform: translateX(-50%); -webkit-box-orient: vertical; -webkit-line-clamp: 3; }
.fortune-actions { position: absolute; right: 7%; bottom: 9%; left: 18%; display: grid; grid-template-columns: 1fr; gap: 4px; }
.fortune-actions button { min-height: 28px; padding: 0 6px; border: 1px solid rgba(49,91,90,.22); background: rgba(250,244,225,.72); color: #315b5a; cursor: pointer; font-family: inherit; font-size: 7px; }
.fortune-actions button:last-child { border-color: #a6533f; background: #a6533f; color: #fffaf1; }
.fortune-actions i { display: inline-block; width: 0; height: 0; margin-right: 3px; border-top: 3px solid transparent; border-bottom: 3px solid transparent; border-left: 5px solid currentColor; }
.fortune-plan-result { display: grid; width: 100%; height: 100%; min-height: 0; grid-template-columns: minmax(175px,.9fr) minmax(210px,1.1fr); align-items: center; gap: 18px; padding: 24px; box-sizing: border-box; }
.plan-summary { display: flex; height: 100%; min-height: 0; flex-direction: column; gap: 12px; color: #293f38; }
.plan-summary header > span { color: #a6533f; font: 700 8px/1 Georgia, serif; letter-spacing: 1.6px; }
.plan-summary h4 { margin: 6px 0; font-family: 'Noto Serif SC', serif; font-size: 20px; }
.plan-summary header p { display: -webkit-box; overflow: hidden; margin: 0; color: #72766b; font-size: 10px; line-height: 1.7; -webkit-box-orient: vertical; -webkit-line-clamp: 2; }
.fortune-day-list { flex: 1; min-height: 70px; padding: 0 8px 0 0; margin: 0; overflow-y: auto; list-style: none; scrollbar-width: thin; scrollbar-color: #b9b49e transparent; }
.fortune-day-list li { padding: 10px 0; border-top: 1px solid rgba(92,84,65,.18); }
.fortune-day-list li > span { color: #a6533f; font: 700 9px/1 Georgia, serif; letter-spacing: 1px; }
.fortune-day-list h5 { margin: 5px 0; font-size: 12px; line-height: 1.5; }
.fortune-day-list p { margin: 0; color: #72766b; font-size: 10px; line-height: 1.8; }
.plan-total { display: flex; align-items: baseline; flex-wrap: wrap; gap: 6px; padding-top: 10px; border-top: 1px solid rgba(92,84,65,.2); }
.plan-total span, .plan-total small { color: #72766b; font-size: 9px; }
.plan-total strong { font: 600 22px/1.2 Georgia, serif; }
.plan-engine-note { margin: 0; color: #9b583e; font-size: 9px; }
.plan-result-actions { display: flex; gap: 7px; }
.plan-result-actions button { flex: 1; padding: 10px 5px; border: 1px solid #315b5a; background: #315b5a; color: #fffaf1; font-family: inherit; font-size: 11px; line-height: 1.4; cursor: pointer; }
.plan-result-actions button:last-child { background: transparent; color: #315b5a; }
.plan-result-actions button:disabled { opacity: .6; cursor: default; }
@keyframes cylinder-shake { from { transform: rotate(-2.4deg) translateY(1px); } to { transform: rotate(2.4deg) translateY(-2px); } }
@keyframes status-pulse { 50% { opacity: .35; transform: scale(.72); } }
@keyframes reading-spin { to { transform: rotate(360deg); } }
@keyframes lifted-arrive { from { opacity: 0; transform: translateY(70px); } to { opacity: 1; transform: translateY(0); } }
@keyframes unfold-fortune { 0% { clip-path: inset(0 48%); opacity: .2; transform: translateX(-90px) rotateY(72deg) scale(.86); } 62% { clip-path: inset(0 4%); opacity: 1; transform: translateX(8px) rotateY(-5deg) scale(1.03); } 100% { clip-path: inset(0); transform: none; } }
@keyframes settle-fortune { from { transform: scale(1.025); } to { transform: scale(1); } }
@media (prefers-reduced-motion: reduce) { .fortune-cylinder.shaking, .fortune-reading i, .fortune-paper.opening, .fortune-paper.revealed, .lifted-lot { animation-duration: .01ms; animation-iteration-count: 1; } }
@media (max-width: 900px) {
  .fortune-stage { height: auto; min-height: 850px; grid-template-columns: 1fr; grid-template-rows: 410px 440px; }
  .fortune-cylinder { height: 275px; }
}
@media (max-width: 680px) {
  .fortune-preferences { grid-template-columns: 1fr; padding: 14px; }
  .fortune-head { align-items: start; flex-direction: column; }
  .fortune-head nav { width: 100%; overflow-x: auto; }
  .fortune-controls { flex-wrap: wrap; }
  .fortune-controls > span { width: 100%; }
  .fortune-controls label { flex: 1; border-left: 0; border-top: 1px solid rgba(44,36,27,.1); }
  .fortune-stage { grid-template-rows: 380px 430px; }
  .draw-scene { padding-inline: 18px; }
  .fortune-stage { height: auto; grid-template-rows: 360px minmax(430px,auto); }
  .fortune-result.has-plan { overflow: visible; }
  .fortune-plan-result { grid-template-columns: 1fr; height: auto; justify-items: center; padding: 22px; }
  .fortune-paper { width: min(230px, 78%); }
  .plan-summary { width: 100%; height: auto; }
  .fortune-day-list { max-height: 280px; flex: none; }
}
</style>
