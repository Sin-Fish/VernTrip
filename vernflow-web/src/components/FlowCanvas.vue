<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { useAppState } from "@/composables/useAppState";

const props = defineProps({
  collapsed: {
    type: Boolean,
    default: false,
  },
});

const emit = defineEmits(["select-node", "clear-selection"]);

const ui = {
  detailTitle: "节点详情",
  detailSummary: "节点摘要",
  executeTrackerTitle: "执行智能体正在此节点推进",
  inspectTrackerTitle: "检查智能体正在此节点复核",
  nodeId: "节点 ID",
  type: "类型",
  status: "状态",
  execute: "执行",
  inspect: "检查",
  checks: "检查项",
  close: "收起",
  noChecks: "当前节点还没有返回检查项。",
};

const {
  workbenchNodes,
  graphLinks,
  graphMainPath,
  selectedNode,
  nodeLog,
} = useAppState();

const viewportRef = ref(null);
const stageRef = ref(null);
const inspectCardStyle = ref({});
const inspectCardSide = ref("right");
const scale = ref(1);
const translateX = ref(0);
const translateY = ref(0);
const isPanning = ref(false);
const stageSize = ref({ width: 1200, height: 520 });

const dragState = {
  pointerId: null,
  startX: 0,
  startY: 0,
  originX: 0,
  originY: 0,
};

const NODE_WIDTH = 188;
const NODE_MIN_HEIGHT = 108;
const MAIN_GAP_X = 86;
const BRANCH_GAP_Y = 150;
const TOP_PADDING = 90;
const LEFT_PADDING = 84;
const PORT_OFFSET = 18;
const INSPECT_CARD_GAP = 14;
const INSPECT_CARD_MIN_MARGIN = 12;
const MIN_SCALE = 0.45;
const MAX_SCALE = 1.9;
const DEFAULT_FIT_MARGIN_X = 64;
const DEFAULT_FIT_MARGIN_Y = 56;
const MIN_VISIBLE_GRAPH_PX = 96;

const labelMap = {
  scope: "范围",
  retriever: "检索",
  branch: "分支",
  selector: "整理",
  writer: "输出",
  audit: "审查",
  organizer: "规划",
  mover: "执行",
  cleanup: "清理",
  review: "复核",
  report: "报告",
  done: "完成",
  running: "运行中",
  queued: "排队中",
  bypassed: "跳过",
  prepared: "已准备",
  pending: "待执行",
  completed: "已完成",
  failed: "失败",
  passed: "通过",
  warn: "待复核",
  skipped: "跳过",
  focus: "重点",
  full: "完整",
  off: "关闭",
  workspace: "工作区",
  coverage: "覆盖优先",
  fallback: "补充分支",
  "local path": "本地路径",
  "working files": "工作文件",
  executing: "执行中",
  inspecting: "检查中",
};

const hasSelectedNode = computed(() => Boolean(selectedNode.value));
const selectedNodeChecks = computed(() => selectedNode.value?.checks ?? []);

const nodeById = computed(() => {
  const map = new Map();
  workbenchNodes.value.forEach((node) => {
    map.set(node.id, node);
  });
  return map;
});

const branchNodeIds = computed(() => {
  const mainSet = new Set(graphMainPath.value);
  return workbenchNodes.value
    .filter((node) => !mainSet.has(node.id))
    .map((node) => node.id);
});

