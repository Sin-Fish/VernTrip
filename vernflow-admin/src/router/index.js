import { createRouter, createWebHistory } from "vue-router";
import { clearAdminToken, hasAdminToken } from "@/lib/adminApi";
import AdminShell from "@/layouts/AdminShell.vue";
import AdminDashboardPage from "@/pages/AdminDashboardPage.vue";
import AdminLoginPage from "@/pages/AdminLoginPage.vue";
import AdminRunsPage from "@/pages/AdminRunsPage.vue";
import AdminSessionsPage from "@/pages/AdminSessionsPage.vue";
import AdminStoragePage from "@/pages/AdminStoragePage.vue";
import AdminSystemPage from "@/pages/AdminSystemPage.vue";

const routes = [
  {
    path: "/login",
    name: "admin-login",
    component: AdminLoginPage,
    meta: { public: true, title: "管理员登录" },
  },
  {
    path: "/",
    component: AdminShell,
    children: [
      {
        path: "",
        redirect: { name: "admin-dashboard" },
      },
      {
        path: "dashboard",
        name: "admin-dashboard",
        component: AdminDashboardPage,
        meta: { title: "全控台" },
      },
      {
        path: "users/:userId",
        redirect: (to) => ({ name: "admin-user-storage", params: { userId: to.params.userId } }),
      },
      {
        path: "users/:userId/storage",
        name: "admin-user-storage",
        component: AdminStoragePage,
        meta: { title: "内部存储" },
      },
      {
        path: "users/:userId/sessions",
        name: "admin-user-sessions",
        component: AdminSessionsPage,
        meta: { title: "助理会话" },
      },
      {
        path: "users/:userId/runs",
        name: "admin-user-runs",
        component: AdminRunsPage,
        meta: { title: "运行与任务" },
      },
      {
        path: "system",
        name: "admin-system",
        component: AdminSystemPage,
        meta: { title: "系统治理" },
      },
    ],
  },
];

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior() {
    return { top: 0 };
  },
});

router.beforeEach(async (to) => {
  if (to.meta.public) {
    return true;
  }

  if (!hasAdminToken()) {
    return { name: "admin-login", query: { redirect: to.fullPath } };
  }

  return true;
});

router.onError((error) => {
  if (error?.status === 401) {
    clearAdminToken();
  }
});

export default router;
