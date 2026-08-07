<template>
  <main class="planner-page">
    <header class="planner-topbar">
      <RouterLink class="back-link" :to="{ name: 'travel-home' }">← 返回首页</RouterLink>
      <div class="title-group">
        <span>AI TRAVEL STUDIO</span>
        <h1>和山水灵一起，把想法变成行程</h1>
      </div>
      <div class="planner-demo-actions">
        <span class="service-state"><i></i>{{ engineLabel }}</span>
        <button type="button" :disabled="demoRunning || typing || state.isGenerating" @click="runDemo">
          {{ demoRunning ? '演示进行中…' : '一键生成演示行程' }}
        </button>
      </div>
    </header>

    <div class="planner-workspace">
      <section class="assistant-panel">
        <div class="panel-heading">
          <div class="assistant-mark">灵</div>
          <div>
            <h2>旅行规划助手</h2>
            <p>先聊聊想去哪里、为什么出发，以及不希望旅程太累还是太赶。</p>
          </div>
        </div>

        <div ref="chatRef" class="chat-stream">
          <div v-if="messages.length === 0" class="welcome-card">
            <span>从一句模糊的想法开始就可以</span>
            <h3>这次旅行，你最想获得什么感受？</h3>
            <p>我会帮你梳理目的地、节奏和体验偏好。右侧条件可以随时修改，不必一次想完整。</p>
            <div class="quick-questions">
              <button v-for="question in quickQuestions" :key="question" type="button" @click="sendQuick(question)">
                {{ question }}
              </button>
            </div>
          </div>

          <article v-for="(message, index) in messages" :key="`${message.role}-${index}`" :class="['message', message.role]">
            <span>{{ message.role === 'user' ? '你' : '山水灵' }}</span>
            <p>{{ message.text }}</p>
            <div v-if="message.changes?.length" class="message-sync">
              <small>已同步到右侧</small>
              <div>
                <b v-for="change in message.changes" :key="change.field">{{ change.label }} · {{ change.value }}</b>
              </div>
            </div>
            <p v-if="message.missingFields?.length" class="message-missing">
              待确认：{{ message.missingFields.join('、') }}
            </p>
            <button v-if="message.readyToGenerate" class="message-generate" type="button" :disabled="state.isGenerating" @click="handleSubmit">
              {{ state.isGenerating ? '正在生成…' : '按这些条件生成行程 →' }}
            </button>
            <RouterLink v-if="message.generatedPlan" class="message-plan-link" :to="{ name: 'travel-plan' }">
              查看完整日程与预算 →
            </RouterLink>
          </article>

          <article v-if="typing" class="message ai typing-message">
            <span>山水灵</span>
            <p><i></i><i></i><i></i></p>
          </article>
        </div>

        <form class="chat-composer" @submit.prevent="sendMessage">
          <textarea
            v-model.trim="input"
            rows="2"
            placeholder="例如：从上海出发，周末带孩子体验杭州非遗，不想安排得太赶……"
            @keydown.enter.exact.prevent="sendMessage"
          ></textarea>
          <div>
            <span>Enter 发送 · Shift + Enter 换行</span>
            <button type="submit" :disabled="!input || typing">发送</button>
          </div>
        </form>
      </section>

      <aside class="conditions-panel">
        <div class="conditions-heading">
          <div>
            <span>TRAVEL CONDITIONS</span>
            <h2>本次出行条件</h2>
          </div>
          <small>{{ recentChanges.length ? 'AI 已同步' : '可随时修改' }}</small>
        </div>

        <Transition name="sync-banner">
          <div v-if="recentChanges.length" class="condition-sync-banner">
            <span>本轮已更新</span>
            <b v-for="change in recentChanges" :key="change.field">{{ change.label }}</b>
          </div>
        </Transition>

        <form class="conditions-form" @submit.prevent="handleSubmit">
          <div class="form-row two-columns">
            <label>
              <span>目的地</span>
              <input v-model.trim="form.destination" type="text" placeholder="目前支持杭州" />
            </label>
            <label>
              <span>出发地</span>
              <input v-model.trim="form.departure" type="text" placeholder="例如：上海" />
            </label>
          </div>

          <div class="form-row two-columns">
            <label>
              <span>出发日期</span>
              <input v-model="form.date" type="date" />
            </label>
            <label>
              <span>出行天数</span>
              <select v-model="form.days">
                <option value="1">1 天</option>
                <option value="2">2 天</option>
              </select>
            </label>
          </div>

          <fieldset class="form-row">
            <legend>预算范围</legend>
            <div class="chip-list">
              <button
                v-for="option in budgetOptions"
                :key="option.value"
                type="button"
                :class="{ selected: form.budget === option.value }"
                @click="form.budget = option.value"
              >{{ option.label }}</button>
            </div>
          </fieldset>

          <fieldset class="form-row">
            <legend>同行伙伴</legend>
            <div class="chip-list">
              <button
                v-for="option in travelerOptions"
                :key="option"
                type="button"
                :class="{ selected: form.travelers === option }"
                @click="form.travelers = option"
              >{{ option }}</button>
            </div>
          </fieldset>

          <fieldset class="form-row">
            <legend>旅行偏好 <small>可多选</small></legend>
            <div class="chip-list">
              <button
                v-for="option in interests"
                :key="option"
                type="button"
                :class="{ selected: form.interests.includes(option) }"
                @click="toggleInterest(option)"
              >{{ option }}</button>
            </div>
          </fieldset>

          <label class="notes-field">
            <span>其他要求</span>
            <textarea v-model.trim="form.notes" rows="2" placeholder="如：有老人同行、下午安排轻松一些"></textarea>
          </label>

          <p v-if="validationError || state.error" class="form-error">{{ validationError || state.error }}</p>
          <button class="generate-button" type="submit" :disabled="state.isGenerating">
            <span>{{ state.isGenerating ? '正在生成方案…' : state.currentPlan ? '根据新条件重新生成' : '生成我的旅行方案' }}</span>
            <b>→</b>
          </button>
        </form>

        <Transition name="plan-reveal">
          <section v-if="state.currentPlan" class="plan-preview">
            <div class="preview-topline">
              <span>已生成 · V{{ String(state.currentPlan.version).padStart(2, '0') }}</span>
              <b>适配度 {{ state.currentPlan.match }}%</b>
            </div>
            <h3>{{ state.currentPlan.title }}</h3>
            <p>{{ state.currentPlan.summary }}</p>
            <div class="preview-meta">
              <span>{{ state.currentPlan.request.days }} 天</span>
              <span>预算 ¥{{ Number(state.currentPlan.budget.total).toLocaleString('zh-CN') }}</span>
              <span>{{ state.currentPlan.request.travelers }}</span>
            </div>
            <RouterLink :to="{ name: 'travel-plan' }">查看完整日程与预算 →</RouterLink>
          </section>
        </Transition>
      </aside>
    </div>
  </main>
