import type { VernTripPluginConfig } from "./config.js";

const WORKSPACE_SCOPE_PATH = "/api/internal/openclaw/v1/tools/workspace-scope-read";
const TOURISM_CATALOG_PATH = "/api/internal/openclaw/v1/tools/tourism-catalog-read";

interface ApiResponse<T> {
  success: boolean;
  message?: string;
  data?: T;
}

export interface WorkspaceScopeResponse {
  globalScope: boolean;
  totalScopeFileCount: number;
  documents: Array<{
    path: string;
    citeLabel: string;
    sectionTitle: string;
    chunks: string[];
  }>;
  unreadableFiles: string[];
}

export interface TourismCatalogResponse {
  city: string;
  supported: boolean;
  total: number;
  resources: Array<{
    id: number;
    type: string;
    name: string;
    area: string | null;
    note: string;
    source: string;
    sourceUrl: string | null;
    priceMin: number;
    priceMax: number;
    tags: string[];
  }>;
}

export async function readWorkspaceScope(
  config: VernTripPluginConfig,
  sessionKey: string,
): Promise<WorkspaceScopeResponse> {
  const serviceToken = process.env[config.serviceTokenEnv];
  if (!serviceToken) {
    throw new Error("VernTrip internal service token is not configured.");
  }

  const endpoint = new URL(WORKSPACE_SCOPE_PATH, `${config.backendBaseUrl}/`);
  const response = await fetch(endpoint, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${serviceToken}`,
      "Content-Type": "application/json",
      "X-OpenClaw-Session-Key": sessionKey,
    },
    body: "{}",
    signal: AbortSignal.timeout(config.requestTimeoutMs),
  });

  const payload = (await response.json().catch(() => null)) as ApiResponse<WorkspaceScopeResponse> | null;
  if (!response.ok || !payload?.success || !payload.data) {
    throw new Error(
      payload?.message || `VernTrip backend request failed with status ${response.status}.`,
    );
  }
  return payload.data;
}

export async function readTourismCatalog(
  config: VernTripPluginConfig,
  sessionKey: string,
  city?: string,
): Promise<TourismCatalogResponse> {
  return postInternalTool<TourismCatalogResponse>(
    config,
    sessionKey,
    TOURISM_CATALOG_PATH,
    { city: city?.trim() || null },
  );
}

async function postInternalTool<T>(
  config: VernTripPluginConfig,
  sessionKey: string,
  path: string,
  body: Record<string, unknown>,
): Promise<T> {
  const serviceToken = process.env[config.serviceTokenEnv];
  if (!serviceToken) {
    throw new Error("VernTrip internal service token is not configured.");
  }

  const endpoint = new URL(path, `${config.backendBaseUrl}/`);
  const response = await fetch(endpoint, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${serviceToken}`,
      "Content-Type": "application/json",
      "X-OpenClaw-Session-Key": sessionKey,
    },
    body: JSON.stringify(body),
    signal: AbortSignal.timeout(config.requestTimeoutMs),
  });

  const payload = (await response.json().catch(() => null)) as ApiResponse<T> | null;
  if (!response.ok || !payload?.success || !payload.data) {
    throw new Error(
      payload?.message || `VernTrip backend request failed with status ${response.status}.`,
    );
  }
  return payload.data;
}