const nodeLayouts = computed(() => {
  const layouts = new Map();
  const mainPath = graphMainPath.value.length
    ? graphMainPath.value.filter((id) => nodeById.value.has(id))
    : workbenchNodes.value.map((node) => node.id);

  const anchorIndexById = new Map();
  let currentX = LEFT_PADDING;
  const mainY = TOP_PADDING + BRANCH_GAP_Y;

  mainPath.forEach((nodeId, index) => {
    layouts.set(nodeId, {
      x: currentX,
      y: mainY,
      width: NODE_WIDTH,
      height: NODE_MIN_HEIGHT,
      branch: false,
      order: index,
    });
    anchorIndexById.set(nodeId, index);
    currentX += NODE_WIDTH + MAIN_GAP_X;
  });

  const linksFrom = new Map();
  const linksTo = new Map();
  graphLinks.value.forEach((link) => {
    if (!linksFrom.has(link.from)) {
      linksFrom.set(link.from, []);
    }
    if (!linksTo.has(link.to)) {
      linksTo.set(link.to, []);
    }
    linksFrom.get(link.from).push(link.to);
    linksTo.get(link.to).push(link.from);
  });

  const branchCounts = new Map();
  branchNodeIds.value.forEach((nodeId) => {
    const inbound = linksTo.get(nodeId) ?? [];
    const outbound = linksFrom.get(nodeId) ?? [];
    const anchors = [...inbound, ...outbound].filter((id) => anchorIndexById.has(id));
    const anchorIndex = anchors.length ? anchorIndexById.get(anchors[0]) : Math.max(mainPath.length - 2, 0);
    const anchorNodeId = mainPath[anchorIndex] ?? mainPath[0];
    const anchorLayout = layouts.get(anchorNodeId);
    const usedCount = branchCounts.get(anchorIndex) ?? 0;
    branchCounts.set(anchorIndex, usedCount + 1);

    layouts.set(nodeId, {
      x: anchorLayout ? anchorLayout.x : LEFT_PADDING,
      y: mainY - BRANCH_GAP_Y * (usedCount + 1),
      width: NODE_WIDTH,
      height: NODE_MIN_HEIGHT,
      branch: true,
      order: mainPath.length + usedCount,
    });
  });

  return layouts;
});

const positionedNodes = computed(() => {
  const layouts = nodeLayouts.value;
  return workbenchNodes.value.map((node) => {
    const layout = layouts.get(node.id) ?? {
      x: LEFT_PADDING,
      y: TOP_PADDING + BRANCH_GAP_Y,
      width: NODE_WIDTH,
      height: NODE_MIN_HEIGHT,
      branch: false,
      order: 0,
    };
    return {
      ...node,
      layout,
      style: {
        left: `${layout.x}px`,
        top: `${layout.y}px`,
        width: `${layout.width}px`,
        minHeight: `${layout.height}px`,
      },
    };
  });
});

const bounds = computed(() => {
  const items = positionedNodes.value;
  if (!items.length) {
    return {
      minX: 0,
      minY: 0,
      maxX: 1200,
      maxY: 520,
      width: 1200,
      height: 520,
    };
  }

  let minX = Number.POSITIVE_INFINITY;
  let minY = Number.POSITIVE_INFINITY;
  let maxX = Number.NEGATIVE_INFINITY;
  let maxY = Number.NEGATIVE_INFINITY;

  items.forEach((node) => {
    minX = Math.min(minX, node.layout.x);
    minY = Math.min(minY, node.layout.y);
    maxX = Math.max(maxX, node.layout.x + node.layout.width);
    maxY = Math.max(maxY, node.layout.y + node.layout.height);
  });

  return {
    minX,
    minY,
    maxX,
    maxY,
    width: maxX - minX,
    height: maxY - minY,
  };
});

const edgeMarkup = computed(() => {
  const mainPairs = new Set(
    graphMainPath.value
      .slice(0, -1)
      .map((nodeId, index) => `${nodeId}>${graphMainPath.value[index + 1]}`),
  );

  let markup = "";
  let mainSegmentIndex = 0;

  graphLinks.value.forEach((link) => {
    const path = buildLinkPath(link.from, link.to);
    if (!path) {
      return;
    }

    const key = `${link.from}>${link.to}`;
    const dashed = link.dashed && !mainPairs.has(key);
    markup += `<path class="edge-base${dashed ? " dashed" : ""}" d="${path}"></path>`;

    if (mainPairs.has(key)) {
      markup += `<path class="main-route" d="${path}"></path>`;
      markup += `<path class="main-flow" d="${path}" style="animation-delay: -${mainSegmentIndex * 0.36}s"></path>`;
      mainSegmentIndex += 1;
    }
  });

  return markup;
});

const stageTransformStyle = computed(() => ({
  width: `${stageSize.value.width}px`,
  height: `${stageSize.value.height}px`,
  transform: `translate(${translateX.value}px, ${translateY.value}px) scale(${scale.value})`,
}));

function displayText(value) {
  if (typeof value !== "string" || !value.trim()) {
    return value;
  }

  const normalized = value.trim().toLowerCase();
  return labelMap[normalized] ?? value;
}

function displayNodeTitle(node) {
  return node.title;
}

