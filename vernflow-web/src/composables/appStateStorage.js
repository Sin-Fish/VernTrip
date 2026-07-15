export function readPinnedIds(storageKey) {
  if (typeof window === "undefined") {
    return [];
  }

  try {
    const raw = window.localStorage.getItem(storageKey);
    const parsed = raw ? JSON.parse(raw) : [];
    return Array.isArray(parsed) ? parsed.map((item) => String(item)) : [];
  } catch {
    return [];
  }
}

export function writePinnedIds(storageKey, ids) {
  if (typeof window === "undefined") {
    return;
  }

  window.localStorage.setItem(storageKey, JSON.stringify(ids));
}

export function readWorkbenchView(storageKey) {
  if (typeof window === "undefined") {
    return "list";
  }

  const raw = window.localStorage.getItem(storageKey);
  return raw === "run" ? "run" : "list";
}

export function writeWorkbenchView(storageKey, view) {
  if (typeof window === "undefined") {
    return;
  }

  window.localStorage.setItem(storageKey, view === "run" ? "run" : "list");
}

export function readWorkbenchRunId(storageKey) {
  if (typeof window === "undefined") {
    return null;
  }

  const raw = window.localStorage.getItem(storageKey);
  const parsed = raw == null ? Number.NaN : Number(raw);
  return Number.isFinite(parsed) ? parsed : null;
}

export function writeWorkbenchRunId(storageKey, runId) {
  if (typeof window === "undefined") {
    return;
  }

  if (typeof runId === "number" && Number.isFinite(runId)) {
    window.localStorage.setItem(storageKey, String(runId));
    return;
  }

  window.localStorage.removeItem(storageKey);
}