</template>

<script setup>
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useTravelPlanning } from '@/composables/useTravelPlanning'
import { analyzeTravelConversation } from '@/lib/travelAssistantApi'

const route = useRoute()
const router = useRouter()
const { state, interests, travelerOptions, updateRequest, generatePlan } = useTravelPlanning()
const chatRef = ref(null)
const input = ref('')
const typing = ref(false)
const messages = ref([])
const validationError = ref('')
const recentChanges = ref([])
const assistantEngine = ref('local')
const demoRunning = ref(false)

const engineLabel = computed(() => assistantEngine.value === 'openclaw' ? 'OpenClaw 规划中枢' : '结构化规划服务')

const budgetOptions = [
  { label: '¥1,200 左右', value: 1200 },
  { label: '¥3,000 左右', value: 3000 },
  { label: '¥4,500 左右', value: 4500 },
]

const quickQuestions = [
  '从上海出发，周末两天带孩子慢游杭州，想体验非遗和本地美食，预算3000元',
  '带孩子体验非遗，怎样安排不累？',
  '一个人从上海去杭州，想避开热门打卡点，看看本地生活',
]
const demoQuestion = quickQuestions[0]

const form = reactive({
  destination: state.request.destination,
  departure: state.request.departure,
  date: state.request.date,
  days: String(state.request.days),
  budget: Number(state.request.budget),
  travelers: state.request.travelers,
  pace: state.request.pace,
  interests: [...state.request.interests],
  notes: state.request.notes,
})

function toggleInterest(option) {
  const index = form.interests.indexOf(option)
  if (index >= 0) form.interests.splice(index, 1)
  else form.interests.push(option)
}