function displayNodeStatus(node) {
  if (node.executionActive) {
    return "执行中";
  }
  if (node.inspectionActive) {
    return "检查中";
  }
  return displayText(node.status);
}

function displayNodeDetail(node) {
  return displayText(node.detail);
}

function displayExecutionStatus(node) {
  if (node.executionActive) {
    return "执行中";
  }
  return displayText(node.executeStatus ?? "pending");
}

function displayInspectionStatus(node) {
  if (node.inspectionActive) {
    return "检查中";
  }
  return displayText(node.inspectStatus ?? "pending");
}

function layoutFor(nodeId) {
  return nodeLayouts.value.get(nodeId) ?? null;
}

function centerOf(nodeId) {
  const layout = layoutFor(nodeId);
  if (!layout) {
    return null;
  }
  return {
    x: layout.x + layout.width / 2,
    y: layout.y + layout.height / 2,
    width: layout.width,
    height: layout.height,
    left: layout.x,
    top: layout.y,
  };
}

function ports(fromId, toId) {
  const from = centerOf(fromId);
  const to = centerOf(toId);
  if (!from || !to) {
    return null;
  }

  const dx = to.x - from.x;
  const dy = to.y - from.y;
  if (Math.abs(dx) >= Math.abs(dy) * 0.8) {
    if (dx >= 0) {
      return {
        p: { x: from.left + from.width, y: from.y },
        q: { x: to.left, y: to.y },
        axis: "h",
        pDir: "right",
        qDir: "left",
      };
    }
    return {
      p: { x: from.left, y: from.y },
      q: { x: to.left + to.width, y: to.y },
      axis: "h",
      pDir: "left",
      qDir: "right",
    };
  }

  if (dy >= 0) {
    return {
      p: { x: from.x, y: from.top + from.height },
      q: { x: to.x, y: to.top },
      axis: "v",
      pDir: "down",
      qDir: "up",
    };
  }

  return {
    p: { x: from.x, y: from.top },
    q: { x: to.x, y: to.top + to.height },
    axis: "v",
    pDir: "up",
    qDir: "down",
  };
}

function offsetPoint(point, direction, distance) {
  if (direction === "right") {
    return { x: point.x + distance, y: point.y };
  }
  if (direction === "left") {
    return { x: point.x - distance, y: point.y };
  }
  if (direction === "down") {
    return { x: point.x, y: point.y + distance };
  }
  return { x: point.x, y: point.y - distance };
}

function routePoints(result) {
  if (!result) {
    return [];
  }
  const { p, q, axis, pDir, qDir } = result;
  const startStub = offsetPoint(p, pDir, PORT_OFFSET);
  const endStub = offsetPoint(q, qDir, PORT_OFFSET);

  if (Math.abs(startStub.x - endStub.x) < 1 || Math.abs(startStub.y - endStub.y) < 1) {
    return [p, startStub, endStub, q];
  }

  if (axis === "h") {
    return [p, startStub, { x: endStub.x, y: startStub.y }, endStub, q];
  }

  return [p, startStub, { x: startStub.x, y: endStub.y }, endStub, q];
}

function pathFromPoints(points) {
  if (!points.length) {
    return "";
  }
  return `M ${points.map((point) => `${point.x} ${point.y}`).join(" L ")}`;
}

function buildLinkPath(fromId, toId) {
  return pathFromPoints(routePoints(ports(fromId, toId)));
}

function clampTransform(nextX, nextY, nextScale = scale.value) {
  const viewport = viewportRef.value;
  if (!viewport) {
    return { x: nextX, y: nextY };
  }

  const viewportWidth = Math.max(viewport.clientWidth, 1);
  const viewportHeight = Math.max(viewport.clientHeight, 1);
  const currentBounds = bounds.value;
  const minVisibleX = Math.min(MIN_VISIBLE_GRAPH_PX, viewportWidth * 0.4);
  const minVisibleY = Math.min(MIN_VISIBLE_GRAPH_PX, viewportHeight * 0.4);
  const minTranslateX = minVisibleX - currentBounds.maxX * nextScale;
  const maxTranslateX = viewportWidth - minVisibleX - currentBounds.minX * nextScale;
  const minTranslateY = minVisibleY - currentBounds.maxY * nextScale;
  const maxTranslateY = viewportHeight - minVisibleY - currentBounds.minY * nextScale;

  const x =
    minTranslateX > maxTranslateX
      ? (minTranslateX + maxTranslateX) / 2
      : Math.min(Math.max(nextX, minTranslateX), maxTranslateX);
  const y =
    minTranslateY > maxTranslateY
      ? (minTranslateY + maxTranslateY) / 2
      : Math.min(Math.max(nextY, minTranslateY), maxTranslateY);

  return { x, y };
}

