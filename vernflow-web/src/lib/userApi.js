const USER_TOKEN_KEY = "vernflow.user.token";
const USER_ROLE_KEY = "vernflow.user.role";

export function getUserToken() {
  return window.localStorage.getItem(USER_TOKEN_KEY) ?? "";
}

export function setUserToken(token) {
  if (token) {
    window.localStorage.setItem(USER_TOKEN_KEY, token);
    return;
  }
  window.localStorage.removeItem(USER_TOKEN_KEY);
}

export function getUserRole() {
  return window.localStorage.getItem(USER_ROLE_KEY) ?? "";
}

export function setUserRole(role) {
  if (role) {
    window.localStorage.setItem(USER_ROLE_KEY, String(role).toUpperCase());
    return;
  }
  window.localStorage.removeItem(USER_ROLE_KEY);
}

export function clearUserToken() {
  window.localStorage.removeItem(USER_TOKEN_KEY);
  window.localStorage.removeItem(USER_ROLE_KEY);
}

export function hasUserToken() {
  return Boolean(getUserToken());
}

function redirectToLogin() {
  if (typeof window === "undefined") {
    return;
  }

  if (!window.location.pathname.startsWith("/login")) {
    const redirect = encodeURIComponent(`${window.location.pathname}${window.location.search}${window.location.hash}`);
    window.location.replace(`/login?redirect=${redirect}`);
  }
}

async function readJson(response) {
  const payload = await response.json().catch(() => null);
  if (!response.ok || payload?.success === false) {
    const message = payload?.message || `\u8bf7\u6c42\u5931\u8d25\uff08${response.status}\uff09`;
    const error = new Error(message);
    error.status = response.status;
    if (response.status === 401) {
      clearUserToken();
      redirectToLogin();
    }
    throw error;
  }
  return payload?.data ?? payload;
}

export async function userRequest(path, options = {}) {
  const headers = new Headers(options.headers ?? {});
  const token = getUserToken();
  if (token) {
    headers.set("Authorization", `Bearer ${token}`);
  }

  const response = await fetch(path, {
    ...options,
    headers,
  });

  return readJson(response);
}

export async function loginUser(username, password) {
  const data = await userRequest("/api/user/auth/login", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
    },
    body: JSON.stringify({ username, password }),
  });
  setUserToken(data.token);
  setUserRole(data.role);
  return data;
}

export async function logoutUser() {
  try {
    await userRequest("/api/user/auth/logout", {
      method: "POST",
    });
  } finally {
    clearUserToken();
  }
}

export async function getUserProfile() {
  return userRequest("/api/user/auth/me");
}

export async function getWorkspaceFileRelationGraph(path, limit = 8) {
  const params = new URLSearchParams({
    path,
    limit: String(limit),
  });
  return userRequest(`/api/library/relations?${params.toString()}`);
}
