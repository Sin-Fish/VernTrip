<script setup>
import { computed, ref } from 'vue'
import { matchesKeywords, rankingCategories, rankingScore } from '../data/attractionShowcase'

const props = defineProps({
  resources: { type: Array, default: () => [] },
  city: { type: String, required: true },
  imageFor: { type: Function, required: true },
})

const emit = defineEmits(['details', 'video', 'plan'])
const activeCategory = ref('all')
const listSection = ref(null)
const category = computed(() => rankingCategories.find((item) => item.value === activeCategory.value) ?? rankingCategories[0])
const rankedResources = computed(() => {
  const attractionPool = props.resources.filter((resource) => !['餐饮', '住宿'].includes(resource.type))
  const searchablePool = activeCategory.value === 'food' ? props.resources : attractionPool
  const filtered = activeCategory.value === 'all'
    ? attractionPool
    : searchablePool.filter((resource) => matchesKeywords(resource, category.value.keywords))
  const candidates = filtered.length >= 3 ? filtered : attractionPool
  return candidates.slice(0, 8).map((resource, index) => ({ resource, score: rankingScore(resource, index) }))
})
const podium = computed(() => rankedResources.value.slice(0, 3))
const rankingList = computed(() => rankedResources.value.slice(3))

function scrollToList() {
  listSection.value?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}
</script>

<template>
  <section class="attraction-ranking">
    <header class="ranking-toolbar">
      <div><span>CITY HEAT INDEX</span><b>{{ city }}热度榜</b></div>
      <nav aria-label="榜单分类">
        <button v-for="item in rankingCategories" :key="item.value" :class="{ active: activeCategory === item.value }" type="button" @click="activeCategory = item.value">{{ item.label }}</button>
      </nav>
    </header>

    <template v-if="rankedResources.length">
      <section class="ranking-hero">
        <img :src="imageFor(podium[0].resource, 0)" :alt="`${city}榜首景点`" />
        <div class="ranking-hero-shade"></div>

        <div class="ranking-hero-copy">
          <span>EDITOR'S PICK <i></i> 本周城市热度</span>
          <div class="ranking-tags"><b>实时热度</b><b>{{ category.label }}景点</b><b>旅行灵感</b></div>
          <h3>{{ city }}景点热度排行榜</h3>
          <p>{{ podium[0].resource.name }}领衔本周榜单，从真实文旅资源中发现值得专程停留的地方。</p>
          <div class="hero-actions">
            <button type="button" @click="scrollToList">查看完整榜单 <b>→</b></button>
            <button type="button" @click="emit('video', podium[0].resource)"><i></i> 榜首导览</button>
          </div>
        </div>

        <aside class="top-three" aria-label="排行榜前三名">
          <div class="window-frame" aria-hidden="true"><i></i><i></i><i></i><i></i></div>
          <span>TOP THREE · {{ category.label }}</span>
          <button v-for="(item, index) in podium" :key="item.resource.id" type="button" @click="emit('details', item.resource)">
            <b>0{{ index + 1 }}</b>
            <strong>{{ item.resource.name }}</strong>
            <em>{{ item.score }}<small>°</small></em>
          </button>
          <p>榜单随城市资源与偏好分类更新</p>
        </aside>
      </section>

      <section ref="listSection" class="ranking-catalog">
        <header>
          <div><span>FULL LIST · {{ city }}</span><h3>继续发现上榜地点</h3></div>
          <b>{{ String(rankedResources.length).padStart(2, '0') }}</b>
        </header>

        <div class="ranking-card-grid">
          <article v-for="(item, index) in rankingList" :key="item.resource.id" class="ranking-card">
            <div class="ranking-card-visual">
              <img :src="imageFor(item.resource, index + 3)" alt="" />
              <span>0{{ index + 4 }}</span>
              <em>{{ item.score }}<small> 热度</small></em>
            </div>
            <div class="ranking-card-copy">
              <small>{{ item.resource.area || city }} · {{ item.resource.type }}</small>
              <h4>{{ item.resource.name }}</h4>
              <p>{{ (item.resource.tags || []).slice(0, 3).join(' · ') || item.resource.note }}</p>
            </div>
            <button class="ranking-card-hit" type="button" :aria-label="`查看${item.resource.name}`" @click="emit('details', item.resource)"></button>
            <button class="ranking-bookmark" type="button" :aria-label="`将${item.resource.name}加入规划`" @click="emit('plan', item.resource)">
              <svg aria-hidden="true" viewBox="0 0 24 24"><path d="M7 4h10v16l-5-3-5 3z" /></svg><span>加入规划</span>
            </button>
            <button class="ranking-video" type="button" :aria-label="`观看${item.resource.name}视频导览`" @click="emit('video', item.resource)"><i></i><span>视频导览</span></button>
          </article>
        </div>
      </section>
    </template>
  </section>