function fitGraph() {
  const viewport = viewportRef.value;
  if (!viewport) {
    return;
  }

  const viewportWidth = Math.max(viewport.clientWidth, 1);
  const viewportHeight = Math.max(viewport.clientHeight, 1);
  const currentBounds = bounds.value;

  stageSize.value = {
    width: Math.max(currentBounds.maxX + LEFT_PADDING + DEFAULT_FIT_MARGIN_X, viewportWidth),
    height: Math.max(currentBounds.maxY + TOP_PADDING + DEFAULT_FIT_MARGIN_Y, viewportHeight),
  };

  const paddingX = Math.max(DEFAULT_FIT_MARGIN_X, viewportWidth * 0.06);
  const paddingY = Math.max(DEFAULT_FIT_MARGIN_Y, viewportHeight * 0.08);
  const fitWidth = currentBounds.width + paddingX * 2;
  const fitHeight = currentBounds.height + paddingY * 2;
  const nextScale = Math.min(
    Math.max(Math.min(viewportWidth / fitWidth, viewportHeight / fitHeight), MIN_SCALE),
    1.25,
  );

  scale.value = nextScale;
  const centeredX = (viewportWidth - currentBounds.width * nextScale) / 2 - currentBounds.minX * nextScale;
  const centeredY = (viewportHeight - currentBounds.height * nextScale) / 2 - currentBounds.minY * nextScale;
  const clamped = clampTransform(centeredX, centeredY, nextScale);
  translateX.value = clamped.x;
  translateY.value = clamped.y;

  positionInspectCard();
}

function handleWheel(event) {
  if (!event.ctrlKey) {
    return;
  }

  event.preventDefault();
  const viewport = viewportRef.value;
  if (!viewport) {
    return;
  }

  const rect = viewport.getBoundingClientRect();
  const anchorX = event.clientX - rect.left;
  const anchorY = event.clientY - rect.top;
  const previousScale = scale.value;
  const delta = event.deltaY < 0 ? 1.08 : 0.92;
  const nextScale = Math.min(MAX_SCALE, Math.max(MIN_SCALE, previousScale * delta));

  if (nextScale === previousScale) {
    return;
  }

  const worldX = (anchorX - translateX.value) / previousScale;
  const worldY = (anchorY - translateY.value) / previousScale;

  scale.value = nextScale;
  const clamped = clampTransform(anchorX - worldX * nextScale, anchorY - worldY * nextScale, nextScale);
  translateX.value = clamped.x;
  translateY.value = clamped.y;
  positionInspectCard();
}

function startPan(event) {
  if (event.button !== 0) {
    return;
  }
  if (event.target.closest(".node, .inspect-popover")) {
    return;
  }

  dragState.pointerId = event.pointerId ?? null;
  dragState.startX = event.clientX;
  dragState.startY = event.clientY;
  dragState.originX = translateX.value;
  dragState.originY = translateY.value;
  isPanning.value = true;
  window.addEventListener("pointermove", handlePointerMove);
  window.addEventListener("pointerup", stopPan);
  window.addEventListener("pointercancel", stopPan);
}

function handlePointerMove(event) {
  if (!isPanning.value) {
    return;
  }

  const clamped = clampTransform(
    dragState.originX + (event.clientX - dragState.startX),
    dragState.originY + (event.clientY - dragState.startY),
  );
  translateX.value = clamped.x;
  translateY.value = clamped.y;
  positionInspectCard();
}

function stopPan() {
  isPanning.value = false;
  dragState.pointerId = null;
  window.removeEventListener("pointermove", handlePointerMove);
  window.removeEventListener("pointerup", stopPan);
  window.removeEventListener("pointercancel", stopPan);
}