async function handleSubmit() {
  validationError.value = ''
  if (!form.destination || !form.departure || !form.date) {
    validationError.value = '请填写目的地、出发地和出发日期。'
    return
  }
  if (!form.travelers) {
    validationError.value = '请确认同行伙伴。'
    return null
  }
  if (!form.budget || !form.pace) {
    validationError.value = '请确认预算和旅行节奏。'
    return null
  }
  if (!form.interests.length) {
    validationError.value = '请至少选择一项旅行偏好。'
    return null
  }

  updateRequest({ ...form, interests: [...form.interests] })
  const plan = await generatePlan()
  if (plan) {
    messages.value.push({
      role: 'ai',
      text: `行程已经生成：${plan.title}。我保留了预算余量和调整入口，你可以先查看完整日程，再回来继续修改。`,
      generatedPlan: true,
    })
    scrollChat()
  }
  return plan
}

async function sendMessage() {
  const question = input.value.trim()
  if (!question || typing.value) return
  input.value = ''
  await sendQuestion(question)
}

async function sendQuick(question) {
  if (typing.value) return
  await sendQuestion(question)
}

async function sendQuestion(question) {
  const history = messages.value.map((message) => ({
    role: message.role === 'ai' ? 'assistant' : 'user',
    text: message.text,
  }))
  messages.value.push({ role: 'user', text: question })
  typing.value = true
  scrollChat()

  try {
    const result = await analyzeTravelConversation(question, currentDraft(), history)
    applyDraft(result.draft)
    recentChanges.value = [...(result.changes || [])]
    assistantEngine.value = result.engine || 'local'
    messages.value.push({
      role: 'ai',
      text: result.reply || '我已经整理了这次需求，请检查右侧出行条件。',
      changes: result.changes || [],
      missingFields: result.missingFields || [],
      readyToGenerate: Boolean(result.readyToGenerate),
    })
    return result
  } catch (error) {
    messages.value.push({
      role: 'ai',
      text: error.message || '文旅助手暂时不可用，你仍可在右侧填写条件并生成方案。',
    })
    return null
  } finally {
    typing.value = false
    scrollChat()
  }
}

async function runDemo() {
  if (demoRunning.value || typing.value || state.isGenerating) return
  demoRunning.value = true
  validationError.value = ''
  try {
    const result = await sendQuestion(demoQuestion)
    if (!result?.readyToGenerate) return
    const plan = await handleSubmit()
    if (plan) await router.push({ name: 'travel-plan' })
  } finally {
    demoRunning.value = false
  }
}

function currentDraft() {
  return {
    destination: form.destination,
    departure: form.departure,
    date: form.date,
    days: form.days,
    travelers: form.travelers,
    budget: form.budget,
    pace: form.pace,
    interests: [...form.interests],
    notes: form.notes,
  }
}

function applyDraft(draft) {
  if (!draft) return
  form.destination = draft.destination || ''
  form.departure = draft.departure || ''
  form.date = draft.date || ''
  form.days = draft.days ? String(draft.days) : ''
  form.travelers = draft.travelers || ''
  form.budget = Number(draft.budget || 0)
  form.pace = draft.pace || ''
  form.interests = [...(draft.interests || [])]
  form.notes = draft.notes || ''
}

function scrollChat() {
  nextTick(() => {
    if (chatRef.value) chatRef.value.scrollTop = chatRef.value.scrollHeight
  })
}

onMounted(() => {
  if (route.query.demo === '1') {
    window.setTimeout(runDemo, 260)
    return
  }
  const question = typeof route.query.q === 'string' ? route.query.q.trim() : ''
  if (question) sendQuick(question)
})
</script>

<style scoped>
.planner-page {
  position: relative;
  isolation: isolate;
  min-height: calc(100vh - 77px);
  background:
    radial-gradient(circle at 8% 16%, rgba(193, 127, 90, .14), transparent 27%),
    radial-gradient(circle at 92% 8%, rgba(49, 91, 90, .13), transparent 30%),
    #eee7da;
  color: var(--ink);
}

.planner-topbar {
  display: grid;
  grid-template-columns: 190px 1fr 190px;
  align-items: center;
  gap: 18px;
  padding: 18px clamp(20px, 4vw, 56px);
  border-bottom: 1px solid rgba(44, 36, 27, .1);
  background: rgba(249, 246, 238, .68);
  backdrop-filter: blur(16px);
}

.planner-demo-actions {
  display: grid;
  justify-items: end;
  gap: 7px;
}

