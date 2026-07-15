const ADMIN_TOKEN_KEY = "vernflow.admin.token";

export function getAdminToken() {
  return window.localStorage.getItem(ADMIN_TOKEN_KEY) ?? "";
}

export function setAdminToken(token) {
  if (token) {
    window.localStorage.setItem(ADMIN_TOKEN_KEY, token);
    return;
  }
  window.localStorage.removeItem(ADMIN_TOKEN_KEY);
}

export function clearAdminToken() {
  window.localStorage.removeItem(ADMIN_TOKEN_KEY);
}

export function hasAdminToken() {
  return Boolean(getAdminToken());
}

async function readJson(response) {
  const payload = await response.json().catch(() => null);
  if (!response.ok || payload?.success === false) {
    const message = payload?.message || `请求失败（${response.status}）`;
    const error = new Error(message);
    error.status = response.status;
    if (response.status === 401) {
      clearAdminToken();
    }
    throw error;
  }
  return payload?.data ?? payload;
}

export async function adminRequest(path, options = {}) {
  const headers = new Headers(options.headers ?? {});
  const token = getAdminToken();
  if (token) {
    headers.set("Authorization", `Bearer ${token}`);
  }

  const response = await fetch(path, {
    ...options,
    headers,
  });
  return readJson(response);
}

export async function loginAdmin(username, password) {
  const data = await adminRequest("/api/admin/auth/login", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
    },
    body: JSON.stringify({ username, password }),
  });
  setAdminToken(data.token);
  return data;
}

export async function logoutAdmin() {
  try {
    await adminRequest("/api/admin/auth/logout", {
      method: "POST",
    });
  } finally {
    clearAdminToken();
  }
}

export async function getAdminProfile() {
  return adminRequest("/api/admin/auth/me");
}