function positionInspectCard() {
  nextTick(() => {
    if (!viewportRef.value || !selectedNode.value) {
      inspectCardStyle.value = {};
      return;
    }

    const viewport = viewportRef.value;
    const selectedNodeElement = viewport.querySelector(`[data-node="${selectedNode.value.id}"]`);
    const inspectCard = viewport.querySelector(".inspect-popover");
    if (!selectedNodeElement || !inspectCard) {
      inspectCardStyle.value = {};
      return;
    }

    const viewportRect = viewport.getBoundingClientRect();
    const nodeRect = selectedNodeElement.getBoundingClientRect();
    const cardWidth = inspectCard.offsetWidth || 300;
    const cardHeight = inspectCard.offsetHeight || 240;

    let left = nodeRect.right - viewportRect.left + INSPECT_CARD_GAP;
    let side = "right";
    if (left + cardWidth > viewportRect.width - INSPECT_CARD_MIN_MARGIN) {
      left = nodeRect.left - viewportRect.left - cardWidth - INSPECT_CARD_GAP;
      side = "left";
    }
    if (left < INSPECT_CARD_MIN_MARGIN) {
      left = Math.max(
        INSPECT_CARD_MIN_MARGIN,
        Math.min(nodeRect.left - viewportRect.left, viewportRect.width - cardWidth - INSPECT_CARD_MIN_MARGIN),
      );
      side = "right";
    }

    let top = nodeRect.bottom - viewportRect.top + INSPECT_CARD_GAP;
    if (top + cardHeight > viewportRect.height - INSPECT_CARD_MIN_MARGIN) {
      top = Math.max(INSPECT_CARD_MIN_MARGIN, nodeRect.top - viewportRect.top - cardHeight - INSPECT_CARD_GAP);
    }

    inspectCardSide.value = side;
    inspectCardStyle.value = {
      left: `${Math.round(left)}px`,
      top: `${Math.round(top)}px`,
    };
  });
}

function redraw() {
  fitGraph();
}

function handleResize() {
  fitGraph();
}

function clearSelection() {
  emit("clear-selection");
}

onMounted(() => {
  fitGraph();
  window.addEventListener("resize", handleResize);
});

onBeforeUnmount(() => {
  stopPan();
  window.removeEventListener("resize", handleResize);
});

watch(
  () => [
    props.collapsed,
    workbenchNodes.value.map((node) => `${node.id}:${node.type}:${node.title}`).join("|"),
    graphLinks.value.map((link) => `${link.from}>${link.to}:${link.dashed ? 1 : 0}`).join("|"),
    graphMainPath.value.join("|"),
  ],
  () => {
    fitGraph();
  },
);

watch(
  () => [
    selectedNode.value?.id ?? null,
    selectedNode.value?.status ?? null,
    selectedNode.value?.executeStatus ?? null,
    selectedNode.value?.inspectStatus ?? null,
    selectedNodeChecks.value.length,
    workbenchNodes.value
      .map((node) => `${node.id}:${node.executionActive}:${node.inspectionActive}:${node.detail}`)
      .join("|"),
  ],
  () => {
    positionInspectCard();
  },
);

defineExpose({
  redraw,
});
</script>

