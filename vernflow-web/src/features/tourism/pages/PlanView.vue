<template>
  <main class="planner-page">
    <header class="planner-topbar">
      <div class="title-group">
        <RouterLink class="back-link" :to="{ name: 'travel-home' }">返回旅行首页</RouterLink>
        <span>AI TRAVEL STUDIO / OPENCLAW</span>
        <h1>先说说，你想怎样出发</h1>
      </div>
    </header>

    <nav class="planner-stage-rail" aria-label="旅行规划进度">
      <span :class="{ active: !messages.length }"><b>01</b> 说出想法</span>
      <i></i>
      <span :class="{ active: messages.length && !state.currentPlan }"><b>02</b> 核对条件</span>
      <i></i>
      <span :class="{ active: state.currentPlan }"><b>03</b> 收下行程</span>
    </nav>

    <div class="planner-workspace">
      <section class="assistant-panel">
        <div class="panel-heading">
          <div class="assistant-mark">灵</div>
          <div>
            <span>TRAVEL CONVERSATION</span>
            <h2>从一句真实的想法开始</h2>
            <p>目的地、预算和偏好会在对话中自动整理到右侧，不需要重复填写。</p>
          </div>
        </div>

        <div ref="chatRef" class="chat-stream">
          <div v-if="messages.length === 0" class="welcome-card">
            <span>从一句模糊的想法开始</span>
            <h3>这趟旅程，你想记住什么？</h3>
            <p>可以说一个地方、一种心情，或一件想和同行人完成的小事。其余条件，我们边聊边补全。</p>
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
            <RouterLink v-if="message.generatedPlan" class="message-plan-link" :to="{ name: 'travel-plan', query: { plan: String(state.currentPlan.id) } }">
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
              <input v-model.trim="form.destination" type="text" placeholder="例如：杭州、苏州、南京" />
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
              <span>出行天数 <small>1–7 天</small></span>
              <span class="number-input days-input">
                <input v-model.number="form.days" type="number" min="1" max="7" step="1" inputmode="numeric" />
                <b>天</b>
              </span>
            </label>
          </div>

          <fieldset class="form-row">
            <legend>旅行总预算 <small>可填写具体金额</small></legend>
            <label class="budget-input">
              <span>¥</span>
              <input
                v-model.number="form.budget"
                type="number"
                :min="minimumBudget"
                max="200000"
                step="100"
                inputmode="numeric"
                aria-label="旅行总预算"
              />
              <b>元</b>
            </label>
            <small class="input-hint">{{ form.days || 1 }} 天行程建议总预算不低于 ¥{{ minimumBudget.toLocaleString('zh-CN') }}</small>
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
            <label class="custom-text-input">
              <span>自定义</span>
              <input v-model.trim="form.travelers" type="text" maxlength="40" placeholder="例如：两位朋友和一位长辈" />
            </label>
          </fieldset>

          <fieldset class="form-row">
            <legend>旅行节奏</legend>
            <div class="chip-list">
              <button
                v-for="option in paceOptions"
                :key="option"
                type="button"
                :class="{ selected: form.pace === option }"
                @click="form.pace = option"
              >{{ option }}</button>
            </div>
            <label class="custom-text-input">
              <span>自定义</span>
              <input v-model.trim="form.pace" type="text" maxlength="40" placeholder="例如：上午充实，下午尽量轻松" />
            </label>
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
            <div v-if="customInterests.length" class="custom-interest-list" aria-label="已添加的自定义兴趣">
              <span>已添加</span>
              <button v-for="option in customInterests" :key="option" type="button" @click="toggleInterest(option)">
                {{ option }} <b>×</b>
              </button>
            </div>
            <div class="custom-interest-input">
              <input
                v-model.trim="customInterest"
                type="text"
                maxlength="40"
                placeholder="输入其他兴趣，如摄影、咖啡探店"
                @keydown.enter.prevent="addCustomInterest"
              />
              <button type="button" :disabled="!customInterest" @click="addCustomInterest">添加</button>
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
            <RouterLink :to="{ name: 'travel-plan', query: { plan: String(state.currentPlan.id) } }">查看完整日程与预算 →</RouterLink>
          </section>
        </Transition>
      </aside>
    </div>
  </main>
</template>

<script setup>
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { useTravelPlanning } from '@/composables/useTravelPlanning'
import { analyzeTravelConversation } from '@/lib/travelAssistantApi'

