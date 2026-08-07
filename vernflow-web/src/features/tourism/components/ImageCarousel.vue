<template>
  <div
    class="image-carousel"
    @mouseenter="pause"
    @mouseleave="resume"
  >
    <!-- 图片轨道 -->
    <div class="carousel-viewport">
      <Transition name="carousel-fade" mode="out-in">
        <img
          :key="currentIndex"
          :src="images[currentIndex]"
          :alt="`轮播图 ${currentIndex + 1}`"
          class="carousel-img"
        />
      </Transition>
    </div>

    <!-- 左右箭头 -->
    <button
      v-if="images.length > 1"
      class="carousel-arrow prev"
      @click="prev"
      aria-label="上一张"
    >‹</button>
    <button
      v-if="images.length > 1"
      class="carousel-arrow next"
      @click="next"
      aria-label="下一张"
    >›</button>

    <!-- 圆点导航 -->
    <div v-if="images.length > 1" class="carousel-dots">
      <span
        v-for="(_, idx) in images"
        :key="idx"
        class="dot"
        :class="{ active: idx === currentIndex }"
        @click="goTo(idx)"
      ></span>
    </div>
  </div>
</template>

<script setup>
import { ref, watch, onMounted, onBeforeUnmount } from 'vue'

const props = defineProps({
  images: {
    type: Array,
    default: () => [],
  },
  interval: {
    type: Number,
    default: 4000,
  },
})

const currentIndex = ref(0)
let timer = null

function startAutoPlay() {
  stopAutoPlay()
  if (props.images.length <= 1) return
  timer = setInterval(() => {
    currentIndex.value = (currentIndex.value + 1) % props.images.length
  }, props.interval)
}

function stopAutoPlay() {
  if (timer) {
    clearInterval(timer)
    timer = null
  }
}

function pause() {
  stopAutoPlay()
}

function resume() {
  startAutoPlay()
}

function prev() {
  stopAutoPlay()
  const len = props.images.length
  currentIndex.value = (currentIndex.value - 1 + len) % len
  startAutoPlay()
}

function next() {
  stopAutoPlay()
  currentIndex.value = (currentIndex.value + 1) % props.images.length
  startAutoPlay()
}

function goTo(idx) {
  if (idx === currentIndex.value) return
  stopAutoPlay()
  currentIndex.value = idx
  startAutoPlay()
}

// 当图片列表变化时重置到第一张
watch(() => props.images, () => {
  currentIndex.value = 0
  stopAutoPlay()
  startAutoPlay()
})

onMounted(() => {
  startAutoPlay()
})

onBeforeUnmount(() => {
  stopAutoPlay()
})
</script>

<style scoped>
.image-carousel {
  position: relative;
  width: 100%;
  height: 100%;
  border-radius: 16px;
  overflow: hidden;
  background: rgba(44, 38, 32, .04);
}

.carousel-viewport {
  width: 100%;
  height: 100%;
}

.carousel-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

/* ---- fade 过渡 ---- */
.carousel-fade-enter-active,
.carousel-fade-leave-active {
  transition: opacity .6s ease;
}
.carousel-fade-enter-from,
.carousel-fade-leave-to {
  opacity: 0;
}

/* ---- 箭头 ---- */
.carousel-arrow {
  position: absolute;
  top: 50%;
  transform: translateY(-50%);
  width: 38px;
  height: 38px;
  border-radius: 50%;
  border: none;
  background: rgba(255, 255, 255, .78);
  color: var(--ink);
  font-size: 20px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  opacity: 0;
  transition: opacity .25s, background .2s;
  z-index: 2;
}
.image-carousel:hover .carousel-arrow {
  opacity: 1;
}
.carousel-arrow:hover {
  background: rgba(255, 255, 255, .95);
}
.carousel-arrow.prev { left: 12px; }
.carousel-arrow.next { right: 12px; }

/* ---- 圆点 ---- */
.carousel-dots {
  position: absolute;
  bottom: 14px;
  left: 50%;
  transform: translateX(-50%);
  display: flex;
  gap: 8px;
  z-index: 2;
}
.dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: rgba(255, 255, 255, .55);
  cursor: pointer;
  transition: all .25s;
}
.dot.active {
  background: #fff;
  width: 22px;
  border-radius: 999px;
}
.dot:hover {
  background: rgba(255, 255, 255, .85);
}
</style>
