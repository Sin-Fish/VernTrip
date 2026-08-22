<script setup>
import { computed, onMounted, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { useTravelPlanning } from "@/composables/useTravelPlanning";
import TripRouteMap from "@/features/tourism/components/TripRouteMap.vue";

const route = useRoute();
const router = useRouter();
const { state, adjustCurrentPlan, loadPlan, saveCurrentPlan } = useTravelPlanning();
const activeDay = ref(1);
const adjustmentOpen = ref(false);
const adjustment = ref({ type: "pace", note: "" });
const nodeAdjustmentOpen = ref(false);
const selectedNode = ref(null);
const nodeAdjustment = ref({ action: "replace", note: "", time: "" });
const savedNotice = ref("");

const plan = computed(() => state.currentPlan);
const selectedDay = computed(() => plan.value?.days.find((day) => day.day === activeDay.value) ?? plan.value?.days[0]);
const nodeSubmitLabel = computed(() => {
  if (state.isGenerating) return "正在调整此节点…";
  if (nodeAdjustment.value.action === "delete") return "确认删除此节点";
  if (nodeAdjustment.value.action === "time") return "保存新的到达时间";
  return "让 OpenClaw 替换此节点";
});
const engineSteps = computed(() => {
  if (plan.value?.engine === "openclaw") {
    return ["OpenClaw 需求理解", "OpenClaw 结构化编排", "后端可信校验"];
  }
  if (plan.value?.engine === "local-fallback") {
    return ["OpenClaw 暂不可用", "本地规则回退", "后端可信校验"];
  }
  return ["结构化需求", "本地规则编排", "后端可信校验"];
});

onMounted(() => {
  window.scrollTo({ top: 0, behavior: "auto" });
});

watch(
  () => route.query.plan,
  async (planId) => {
    if (!planId) {
      if (!state.currentPlan) await router.replace({ name: "travel-planner" });
      return;
    }
    const loaded = await loadPlan(planId);
    if (loaded?.days?.length) activeDay.value = loaded.days[0].day;
  },
  { immediate: true },
);

async function syncPlanUrl(nextPlan) {
  if (!nextPlan || String(route.query.plan ?? "") === String(nextPlan.id)) return;
  await router.replace({ name: "travel-plan", query: { plan: String(nextPlan.id) } });
}

function budgetWidth(value) {
  if (!plan.value?.budget.total) {
    return 0;
  }
  return `${Math.min(100, Math.round((value / plan.value.budget.total) * 100))}%`;
}

async function applyAdjustment() {
  const nextPlan = await adjustCurrentPlan({
    type: adjustment.value.type,
    note: adjustment.value.note.trim(),
  });
  if (!nextPlan) {
    return;
  }
  await syncPlanUrl(nextPlan);
  activeDay.value = 1;
  adjustmentOpen.value = false;
  adjustment.value.note = "";
  savedNotice.value = "已根据你的要求生成新版本";
}

function openNodeAdjustment(item) {
  state.error = "";
  selectedNode.value = {
    ...item,
    day: selectedDay.value.day,
  };
  nodeAdjustment.value = {
    action: "replace",
    note: "",
    time: item.time,
  };
  nodeAdjustmentOpen.value = true;
  savedNotice.value = "";
}

function closeNodeAdjustment() {
  if (state.isGenerating) return;
  nodeAdjustmentOpen.value = false;
  selectedNode.value = null;
}

async function applyNodeAdjustment() {
  if (!selectedNode.value) return;
  const currentDay = selectedNode.value.day;
  const payload = {
    type: "node",
    action: nodeAdjustment.value.action,
    itemId: selectedNode.value.itemId,
  };
  if (nodeAdjustment.value.action === "replace") {
    payload.note = nodeAdjustment.value.note.trim();
  }
  if (nodeAdjustment.value.action === "time") {
    payload.time = nodeAdjustment.value.time;
  }

  const previousTitle = selectedNode.value.title;
  const nextPlan = await adjustCurrentPlan(payload);
  if (!nextPlan) return;

  await syncPlanUrl(nextPlan);
  activeDay.value = currentDay;
  nodeAdjustmentOpen.value = false;
  selectedNode.value = null;
  savedNotice.value = nodeAdjustment.value.action === "replace"
    ? `已局部替换“${previousTitle}”，其他节点保持不变`
    : nodeAdjustment.value.action === "delete"
      ? `已删除“${previousTitle}”，路线与预算已更新`
      : `已调整“${previousTitle}”的时间，地图路线已同步`;
}

async function savePlan() {
  if (plan.value?.status === "已收藏") {
    savedNotice.value = "这份行程已经收藏";
    return;
  }
  const saved = await saveCurrentPlan();
  if (saved) savedNotice.value = "已收藏，可继续调整这份行程";
}

function startOver() {
  router.push({ name: "travel-planner" });
}
</script>

<template>
  <main v-if="plan" class="travel-app plan-page">
    <section class="plan-hero">
      <div class="plan-hero-copy">
        <p class="travel-eyebrow">PLAN / V{{ plan.version.toString().padStart(2, "0") }}</p>
        <h1>{{ plan.title }}</h1>
        <p>{{ plan.summary }}</p>
        <div class="plan-meta"><span>{{ plan.request.date }}</span><i></i><span>{{ plan.request.days }} 天</span><i></i><span>{{ plan.request.travelers }}</span></div>
        <div :class="['plan-tech-line', { fallback: plan.engine !== 'openclaw' }]">
          <template v-for="(step, index) in engineSteps" :key="step">
            <span>{{ step }}</span><i v-if="index < engineSteps.length - 1"></i>
          </template>
        </div>
      </div>
      <div class="plan-hero-side">
        <div class="match-stamp"><span>适配度</span><b>{{ plan.match }}<small>%</small></b><em>根据本次出行需求</em></div>
        <div class="plan-hero-actions">
          <button class="plan-save-button" type="button" :disabled="state.isSaving" @click="savePlan">
            {{ state.isSaving ? "保存中…" : plan.status === "已收藏" ? "已收藏" : "收藏这份行程" }}
          </button>
          <button type="button" @click="startOver">重新规划</button>
        </div>
      </div>
    </section>

    <section class="plan-body">
      <div class="plan-main-column">
        <section class="plan-why">
          <span>为什么这样安排</span>
          <ul><li v-for="reason in plan.reasons" :key="reason">{{ reason }}</li></ul>
        </section>

        <section class="itinerary-section">
          <div class="section-heading itinerary-heading">
            <div><p class="travel-eyebrow">ITINERARY</p><h2>按自己的节奏走</h2></div>
            <div class="day-tabs">
              <button v-for="day in plan.days" :key="day.day" :class="{ active: activeDay === day.day }" type="button" @click="activeDay = day.day">
                <span>DAY {{ day.day }}</span><b>{{ day.title }}</b>
              </button>
            </div>
          </div>
          <div class="day-story-layout">
            <article class="day-plan">
              <div class="day-plan-title">
                <span>DAY {{ selectedDay.day }}</span>
                <h3>{{ selectedDay.title }}</h3>
                <p>点选任一站可局部调整，其他安排保持不变</p>
              </div>
              <div class="route-overview" aria-label="当日路线速览">
                <span v-for="(item, index) in selectedDay.items" :key="`route-${item.itemId}`">
                  <small>{{ item.time }}</small><b>{{ item.title }}</b><i v-if="index < selectedDay.items.length - 1">→</i>
                </span>
              </div>
              <ol class="timeline">
                <li v-for="item in selectedDay.items" :key="item.itemId">
                  <time>{{ item.time }}</time>
                  <span :class="['timeline-dot', item.tone]"></span>
                  <div class="timeline-copy">
                    <small>{{ item.label }} · {{ item.tag }}</small>
                    <h4>{{ item.title }}</h4>
                    <p>{{ item.description }}</p>
                    <b v-if="item.cost">预计 ¥ {{ item.cost.toLocaleString("zh-CN") }}</b>
                    <button
                      class="node-adjust-button"
                      type="button"
                      :aria-label="`调整节点 ${item.title}`"
                      @click="openNodeAdjustment(item)"
                    >
                      调整此节点 <span>↗</span>
                    </button>
                  </div>
                </li>
              </ol>
            </article>
            <div class="day-map-column">
              <TripRouteMap
                v-if="selectedDay"
                :day="selectedDay"
                :city="plan.request.destination"
              />
            </div>
          </div>
        </section>

        <section class="plan-risks">
          <span>出发前留意</span>
          <p v-for="risk in plan.risks" :key="risk">{{ risk }}</p>
        </section>
      </div>

      <aside class="plan-side-column">
        <section class="budget-card">
          <div class="budget-card-head"><span>预算安排</span><b>¥ {{ plan.budget.total.toLocaleString("zh-CN") }}</b></div>
          <p>这份计划预计使用 ¥ {{ (plan.budget.total - plan.budget.reserve).toLocaleString("zh-CN") }}，保留部分弹性预算。</p>
          <div class="budget-bars">
            <div><span>住宿</span><i><b :style="{ width: budgetWidth(plan.budget.stay) }"></b></i><em>¥ {{ plan.budget.stay }}</em></div>
            <div><span>餐饮</span><i><b :style="{ width: budgetWidth(plan.budget.food) }"></b></i><em>¥ {{ plan.budget.food }}</em></div>
            <div><span>体验</span><i><b :style="{ width: budgetWidth(plan.budget.activity) }"></b></i><em>¥ {{ plan.budget.activity }}</em></div>
            <div><span>交通</span><i><b :style="{ width: budgetWidth(plan.budget.transportation) }"></b></i><em>¥ {{ plan.budget.transportation }}</em></div>
          </div>
          <div class="budget-reserve">留作自由安排 ¥ {{ plan.budget.reserve.toLocaleString("zh-CN") }}</div>
        </section>

        <section class="plan-actions-card">
          <p>计划总会随着心情改变。</p>
          <button class="travel-button travel-button-primary" type="button" @click="adjustmentOpen = true">整体调整这份计划 <span>→</span></button>
          <span v-if="savedNotice" class="saved-notice">{{ savedNotice }}</span>
          <span v-if="state.error" class="field-error">{{ state.error }}</span>
        </section>

        <section class="sources-card">
          <p>使用了 {{ plan.sources.length }} 条已整理资源</p>
          <div v-for="source in plan.sources.slice(0, 4)" :key="source.name" class="source-row"><span>{{ source.type }}</span><b>{{ source.name }}</b></div>
        </section>
      </aside>
    </section>

    <div v-if="nodeAdjustmentOpen && selectedNode" class="adjustment-backdrop" @click.self="closeNodeAdjustment">
      <section class="adjustment-sheet node-adjustment-sheet" aria-label="调整单个行程节点">
        <button class="sheet-close" type="button" aria-label="关闭节点调整" @click="closeNodeAdjustment">×</button>
        <p class="travel-eyebrow">ADJUST ONE STOP / DAY {{ selectedNode.day }}</p>
        <div class="selected-node-summary">
          <span>{{ selectedNode.time }}</span>
          <div><small>当前选择</small><h2>{{ selectedNode.title }}</h2></div>
          <em>其余节点已锁定</em>
        </div>

        <div class="adjustment-options node-action-options" role="group" aria-label="节点调整方式">
          <button :class="{ selected: nodeAdjustment.action === 'replace' }" type="button" @click="nodeAdjustment.action = 'replace'">AI 替换</button>
          <button :class="{ selected: nodeAdjustment.action === 'time' }" type="button" @click="nodeAdjustment.action = 'time'">调整时间</button>
          <button :class="{ selected: nodeAdjustment.action === 'delete' }" type="button" @click="nodeAdjustment.action = 'delete'">删除节点</button>
        </div>

        <label v-if="nodeAdjustment.action === 'replace'" class="planner-field notes-field">
          <span>希望替换成什么样的安排</span>
          <textarea v-model="nodeAdjustment.note" rows="3" placeholder="例如：换成适合雨天的室内场馆，距离下一站近一些"></textarea>
          <small>OpenClaw 只会从同类型、已审核且当前行程未使用的资源中选择。</small>
        </label>
        <label v-else-if="nodeAdjustment.action === 'time'" class="planner-field node-time-field">
          <span>新的到达时间</span>
          <input v-model="nodeAdjustment.time" type="time" />
          <small>仅修改该节点时间，节点内容和其他安排保持不变。</small>
        </label>
        <div v-else class="node-delete-warning">
          <strong>删除后将重新连接前后路线</strong>
          <p>系统会生成一个新版本，并重新计算当天路线与预算，不会覆盖当前版本。</p>
        </div>

        <p v-if="state.error" class="field-error">{{ state.error }}</p>
        <button
          class="travel-button travel-button-primary sheet-submit"
          type="button"
          :disabled="state.isGenerating || (nodeAdjustment.action === 'time' && !nodeAdjustment.time)"
          @click="applyNodeAdjustment"
        >
          {{ nodeSubmitLabel }} <span>→</span>
        </button>
      </section>
    </div>

    <div v-if="adjustmentOpen" class="adjustment-backdrop" @click.self="adjustmentOpen = false">
      <section class="adjustment-sheet" aria-label="调整旅行计划">
        <button class="sheet-close" type="button" aria-label="关闭" @click="adjustmentOpen = false">×</button>
        <p class="travel-eyebrow">ADJUST THE PLAN</p>
        <h2>哪一部分，想换个方式？</h2>
        <p>我们会保留已经喜欢的安排，只调整与你这次想法有关的部分。</p>
        <div class="adjustment-options">
          <button :class="{ selected: adjustment.type === 'pace' }" type="button" @click="adjustment.type = 'pace'">节奏慢一点</button>
          <button :class="{ selected: adjustment.type === 'budget' }" type="button" @click="adjustment.type = 'budget'">预算少一点</button>
          <button :class="{ selected: adjustment.type === 'hotel' }" type="button" @click="adjustment.type = 'hotel'">换更实惠的住处</button>
        </div>
        <label class="planner-field notes-field"><span>或者直接告诉我们</span><textarea v-model="adjustment.note" rows="3" placeholder="例如：第二天下午想安排得更轻松一些"></textarea></label>
        <p v-if="state.error" class="field-error">{{ state.error }}</p>
        <button class="travel-button travel-button-primary sheet-submit" type="button" :disabled="state.isGenerating" @click="applyAdjustment">
          {{ state.isGenerating ? "正在重新安排…" : "只调整这些部分 →" }}
        </button>
      </section>
    </div>
  </main>
  <main v-else class="plan-restore-page">
    <div v-if="state.isLoadingPlan" class="plan-restore-card">
      <i></i>
      <p class="travel-eyebrow">RESTORING JOURNEY</p>
      <h1>正在取回这份行程</h1>
      <span>地图节点、预算和历史版本会一起恢复。</span>
    </div>
    <div v-else class="plan-restore-card error">
      <b>!</b>
      <p class="travel-eyebrow">JOURNEY UNAVAILABLE</p>
      <h1>暂时无法打开这份行程</h1>
      <span>{{ state.error || "行程不存在，或当前账号没有访问权限。" }}</span>
      <button type="button" @click="router.replace({ name: 'travel-trips' })">返回我的行程</button>
    </div>
  </main>
</template>

<style scoped>
.plan-restore-page {
  display: grid;
  min-height: calc(100vh - 77px);
  place-items: center;
  padding: 30px;
  background:
    radial-gradient(circle at 50% 30%, rgba(39, 109, 98, .12), transparent 32%),
    #f6f0e5;
}

.plan-restore-card {
  display: grid;
  min-width: min(460px, 90vw);
  justify-items: center;
  padding: 55px 40px;
  border-top: 4px solid #276d62;
  background: rgba(255, 253, 248, .82);
  box-shadow: 0 24px 60px rgba(40, 71, 64, .1);
  text-align: center;
}

.plan-restore-card > i {
  width: 34px;
  height: 34px;
  margin-bottom: 24px;
  border: 2px solid rgba(39, 109, 98, .17);
  border-top-color: #276d62;
  border-radius: 50%;
  animation: restore-spin .8s linear infinite;
}

.plan-restore-card > b {
  display: grid;
  width: 38px;
  height: 38px;
  margin-bottom: 22px;
  place-items: center;
  border: 1px solid #b65c49;
  border-radius: 50%;
  color: #b65c49;
  font: 700 20px/1 Georgia, serif;
}

.plan-restore-card h1 {
  margin: 9px 0 13px;
  color: #20362f;
  font-family: 'Noto Serif SC', 'Songti SC', serif;
  font-size: 31px;
  font-weight: 500;
}

.plan-restore-card > span { color: #758078; font-size: 12px; }
.plan-restore-card button { margin-top: 25px; padding: 10px 18px; border: 1px solid #276d62; background: #276d62; color: #fffdf8; cursor: pointer; font-family: inherit; }
.plan-restore-card.error { border-top-color: #b65c49; }

@keyframes restore-spin { to { transform: rotate(360deg); } }

.plan-page {
  min-height: calc(100vh - 77px);
  padding-bottom: 90px;
  background:
    linear-gradient(90deg, rgba(36, 95, 90, .026) 1px, transparent 1px),
    linear-gradient(rgba(36, 95, 90, .02) 1px, transparent 1px),
    radial-gradient(circle at 82% 5%, rgba(36, 95, 90, .12), transparent 24%),
    #f6f0e5;
  background-size: 42px 42px, 42px 42px, auto, auto;
}

.plan-hero {
  position: relative;
  display: grid;
  grid-template-columns: minmax(0, 1fr) 230px;
  align-items: end;
  gap: clamp(40px, 7vw, 100px);
  width: min(1280px, calc(100% - 72px));
  padding: 76px 0 55px;
  border-bottom: 1px solid rgba(31, 41, 39, .2);
}

.plan-hero::before {
  position: absolute;
  top: 45px;
  right: 260px;
  width: 180px;
  height: 180px;
  content: "";
  border: 1px solid rgba(36, 95, 90, .08);
  border-radius: 50%;
  box-shadow: inset 0 0 0 22px rgba(255, 253, 248, .16);
  pointer-events: none;
}

.plan-hero-copy {
  position: relative;
  z-index: 1;
  max-width: 850px;
}

.plan-hero h1 {
  max-width: 820px;
  margin-top: 9px;
  font-size: clamp(44px, 5.5vw, 74px);
  letter-spacing: -4px;
  line-height: 1.12;
}

.plan-hero > .plan-hero-copy > p:not(.travel-eyebrow) {
  max-width: 720px;
  margin-top: 20px;
  font-size: 14px;
  line-height: 1.9;
}

.plan-meta {
  margin-top: 25px;
  font-size: 13px;
}

.plan-tech-line {
  margin-top: 16px;
  font-size: 10px;
}

.plan-hero-side {
  position: relative;
  z-index: 1;
  display: grid;
  justify-items: center;
  gap: 24px;
}

.match-stamp {
  width: 155px;
  height: 155px;
  flex-basis: 155px;
  background: rgba(255, 253, 248, .44);
}

.plan-hero-actions {
  display: grid;
  width: 100%;
  gap: 8px;
}

.plan-hero-actions button {
  min-height: 41px;
  padding: 0 13px;
  border: 1px solid rgba(36, 95, 90, .26);
  border-radius: 2px;
  background: transparent;
  color: var(--travel-forest);
  font-size: 11px;
  font-weight: 800;
}

.plan-hero-actions .plan-save-button {
  border-color: var(--travel-forest);
  background: var(--travel-forest);
  color: #fffdf8;
  box-shadow: 4px 4px 0 rgba(201, 91, 62, .58);
}

.plan-body {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: 43px;
  width: min(1280px, calc(100% - 72px));
  padding: 46px 0 0;
}

.plan-why {
  grid-template-columns: 160px minmax(0, 1fr);
  gap: 28px;
  padding: 25px 0 30px;
  border-top-width: 4px;
}

.plan-why > span {
  font-family: var(--travel-serif);
  font-size: 15px;
}

.plan-why ul {
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 20px;
}

.plan-why li {
  font-size: 12px;
}

.itinerary-section {
  margin-top: 70px;
}

.itinerary-heading {
  display: flex;
  align-items: end;
  justify-content: space-between;
  gap: 32px;
  margin-bottom: 31px;
}

.itinerary-heading h2 {
  font-size: clamp(31px, 3.4vw, 46px);
  letter-spacing: -2px;
}

.day-tabs {
  gap: 8px;
}

.day-tabs button {
  min-width: 145px;
  padding: 12px 14px;
  border: 1px solid rgba(31, 41, 39, .13);
  border-radius: 2px;
  background: rgba(255, 253, 248, .52);
}

.day-tabs button b {
  max-width: 150px;
  margin-top: 2px;
  font-size: 12px;
}

.day-story-layout {
  display: grid;
  grid-template-columns: minmax(350px, .72fr) minmax(540px, 1.28fr);
  align-items: start;
  gap: 24px;
}

.day-plan {
  padding: 31px 30px 24px;
  border: 0;
  border-top: 4px solid var(--travel-clay);
  background: rgba(255, 253, 248, .86);
  box-shadow: 0 20px 50px rgba(40, 71, 64, .07);
}

.day-plan-title {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: 5px 12px;
  align-items: baseline;
  padding-bottom: 23px;
}

.day-plan-title h3 {
  font-size: 27px;
}

.day-plan-title p {
  grid-column: 1 / -1;
  margin: 6px 0 0;
  font-size: 11px;
}

.route-overview {
  padding: 17px 0 15px;
}

.route-overview > span {
  min-width: 112px;
  padding: 7px 8px;
  background: #f2ebdf;
}

.timeline {
  padding-top: 22px;
}

.timeline li {
  grid-template-columns: 49px 21px 1fr;
  min-height: 139px;
}

.timeline li:last-child {
  min-height: 88px;
}

.timeline time {
  color: var(--travel-clay);
  font-family: Georgia, serif;
  font-size: 12px;
}

.timeline-dot::after {
  height: 124px;
}

.timeline-copy h4 {
  margin-top: 5px;
  font-size: 21px;
}

.timeline-copy p {
  font-size: 12px;
  line-height: 1.8;
}

.node-adjust-button {
  margin-top: 10px;
  font-size: 11px;
}

.day-map-column {
  position: sticky;
  top: 95px;
  min-width: 0;
}

.day-map-column :deep(.trip-route-card) {
  margin-top: 0;
}

.plan-risks {
  grid-template-columns: 160px repeat(2, minmax(0, 1fr));
  gap: 18px 28px;
  padding: 30px 0;
  margin-top: 58px;
  border-top: 1px solid var(--travel-line);
  border-bottom: 1px solid var(--travel-line);
}

.plan-risks > span {
  font-family: var(--travel-serif);
  font-size: 15px;
}

.plan-risks p {
  grid-column: auto;
  font-size: 12px;
}

.plan-side-column {
  display: grid;
  grid-template-columns: 1.2fr .8fr .9fr;
  gap: 18px;
}

.budget-card,
.plan-actions-card,
.sources-card {
  min-height: 100%;
  padding: 27px;
  border: 0;
  border-top: 3px solid var(--travel-forest);
  background: rgba(255, 253, 248, .78);
}

.budget-card-head b {
  font-size: 29px;
}

.plan-actions-card {
  align-content: center;
  padding: 29px;
  border-top-color: var(--travel-clay);
  background: #e6d4b5;
}

.sources-card {
  border-top-color: var(--travel-ochre);
}

@media (max-width: 1080px) {
  .plan-hero,
  .plan-body {
    width: min(100% - 40px, 800px);
  }

  .plan-why ul {
    grid-template-columns: 1fr;
    gap: 9px;
  }

  .day-story-layout {
    grid-template-columns: 1fr;
  }

  .day-map-column {
    position: static;
  }

  .plan-side-column {
    grid-template-columns: 1fr 1fr;
  }

  .sources-card {
    grid-column: 1 / -1;
  }
}

@media (max-width: 680px) {
  .plan-page { padding-bottom: 50px; }
  .plan-hero,
  .plan-body { width: calc(100% - 28px); }
  .plan-hero { grid-template-columns: 1fr; gap: 30px; padding: 43px 0 37px; }
  .plan-hero::before { display: none; }
  .plan-hero h1 { font-size: 42px; letter-spacing: -2px; }
  .plan-hero-side { grid-template-columns: 92px 1fr; justify-items: stretch; }
  .match-stamp { width: 90px; height: 90px; }
  .plan-hero-actions { align-content: center; }
  .plan-body { padding-top: 29px; }
  .plan-why { grid-template-columns: 1fr; gap: 14px; }
  .itinerary-section { margin-top: 48px; }
  .itinerary-heading { display: grid; align-items: start; }
  .day-tabs { width: 100%; overflow-x: auto; }
  .day-tabs button { flex: 0 0 145px; }
  .day-plan { padding: 25px 18px 20px; }
  .timeline li { grid-template-columns: 42px 18px 1fr; gap: 8px; }
  .plan-risks { grid-template-columns: 1fr; }
  .plan-risks p { grid-column: auto; }
  .plan-side-column { grid-template-columns: 1fr; }
  .sources-card { grid-column: auto; }
}
</style>
