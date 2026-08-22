#!/bin/sh
set -eu

: "${OPENCLAW_GATEWAY_TOKEN:?OPENCLAW_GATEWAY_TOKEN is required}"
: "${VERNTRIP_OPENCLAW_SERVICE_TOKEN:?VERNTRIP_OPENCLAW_SERVICE_TOKEN is required}"
: "${DEEPSEEK_API_KEY:?DEEPSEEK_API_KEY is required}"

CLI="dist/index.js"
CONFIG_BATCH="/opt/verntrip/config/verntrip.docker.config.batch.json"
PLUGIN_PACKAGE="/opt/verntrip/packages/verntrip-plugin.tgz"
AGENT_WORKSPACE="${OPENCLAW_STATE_DIR}/workspace-verntrip"

mkdir -p "${OPENCLAW_STATE_DIR}" "${OPENCLAW_WORKSPACE_DIR}" "${AGENT_WORKSPACE}"

echo "Installing VernTrip OpenClaw plugin..."
node "${CLI}" plugins install "npm-pack:${PLUGIN_PACKAGE}" --force

echo "Applying VernTrip OpenClaw configuration..."
node "${CLI}" config set --batch-file "${CONFIG_BATCH}"

if ! node "${CLI}" agents list --json | grep -q 'verntrip-main'; then
  echo "Registering verntrip-main agent..."
  node "${CLI}" agents add verntrip-main --workspace "${AGENT_WORKSPACE}" --non-interactive
fi

node "${CLI}" config validate
node "${CLI}" plugins inspect verntrip --runtime --json >/dev/null
echo "VernTrip OpenClaw state is ready."