.planner-demo-actions button {
  padding: 7px 12px;
  border: 1px solid rgba(49, 91, 90, .25);
  border-radius: 999px;
  background: rgba(49, 91, 90, .08);
  color: var(--teal-deep);
  cursor: pointer;
  font-family: 'Noto Serif SC', serif;
  font-size: 10px;
  letter-spacing: .6px;
}

.planner-demo-actions button:disabled { cursor: wait; opacity: .55; }

.back-link {
  color: var(--ink-soft);
  font-size: 12px;
  letter-spacing: 1px;
  text-decoration: none;
}

.title-group {
  text-align: center;
}

.title-group > span,
.conditions-heading span {
  color: var(--teal);
  font-size: 9px;
  letter-spacing: 3px;
}

.title-group h1 {
  margin-top: 4px;
  font-family: 'Noto Serif SC', serif;
  font-size: clamp(20px, 2.4vw, 30px);
  font-weight: 600;
  letter-spacing: 2px;
}

.service-state {
  justify-self: end;
  color: var(--ink-soft);
  font-size: 11px;
  letter-spacing: 1px;
}

.service-state i {
  display: inline-block;
  width: 7px;
  height: 7px;
  margin-right: 7px;
  border-radius: 50%;
  background: #5d8b70;
  box-shadow: 0 0 0 4px rgba(93, 139, 112, .12);
}

.planner-workspace {
  display: grid;
  grid-template-columns: minmax(0, 1.15fr) minmax(430px, .85fr);
  gap: 18px;
  width: min(1520px, calc(100% - 48px));
  height: calc(100vh - 178px);
  min-height: 650px;
  margin: 18px auto 0;
}

.assistant-panel,
.conditions-panel {
  min-width: 0;
  overflow: hidden;
  border: 1px solid rgba(44, 36, 27, .1);
  border-radius: 20px 20px 0 0;
  background: rgba(251, 248, 241, .8);
  box-shadow: 0 18px 60px rgba(77, 65, 48, .08);
  backdrop-filter: blur(14px);
}

.assistant-panel {
  display: flex;
  flex-direction: column;
}

.panel-heading {
  display: flex;
  gap: 14px;
  align-items: center;
  padding: 19px 22px;
  border-bottom: 1px solid rgba(44, 36, 27, .08);
}

.assistant-mark {
  display: grid;
  width: 43px;
  height: 43px;
  place-items: center;
  flex-shrink: 0;
  border-radius: 13px;
  background: var(--teal);
  color: #f7f1e6;
  font-family: 'Noto Serif SC', serif;
  font-size: 20px;
  box-shadow: inset 0 0 0 4px rgba(255, 255, 255, .1);
}

.panel-heading h2,
.conditions-heading h2 {
  margin-bottom: 4px;
  font-family: 'Noto Serif SC', serif;
  font-size: 18px;
  font-weight: 600;
  letter-spacing: 1px;
}

.panel-heading p {
  color: var(--ink-soft);
  font-size: 11px;
  line-height: 1.6;
}

.chat-stream {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 16px;
  padding: 22px;
  overflow-y: auto;
}

.welcome-card {
  max-width: 570px;
  margin: auto;
  padding: 30px;
  border: 1px solid rgba(49, 91, 90, .13);
  border-radius: 18px;
  background: linear-gradient(145deg, rgba(255, 255, 255, .76), rgba(229, 237, 229, .58));
  text-align: center;
}

.welcome-card > span {
  color: var(--terracotta-deep);
  font-size: 10px;
  letter-spacing: 2px;
}

.welcome-card h3 {
  margin: 10px 0;
  font-family: 'Noto Serif SC', serif;
  font-size: clamp(21px, 2.2vw, 29px);
  font-weight: 600;
}

.welcome-card p {
  color: var(--ink-soft);
  font-size: 12px;
  line-height: 1.8;
}

.quick-questions {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 8px;
  margin-top: 20px;
}

.quick-questions button {
  padding: 8px 13px;
  border: 1px solid rgba(49, 91, 90, .2);
  border-radius: 999px;
  background: rgba(255, 255, 255, .58);
  color: var(--teal-deep);
  cursor: pointer;
  font-size: 11px;
}

.message {
  max-width: 78%;
  padding: 13px 16px;
  border-radius: 15px;
}

