const DEFAULT_BACKEND_BASE_URL = "http://backend:8080";
const DEFAULT_REQUEST_TIMEOUT_MS = 30_000;
const DEFAULT_SERVICE_TOKEN_ENV = "VERNTRIP_OPENCLAW_SERVICE_TOKEN";
export function resolveVernTripPluginConfig(input) {
    return {
        backendBaseUrl: normalizeBaseUrl(input.backendBaseUrl),
        requestTimeoutMs: normalizeTimeout(input.requestTimeoutMs),
        serviceTokenEnv: normalizeEnvName(input.serviceTokenEnv),
    };
}
function normalizeBaseUrl(value) {
    if (typeof value !== "string" || value.trim().length === 0) {
        return DEFAULT_BACKEND_BASE_URL;
    }
    const normalized = value.trim().replace(/\/+$/, "");
    try {
        const url = new URL(normalized);
        if (url.protocol === "http:" || url.protocol === "https:") {
            return normalized;
        }
    }
    catch {
        // Manifest validation normally catches this; keep a safe runtime fallback.
    }
    return DEFAULT_BACKEND_BASE_URL;
}
function normalizeTimeout(value) {
    if (typeof value !== "number" || !Number.isInteger(value)) {
        return DEFAULT_REQUEST_TIMEOUT_MS;
    }
    return Math.min(180_000, Math.max(1_000, value));
}
function normalizeEnvName(value) {
    if (typeof value !== "string" || !/^[A-Z][A-Z0-9_]*$/.test(value)) {
        return DEFAULT_SERVICE_TOKEN_ENV;
    }
    return value;
}
//# sourceMappingURL=config.js.map