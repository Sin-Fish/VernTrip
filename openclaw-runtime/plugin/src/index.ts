import { Type } from "typebox";
import {
  definePluginEntry,
  type OpenClawPluginDefinition,
} from "openclaw/plugin-sdk/plugin-entry";

import { readTourismCatalog, readWorkspaceScope } from "./backend-client.js";
import { resolveVernTripPluginConfig } from "./config.js";

const PLUGIN_VERSION = "0.1.0";
const VERNTRIP_SESSION_PREFIX = "verntrip:";

function resolveVernTripSessionKey(value: string | undefined): string | null {
  const normalized = value?.trim();
  if (!normalized) {
    return null;
  }
  const prefixIndex = normalized.indexOf(VERNTRIP_SESSION_PREFIX);
  return prefixIndex >= 0 ? normalized.slice(prefixIndex) : null;
}

const vernTripPlugin: OpenClawPluginDefinition = definePluginEntry({
  id: "verntrip",
  name: "VernTrip Runtime",
  description: "Connects OpenClaw to VernTrip workspace and tourism capabilities.",
  register(api) {
    const config = resolveVernTripPluginConfig(api.pluginConfig ?? {});

    api.registerTool({
      name: "verntrip_runtime_status",
      label: "VernTrip runtime status",
      description:
        "Check whether the VernTrip OpenClaw integration is loaded and ready for internal tool calls.",
      parameters: Type.Object(
        {
          includeConfiguration: Type.Optional(
            Type.Boolean({
              description: "Include non-secret integration settings in the result.",
            }),
          ),
        },
        { additionalProperties: false },
      ),
      async execute(_toolCallId, params) {
        const includeConfiguration =
          typeof params === "object" &&
          params !== null &&
          "includeConfiguration" in params &&
          params.includeConfiguration === true;
        const status: Record<string, unknown> = {
          status: "ready",
          pluginId: "verntrip",
          pluginVersion: PLUGIN_VERSION,
          serviceTokenConfigured: Boolean(process.env[config.serviceTokenEnv]),
        };

        if (includeConfiguration) {
          status.requestTimeoutMs = config.requestTimeoutMs;
          status.backendProtocol = new URL(config.backendBaseUrl).protocol.replace(":", "");
        }

        return {
          content: [
            {
              type: "text",
              text: JSON.stringify(status),
            },
          ],
          details: status,
        };
      },
    });

    api.registerTool(
      (context) => {
        const sessionKey = resolveVernTripSessionKey(context.sessionKey);
        if (!sessionKey) {
          return null;
        }

        return {
          name: "verntrip_workspace_scope_read",
          label: "Read VernTrip workspace scope",
          description:
            "Read the VernTrip workspace documents already bound to this trusted OpenClaw session. Identity and scope are resolved by the VernTrip backend.",
          parameters: Type.Object({}, { additionalProperties: false }),
          async execute() {
            const scope = await readWorkspaceScope(config, sessionKey);
            return {
              content: [
                {
                  type: "text",
                  text: JSON.stringify(scope),
                },
              ],
              details: scope,
            };
          },
        };
      },
      { names: ["verntrip_workspace_scope_read"] },
    );

    api.registerTool(
      (context) => {
        const sessionKey = resolveVernTripSessionKey(context.sessionKey);
        if (!sessionKey) {
          return null;
        }

        return {
          name: "verntrip_tourism_catalog_read",
          label: "Read VernTrip tourism catalog",
          description:
            "Read server-managed tourism resources for a city, including type, area, price range, tags, and source. Use this before recommending named places or claiming resource availability.",
          parameters: Type.Object(
            {
              city: Type.Optional(
                Type.String({
                  maxLength: 80,
                  description: "Destination city. Defaults to Hangzhou when omitted.",
                }),
              ),
            },
            { additionalProperties: false },
          ),
          async execute(_toolCallId, params) {
            const city =
              typeof params === "object" &&
              params !== null &&
              "city" in params &&
              typeof params.city === "string"
                ? params.city
                : undefined;
            const catalog = await readTourismCatalog(config, sessionKey, city);
            return {
              content: [
                {
                  type: "text",
                  text: JSON.stringify(catalog),
                },
              ],
              details: catalog,
            };
          },
        };
      },
      { names: ["verntrip_tourism_catalog_read"] },
    );

    api.logger.info(`VernTrip plugin ${PLUGIN_VERSION} registered`);
  },
});

export default vernTripPlugin;
