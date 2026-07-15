import { RemoteDriver } from "vuefinder";
import { getUserToken } from "@/lib/userApi";

export const WORKSPACE_FINDER_ID = "workspace-library";
export const WORKSPACE_ROOT_PATH = "workspace://";

export const workspaceFinderDriver = new RemoteDriver({
  baseURL: "/api/finder",
});

const rawGetPreviewUrl = workspaceFinderDriver.getPreviewUrl.bind(workspaceFinderDriver);
const rawGetDownloadUrl = workspaceFinderDriver.getDownloadUrl.bind(workspaceFinderDriver);

function withToken(url) {
  const token = getUserToken();
  if (!token || typeof window === "undefined") {
    return url;
  }

  const nextUrl = new URL(url, window.location.origin);
  nextUrl.searchParams.set("token", token);
  return `${nextUrl.pathname}${nextUrl.search}${nextUrl.hash}`;
}

workspaceFinderDriver.getPreviewUrl = (...args) => withToken(rawGetPreviewUrl(...args));
workspaceFinderDriver.getDownloadUrl = (...args) => withToken(rawGetDownloadUrl(...args));

export function syncWorkspaceFinderAuth() {
  workspaceFinderDriver.setToken(getUserToken());
}

export const workspaceFinderFeatures = {
  archive: false,
  copy: false,
  delete: true,
  edit: false,
  fullscreen: true,
  history: true,
  language: false,
  move: false,
  newfile: true,
  newfolder: true,
  pinned: true,
  preview: false,
  rename: true,
  search: true,
  theme: false,
  unarchive: false,
  upload: true,
  download: true,
};

export const workspaceFinderConfig = {
  initialPath: WORKSPACE_ROOT_PATH,
  path: WORKSPACE_ROOT_PATH,
  locale: "zhCN",
  persist: false,
  view: "grid",
  showTreeView: true,
  showThumbnails: false,
  showMenuBar: true,
  showToolbar: true,
  notificationPosition: "bottom-right",
  gridItemWidth: 132,
  gridItemHeight: 114,
  gridItemGap: 18,
  listItemHeight: 42,
  listItemGap: 0,
};

export function labelForWorkspacePath(path) {
  if (!path || path === WORKSPACE_ROOT_PATH) {
    return "\u5de5\u4f5c\u533a";
  }

  const normalized = path.endsWith("/") ? path.slice(0, -1) : path;
  return normalized.split("/").pop() ?? normalized;
}

export function formatWorkspaceFileSize(bytes) {
  if (typeof bytes !== "number" || Number.isNaN(bytes) || bytes < 0) {
    return "-";
  }

  if (bytes < 1024) {
    return `${bytes} B`;
  }

  if (bytes < 1024 * 1024) {
    return `${(bytes / 1024).toFixed(1)} KB`;
  }

  if (bytes < 1024 * 1024 * 1024) {
    return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
  }

  return `${(bytes / (1024 * 1024 * 1024)).toFixed(1)} GB`;
}

export function formatWorkspaceTimestamp(timestamp) {
  if (typeof timestamp !== "number" || Number.isNaN(timestamp) || timestamp <= 0) {
    return "-";
  }

  return new Intl.DateTimeFormat("zh-CN", {
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
  }).format(new Date(timestamp));
}