</template>

<style scoped>
.attraction-ranking { display: grid; gap: 12px; }
.ranking-toolbar { display: flex; min-height: 38px; align-items: center; justify-content: space-between; gap: 20px; border-bottom: 1px solid rgba(44, 36, 27, .13); }
.ranking-toolbar > div { display: flex; align-items: baseline; gap: 12px; }
.ranking-toolbar > div span { color: #a6533f; font: 700 8px/1 Georgia, serif; letter-spacing: 2px; }
.ranking-toolbar > div b { color: #2c423b; font-family: 'Noto Serif SC', serif; font-size: 12px; font-weight: 600; }
.ranking-toolbar nav { display: flex; gap: 5px; }
.ranking-toolbar nav button { min-width: 52px; padding: 7px 12px; border: 1px solid rgba(49,91,90,.15); border-radius: 999px; background: rgba(255,255,255,.28); color: #64716b; cursor: pointer; font-family: inherit; font-size: 8px; }
.ranking-toolbar nav button.active { border-color: #315b5a; background: #315b5a; color: #fffaf1; }
.ranking-hero { position: relative; height: clamp(340px, calc(100vh - 315px), 420px); min-height: 340px; overflow: hidden; border-top: 2px solid #315b5a; background: #193831; box-shadow: 0 18px 42px rgba(38, 51, 45, .12); }
.ranking-hero > img { width: 100%; height: 100%; object-fit: cover; transition: transform .8s ease; }
.ranking-hero:hover > img { transform: scale(1.018); }
.ranking-hero-shade { position: absolute; inset: 0; background: linear-gradient(90deg, rgba(14, 47, 39, .94) 0%, rgba(14, 47, 39, .7) 34%, rgba(14, 47, 39, .13) 68%, rgba(14, 47, 39, .25) 100%), linear-gradient(180deg, rgba(0,0,0,.02), rgba(6,27,22,.28)); }
.ranking-hero-copy { position: absolute; z-index: 2; top: 50%; left: clamp(25px, 3.5vw, 58px); width: min(530px, 48%); color: #fffaf1; transform: translateY(-50%); }
.ranking-hero-copy > span { display: flex; align-items: center; gap: 11px; color: #dfa07f; font: 700 8px/1 Georgia, 'Noto Serif SC', serif; letter-spacing: 1.2px; }
.ranking-hero-copy > span i { width: 34px; border-top: 1px solid rgba(226,165,129,.6); }
.ranking-tags { display: flex; gap: 7px; margin-top: 13px; }
.ranking-tags b { padding: 5px 8px; border: 1px solid rgba(255,255,255,.28); font-size: 7px; font-weight: 400; }
.ranking-hero-copy h3 { margin: 15px 0 10px; font-family: 'Noto Serif SC', serif; font-size: clamp(27px, 3vw, 43px); font-weight: 500; letter-spacing: 2px; line-height: 1.18; }
.ranking-hero-copy p { max-width: 460px; margin: 0; color: rgba(255,255,255,.66); font-size: 9px; line-height: 1.8; }
.hero-actions { display: flex; gap: 8px; margin-top: 21px; }
.hero-actions button { min-height: 36px; padding: 0 17px; border: 1px solid rgba(255,255,255,.38); background: rgba(255,255,255,.05); color: #fffaf1; cursor: pointer; font-family: inherit; font-size: 8px; }
.hero-actions button:last-child { border-color: rgba(215,137,102,.78); background: rgba(176,88,64,.68); }
.hero-actions button b { margin-left: 18px; color: #dfa07f; }
.hero-actions button i, .ranking-video i { display: inline-block; width: 0; height: 0; margin-right: 7px; border-top: 4px solid transparent; border-bottom: 4px solid transparent; border-left: 7px solid currentColor; }
.top-three { position: absolute; z-index: 2; top: 27px; right: 25px; bottom: 27px; display: flex; width: clamp(300px, 30%, 405px); flex-direction: column; justify-content: center; padding: 29px 31px; overflow: hidden; border: 1px solid rgba(102, 79, 48, .28); background: linear-gradient(rgba(248,242,220,.28), rgba(230,214,177,.18)), url('/images/ranking-paper-v1.png') center / cover; color: #30443a; box-shadow: inset 0 0 2px rgba(255,255,255,.75), inset 0 0 26px rgba(98,70,37,.16), 0 18px 38px rgba(16,35,29,.28); }
.top-three::before { position: absolute; z-index: 0; inset: 0; background: radial-gradient(ellipse at center, transparent 48%, rgba(104,72,36,.13) 100%); content: ''; pointer-events: none; }
.top-three::after { position: absolute; z-index: 0; inset: 8px; border: 3px double rgba(91,61,31,.34); box-shadow: inset 0 0 13px rgba(91,55,25,.05), 0 0 0 1px rgba(250,241,211,.35); content: ''; pointer-events: none; }
.top-three > * { position: relative; z-index: 1; }
.window-frame { position: absolute; z-index: 2; inset: 13px; pointer-events: none; }
.window-frame i { position: absolute; width: 34px; height: 34px; background: center / contain no-repeat url("data:image/svg+xml,%3Csvg viewBox='0 0 36 36' xmlns='http://www.w3.org/2000/svg'%3E%3Cpath d='M2 34V2h32M8 28V8h20M14 22v-8h8' fill='none' stroke='%23725232' stroke-width='1.5'/%3E%3C/svg%3E"); opacity: .6; }
.window-frame i:nth-child(1) { top: 0; left: 0; }
.window-frame i:nth-child(2) { top: 0; right: 0; transform: rotate(90deg); }
.window-frame i:nth-child(3) { right: 0; bottom: 0; transform: rotate(180deg); }
.window-frame i:nth-child(4) { bottom: 0; left: 0; transform: rotate(270deg); }
.top-three > span { margin-bottom: 11px; color: #974c3b; font: 700 7px/1 Georgia, serif; letter-spacing: 1.8px; text-shadow: 0 1px rgba(255,244,210,.55); }
.top-three > button { display: grid; min-height: 65px; grid-template-columns: 52px minmax(0,1fr) 47px; align-items: center; gap: 8px; padding: 0; border: 0; border-bottom: 1px solid rgba(94,61,31,.17); background: transparent; color: inherit; cursor: pointer; text-align: left; }
.top-three > button:hover strong { color: #a6533f; transform: translateX(3px); }
.top-three button > b { color: #a74332; font: 600 24px/1 Georgia, serif; text-shadow: 0 1px rgba(255,241,202,.65); }
.top-three button > strong { overflow: hidden; font-family: 'Noto Serif SC', serif; font-size: 13px; font-weight: 600; text-overflow: ellipsis; text-shadow: 0 1px rgba(255,244,210,.45); transition: .2s ease; white-space: nowrap; }
.top-three button > em { color: #315b5a; font: 500 16px/1 Georgia, serif; font-style: normal; text-align: right; text-shadow: 0 1px rgba(255,244,210,.5); }
.top-three button > em small { font-size: 8px; }
.top-three > p { margin: 13px 0 0; color: #75654c; font-size: 7px; letter-spacing: .2px; }
.ranking-catalog { scroll-margin-top: 85px; }
.ranking-catalog > header { display: flex; align-items: end; justify-content: space-between; padding: 11px 0 8px; border-bottom: 1px solid rgba(44,36,27,.14); }
.ranking-catalog > header span { color: #a6533f; font: 700 7px/1 Georgia, serif; letter-spacing: 1.6px; }
.ranking-catalog > header h3 { margin: 5px 0 0; color: #293f38; font-family: 'Noto Serif SC', serif; font-size: 18px; font-weight: 600; }
.ranking-catalog > header > b { color: rgba(49,91,90,.25); font: 500 25px/1 Georgia, serif; }
.ranking-card-grid { display: grid; grid-template-columns: repeat(3, minmax(0,1fr)); gap: 12px; padding-top: 10px; }
.ranking-card { position: relative; min-width: 0; overflow: hidden; border-top: 2px solid #315b5a; background: rgba(255,252,246,.88); box-shadow: 0 10px 24px rgba(38,51,45,.07); }
.ranking-card-visual { position: relative; height: 145px; overflow: hidden; }
.ranking-card-visual::after { position: absolute; inset: 0; background: linear-gradient(180deg, transparent 45%, rgba(20,39,33,.5)); content: ''; }
.ranking-card-visual img { width: 100%; height: 100%; object-fit: cover; transition: transform .45s ease; }
.ranking-card:hover .ranking-card-visual img { transform: scale(1.04); }
.ranking-card-visual > span { position: absolute; z-index: 2; top: 12px; right: 13px; color: #fff; font: 600 9px/1 Georgia, serif; }
.ranking-card-visual > em { position: absolute; z-index: 2; right: 13px; bottom: 10px; color: #fffaf1; font: 500 19px/1 Georgia, serif; font-style: normal; }
.ranking-card-visual > em small { font: 400 7px/1 'Noto Serif SC', serif; }
.ranking-card-copy { min-height: 83px; padding: 13px 16px 14px; }
.ranking-card-copy small { color: #a6533f; font-size: 7px; }
.ranking-card-copy h4 { margin: 6px 0 5px; overflow: hidden; color: #293f38; font-family: 'Noto Serif SC', serif; font-size: 14px; font-weight: 600; text-overflow: ellipsis; white-space: nowrap; }
.ranking-card-copy p { margin: 0; overflow: hidden; color: #85877f; font-size: 8px; text-overflow: ellipsis; white-space: nowrap; }
.ranking-card-hit { position: absolute; z-index: 1; inset: 0; width: 100%; border: 0; background: transparent; cursor: pointer; }
.ranking-bookmark { position: absolute; z-index: 3; top: 0; left: 15px; display: flex; width: 35px; height: 43px; align-items: center; gap: 7px; overflow: hidden; padding: 0 10px; border: 0; border-radius: 0 0 10px 10px; background: #b76249; color: #fffaf1; cursor: pointer; transition: width .28s ease; white-space: nowrap; }
.ranking-bookmark:hover, .ranking-bookmark:focus-visible { width: 93px; }
.ranking-bookmark svg { width: 15px; min-width: 15px; fill: none; stroke: currentColor; stroke-width: 1.6; }
.ranking-bookmark span { font-size: 8px; opacity: 0; transition: opacity .18s ease .08s; }
.ranking-bookmark:hover span, .ranking-bookmark:focus-visible span { opacity: 1; }
.ranking-video { position: absolute; z-index: 3; right: 13px; bottom: 12px; display: flex; min-height: 27px; align-items: center; padding: 0 9px; border: 1px solid rgba(49,91,90,.2); background: rgba(255,252,246,.94); color: #315b5a; cursor: pointer; font-family: inherit; font-size: 7px; }
@media (max-width: 1050px) {
  .ranking-hero-copy { width: 45%; }
  .top-three { width: 40%; }
}
@media (max-width: 820px) {
  .ranking-toolbar { align-items: start; flex-direction: column; padding-bottom: 9px; }
  .ranking-toolbar nav { width: 100%; overflow-x: auto; }
  .ranking-hero { height: auto; min-height: 670px; }
  .ranking-hero-copy { top: 35px; width: auto; max-width: none; transform: none; right: 30px; }
  .top-three { top: auto; right: 25px; bottom: 25px; left: 25px; width: auto; }
  .ranking-card-grid { grid-template-columns: repeat(2, minmax(0,1fr)); }
}
@media (max-width: 560px) {
  .ranking-hero { min-height: 630px; }
  .ranking-hero-copy h3 { font-size: 27px; }
  .ranking-hero-copy p { display: none; }
  .top-three { padding: 20px; }
  .top-three > button { grid-template-columns: 45px minmax(0,1fr) 42px; }
  .ranking-card-grid { grid-template-columns: 1fr; }
}
</style>
