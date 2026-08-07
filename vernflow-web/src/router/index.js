import { createRouter, createWebHistory } from "vue-router";
import { clearUserToken, getUserRole, hasUserToken } from "@/lib/userApi";
import AssistantPage from "@/pages/AssistantPage.vue";
import HomePage from "@/pages/HomePage.vue";
import LibraryPage from "@/pages/LibraryPage.vue";
import SettingsPage from "@/pages/SettingsPage.vue";
import TripPlanPage from "@/pages/TripPlanPage.vue";
import UserLoginPage from "@/pages/UserLoginPage.vue";
import WorkbenchPage from "@/pages/WorkbenchPage.vue";
import MerchantHomePage from "@/features/merchant/pages/MerchantHomePage.vue";
import TourismHomePage from "@/features/tourism/pages/HomeView.vue";
import TourismPlannerPage from "@/features/tourism/pages/PlanView.vue";

const internalMeta = (title) => ({ title, description: "", requiredRole: "INTERNAL" });

const routes = [
  {
    path: "/login",
    name: "login",
    component: UserLoginPage,
    meta: { public: true, title: "登录", description: "" },
  },
  {
    path: "/",
    alias: "/travel",
    name: "travel-home",
    component: TourismHomePage,
    meta: { public: true, tourismShell: true, title: "山水灵 · VernTrip" },
  },
  {
    path: "/travel/plan",
    alias: "/plan",
    name: "travel-planner",
    component: TourismPlannerPage,
    meta: { public: true, tourismShell: true, title: "AI 个性规划" },
  },
  {
    path: "/travel/ai",
    alias: "/ai-assistant",
    redirect: (to) => ({ name: "travel-planner", query: to.query }),
  },
  {
    path: "/travel/explore",
    alias: "/explore",
    redirect: { name: "travel-home", query: { scene: "explore" } },
  },
  {
    path: "/travel/marketing",
    alias: "/marketing",
    redirect: { name: "travel-home" },
  },
  {
    path: "/travel/trips",
    redirect: { name: "travel-home" },
  },
  {
    path: "/travel/itinerary",
    name: "travel-plan",
    component: TripPlanPage,
    meta: { public: true, title: "旅行计划" },
  },
  {
    path: "/merchant",
    name: "merchant-home",
    component: MerchantHomePage,
    meta: { merchantShell: true, requiredRole: "MERCHANT", title: "商家工作台" },
  },
  { path: "/workspace", name: "home", component: HomePage, meta: internalMeta("主页") },
  { path: "/library", name: "library", component: LibraryPage, meta: internalMeta("资料库") },
  { path: "/workbench", name: "workbench", component: WorkbenchPage, meta: internalMeta("工作台") },
  { path: "/assistant", name: "assistant", component: AssistantPage, meta: internalMeta("文档助手") },
  { path: "/settings", name: "settings", component: SettingsPage, meta: internalMeta("设置") },
  { path: "/:pathMatch(.*)*", redirect: { name: "travel-home" } },
];

function landingForRole(role) {
  if (role === "MERCHANT") return { name: "merchant-home" };
  if (role === "INTERNAL") return { name: "home" };
  return { name: "travel-home" };
}

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior() {
    return { top: 0 };
  },
});

router.beforeEach((to) => {
  let loggedIn = hasUserToken();
  const role = getUserRole();

  if (loggedIn && !role) {
    clearUserToken();
    loggedIn = false;
  }

  if (to.meta.public) {
    if (to.name === "login" && loggedIn) return landingForRole(role);
    return true;
  }

  if (!loggedIn) {
    return { name: "login", query: { redirect: to.fullPath } };
  }

  if (to.meta.requiredRole && to.meta.requiredRole !== role) {
    return landingForRole(role);
  }

  return true;
});

router.onError(() => {
  clearUserToken();
});

export default router;
