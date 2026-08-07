const WORKSPACE_SCOPE_PATH = "/api/internal/openclaw/v1/tools/workspace-scope-read";
export async function readWorkspaceScope(config, sessionKey) {
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
    const payload = (await response.json().catch(() => null));
    if (!response.ok || !payload?.success || !payload.data) {
        throw new Error(payload?.message || `VernTrip backend request failed with status ${response.status}.`);
    }
    return payload.data;
}
//# sourceMappingURL=backend-client.js.map