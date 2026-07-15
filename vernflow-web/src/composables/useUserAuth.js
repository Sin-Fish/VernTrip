import { computed, reactive } from "vue";
import { getUserProfile, loginUser, logoutUser } from "@/lib/userApi";
import { syncWorkspaceFinderAuth } from "@/lib/workspaceFinder";

const authState = reactive({
  profile: null,
  loading: false,
  error: null,
});

function clearAuthError() {
  authState.error = null;
}

function normalizeProfile(profile) {
  if (!profile || typeof profile !== "object") {
    return null;
  }

  return {
    id: Number.isFinite(profile.id) ? profile.id : null,
    username: typeof profile.username === "string" ? profile.username : "demo",
    displayName: typeof profile.displayName === "string" && profile.displayName
      ? profile.displayName
      : typeof profile.username === "string" && profile.username
        ? profile.username
        : "\u6f14\u793a\u7528\u6237",
    expiresAt: typeof profile.expiresAt === "string" ? profile.expiresAt : null,
  };
}

async function hydrateUserProfile() {
  authState.loading = true;
  authState.error = null;

  try {
    const profile = normalizeProfile(await getUserProfile());
    authState.profile = profile;
    syncWorkspaceFinderAuth();
    return profile;
  } catch (error) {
    authState.error = error instanceof Error ? error.message : "\u52a0\u8f7d\u7528\u6237\u4fe1\u606f\u5931\u8d25\u3002";
    authState.profile = null;
    syncWorkspaceFinderAuth();
    throw error;
  } finally {
    authState.loading = false;
  }
}

async function signInUser(username, password) {
  authState.loading = true;
  authState.error = null;

  try {
    const payload = await loginUser(username, password);
    authState.profile = normalizeProfile(payload);
    syncWorkspaceFinderAuth();
    return authState.profile;
  } catch (error) {
    authState.error = error instanceof Error ? error.message : "\u767b\u5f55\u5931\u8d25\u3002";
    authState.profile = null;
    syncWorkspaceFinderAuth();
    throw error;
  } finally {
    authState.loading = false;
  }
}

async function signOutUser() {
  authState.loading = true;

  try {
    await logoutUser();
  } finally {
    authState.profile = null;
    authState.loading = false;
    authState.error = null;
    syncWorkspaceFinderAuth();
  }
}

export function useUserAuth() {
  return {
    authState,
    currentUser: computed(() => authState.profile),
    hydrateUserProfile,
    signInUser,
    signOutUser,
    clearAuthError,
  };
}
