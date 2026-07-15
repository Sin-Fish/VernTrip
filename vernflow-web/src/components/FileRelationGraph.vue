<script setup>
import cytoscape from "cytoscape";
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from "vue";

const props = defineProps({
  graph: {
    type: Object,
    default: null,
  },
  loading: {
    type: Boolean,
    default: false,
  },
});

const emit = defineEmits(["select-node", "select-edge", "clear-selection"]);

const hostRef = ref(null);
let cy = null;

const hasGraph = computed(() => Array.isArray(props.graph?.nodes) && props.graph.nodes.length > 0);
const isSingleNodeGraph = computed(() => {
  return hasGraph.value
    && (props.graph?.nodes?.length ?? 0) === 1
    && (props.graph?.edges?.length ?? 0) === 0;
});
const singleNode = computed(() => props.graph?.nodes?.[0] ?? null);

function relationLineColor(type) {
  if (type === "semantic_similar") {
    return "#2563eb";
  }
  if (type === "shared_concepts") {
    return "#7c3aed";
  }
  if (type === "co_hit") {
    return "#0f766e";
  }
  return "#94a3b8";
}

function buildElements(graph) {
  const nodes = (graph?.nodes ?? []).map((node) => ({
    data: {
      id: node.id,
      path: node.path,
      label: node.label,
      directoryLabel: node.directoryLabel,
      role: node.role,
      score: node.score ?? 0,
      concepts: node.concepts ?? [],
    },
  }));

  const edges = (graph?.edges ?? []).map((edge) => ({
    data: {
      id: edge.id,
      source: edge.source,
      target: edge.target,
      relationType: edge.relationType,
      score: edge.score ?? 0,
      reasons: edge.reasons ?? [],
      lineColor: relationLineColor(edge.relationType),
    },
  }));

  return [...nodes, ...edges];
}

function layoutOptions() {
  return {
    name: "concentric",
    animate: false,
    fit: true,
    padding: 36,
    avoidOverlap: true,
    minNodeSpacing: 24,
    concentric(node) {
      return node.data("role") === "focus" ? 4 : Math.max(1, node.data("score") || 0.4);
    },
    levelWidth() {
      return 1;
    },
  };
}

function singleNodeLayoutOptions() {
  return {
    name: "grid",
    rows: 1,
    cols: 1,
    fit: true,
    padding: 48,
    avoidOverlap: true,
    animate: false,
  };
}

function emitNodeSelection(node) {
  emit("select-node", {
    id: node.data("id"),
    path: node.data("path"),
    label: node.data("label"),
    directoryLabel: node.data("directoryLabel"),
    role: node.data("role"),
    score: node.data("score"),
    concepts: node.data("concepts") ?? [],
  });
}

function emitEdgeSelection(edge) {
  emit("select-edge", {
    id: edge.data("id"),
    source: edge.data("source"),
    target: edge.data("target"),
    relationType: edge.data("relationType"),
    score: edge.data("score"),
    reasons: edge.data("reasons") ?? [],
  });
}

function destroyGraph() {
  if (cy) {
    cy.destroy();
    cy = null;
  }
}

function ensureGraph() {
  if (!hostRef.value) {
    return;
  }

  if (!cy) {
    cy = cytoscape({
      container: hostRef.value,
      elements: [],
      style: [
        {
          selector: "node",
          style: {
            width: 84,
            height: 84,
            shape: "round-rectangle",
            "background-color": "#ffffff",
            "border-width": 2,
            "border-color": "#cbd5e1",
            color: "#0f172a",
            label: "data(label)",
            "text-wrap": "wrap",
            "text-max-width": 72,
            "text-valign": "center",
            "text-halign": "center",
            "font-size": 11,
            "font-weight": 700,
            padding: "10px",
          },
        },
        {
          selector: 'node[role = "focus"]',
          style: {
            "background-color": "#eff6ff",
            "border-color": "#2563eb",
            "border-width": 3,
          },
        },
        {
          selector: "node:selected",
          style: {
            "border-color": "#0f172a",
            "overlay-opacity": 0,
          },
        },
        {
          selector: "edge",
          style: {
            width: 2,
            "curve-style": "bezier",
            "line-color": "data(lineColor)",
            "target-arrow-color": "data(lineColor)",
            "target-arrow-shape": "triangle",
            "arrow-scale": 0.85,
            opacity: 0.82,
          },
        },
        {
          selector: "edge:selected",
          style: {
            width: 3,
            opacity: 1,
            "overlay-opacity": 0,
          },
        },
      ],
      userZoomingEnabled: true,
      userPanningEnabled: true,
      wheelSensitivity: 0.2,
      minZoom: 0.45,
      maxZoom: 1.8,
    });

    cy.on("tap", "node", (event) => {
      emitNodeSelection(event.target);
    });

    cy.on("tap", "edge", (event) => {
      emitEdgeSelection(event.target);
    });

    cy.on("tap", (event) => {
      if (event.target === cy) {
        cy.elements().unselect();
        emit("clear-selection");
      }
    });
  }

  cy.elements().remove();
  cy.add(buildElements(props.graph));
  nextTick(() => {
    if (!cy) {
      return;
    }

    requestAnimationFrame(() => {
      if (!cy) {
        return;
      }

      cy.resize();
      const layout = cy.nodes().length <= 1 ? singleNodeLayoutOptions() : layoutOptions();
      cy.layout(layout).run();
      cy.fit(cy.elements(), cy.nodes().length <= 1 ? 80 : 36);
      if (cy.nodes().length <= 1) {
        cy.center(cy.nodes());
      }
    });
  });
}

onMounted(() => {
  ensureGraph();
});

watch(
  () => props.graph,
  () => {
    ensureGraph();
  },
  { deep: true }
);

onBeforeUnmount(() => {
  destroyGraph();
});
</script>

<template>
  <div class="relation-graph-shell">
    <div v-if="loading" class="relation-graph-state">
      正在分析文件关联...
    </div>
    <div v-else-if="!hasGraph" class="relation-graph-state">
      当前还没有可展示的文件关联。
    </div>
    <div v-else-if="isSingleNodeGraph" class="relation-graph-single">
      <div class="relation-graph-single-card">
        <span class="relation-graph-single-tag">当前文件</span>
        <b>{{ singleNode?.label }}</b>
        <span>{{ singleNode?.directoryLabel || "当前目录" }}</span>
      </div>
    </div>
    <div v-else ref="hostRef" class="relation-graph-host"></div>
  </div>
</template>
