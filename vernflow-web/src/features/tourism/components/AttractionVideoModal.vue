<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { buildGuideScenes } from '../data/attractionShowcase'

const props = defineProps({
  resource: { type: Object, required: true },
  city: { type: String, required: true },
  images: { type: Array, default: () => [] },
})

const emit = defineEmits(['close', 'plan'])
const activeScene = ref(0)
const isPlaying = ref(true)
let sceneTimer

const scenes = computed(() => buildGuideScenes(props.resource, props.city))
const sceneImages = computed(() => props.images.length ? props.images : ['/images/banner1.jpg'])
const progress = computed(() => ((activeScene.value + 1) / scenes.value.length) * 100)

function advanceScene() {
  activeScene.value = (activeScene.value + 1) % scenes.value.length
}

function startTimer() {
  window.clearInterval(sceneTimer)
  if (isPlaying.value) sceneTimer = window.setInterval(advanceScene, 4200)
}

function togglePlayback() {
  isPlaying.value = !isPlaying.value
  startTimer()
}

function selectScene(index) {
  activeScene.value = index
  startTimer()
}

function handleKeydown(event) {
  if (event.key === 'Escape') emit('close')
  if (event.key === ' ') {
    event.preventDefault()
    togglePlayback()
  }
}

watch(() => props.resource?.id, () => {
  activeScene.value = 0
  isPlaying.value = true
  startTimer()
})

onMounted(() => {
  document.body.classList.add('video-guide-open')
  window.addEventListener('keydown', handleKeydown)
  startTimer()
})

onBeforeUnmount(() => {
  document.body.classList.remove('video-guide-open')
  window.removeEventListener('keydown', handleKeydown)
  window.clearInterval(sceneTimer)
})
</script>

<template>
  <Teleport to="body">
    <div class="video-guide-backdrop" @click.self="emit('close')">
      <section class="video-guide" aria-modal="true" aria-label="AI 景点视频导览" role="dialog">
        <div class="guide-stage">
          <video v-if="resource.videoUrl" :src="resource.videoUrl" autoplay controls playsinline></video>
          <template v-else>
            <TransitionGroup name="guide-frame">
              <img
                v-for="(image, index) in sceneImages"
                v-show="index === activeScene % sceneImages.length"
                :key="image"
                :src="image"
                alt=""
              />
            </TransitionGroup>
          </template>
          <div class="guide-shade"></div>
          <header>
            <span>AI VIDEO GUIDE</span>
            <button type="button" aria-label="关闭视频导览" @click="emit('close')">×</button>
          </header>
          <div class="guide-caption">
            <span>{{ scenes[activeScene].kicker }}</span>
            <h2>{{ scenes[activeScene].title }}</h2>
            <p>{{ scenes[activeScene].narration }}</p>
          </div>
          <div class="guide-controls">
            <button class="play-control" type="button" :aria-label="isPlaying ? '暂停' : '播放'" @click="togglePlayback">
              <i :class="{ paused: !isPlaying }"></i>
              <span>{{ isPlaying ? '暂停' : '播放' }}</span>
            </button>
            <div class="scene-dots">
              <button v-for="(_, index) in scenes" :key="index" :class="{ active: index === activeScene }" type="button" @click="selectScene(index)"><i></i></button>
            </div>
            <button class="guide-plan" type="button" @click="emit('plan', resource)">加入规划 <span>→</span></button>
          </div>
          <div class="guide-progress"><i :style="{ width: `${progress}%` }"></i></div>
        </div>
        <aside>
          <span>NOW SHOWING</span>
          <h3>{{ resource.name }}</h3>
          <p>{{ city }} · {{ resource.area || resource.type }}</p>
          <ol>
            <li v-for="(scene, index) in scenes" :key="scene.kicker" :class="{ active: index === activeScene }" @click="selectScene(index)">
              <b>0{{ index + 1 }}</b><div><span>{{ scene.kicker }}</span><strong>{{ scene.title }}</strong></div>
            </li>
          </ol>
        </aside>
      </section>
    </div>
  </Teleport>
</template>

