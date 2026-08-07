import assert from "node:assert/strict";
import test from "node:test";

import vernTripPlugin from "../dist/index.js";

test("workspace tool uses trusted session context and internal service token", async () => {
  const registrations = [];
  const api = {
    pluginConfig: {
      backendBaseUrl: "http://127.0.0.1:8080",
      requestTimeoutMs: 5_000,
      serviceTokenEnv: "VERNTRIP_TEST_SERVICE_TOKEN",
    },
    registerTool(tool, options) {
      registrations.push({ tool, options });
    },
    logger: { info() {} },
  };
  vernTripPlugin.register(api);

  const registration = registrations.find((candidate) =>
    candidate.options?.names?.includes("verntrip_workspace_scope_read"),
  );
  assert.ok(registration);
  assert.equal(registration.tool({}), null);

  const sessionKey = "verntrip:trusted-session-key";
  const workspaceTool = registration.tool({ sessionKey });
  assert.equal(workspaceTool.name, "verntrip_workspace_scope_read");
  assert.deepEqual(workspaceTool.parameters.required, undefined);

  const previousToken = process.env.VERNTRIP_TEST_SERVICE_TOKEN;
  const previousFetch = globalThis.fetch;
  process.env.VERNTRIP_TEST_SERVICE_TOKEN = "internal-test-token";
  globalThis.fetch = async (url, options) => {
    assert.equal(
      String(url),
      "http://127.0.0.1:8080/api/internal/openclaw/v1/tools/workspace-scope-read",
    );
    assert.equal(options.method, "POST");
    assert.equal(options.headers.Authorization, "Bearer internal-test-token");
    assert.equal(options.headers["X-OpenClaw-Session-Key"], sessionKey);
    assert.equal(options.body, "{}");
    assert.equal(options.body.includes("userId"), false);
    return new Response(
      JSON.stringify({
        success: true,
        message: "ok",
        data: {
          globalScope: false,
          totalScopeFileCount: 1,
          documents: [
            {
              path: "workspace://travel/guide.md",
              citeLabel: "travel/guide.md",
              sectionTitle: "travel",
              chunks: ["West Lake cultural route"],
            },
          ],
          unreadableFiles: [],
        },
      }),
      { status: 200, headers: { "Content-Type": "application/json" } },
    );
  };

  try {
    const result = await workspaceTool.execute("call-1", {});
    assert.equal(result.details.totalScopeFileCount, 1);
    assert.equal(result.details.documents[0].citeLabel, "travel/guide.md");
  } finally {
    globalThis.fetch = previousFetch;
    if (previousToken === undefined) {
      delete process.env.VERNTRIP_TEST_SERVICE_TOKEN;
    } else {
      process.env.VERNTRIP_TEST_SERVICE_TOKEN = previousToken;
    }
  }
});

test("tourism catalog tool reads grounded resources without accepting identity", async () => {
  const registrations = [];
  vernTripPlugin.register({
    pluginConfig: {
      backendBaseUrl: "http://127.0.0.1:8080",
      requestTimeoutMs: 5_000,
      serviceTokenEnv: "VERNTRIP_TEST_SERVICE_TOKEN",
    },
    registerTool(tool, options) {
      registrations.push({ tool, options });
    },
    logger: { info() {} },
  });

  const registration = registrations.find((candidate) =>
    candidate.options?.names?.includes("verntrip_tourism_catalog_read"),
  );
  assert.ok(registration);
  assert.equal(registration.tool({}), null);

  const sessionKey = "verntrip:tourism-session";
  const catalogTool = registration.tool({
    sessionKey: `agent:main:${sessionKey}`,
  });
  assert.equal(catalogTool.name, "verntrip_tourism_catalog_read");

  const previousToken = process.env.VERNTRIP_TEST_SERVICE_TOKEN;
  const previousFetch = globalThis.fetch;
  process.env.VERNTRIP_TEST_SERVICE_TOKEN = "internal-test-token";
  globalThis.fetch = async (url, options) => {
    assert.equal(
      String(url),
      "http://127.0.0.1:8080/api/internal/openclaw/v1/tools/tourism-catalog-read",
    );
    assert.equal(options.headers.Authorization, "Bearer internal-test-token");
    assert.equal(options.headers["X-OpenClaw-Session-Key"], sessionKey);
    assert.deepEqual(JSON.parse(options.body), { city: "杭州" });
    assert.equal(options.body.includes("userId"), false);
    return new Response(
      JSON.stringify({
        success: true,
        message: "ok",
        data: {
          city: "杭州",
          supported: true,
          total: 1,
          resources: [
            {
              id: 1,
              type: "文化体验",
              name: "中国杭州工艺美术博物馆",
              area: "拱墅区",
              note: "适合亲子文化体验。",
              source: "杭州文旅公开资料",
              sourceUrl: null,
              priceMin: 0,
              priceMax: 0,
              tags: ["亲子友好"],
            },
          ],
        },
      }),
      { status: 200, headers: { "Content-Type": "application/json" } },
    );
  };

  try {
    const result = await catalogTool.execute("call-tourism", { city: "杭州" });
    assert.equal(result.details.supported, true);
    assert.equal(result.details.resources[0].name, "中国杭州工艺美术博物馆");
  } finally {
    globalThis.fetch = previousFetch;
    if (previousToken === undefined) {
      delete process.env.VERNTRIP_TEST_SERVICE_TOKEN;
    } else {
      process.env.VERNTRIP_TEST_SERVICE_TOKEN = previousToken;
    }
  }
});
