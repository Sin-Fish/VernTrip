<script setup>
import { computed, onMounted, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import AppSidebar from "@/components/AppSidebar.vue";
import AppTopbar from "@/components/AppTopbar.vue";
import SkillConfigModal from "@/components/SkillConfigModal.vue";
import { useAppState } from "@/composables/useAppState";
import { useUserAuth } from "@/composables/useUserAuth";
import { syncWorkspaceFinderAuth } from "@/lib/workspaceFinder";

const route = useRoute();
const router = useRouter();
const { loadBootstrap, refreshBootstrap, openWorkbenchDraft } = useAppState();
const { currentUser, hydrateUserProfile, signOutUser } = useUserAuth();

const pageTitle = computed(() => route.meta.title ?? "VernFlow");
const pageDescription = computed(() => route.meta.description ?? "");
const showShell = computed(() => !route.meta.public);

function goTo(page) {
  if (!page) {
    return;
  }

  router.push({ name: page });
}

async function startWorkbenchTask() {
  openWorkbenchDraft();

  if (route.name !== "workbench") {
    router.push({ name: "workbench" });
  }
}

async function logout() {
  await signOutUser();
  router.replace({ name: "login" });
}

onMounted(async () => {
  syncWorkspaceFinderAuth();

  if (showShell.value) {
    try {
      await hydrateUserProfile();
      await loadBootstrap();
    } catch {
      // Auth failures are handled by the shared request layer.
    }
  }
});

watch(
  () => route.name,
  async (nextRoute, previousRoute) => {
    if (route.meta.public) {
      return;
    }

    if (previousRoute && nextRoute && nextRoute !== previousRoute) {
      try {
        await hydrateUserProfile();
        await refreshBootstrap();
      } catch {
        // Auth failures are handled by the shared request layer.
      }
    }
  },
);
</script>

<template>
  <RouterView v-if="!showShell" />
  <div
    v-else
    class="app"
  >
    <AppSidebar
      :active-page="route.name"
      :current-user="currentUser"
      @navigate="goTo"
      @logout="logout"
    />
    <main class="main">
      <AppTopbar
        :title="pageTitle"
        :description="pageDescription"
        @import="goTo('library')"
        @new-task="startWorkbenchTask"
      />
      <section class="workspace">
        <RouterView />
      </section>
    </main>
    <SkillConfigModal />
  </div>
</template>