<style scoped>
.video-guide-backdrop { position: fixed; z-index: 1300; inset: 0; display: grid; place-items: center; padding: 34px; background: rgba(13, 25, 22, .78); backdrop-filter: blur(12px); }
.video-guide { display: grid; width: min(1180px, 96vw); height: min(690px, 88vh); grid-template-columns: minmax(0, 1fr) 270px; overflow: hidden; background: #152d27; box-shadow: 0 35px 100px rgba(0, 0, 0, .38); }
.guide-stage { position: relative; min-width: 0; overflow: hidden; background: #17322b; }
.guide-stage > img, .guide-stage > video { position: absolute; inset: 0; width: 100%; height: 100%; object-fit: cover; }
.guide-stage > img { transform: scale(1.035); animation: guide-pan 8s ease-out both; }
.guide-shade { position: absolute; inset: 0; background: linear-gradient(180deg, rgba(13, 28, 24, .3), transparent 34%, rgba(10, 25, 21, .88)), linear-gradient(90deg, rgba(11, 27, 23, .35), transparent 65%); }
.guide-stage header { position: absolute; z-index: 2; top: 0; right: 0; left: 0; display: flex; align-items: center; justify-content: space-between; padding: 26px 30px; color: #fff8e9; }
.guide-stage header span { font: 700 10px/1 Georgia, serif; letter-spacing: 3px; }
.guide-stage header button { display: grid; width: 38px; height: 38px; place-items: center; border: 1px solid rgba(255,255,255,.35); border-radius: 50%; background: rgba(20, 39, 34, .28); color: white; cursor: pointer; font-size: 22px; }
.guide-caption { position: absolute; z-index: 2; right: 40px; bottom: 112px; left: 42px; max-width: 680px; color: #fff8e9; }
.guide-caption > span { color: #e5ae88; font: 700 10px/1 Georgia, serif; letter-spacing: 2.5px; }
.guide-caption h2 { margin: 13px 0 10px; font-family: 'Noto Serif SC', serif; font-size: clamp(34px, 4vw, 56px); font-weight: 500; letter-spacing: -2px; line-height: 1.13; }
.guide-caption p { max-width: 620px; margin: 0; color: rgba(255, 248, 233, .78); font-size: 13px; line-height: 1.9; }
.guide-controls { position: absolute; z-index: 3; right: 30px; bottom: 35px; left: 36px; display: grid; grid-template-columns: 110px 1fr auto; align-items: center; gap: 24px; color: white; }
.play-control, .guide-plan { border: 0; background: transparent; color: #fff8e9; cursor: pointer; font-family: inherit; }
.play-control { display: flex; align-items: center; gap: 10px; font-size: 10px; }
.play-control i { width: 12px; height: 14px; border-right: 3px solid currentColor; border-left: 3px solid currentColor; }
.play-control i.paused { width: 0; height: 0; border: 0; border-top: 7px solid transparent; border-bottom: 7px solid transparent; border-left: 11px solid currentColor; }
.scene-dots { display: flex; align-items: center; gap: 7px; }
.scene-dots button { width: 28px; padding: 8px 0; border: 0; background: transparent; cursor: pointer; }
.scene-dots i { display: block; height: 2px; background: rgba(255,255,255,.35); transition: background .2s ease, transform .2s ease; }
.scene-dots button.active i { background: #e2a37f; transform: scaleY(2); }
.guide-plan { min-height: 38px; padding: 0 17px; border: 1px solid rgba(255,255,255,.48); font-size: 10px; }
.guide-plan span { margin-left: 14px; }
.guide-progress { position: absolute; z-index: 3; right: 0; bottom: 0; left: 0; height: 3px; background: rgba(255,255,255,.16); }
.guide-progress i { display: block; height: 100%; background: #d88567; transition: width .45s ease; }
.video-guide aside { display: flex; padding: 34px 25px 26px; color: #fff8e9; flex-direction: column; }
.video-guide aside > span { color: #d99a76; font: 700 8px/1 Georgia, serif; letter-spacing: 2.5px; }
.video-guide aside h3 { margin: 13px 0 6px; font-family: 'Noto Serif SC', serif; font-size: 23px; font-weight: 500; line-height: 1.35; }
.video-guide aside > p { margin: 0; color: rgba(255,255,255,.48); font-size: 9px; }
.video-guide ol { display: grid; gap: 0; margin: auto 0 0; padding: 0; list-style: none; }
.video-guide li { display: grid; grid-template-columns: 28px 1fr; gap: 9px; padding: 17px 0; border-top: 1px solid rgba(255,255,255,.1); color: rgba(255,255,255,.36); cursor: pointer; transition: color .2s ease; }
.video-guide li.active { color: #fff8e9; }
.video-guide li > b { color: #d28a69; font: 500 10px/1 Georgia, serif; }
.video-guide li div { display: grid; gap: 5px; }
.video-guide li span { font-size: 7px; letter-spacing: 1px; }
.video-guide li strong { font-family: 'Noto Serif SC', serif; font-size: 10px; font-weight: 500; line-height: 1.5; }
.guide-frame-enter-active, .guide-frame-leave-active { transition: opacity .8s ease, transform 1s ease; }
.guide-frame-enter-from, .guide-frame-leave-to { opacity: 0; transform: scale(1.08); }
@keyframes guide-pan { from { transform: scale(1.06) translateX(-1%); } to { transform: scale(1.01) translateX(1%); } }
@media (max-width: 820px) {
  .video-guide-backdrop { padding: 0; }
  .video-guide { width: 100%; height: 100%; grid-template-columns: 1fr; }
  .video-guide aside { display: none; }
  .guide-caption { right: 24px; bottom: 125px; left: 24px; }
  .guide-caption h2 { font-size: 34px; }
  .guide-controls { right: 22px; bottom: 46px; left: 22px; grid-template-columns: auto 1fr auto; gap: 12px; }
}
</style>

<style>
body.video-guide-open { overflow: hidden; }
</style>
