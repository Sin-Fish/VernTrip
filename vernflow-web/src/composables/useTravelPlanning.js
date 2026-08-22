import { computed, reactive } from "vue";
import {
  adjustTravelPlan,
  createTravelPlan,
  getTravelPlan,
  listSavedTravelPlans,
  removeSavedTravelPlan,
  saveTravelPlan,
} from "@/lib/travelApi";

function nextSaturday() {
  const date = new Date();
  const daysUntilSaturday = (6 - date.getDay() + 7) % 7;
  date.setDate(date.getDate() + daysUntilSaturday);
  return date.toISOString().slice(0, 10);
}

const defaultRequest = () => ({
  destination: "杭州",
  departure: "",
  date: nextSaturday(),
  days: "2",
  travelers: "",
  budget: 3000,
  pace: "轻松一些",
  interests: [],
  notes: "",
});

const themes = [
  {
    id: "heritage-family",
    title: "亲子非遗慢游",
    subtitle: "把一座城的故事，留在孩子的手心里",
    visual: "heritage",
    interests: ["非遗体验", "本地美食"],
    travelers: "亲子出行",
    pace: "轻松一些",
  },
  {
    id: "west-lake-weekend",
    title: "西湖周末散步",
    subtitle: "看湖、吃茶、在傍晚等一阵风",
    visual: "lake",
    interests: ["自然风景", "城市漫步"],
    travelers: "朋友结伴",
    pace: "随意一些",
  },
  {
    id: "song-rhyme",
    title: "宋韵夜色",
    subtitle: "从白天的街巷，走进夜里的南宋",
    visual: "night",
    interests: ["人文历史", "夜游体验"],
    travelers: "情侣出行",
    pace: "丰富一点",
  },
];

const state = reactive({
  request: defaultRequest(),
  currentPlan: null,
  savedPlans: [],
  isGenerating: false,
  isSaving: false,
  isLoadingSavedPlans: false,
  isLoadingPlan: false,
  hasGenerated: false,
  error: "",
});

function cloneRequest(request = state.request) {
  return {
    ...request,
    days: String(request.days),
    interests: [...request.interests],
  };
}

function applyTheme(themeId) {
  const theme = themes.find((item) => item.id === themeId);
  if (!theme) {
    return;
  }
  state.request = {
    ...defaultRequest(),
    interests: [...theme.interests],
    travelers: theme.travelers,
    pace: theme.pace,
  };
}

function updateRequest(patch) {
  state.request = {
    ...state.request,
    ...patch,
    interests: patch.interests ? [...patch.interests] : state.request.interests,
  };
}

async function generatePlan() {
  state.isGenerating = true;
  state.error = "";
  try {
    const plan = await createTravelPlan(state.request);
    state.currentPlan = plan;
    state.request = cloneRequest(plan.request);
    state.hasGenerated = true;
    return plan;
  } catch (error) {
    state.error = error.message || "旅行计划生成失败，请稍后重试。";
    return null;
  } finally {
    state.isGenerating = false;
  }
}

async function adjustCurrentPlan(adjustment) {
  if (!state.currentPlan) {
    state.error = "请先生成一份旅行计划。";
    return null;
  }

  state.isGenerating = true;
  state.error = "";
  try {
    const plan = await adjustTravelPlan(state.currentPlan.id, adjustment);
    state.currentPlan = plan;
    state.request = cloneRequest(plan.request);
    return plan;
  } catch (error) {
    state.error = error.message || "旅行计划调整失败，请稍后重试。";
    return null;
  } finally {
    state.isGenerating = false;
  }
}

async function loadPlan(planId) {
  const normalizedId = Number(planId);
  if (!Number.isInteger(normalizedId) || normalizedId <= 0) {
    state.error = "行程编号无效。";
    return null;
  }

  if (state.currentPlan?.id === normalizedId) {
    return state.currentPlan;
  }

  state.isLoadingPlan = true;
  state.error = "";
  try {
    const plan = await getTravelPlan(normalizedId);
    restorePlan(plan);
    state.hasGenerated = true;
    return plan;
  } catch (error) {
    state.error = error.message || "暂时无法读取这份行程。";
    return null;
  } finally {
    state.isLoadingPlan = false;
  }
}

async function saveCurrentPlan() {
  if (!state.currentPlan) {
    return null;
  }

  state.isSaving = true;
  state.error = "";
  try {
    const savedPlan = await saveTravelPlan(state.currentPlan.id);
    state.currentPlan = savedPlan;
    const existingIndex = state.savedPlans.findIndex((item) => item.id === savedPlan.id);
    if (existingIndex >= 0) {
      state.savedPlans.splice(existingIndex, 1, savedPlan);
    } else {
      state.savedPlans.unshift(savedPlan);
    }
    return savedPlan;
  } catch (error) {
    state.error = error.message || "收藏失败，请稍后重试。";
    return null;
  } finally {
    state.isSaving = false;
  }
}

async function loadSavedPlans() {
  state.isLoadingSavedPlans = true;
  state.error = "";
  try {
    state.savedPlans = await listSavedTravelPlans();
    return state.savedPlans;
  } catch (error) {
    state.error = error.message || "暂时无法读取已收藏的行程。";
    return [];
  } finally {
    state.isLoadingSavedPlans = false;
  }
}

async function removeSavedPlan(planId) {
  state.error = "";
  try {
    await removeSavedTravelPlan(planId);
    state.savedPlans = state.savedPlans.filter((item) => item.id !== planId);
  } catch (error) {
    state.error = error.message || "移除行程失败，请稍后重试。";
  }
}

function restorePlan(plan) {
  state.currentPlan = plan;
  state.request = cloneRequest(plan.request);
}

export function useTravelPlanning() {
  return {
    state,
    themes,
    interests: ["非遗体验", "本地美食", "自然风景", "人文历史", "亲子友好", "城市漫步", "夜游体验"],
    travelerOptions: ["独自出行", "情侣出行", "朋友结伴", "亲子出行", "携长辈出行"],
    paceOptions: ["轻松一些", "随意一些", "丰富一点"],
    hasSavedPlans: computed(() => state.savedPlans.length > 0),
    applyTheme,
    updateRequest,
    generatePlan,
    loadPlan,
    adjustCurrentPlan,
    saveCurrentPlan,
    loadSavedPlans,
    removeSavedPlan,
    restorePlan,
  };
}