const route = useRoute()
const { state, interests, travelerOptions, paceOptions, updateRequest, generatePlan } = useTravelPlanning()
const chatRef = ref(null)
const input = ref('')
const customInterest = ref('')
const typing = ref(false)
const messages = ref([])
const validationError = ref('')
const recentChanges = ref([])

const quickQuestions = [
  '从上海出发，周末两天带孩子慢游杭州，想体验非遗和本地美食，预算3000元',
  '从南京出发去苏州三天，想看园林、博物馆，也想吃本地菜',
  '一个人去成都四天，想避开太赶的打卡路线，看看本地生活',
]
const form = reactive({
  destination: state.request.destination,
  departure: state.request.departure,
  date: state.request.date,
  days: Number(state.request.days),
  budget: Number(state.request.budget),
  travelers: state.request.travelers,
  pace: state.request.pace,
  interests: [...state.request.interests],
  notes: state.request.notes,
})

const minimumBudget = computed(() => Math.max(600, (Number(form.days) || 1) * 600))
const customInterests = computed(() => form.interests.filter((option) => !interests.includes(option)))
const budgetOptions = computed(() => {
  const days = Math.max(1, Number(form.days) || 1)
  return [
    { label: `轻省 ¥${(days * 800).toLocaleString('zh-CN')}`, value: days * 800 },
    { label: `舒适 ¥${(days * 1500).toLocaleString('zh-CN')}`, value: days * 1500 },
    { label: `品质 ¥${(days * 2500).toLocaleString('zh-CN')}`, value: days * 2500 },
  ]
})

function toggleInterest(option) {
  const index = form.interests.indexOf(option)
  if (index >= 0) form.interests.splice(index, 1)
  else form.interests.push(option)
}

function addCustomInterest() {
  const value = customInterest.value.trim()
  if (!value) return
  if (!form.interests.includes(value)) form.interests.push(value)
  customInterest.value = ''
}

