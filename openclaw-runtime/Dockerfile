ARG OPENCLAW_IMAGE=ghcr.io/openclaw/openclaw:2026.7.1

FROM node:24-bookworm-slim AS plugin-build

WORKDIR /build/plugin
COPY plugin/package*.json plugin/tsconfig.json plugin/openclaw.plugin.json ./
COPY plugin/src ./src

RUN npm ci --ignore-scripts \
    && npm run build \
    && mkdir -p /build/package \
    && npm pack --pack-destination /build/package \
    && mv /build/package/*.tgz /build/package/verntrip-plugin.tgz

FROM ${OPENCLAW_IMAGE}

USER root
RUN install -d -m 0755 -o node -g node /opt/verntrip/packages /opt/verntrip/skills /opt/verntrip/config

COPY --from=plugin-build --chown=node:node /build/package/verntrip-plugin.tgz /opt/verntrip/packages/verntrip-plugin.tgz
COPY --chown=node:node skills /opt/verntrip/skills
COPY --chown=node:node config /opt/verntrip/config

USER node
