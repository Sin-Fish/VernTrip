<script setup>
import { useAppState } from "@/composables/useAppState";

const ui = {
  title: "\u5f53\u524d\u5de5\u4f5c\u533a\u80fd\u529b",
  enableAll: "\u5168\u90e8\u5f00\u542f",
  disableAll: "\u5168\u90e8\u5173\u95ed",
};

const skillMetaMap = {
  "evidence-doc": {
    name: "\u8bc1\u636e\u5316\u6587\u6863\u751f\u6210",
    summary: "\u7cfb\u7edf\u5185\u7f6e\uff1a\u5df2\u542f\u7528",
  },
  organize: {
    name: "\u8d44\u6599\u6574\u7406",
    summary: "\u7cfb\u7edf\u5185\u7f6e\uff1a\u5df2\u542f\u7528",
  },
  "web-fill": {
    name: "\u7f51\u9875\u8d44\u6599\u8865\u5145",
    summary: "\u7cfb\u7edf\u5185\u7f6e\uff1a\u8865\u5145\u5206\u652f",
  },
};

const agentMetaMap = {
  decision: {
    label: "\u51b3\u7b56\u667a\u80fd\u4f53",
    description: "\u5141\u8bb8\u7528\u4e8e\u8def\u5f84\u89c4\u5212",
  },
  execution: {
    label: "\u6267\u884c\u667a\u80fd\u4f53",
    description: "\u5141\u8bb8\u6267\u884c\u68c0\u7d22\u4e0e\u751f\u6210",
  },
  inspection: {
    label: "\u5ba1\u67e5\u667a\u80fd\u4f53",
    description: "\u5141\u8bb8\u68c0\u67e5\u6574\u7406\u7ed3\u679c",
  },
};

const {
  state,
  closeSkillModal,
  toggleAllSkillAgents,
  toggleSkillRow,
} = useAppState();

function closeFromMask(event) {
  if (event.target === event.currentTarget) {
    closeSkillModal();
  }
}

function displaySkillName(skill) {
  return skillMetaMap[skill.id]?.name ?? skill.name;
}

function displaySkillSummary(skill) {
  return skillMetaMap[skill.id]?.summary ?? skill.summary;
}

function displayAgentLabel(agent) {
  return agentMetaMap[agent.id]?.label ?? agent.label;
}

function displayAgentDescription(agent) {
  return agentMetaMap[agent.id]?.description ?? agent.description;
}
</script>

<template>
  <div
    :class="['mask', { open: state.skillModalOpen }]"
    @click="closeFromMask"
  >
    <section class="modal skill-config-modal">
      <div class="mh">
        <b>{{ ui.title }}</b>
        <div class="actions">
          <button class="btn" type="button" @click="toggleAllSkillAgents(true)">{{ ui.enableAll }}</button>
          <button class="btn" type="button" @click="toggleAllSkillAgents(false)">{{ ui.disableAll }}</button>
          <button class="close" type="button" @click="closeSkillModal">x</button>
        </div>
      </div>
      <div class="mb">
        <div class="skill-list">
          <div
            v-for="skill in state.skillPermissions"
            :key="skill.id"
            :class="['skill-row', { 'skill-open': skill.open }]"
          >
            <button
              class="skill-line"
              type="button"
              @click="toggleSkillRow(skill.id)"
            >
              <b>{{ displaySkillName(skill) }}</b>
              <span>{{ displaySkillSummary(skill) }}</span>
              <i>+</i>
            </button>
            <div class="skill-detail">
              <div class="agent-grid">
                <label
                  v-for="agent in skill.agents"
                  :key="agent.id"
                  class="agent-toggle"
                >
                  <span class="agent-option">
                    <input
                      v-model="agent.enabled"
                      type="checkbox"
                    />
                    <b>{{ displayAgentLabel(agent) }}</b>
                  </span>
                  <span>{{ displayAgentDescription(agent) }}</span>
                </label>
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>
  </div>
</template>
