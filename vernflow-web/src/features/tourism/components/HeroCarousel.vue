<template>
  <div
    class="carousel-wrap"
    @mouseenter="pause"
    @mouseleave="resume"
  >
    <!-- 图片轨道 -->
    <div class="carousel-view">
      <Transition name="carousel-fade" mode="out-in">
        <img
          :key="current"
          :src="slides[current]"
          alt=""
          class="carousel-img"
        />
      </Transition>
    </div>

    <!-- 左右按钮 -->
    <button
      v-if="slides.length > 1"
      class="carousel-arrow left"
      @click="prev"
    >
      ‹
    </button>
    <button
      v-if="slides.length > 1"
      class="carousel-arrow right"
      @click="next"
    >
      ›
    </button>

    <!-- 底部圆点 -->
    <div v-if="slides.length > 1" class="carousel-dots">
      <span
        v-for="(_, i) in slides"
        :key="i"
        class="dot"
        :class="{ active: i === current }"
        @click="goTo(i)"
      ></span>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount } from 'vue'

const props = defineProps({
  slides: { type: Array, default: () => [] },
  interval: { type: Number, default: 5000 },
})

const current = ref(0)
let timer = null

function startTimer() {
  stopTimer()
  if (props.slides.length <= 1) return
  timer = setInterval(() => {
    current.value = (current.value + 1) % props.slides.length
  }, props.interval)
}
function stopTimer() {
  if (timer) { clearInterval(timer); timer = null }
}
function pause() { stopTimer() }
function resume() { startTimer() }
function next() { stopTimer(); current.value = (current.value + 1) % props.slides.length; startTimer() }
function prev() { stopTimer(); current.value = (current.value - 1 + props.slides.length) % props.slides.length; startTimer() }
function goTo(i) { if (i === current.value) return; stopTimer(); current.value = i; startTimer() }

onMounted(() => startTimer())
onBeforeUnmount(() => stopTimer())
</script>

<style scoped>
.carousel-wrap{
  position:relative;
  width:100%;
  height:430px;
  border-radius:20px;
  overflow:hidden;
  box-shadow:0 10px 30px rgba(120,90,50,.08);
  background:rgba(0,0,0,.03);
}
.carousel-view{
  width:100%;
  height:100%;
}
.carousel-img{
  width:100%;
  height:100%;
  object-fit:cover;
  display:block;
}

/* fade + scale */
.carousel-fade-enter-active{ transition:opacity .7s ease, transform .7s ease; }
.carousel-fade-leave-active{ transition:opacity .35s ease; }
.carousel-fade-enter-from{ opacity:0; transform:scale(1.05); }
.carousel-fade-leave-to{ opacity:0; }

/* 箭头 */
.carousel-arrow{
  position:absolute;
  top:50%;
  transform:translateY(-50%);
  width:56px; height:56px;
  border-radius:50%;
  border:none;
  background:rgba(255,248,238,.9);
  color:#5a4030;
  font-size:28px;
  cursor:pointer;
  display:flex; align-items:center; justify-content:center;
  transition:all .25s;
  z-index:2;
  line-height:1;
}
.carousel-arrow.left{ left:20px; }
.carousel-arrow.right{ right:20px; }
.carousel-arrow:hover{
  background:#fff;
  box-shadow:0 4px 16px rgba(0,0,0,.1);
  color:#2a1a0a;
}

/* 圆点 */
.carousel-dots{
  position:absolute;
  bottom:16px; left:50%;
  transform:translateX(-50%);
  display:flex; gap:8px; align-items:center;
  z-index:2;
}
.dot{
  width:8px; height:8px;
  border-radius:50%;
  background:rgba(180,160,130,.45);
  cursor:pointer;
  transition:all .3s;
}
.dot.active{
  width:18px;
  border-radius:999px;
  background:#B56A44;
}

/* 响应式 */
@media (max-width:1440px){ .carousel-wrap{ height:430px; } }
@media (max-width:1200px){ .carousel-wrap{ height:390px; } }
@media (max-width:992px){ .carousel-wrap{ height:340px; } }
@media (max-width:768px){
  .carousel-wrap{
    height:300px;
    width:100%;
    min-width:0;
    max-width:100%;
  }
  .carousel-arrow{ display:none; }
}
@media (max-width:540px){
  .carousel-wrap{ height:220px; }
}
</style>
