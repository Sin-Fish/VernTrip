<script setup>
const ui = {
  import: "\u5bfc\u5165",
  newTask: "\u65b0\u5efa\u4efb\u52a1",
};

defineProps({
  title: {
    type: String,
    required: true,
  },
  description: {
    type: String,
    default: "",
  },
});

defineEmits(["import", "new-task"]);
</script>

<template>
  <header class="top">
    <div class="title">
      <b>{{ title }}</b>
      <span v-if="description">{{ description }}</span>
    </div>
    <div class="actions">
      <button
        class="btn"
        type="button"
        @click="$emit('import')"
      >
        {{ ui.import }}
      </button>
      <button
        class="btn primary"
        type="button"
        @click="$emit('new-task')"
      >
        {{ ui.newTask }}
      </button>
    </div>
  </header>
</template>