async function handleSubmit() {
  validationError.value = ''
  if (!form.destination || !form.departure || !form.date) {
    validationError.value = '请填写目的地、出发地和出发日期。'
    return
  }
  const days = Number(form.days)
  if (!Number.isInteger(days) || days < 1 || days > 7) {
    validationError.value = '出行天数请填写 1 至 7 天的整数。'
    return null
  }
  if (!form.travelers) {
    validationError.value = '请确认同行伙伴。'
    return null
  }
  const budget = Number(form.budget)
  if (!Number.isFinite(budget) || budget < minimumBudget.value || budget > 200000) {
    validationError.value = `请填写 ¥${minimumBudget.value.toLocaleString('zh-CN')} 至 ¥200,000 之间的旅行总预算。`
    return null
  }
  if (!form.pace) {
    validationError.value = '请确认旅行节奏。'
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
  form.days = draft.days ? Number(draft.days) : ''
  form.travelers = draft.travelers || ''
  form.budget = Number(draft.budget || 0)
  form.pace = draft.pace || ''
  form.interests = [...(draft.interests || [])]
  form.notes = draft.notes || ''
}

function scrollChat() {
  nextTick(() => {
    chatRef.value?.lastElementChild?.scrollIntoView({ behavior: 'smooth', block: 'center' })
  })
}

onMounted(() => {
  const destination = typeof route.query.destination === 'string' ? route.query.destination.trim() : ''
  const focus = typeof route.query.focus === 'string' ? route.query.focus.trim() : ''
  const suggestedInterests = typeof route.query.interests === 'string'
    ? route.query.interests.split(',').map((item) => item.trim()).filter(Boolean)
    : []
  if (destination) form.destination = destination
  if (suggestedInterests.length) {
    form.interests = [...new Set([...form.interests, ...suggestedInterests])]
  }
  if (focus) {
    form.notes = [form.notes, `希望行程包含或重点考虑：${focus}。`].filter(Boolean).join('\n')
    messages.value.push({
      role: 'ai',
      text: `已经把“${focus}”作为${destination || '这次旅程'}的重点地点，并同步到右侧条件。补充出发地、同行人和预算后即可生成。`,
    })
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

@media (max-width: 900px) {
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

/* The planning flow reads like a travel journal instead of a dashboard. */
.planner-page {
  display: grid;
  grid-template-rows: auto auto minmax(0, 1fr);
  height: calc(100dvh - 77px);
  min-height: calc(100vh - 77px);
  overflow: hidden;
  background:
    linear-gradient(90deg, rgba(36, 95, 90, .026) 1px, transparent 1px),
    linear-gradient(rgba(36, 95, 90, .02) 1px, transparent 1px),
    radial-gradient(circle at 84% 7%, rgba(36, 95, 90, .12), transparent 24%),
    #f6f0e5;
  background-size: 42px 42px, 42px 42px, auto, auto;
}

.planner-topbar {
  display: flex;
  align-items: center;
  justify-content: flex-start;
  width: min(1280px, calc(100% - 72px));
  margin: 0 auto;
  padding: 12px 0 10px;
  border: 0;
  background: transparent;
  backdrop-filter: none;
}

.title-group {
  max-width: 900px;
  text-align: left;
}

.title-group .back-link {
  display: block;
  width: max-content;
  margin-bottom: 7px;
  color: var(--teal-deep);
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 1px;
}

.title-group .back-link::before {
  margin-right: 8px;
  content: "←";
  color: var(--terracotta);
}

.title-group > span {
  color: var(--terracotta-deep);
  font-size: 9px;
  font-weight: 700;
  letter-spacing: 2.8px;
}

.title-group h1 {
  margin: 3px 0 0;
  color: var(--ink);
  font-family: 'Noto Serif SC', 'Songti SC', serif;
  font-size: clamp(26px, 2.1vw, 32px);
  font-weight: 600;
  letter-spacing: -1.5px;
  line-height: 1.08;
}

.planner-stage-rail {
  display: grid;
  grid-template-columns: auto 1fr auto 1fr auto;
  align-items: center;
  gap: 18px;
  width: min(1280px, calc(100% - 72px));
  margin: 0 auto 13px;
  padding: 9px 0;
  border-top: 1px solid rgba(44, 36, 27, .14);
  border-bottom: 1px solid rgba(44, 36, 27, .14);
}

.planner-stage-rail span {
  color: rgba(44, 36, 27, .42);
  font-family: 'Noto Serif SC', serif;
  font-size: 12px;
  letter-spacing: 1px;
  white-space: nowrap;
}

.planner-stage-rail span b {
  margin-right: 7px;
  color: inherit;
  font-family: Georgia, serif;
  font-size: 10px;
}

.planner-stage-rail span.active {
  color: var(--teal-deep);
  font-weight: 700;
}

.planner-stage-rail i {
  height: 1px;
  background: rgba(44, 36, 27, .12);
}

.planner-workspace {
  display: grid;
  grid-template-columns: minmax(0, 1.08fr) minmax(0, .92fr);
  align-items: stretch;
  gap: 24px;
  width: min(1280px, calc(100% - 72px));
  height: auto;
  min-height: 0;
  margin: 0 auto;
  padding-bottom: 18px;
}

.assistant-panel,
.conditions-panel {
  min-width: 0;
  height: 100%;
  overflow: hidden;
  border: 1px solid rgba(44, 36, 27, .1);
  border-radius: 2px;
  background: rgba(255, 253, 248, .78);
  box-shadow: 0 18px 48px rgba(44, 36, 27, .07);
  backdrop-filter: blur(10px);
}

.assistant-panel {
  display: flex;
  flex-direction: column;
  border-top: 4px solid var(--terracotta);
}

.panel-heading {
  gap: 17px;
  padding: 16px 20px;
  border-bottom: 1px solid rgba(44, 36, 27, .13);
}

.assistant-mark {
  width: 44px;
  height: 48px;
  border-radius: 50% 50% 46% 46%;
  background: var(--teal-deep);
  font-size: 21px;
  box-shadow: 4px 4px 0 rgba(193, 101, 70, .4);
}

.panel-heading span {
  color: var(--terracotta-deep);
  font-family: Georgia, serif;
  font-size: 9px;
  font-weight: 700;
  letter-spacing: 2px;
}

.panel-heading h2 {
  margin: 4px 0 3px;
  font-size: 20px;
}

.panel-heading p {
  font-size: 11px;
}

.chat-stream {
  display: flex;
  min-height: 0;
  flex: 1;
  padding: 18px;
  overflow-y: auto;
}

.welcome-card {
  display: flex;
  max-width: none;
  min-height: 100%;
  margin: 0;
  padding: 28px clamp(24px, 3.5vw, 42px);
  flex-direction: column;
  justify-content: center;
  border: 0;
  border-left: 4px solid var(--terracotta);
  border-radius: 0;
  background:
    radial-gradient(circle at 88% 18%, rgba(36, 95, 90, .13), transparent 28%),
    rgba(255, 253, 248, .68);
  text-align: left;
}

.welcome-card > span {
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 2px;
}

.welcome-card h3 {
  max-width: 540px;
  margin: 14px 0 13px;
  font-size: clamp(27px, 2.5vw, 36px);
  line-height: 1.35;
}

.welcome-card p {
  max-width: 590px;
  font-size: 13px;
}

.quick-questions {
  display: grid;
  justify-content: stretch;
  gap: 4px;
  margin-top: 17px;
}

.quick-questions button {
  padding: 8px 0;
  border: 0;
  border-bottom: 1px solid rgba(36, 95, 90, .16);
  border-radius: 0;
  background: transparent;
  font-size: 12px;
  line-height: 1.55;
  text-align: left;
}

.quick-questions button::after {
  float: right;
  content: "↗";
  color: var(--terracotta);
}

.message {
  max-width: 76%;
  padding: 16px 19px;
  border-radius: 2px;
}

.message.user {
  border-bottom-right-radius: 0;
  background: var(--terracotta);
}

.message.ai {
  border: 1px solid rgba(44, 36, 27, .1);
  border-left: 3px solid var(--teal);
  border-bottom-left-radius: 0;
  background: rgba(255, 253, 248, .84);
}

.message p {
  font-size: 14px;
}

.chat-composer {
  position: static;
  z-index: 4;
  margin: 0 18px 18px;
  padding: 12px 14px 10px;
  border: 1px solid rgba(44, 36, 27, .18);
  border-radius: 2px;
  background: rgba(255, 253, 248, .96);
  box-shadow: 0 18px 45px rgba(44, 36, 27, .12);
  backdrop-filter: blur(14px);
}

.chat-composer textarea {
  min-height: 46px;
  font-size: 13px;
}

.chat-composer button {
  min-width: 92px;
  padding: 10px 20px;
  border-radius: 2px;
  background: var(--teal-deep);
  font-family: 'Noto Serif SC', serif;
}

.conditions-panel {
  position: static;
  max-height: none;
  padding: 21px 24px 22px;
  overflow-y: auto;
  border-top: 4px solid var(--teal-deep);
  background: rgba(255, 253, 248, .88);
  box-shadow: 0 22px 60px rgba(44, 36, 27, .08);
}

.conditions-heading {
  align-items: start;
  margin-bottom: 15px;
}

.conditions-heading h2 {
  margin-top: 6px;
  font-size: 22px;
}

.conditions-heading > div > span {
  color: var(--terracotta-deep);
}

.conditions-form {
  gap: 13px;
}

.form-row label > span,
.conditions-form .notes-field > span,
.conditions-form legend {
  margin-bottom: 5px;
  color: var(--ink);
  font-size: 11px;
  font-weight: 700;
}

.conditions-form input,
.conditions-form select {
  padding: 8px 0;
  border: 0;
  border-bottom: 1px solid rgba(44, 36, 27, .24);
  border-radius: 0;
  background: transparent;
  font-size: 13px;
}

.conditions-form input:focus,
.conditions-form select:focus {
  border-bottom-color: var(--teal-deep);
}

.form-row label > span small {
  margin-left: 4px;
  color: var(--ink-soft);
  font-size: 9px;
  font-weight: 400;
}

.number-input {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: center;
  border-bottom: 1px solid rgba(44, 36, 27, .24);
}

.number-input input {
  width: 100%;
  border: 0;
}

.number-input b {
  color: var(--teal-deep);
  font-size: 11px;
}

.budget-input {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: baseline;
  gap: 8px;
  margin: 2px 0 7px;
  padding: 8px 11px 7px;
  border: 1px solid rgba(36, 95, 90, .22);
  background: rgba(247, 242, 233, .58);
}

.form-row .budget-input > span {
  margin: 0;
  color: var(--terracotta-deep);
  font-family: Georgia, serif;
  font-size: 19px;
  font-weight: 700;
}

.budget-input input {
  min-width: 0;
  padding: 0;
  border: 0;
  color: var(--ink);
  font-family: Georgia, 'Noto Serif SC', serif;
  font-size: 22px;
  font-weight: 700;
}

.budget-input b {
  color: var(--ink-soft);
  font-size: 10px;
}

.input-hint {
  display: block;
  margin: 0 0 6px;
  color: var(--ink-soft);
  font-size: 9px;
  line-height: 1.5;
}

.custom-text-input {
  display: grid;
  grid-template-columns: 48px minmax(0, 1fr);
  align-items: center;
  gap: 7px;
  margin-top: 9px;
}

.form-row .custom-text-input > span {
  margin: 0;
  color: var(--terracotta-deep);
  font-size: 10px;
}

.custom-text-input input {
  min-width: 0;
  padding: 8px 0;
  font-size: 11px;
}

.custom-interest-input {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 8px;
  margin-top: 9px;
}

.custom-interest-list {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 9px;
}

.custom-interest-list > span {
  margin-right: 2px;
  color: var(--ink-soft);
  font-size: 9px;
}

.custom-interest-list button {
  padding: 5px 8px;
  border: 1px solid rgba(201, 91, 62, .22);
  border-radius: 2px;
  background: rgba(201, 91, 62, .07);
  color: var(--terracotta-deep);
  font-size: 10px;
}

.custom-interest-list button b {
  margin-left: 4px;
  font-weight: 400;
}

.custom-interest-input input {
  min-width: 0;
  padding: 9px 0;
  font-size: 11px;
}

.custom-interest-input button {
  padding: 0 13px;
  border: 1px solid rgba(36, 95, 90, .28);
  border-radius: 2px;
  background: transparent;
  color: var(--teal-deep);
  font-size: 10px;
  font-weight: 700;
}

.custom-interest-input button:disabled {
  cursor: not-allowed;
  opacity: .4;
}

.conditions-form .notes-field {
  display: grid;
  gap: 5px;
  width: 100%;
  padding: 0;
  border: 0;
  border-radius: 0;
  background: transparent;
}

.conditions-form .notes-field textarea {
  min-height: 50px;
  padding: 8px 10px;
  border: 1px solid rgba(44, 36, 27, .16);
  background: rgba(247, 242, 233, .64);
  font-size: 12px;
}

.chip-list button {
  padding: 6px 9px;
  border-radius: 2px;
  background: transparent;
  font-size: 11px;
}

.chip-list button:hover,
.chip-list button.selected {
  border-color: var(--teal-deep);
  background: var(--teal-deep);
}

.generate-button {
  min-height: 44px;
  padding: 0 18px;
  border-radius: 2px;
  box-shadow: 4px 4px 0 rgba(36, 95, 90, .55);
  font-size: 14px;
}

.plan-preview {
  margin-top: 25px;
  padding: 20px;
  border-radius: 2px;
  background: #e4eee7;
}

@media (max-width: 1050px) {
  .planner-page {
    display: block;
    height: auto;
    min-height: calc(100vh - 77px);
    padding-bottom: 40px;
    overflow: visible;
  }

  .planner-topbar,
  .planner-stage-rail,
  .planner-workspace {
    width: min(100% - 40px, 760px);
  }

  .planner-topbar {
    align-items: start;
    padding-top: 52px;
  }

  .planner-workspace {
    grid-template-columns: 1fr;
    padding-bottom: 0;
  }

  .assistant-panel,
  .conditions-panel {
    height: auto;
    overflow: visible;
    border-radius: 0;
  }

  .assistant-panel { min-height: 620px; }

  .chat-stream {
    min-height: 430px;
    overflow: visible;
  }

  .welcome-card { min-height: 0; }

  .conditions-panel {
    position: static;
    border-radius: 0;
  }
}

@media (max-width: 680px) {
  .planner-page { padding-bottom: 50px; }
  .planner-topbar,
  .planner-stage-rail,
  .planner-workspace { width: calc(100% - 28px); }
  .planner-topbar { display: grid; gap: 23px; padding: 35px 0 28px; }
  .title-group { grid-column: auto; grid-row: auto; }
  .title-group h1 { font-size: 28px; letter-spacing: -1.5px; }
  .planner-stage-rail { gap: 7px; margin-bottom: 25px; }
  .planner-stage-rail span { font-size: 10px; letter-spacing: 0; }
  .planner-stage-rail span b { display: none; }
  .planner-workspace { margin-top: 0; }
  .panel-heading { padding: 0 0 20px; }
  .assistant-mark { width: 45px; height: 49px; }
  .panel-heading h2 { font-size: 20px; }
  .panel-heading p { font-size: 11px; }
  .chat-stream { min-height: 390px; padding: 23px 0 17px; }
  .welcome-card { padding: 31px 20px; }
  .welcome-card h3 { font-size: 30px; }
  .message { max-width: 91%; }
  .chat-composer { margin: 0; }
  .chat-composer span { display: none; }
  .conditions-panel { padding: 24px 19px; }
  .two-columns { grid-template-columns: 1fr 1fr; }
}
</style>
