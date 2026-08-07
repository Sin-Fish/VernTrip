<script setup>
import { computed, ref } from "vue";
import { useRouter } from "vue-router";
import { useTravelPlanning } from "@/composables/useTravelPlanning";
import TripRouteMap from "@/features/tourism/components/TripRouteMap.vue";

const router = useRouter();
const { state, adjustCurrentPlan, saveCurrentPlan } = useTravelPlanning();
const activeDay = ref(1);
const adjustmentOpen = ref(false);
const adjustment = ref({ type: "pace", note: "" });
const savedNotice = ref("");

const plan = computed(() => state.currentPlan);
const selectedDay = computed(() => plan.value?.days.find((day) => day.day === activeDay.value) ?? plan.value?.days[0]);
const engineSteps = computed(() => {
  if (plan.value?.engine === "openclaw") {
    return ["OpenClaw 需求理解", "OpenClaw 结构化编排", "后端可信校验"];
  }
  if (plan.value?.engine === "local-fallback") {
    return ["OpenClaw 暂不可用", "本地规则回退", "后端可信校验"];
  }
  return ["结构化需求", "本地规则编排", "后端可信校验"];
});

if (!plan.value) {
  router.replace({ name: "travel-planner" });
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
  activeDay.value = 1;
  adjustmentOpen.value = false;
  adjustment.value.note = "";
  savedNotice.value = "已根据你的要求生成新版本";
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
    <header class="plan-nav">
      <RouterLink class="travel-brand" :to="{ name: 'travel-home' }">
        <span class="brand-seal">V</span>
        <span><strong>VernTrip</strong><small>你的旅行计划</small></span>
      </RouterLink>
      <div class="plan-nav-actions">
        <button class="plan-save-button" type="button" :disabled="state.isSaving" @click="savePlan">
          {{ state.isSaving ? "保存中…" : plan.status === "已收藏" ? "✓ 已收藏" : "收藏行程" }}
        </button>
        <button type="button" @click="startOver">重新规划</button>
      </div>
    </header>

    <section class="plan-hero">
      <div>
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
      <div class="match-stamp"><span>适配度</span><b>{{ plan.match }}<small>%</small></b><em>根据本次出行需求</em></div>
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
          <article class="day-plan">
            <div class="day-plan-title"><span>DAY {{ selectedDay.day }}</span><h3>{{ selectedDay.title }}</h3></div>
            <div class="route-overview" aria-label="当日路线速览">
              <span v-for="(item, index) in selectedDay.items" :key="`route-${item.time}`">
                <small>{{ item.time }}</small><b>{{ item.title }}</b><i v-if="index < selectedDay.items.length - 1">→</i>
              </span>
            </div>
            <ol class="timeline">
              <li v-for="item in selectedDay.items" :key="`${selectedDay.day}-${item.time}`">
                <time>{{ item.time }}</time>
                <span :class="['timeline-dot', item.tone]"></span>
                <div class="timeline-copy">
                  <small>{{ item.label }} · {{ item.tag }}</small>
                  <h4>{{ item.title }}</h4>
                  <p>{{ item.description }}</p>
                  <b v-if="item.cost">预计 ¥ {{ item.cost.toLocaleString("zh-CN") }}</b>
                </div>
              </li>
            </ol>
          </article>
          <TripRouteMap
            v-if="selectedDay"
            :day="selectedDay"
            :city="plan.request.destination"
          />
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
          <button class="travel-button travel-button-primary" type="button" @click="adjustmentOpen = true">改一改这份计划 <span>→</span></button>
          <span v-if="savedNotice" class="saved-notice">{{ savedNotice }}</span>
          <span v-if="state.error" class="field-error">{{ state.error }}</span>
        </section>

        <section class="sources-card">
          <p>使用了 {{ plan.sources.length }} 条已整理资源</p>
          <div v-for="source in plan.sources.slice(0, 4)" :key="source.name" class="source-row"><span>{{ source.type }}</span><b>{{ source.name }}</b></div>
        </section>
      </aside>
    </section>

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
</template>
