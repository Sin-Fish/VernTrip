import vernTripPlugin from "../../openclaw-runtime/plugin/dist/index.js";

const sessionKey = process.env.VERNTRIP_SMOKE_SESSION_KEY;
if (!sessionKey) {
  throw new Error("VERNTRIP_SMOKE_SESSION_KEY is required.");
}

const registrations = [];
vernTripPlugin.register({
  pluginConfig: {
    backendBaseUrl: "http://127.0.0.1:8080",
    requestTimeoutMs: 30_000,
    serviceTokenEnv: "VERNTRIP_OPENCLAW_SERVICE_TOKEN",
  },
  registerTool(tool, options) {
    registrations.push({ tool, options });
  },
  logger: { info() {} },
});

const registration = registrations.find((candidate) =>
  candidate.options?.names?.includes("verntrip_workspace_scope_read"),
);
if (!registration) {
  throw new Error("verntrip_workspace_scope_read was not registered.");
}

const tool = registration.tool({ sessionKey });
if (!tool) {
  throw new Error("Workspace tool was not created for the trusted session.");
}

const result = await tool.execute("verntrip-smoke", {});
const details = result.details;
if (!details || !Array.isArray(details.documents)) {
  throw new Error("Workspace tool returned an invalid response.");
}

console.log(
  JSON.stringify({
    status: "ok",
    totalScopeFileCount: details.totalScopeFileCount,
    documentLabels: details.documents.map((document) => document.citeLabel),
  }),
);