<template>
  <div
    ref="viewportRef"
    class="canvas"
    @click="clearSelection"
    @wheel="handleWheel"
    @pointerdown="startPan"
  >
    <div
      ref="stageRef"
      :class="['canvas-stage', { dragging: isPanning }]"
      :style="stageTransformStyle"
    >
      <svg
        class="edge-svg"
        :viewBox="`0 0 ${stageSize.width} ${stageSize.height}`"
        :style="{ width: `${stageSize.width}px`, height: `${stageSize.height}px` }"
        preserveAspectRatio="none"
        v-html="edgeMarkup"
        @click.stop="clearSelection"
      ></svg>
      <div
        v-for="node in positionedNodes"
        :key="node.id"
        :class="['node', { active: selectedNode?.id === node.id, 'branch-node': node.layout.branch }]"
        :data-node="node.id"
        :style="node.style"
        @click.stop="emit('select-node', node.id)"
      >
        <div class="node-trackers">
          <span
            v-if="node.executionActive"
            class="node-cursor execute"
            :title="ui.executeTrackerTitle"
          >
            <svg class="node-cursor-icon" viewBox="0 0 24 24" aria-hidden="true">
              <path
                d="M14.1 6.2a4.6 4.6 0 0 0 4.9 4.9l-8 8a2.1 2.1 0 0 1-3-3l8-8a4.6 4.6 0 0 0 5.8-5.7l-2.8 2.8-3.1-.7-.7-3.1 2.8-2.8a4.6 4.6 0 0 0-4.9 4.6z"
                fill="none"
                stroke="currentColor"
                stroke-linecap="round"
                stroke-linejoin="round"
                stroke-width="1.8"
              />
            </svg>
          </span>
          <span
            v-if="node.inspectionActive"
            class="node-cursor inspect"
            :title="ui.inspectTrackerTitle"
          >
            <svg class="node-cursor-icon" viewBox="0 0 24 24" aria-hidden="true">
              <path
                d="M2.5 12s3.8-6.5 9.5-6.5 9.5 6.5 9.5 6.5-3.8 6.5-9.5 6.5S2.5 12 2.5 12z"
                fill="none"
                stroke="currentColor"
                stroke-linecap="round"
                stroke-linejoin="round"
                stroke-width="1.8"
              />
              <circle
                cx="12"
                cy="12"
                r="2.6"
                fill="none"
                stroke="currentColor"
                stroke-linecap="round"
                stroke-linejoin="round"
                stroke-width="1.8"
              />
            </svg>
          </span>
        </div>
        <div class="type">{{ displayText(node.type) }}</div>
        <div class="n-title">{{ displayNodeTitle(node) }}</div>
        <div class="meta meta-runtime">
          <span :class="['phase-pill', node.executeTone]">{{ ui.execute }}：{{ displayExecutionStatus(node) }}</span>
          <span :class="['phase-pill', node.inspectTone]">{{ ui.inspect }}：{{ displayInspectionStatus(node) }}</span>
        </div>
        <div class="meta">
          <span>
            <i
              :class="[
                'dot',
                node.tone === 'run' ? 'run' : node.tone === 'wait' ? 'wait' : node.tone === 'warn' ? 'warn' : '',
              ]"
            ></i>{{ displayNodeStatus(node) }}
          </span>
          <span>{{ displayNodeDetail(node) }}</span>
        </div>
      </div>
    </div>

    <div
      v-if="hasSelectedNode"
      :class="['inspect-popover', inspectCardSide]"
      :style="inspectCardStyle"
      @click.stop
    >
      <div class="inspect-popover-header">
        <div>
          <div class="inspect-popover-title">{{ ui.detailTitle }}</div>
          <div class="inspect-popover-subtitle">{{ selectedNode?.title }}</div>
        </div>
        <button class="inspect-popover-close" type="button" @click="clearSelection">{{ ui.close }}</button>
      </div>
      <div class="inspect-popover-body">
        <div class="inspect-popover-grid">
          <div class="inspect-popover-item">
            <span>{{ ui.nodeId }}</span>
            <b>{{ selectedNode?.id }}</b>
          </div>
          <div class="inspect-popover-item">
            <span>{{ ui.type }}</span>
            <b>{{ displayText(selectedNode?.type) }}</b>
          </div>
          <div class="inspect-popover-item">
            <span>{{ ui.status }}</span>
            <b>{{ displayNodeStatus(selectedNode) }}</b>
          </div>
          <div class="inspect-popover-item">
            <span>{{ ui.execute }}</span>
            <b>{{ displayExecutionStatus(selectedNode) }}</b>
          </div>
          <div class="inspect-popover-item">
            <span>{{ ui.inspect }}</span>
            <b>{{ displayInspectionStatus(selectedNode) }}</b>
          </div>
        </div>
        <div class="inspect-popover-section">
          <div class="inspect-popover-section-title">{{ ui.checks }}</div>
          <div v-if="selectedNodeChecks.length" class="inspect-check-list">
            <div v-for="item in selectedNodeChecks" :key="item.label" class="inspect-check-item">
              <span :class="['check', item.tone]"></span>
              <span>{{ item.label }}</span>
              <span>{{ item.result }}</span>
            </div>
          </div>
          <div v-else class="inspect-empty">{{ ui.noChecks }}</div>
        </div>
        <div class="inspect-popover-section">
          <div class="inspect-popover-section-title">{{ ui.detailSummary }}</div>
          <pre class="inspect-popover-log">{{ nodeLog }}</pre>
        </div>
      </div>
    </div>
  </div>
</template>
