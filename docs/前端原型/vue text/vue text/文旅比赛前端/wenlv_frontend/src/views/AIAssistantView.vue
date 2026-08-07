<template>
  <div class="ai-page">
    <!-- 顶部栏 -->
    <header class="ai-topbar">
      <button class="back-btn" @click="goHome">← 返回首页</button>
      <h2 class="ai-title font-brush">AI 智能助手</h2>
      <span class="ai-sub">山水灵 · 你的山水文旅向导</span>
    </header>

    <!-- 对话区域 -->
    <div class="ai-chat" ref="chatRef">
      <!-- 欢迎消息 -->
      <div v-if="messages.length === 0" class="ai-welcome">
        <img class="welcome-icon" src="/images/AI页面.png" alt="AI助手" />
        <p class="welcome-text font-brush">山水灵 AI 助手</p>
        <p class="welcome-hint">你可以问我关于风景、美食、路线攻略、非遗文化等任何文旅问题</p>
        <div class="quick-asks">
          <button
            v-for="q in quickQuestions"
            :key="q"
            class="quick-chip"
            @click="sendQuick(q)"
          >{{ q }}</button>
        </div>
      </div>

      <!-- 消息列表 -->
      <div
        v-for="(msg, idx) in messages"
        :key="idx"
        class="msg-row"
        :class="msg.role"
      >
        <div class="msg-bubble">
          <span class="msg-role">{{ msg.role === 'user' ? '你' : '山水灵' }}</span>
          <p class="msg-text">{{ msg.text }}</p>
        </div>
      </div>

      <!-- 输入中 -->
      <div v-if="typing" class="msg-row ai">
        <div class="msg-bubble typing">
          <span class="msg-role">山水灵</span>
          <p class="msg-text"><span class="dot-pulse">●●●</span></p>
        </div>
      </div>
    </div>

    <!-- 底部输入区 -->
    <div class="ai-input-bar">
      <input
        v-model.trim="input"
        type="text"
        placeholder="输入你的问题，比如：苏州有哪些必去的园林？"
        class="ai-input"
        @keydown.enter="sendMsg"
      />
      <button class="ai-send-btn" @click="sendMsg" :disabled="!input">
        发送
      </button>
    </div>
  </div>
</template>

<script setup>
import { ref, nextTick, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'

const router = useRouter()
const route = useRoute()

const chatRef = ref(null)
const input = ref('')
const typing = ref(false)
const messages = ref([])

const quickQuestions = [
  '苏州有哪些必去的园林？',
  '推荐一条三日游路线',
  '平江路有哪些非遗手作体验？',
  '苏州本地人都去哪吃面？',
]

// 预设回复（后续替换为真实 AI 接口）
const mockReply = (q) => {
  const replies = {
    '苏州有哪些必去的园林？': '苏州园林以拙政园、留园、狮子林、沧浪亭为代表。拙政园是最大最经典的一座，建议清晨去人少。留园以空间布局精妙著称，狮子林的假山迷宫特别有趣。各园林之间距离不远，一天可以逛 2-3 个。',
    '推荐一条三日游路线': 'Day1：拙政园 → 苏州博物馆 → 平江路（午餐本帮菜）→ 耦园 → 山塘街夜景。Day2：虎丘 → 留园 → 观前街（小吃）→ 网师园夜花园。Day3：同里/周庄水乡一日游，晚上返程。建议提前预约园林门票！',
    '平江路有哪些非遗手作体验？': '平江路上可以体验苏绣、桃花坞木版年画、核雕、苏扇制作等非遗项目。推荐"绣娘丝绸"体验苏绣基础针法，"桃花坞木版年画博物馆"可以亲手印一张年画带回家。这些体验一般需要提前预约，时长 1-2 小时。',
    '苏州本地人都去哪吃面？': '苏州人吃面讲究"头汤面"，推荐：同得兴（枫镇大肉面）、朱鸿兴（焖肉面）、陆长兴（爆鱼面）、裕兴记（两面黄）。另外哑巴生煎、潘玉麟糖粥也是本地人常去的老字号。',
  }
  return replies[q] || '这是个好问题！让我为你详细解答……（AI 接口接入后此处将返回实时回复）'
}

function goHome() {
  router.push('/')
}

function sendMsg() {
  const text = input.value
  if (!text) return

  messages.value.push({ role: 'user', text })
  input.value = ''

  typing.value = true
  scrollBottom()

  // 模拟 AI 延迟
  setTimeout(() => {
    typing.value = false
    messages.value.push({ role: 'ai', text: mockReply(text) })
    scrollBottom()
  }, 800 + Math.random() * 600)
}

function sendQuick(q) {
  messages.value.push({ role: 'user', text: q })
  typing.value = true
  scrollBottom()

  setTimeout(() => {
    typing.value = false
    messages.value.push({ role: 'ai', text: mockReply(q) })
    scrollBottom()
  }, 800 + Math.random() * 600)
}

function scrollBottom() {
  nextTick(() => {
    if (chatRef.value) {
      chatRef.value.scrollTop = chatRef.value.scrollHeight
    }
  })
}

onMounted(() => {
  // 如果是从首页搜索跳转过来的，自动发送问题
  const q = route.query.q
  if (q) {
    sendQuick(q)
  }
})
</script>

<style scoped>
.ai-page {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 88px); /* 减去 AppHeader 高度 */
  background: #f5efe3;
}