.message > span {
  display: block;
  margin-bottom: 5px;
  font-size: 10px;
  letter-spacing: 1px;
  opacity: .7;
}

.message p {
  white-space: pre-wrap;
  font-size: 13px;
  line-height: 1.75;
}

.message.user {
  align-self: flex-end;
  border-bottom-right-radius: 4px;
  background: var(--terracotta);
  color: #fffaf2;
}

.message.ai {
  align-self: flex-start;
  border: 1px solid rgba(44, 36, 27, .07);
  border-bottom-left-radius: 4px;
  background: #fffdf8;
}

.message-sync {
  margin-top: 11px;
  padding-top: 10px;
  border-top: 1px solid rgba(49, 91, 90, .1);
}

.message-sync > small {
  display: block;
  margin-bottom: 7px;
  color: var(--teal);
  font-size: 9px;
  letter-spacing: 1px;
}

.message-sync > div {
  display: flex;
  flex-wrap: wrap;
  gap: 5px;
}

.message-sync b {
  padding: 4px 7px;
  border-radius: 6px;
  background: rgba(49, 91, 90, .08);
  color: var(--teal-deep);
  font-size: 9px;
  font-weight: 500;
}

.message .message-missing {
  margin-top: 9px;
  color: var(--terracotta-deep);
  font-size: 10px;
}

.message-generate,
.message-plan-link {
  display: inline-block;
  margin-top: 11px;
  padding: 8px 12px;
  border: 0;
  border-radius: 8px;
  background: var(--teal);
  color: #fffaf2;
  cursor: pointer;
  font-family: 'Noto Serif SC', serif;
  font-size: 10px;
  letter-spacing: .6px;
  text-decoration: none;
}

.message-generate:disabled {
  cursor: wait;
  opacity: .55;
}

.typing-message p {
  display: flex;
  gap: 5px;
  padding: 3px 0;
}

.typing-message i {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: var(--teal);
  animation: typing 1.1s infinite alternate;
}

.typing-message i:nth-child(2) { animation-delay: .18s; }
.typing-message i:nth-child(3) { animation-delay: .36s; }

.chat-composer {
  margin: 0 18px 18px;
  padding: 11px 13px 9px;
  border: 1px solid rgba(44, 36, 27, .14);
  border-radius: 15px;
  background: #fffdf8;
  box-shadow: 0 10px 35px rgba(44, 36, 27, .07);
}

.chat-composer textarea,
.notes-field textarea {
  width: 100%;
  resize: none;
  border: 0;
  outline: 0;
  background: transparent;
  color: var(--ink);
  font-family: 'Noto Sans SC', sans-serif;
  font-size: 13px;
  line-height: 1.6;
}

.chat-composer > div {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 5px;
}

.chat-composer span {
  color: rgba(44, 36, 27, .42);
  font-size: 9px;
}

.chat-composer button {
  padding: 8px 20px;
  border: 0;
  border-radius: 10px;
  background: var(--teal);
  color: #fffaf2;
  cursor: pointer;
  font-size: 12px;
  letter-spacing: 2px;
}

.chat-composer button:disabled { cursor: not-allowed; opacity: .4; }

.conditions-panel {
  padding: 22px;
  overflow-y: auto;
}

.conditions-heading {
  display: flex;
  align-items: end;
  justify-content: space-between;
  margin-bottom: 19px;
}

.conditions-heading small {
  color: var(--ink-soft);
  font-size: 10px;
}

.condition-sync-banner {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
  margin: -5px 0 16px;
  padding: 9px 10px;
  border-left: 2px solid var(--teal);
  background: rgba(49, 91, 90, .06);
}

.condition-sync-banner span {
  margin-right: 3px;
  color: var(--teal-deep);
  font-size: 9px;
  letter-spacing: 1px;
}

.condition-sync-banner b {
  padding: 3px 6px;
  border-radius: 999px;
  background: rgba(255, 255, 255, .65);
  color: var(--ink-soft);
  font-size: 9px;
  font-weight: 500;
}

.conditions-form {
  display: grid;
  gap: 17px;
}

.form-row,
.conditions-form fieldset {
  min-width: 0;
  border: 0;
}

.two-columns {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}

.form-row label,
.notes-field {
  display: grid;
  gap: 7px;
}

.form-row label > span,
.notes-field > span,
.conditions-form legend {
  margin-bottom: 7px;
  color: var(--ink-soft);
  font-size: 11px;
  letter-spacing: .6px;
}

