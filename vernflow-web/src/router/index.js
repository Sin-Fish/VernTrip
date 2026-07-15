import { createRouter, createWebHistory } from "vue-router";
import { clearUserToken, hasUserToken } from "@/lib/userApi";
import AssistantPage from "@/pages/AssistantPage.vue";
import HomePage from "@/pages/HomePage.vue";
import LibraryPage from "@/pages/LibraryPage.vue";
import SettingsPage from "@/pages/SettingsPage.vue";
import UserLoginPage from "@/pages/UserLoginPage.vue";
import WorkbenchPage from "@/pages/WorkbenchPage.vue";

const routeMeta = {
  home: {
    title: "\u4e3b\u9875",
    description: "",
  },
  library: {
    title: "\u8d44\u6599\u5e93",
    description: "",
  },
  workbench: {
    title: "\u5de5\u4f5c\u53f0",
    description: "",
  },
  assistant: {
    title: "\u6587\u6863\u52a9\u624b",
    description: "",
  },
  settings: {
    title: "\u8bbe\u7f6e",
    description: "",
  },
};

const routes = [
  {
    path: "/login",
    name: "login",
    component: UserLoginPage,
    meta: {
      public: true,
      title: "\u767b\u5f55",
      description: "",
    },
  },
  {
    path: "/",
    name: "home",
    component: HomePage,
    meta: routeMeta.home,
  },
  {
    path: "/library",
    name: "library",
    component: LibraryPage,
    meta: routeMeta.library,
  },
  {
    path: "/workbench",
    name: "workbench",
    component: WorkbenchPage,
    meta: routeMeta.workbench,
  },
  {
    path: "/assistant",
    name: "assistant",
    component: AssistantPage,
    meta: routeMeta.assistant,
  },
  {
    path: "/settings",
    name: "settings",
    component: SettingsPage,
    meta: routeMeta.settings,
  },
];

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior() {
    return { top: 0 };
  },
});

router.beforeEach((to) => {
  if (to.meta.public) {
    if (to.name === "login" && hasUserToken()) {
      return { name: "home" };
    }
    return true;
  }

  if (!hasUserToken()) {
    return { name: "login", query: { redirect: to.fullPath } };
  }

  return true;
});

router.onError(() => {
  clearUserToken();
});

export default router;