/* ---- 顶部栏 ---- */
.ai-topbar {
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
.ai-title {
  font-size: 26px;
  color: var(--ink);
  letter-spacing: 2px;
}
.ai-sub {
  font-family: 'Noto Sans SC', sans-serif;
  font-size: 12px;
  color: var(--ink-soft);
  letter-spacing: 1px;
}

/* ---- 对话区域 ---- */
.ai-chat {
  flex: 1;
  overflow-y: auto;
  padding: 28px 32px;
  display: flex;
  flex-direction: column;
  gap: 18px;
}

/* 欢迎区 */
.ai-welcome {
  text-align: center;
  margin: auto;
  max-width: 480px;
}
.welcome-icon {
  width: 180px;
  height: 180px;
  object-fit: contain;
  margin-bottom: 12px;
  background: transparent;
  mix-blend-mode: multiply;
}
.welcome-text {
  font-size: 30px;
  color: var(--ink);
  letter-spacing: 2px;
  margin-bottom: 8px;
}
.welcome-hint {
  font-family: 'Noto Sans SC', sans-serif;
  font-size: 13px;
  color: var(--ink-soft);
  line-height: 1.7;
  margin-bottom: 24px;
}
.quick-asks {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  justify-content: center;
}
.quick-chip {
  padding: 9px 18px;
  border-radius: 999px;
  border: 1px solid rgba(193, 127, 90, .35);
  background: rgba(193, 127, 90, .06);
  color: var(--terracotta-deep);
  font-family: 'Noto Sans SC', sans-serif;
  font-size: 12.5px;
  cursor: pointer;
  letter-spacing: .5px;
  transition: all .2s;
}
.quick-chip:hover {
  background: var(--terracotta);
  color: #fefaf5;
  border-color: var(--terracotta);
}

/* 消息行 */
.msg-row {
  display: flex;
}
.msg-row.user {
  justify-content: flex-end;
}
.msg-row.ai {
  justify-content: flex-start;
}

.msg-bubble {
  max-width: 72%;
  padding: 14px 18px;
  border-radius: 16px;
}
.msg-row.user .msg-bubble {
  background: var(--terracotta);
  color: #fefaf5;
  border-bottom-right-radius: 4px;
}
.msg-row.ai .msg-bubble {
  background: #fff;
  color: var(--ink);
  border-bottom-left-radius: 4px;
  box-shadow: 0 4px 14px -8px rgba(44, 38, 32, .12);
}
.msg-role {
  display: block;
  font-family: 'Noto Sans SC', sans-serif;
  font-size: 11px;
  letter-spacing: 1px;
  margin-bottom: 6px;
  opacity: .7;
}
.msg-row.user .msg-role { color: rgba(255, 255, 255, .8); }
.msg-row.ai .msg-role { color: var(--terracotta); }

.msg-text {
  font-family: 'Noto Sans SC', sans-serif;
  font-size: 14px;
  line-height: 1.75;
  white-space: pre-wrap;
}

/* 输入中动画 */
.dot-pulse {
  letter-spacing: 3px;
  animation: pulse 1.2s infinite;
}
@keyframes pulse {
  0%, 100% { opacity: .3; }
  50% { opacity: 1; }
}

/* ---- 底部输入区 ---- */
.ai-input-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 32px 18px;
  background: #fff;
  border-top: 1px solid rgba(44, 38, 32, .07);
  flex-shrink: 0;
}
.ai-input {
  flex: 1;
  padding: 13px 18px;
  border-radius: 12px;
  border: 1px solid rgba(44, 38, 32, .15);
  background: #faf7f0;
  font-family: 'Noto Sans SC', sans-serif;
  font-size: 14px;
  color: var(--ink);
  outline: none;
  transition: border-color .2s;
}
.ai-input::placeholder {
  color: rgba(44, 38, 32, .35);
}
.ai-input:focus {
  border-color: var(--terracotta);
}
.ai-send-btn {
  padding: 13px 28px;
  border-radius: 12px;
  border: none;
  background: var(--terracotta);
  color: #fefaf5;
  font-family: 'Noto Sans SC', sans-serif;
  font-size: 14px;
  letter-spacing: 2px;
  cursor: pointer;
  transition: background .2s;
  white-space: nowrap;
}
.ai-send-btn:hover {
  background: var(--terracotta-deep);
}
.ai-send-btn:disabled {
  opacity: .45;
  cursor: not-allowed;
}

@media (max-width: 640px) {
  .ai-topbar {
    padding: 12px 16px;
    gap: 10px;
  }
  .ai-title { font-size: 22px; }
  .ai-sub { display: none; }
  .ai-chat { padding: 20px 16px; }
  .ai-input-bar { padding: 12px 16px 16px; }
  .msg-bubble { max-width: 88%; }
}
</style>