.conditions-form legend small {
  opacity: .55;
}

.conditions-form input,
.conditions-form select,
.notes-field {
  width: 100%;
  padding: 10px 12px;
  border: 1px solid rgba(44, 36, 27, .13);
  border-radius: 10px;
  outline: 0;
  background: rgba(255, 253, 248, .78);
  color: var(--ink);
  font-family: 'Noto Sans SC', sans-serif;
  font-size: 12px;
}

.conditions-form input:focus,
.conditions-form select:focus,
.notes-field:focus-within {
  border-color: var(--teal);
}

.chip-list {
  display: flex;
  flex-wrap: wrap;
  gap: 7px;
}

.chip-list button {
  padding: 7px 11px;
  border: 1px solid rgba(44, 36, 27, .12);
  border-radius: 999px;
  background: rgba(255, 253, 248, .6);
  color: var(--ink-soft);
  cursor: pointer;
  font-size: 11px;
  transition: all .2s;
}

.chip-list button:hover,
.chip-list button.selected {
  border-color: var(--terracotta);
  background: var(--terracotta);
  color: #fffaf2;
}

.form-error {
  color: #a34434;
  font-size: 11px;
  line-height: 1.5;
}

.generate-button {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  padding: 13px 17px;
  border: 0;
  border-radius: 11px;
  background: var(--terracotta);
  color: #fffaf2;
  cursor: pointer;
  font-family: 'Noto Serif SC', serif;
  font-size: 13px;
  letter-spacing: 1.5px;
}

.generate-button b { font-size: 18px; font-weight: 400; }
.generate-button:disabled { cursor: wait; opacity: .6; }

.plan-preview {
  margin-top: 22px;
  padding: 18px;
  border: 1px solid rgba(49, 91, 90, .18);
  border-radius: 14px;
  background: linear-gradient(140deg, rgba(226, 237, 229, .68), rgba(255, 253, 248, .8));
}

.preview-topline {
  display: flex;
  justify-content: space-between;
  color: var(--teal);
  font-size: 9px;
  letter-spacing: 1px;
}

.plan-preview h3 {
  margin: 9px 0 6px;
  font-family: 'Noto Serif SC', serif;
  font-size: 20px;
}

.plan-preview > p {
  color: var(--ink-soft);
  font-size: 11px;
  line-height: 1.7;
}

.preview-meta {
  display: flex;
  gap: 7px;
  margin: 12px 0;
}

.preview-meta span {
  padding: 5px 8px;
  border-radius: 6px;
  background: rgba(255, 255, 255, .58);
  font-size: 10px;
}

.plan-preview a {
  color: var(--terracotta-deep);
  font-size: 11px;
  text-decoration: none;
}

.plan-reveal-enter-active { transition: opacity .4s, transform .4s; }
.plan-reveal-enter-from { opacity: 0; transform: translateY(10px); }
.sync-banner-enter-active { transition: opacity .28s, transform .28s; }
.sync-banner-enter-from { opacity: 0; transform: translateY(-6px); }

@keyframes typing {
  from { opacity: .25; transform: translateY(2px); }
  to { opacity: 1; transform: translateY(-2px); }
}

@media (max-width: 1050px) {
  .planner-workspace {
    grid-template-columns: 1fr;
    height: auto;
    min-height: 0;
    padding-bottom: 30px;
  }
  .assistant-panel { min-height: 620px; border-radius: 20px; }
  .conditions-panel { border-radius: 20px; }
}

@media (max-width: 680px) {
  .planner-topbar { grid-template-columns: 1fr auto; padding: 14px 16px; }
  .title-group { grid-column: 1 / -1; grid-row: 1; }
  .back-link { grid-column: 1; grid-row: 2; }
  .service-state { grid-column: 2; grid-row: 2; }
  .planner-workspace { width: calc(100% - 24px); margin-top: 12px; }
  .assistant-panel { min-height: 660px; }
  .panel-heading { padding: 15px; }
  .chat-stream { padding: 15px; }
  .welcome-card { padding: 22px 16px; }
  .message { max-width: 90%; }
  .chat-composer { margin: 0 10px 10px; }
  .chat-composer span { display: none; }
  .conditions-panel { padding: 18px 15px; }
  .two-columns { grid-template-columns: 1fr; }
}
</style>
