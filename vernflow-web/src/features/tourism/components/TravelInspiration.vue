<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { listTourismResources } from '@/lib/travelApi'
import AttractionRanking from './AttractionRanking.vue'
import AttractionVideoModal from './AttractionVideoModal.vue'
import SmartRecommendation from './SmartRecommendation.vue'
import { discoveryViews } from '../data/attractionShowcase'

const router = useRouter()
const primaryCities = ['杭州', '苏州', '南京', '成都', '厦门']
const moreCities = ['北京', '上海', '西安', '重庆', '广州', '青岛', '长沙', '武汉', '昆明', '大理', '泉州', '洛阳']
const cities = [...primaryCities, ...moreCities]
const city = ref('杭州')
const resources = ref([])
const selectedResource = ref(null)
const videoResource = ref(null)
const activeView = ref('featured')
const loading = ref(false)
const errorMessage = ref('')
let loadSequence = 0

const visualImages = [
  '/images/banner1.jpg',
  '/images/banner2.jpg',
  '/images/banner3.jpg',
  '/images/banner4.jpg',
  '/images/banner5.jpg',
  '/images/tripcard6.jpg',
]

const inspirationResources = computed(() => {
  const primary = resources.value.filter((item) => !['餐饮', '住宿'].includes(item.type))
  return (primary.length >= 6 ? primary : resources.value).slice(0, 8)
})
const featured = computed(() => inspirationResources.value[0] ?? null)
const secondary = computed(() => inspirationResources.value.slice(1, 7))
const resourceCounts = computed(() => {
  const districts = new Set(resources.value.map((item) => item.area).filter(Boolean))
  const verified = resources.value.filter((item) => item.locationStatus === 'VERIFIED').length
  return { total: resources.value.length, districts: districts.size, verified }
})

function visualImage(index = 0) {
  const cityOffset = Math.max(0, cities.indexOf(city.value)) * 2
  return visualImages[(cityOffset + index) % visualImages.length]
}

function resourceImage(resource, fallbackIndex = 0) {
  const index = inspirationResources.value.findIndex((item) => item.id === resource?.id)
  return visualImage(index >= 0 ? index : fallbackIndex)
}

function guideImages(resource) {
  const index = Math.max(0, inspirationResources.value.findIndex((item) => item.id === resource?.id))
  return [visualImage(index), visualImage(index + 1), visualImage(index + 2)]
}

function formatPrice(resource) {
  const minimum = Number(resource?.priceMin ?? 0)
  const maximum = Number(resource?.priceMax ?? 0)
  if (!minimum && !maximum) return '免费或现场为准'
  if (minimum === maximum) return `参考 ¥${minimum.toLocaleString('zh-CN')}`
  return `参考 ¥${minimum.toLocaleString('zh-CN')}–${maximum.toLocaleString('zh-CN')}`
}

function availabilityLabel(resource) {
  if (!resource?.reservationRequired) return '可直接前往'
  if (resource.availabilityStatus === 'SOLD_OUT') return '当前名额已满'
  if (resource.availabilityStatus === 'LIMITED') return '余量有限'
  return '建议提前预约'
}

function sourceLabel(resource) {
  if (resource?.source === '城市展示样例') return '城市精选样例'
  return resource?.source?.includes('高德') ? '高德动态 POI' : '已审核文旅资源'
}

function amapUrl(resource) {
  if (!Number.isFinite(Number(resource.longitude)) || !Number.isFinite(Number(resource.latitude))) {
    return `https://uri.amap.com/search?keyword=${encodeURIComponent(resource.placeName || resource.name)}&city=${encodeURIComponent(city.value)}&src=verntrip&callnative=0`
  }
  const position = `${resource.longitude},${resource.latitude}`
  return `https://uri.amap.com/marker?position=${encodeURIComponent(position)}&name=${encodeURIComponent(resource.placeName || resource.name)}&src=verntrip&coordinate=gaode&callnative=0`
}

function plannerQuery(resource) {
  return {
    destination: city.value,
    focus: resource.name,
    interests: (resource.tags ?? []).slice(0, 3).join(','),
  }
}

function startPlanning(resource) {
  selectedResource.value = null
  videoResource.value = null
  router.push({ name: 'travel-planner', query: plannerQuery(resource) })
}

function openDetails(resource) {
  selectedResource.value = resource
}

function closeDetails() {
  selectedResource.value = null
}

function openVideo(resource) {
  selectedResource.value = null
  videoResource.value = resource
}

function closeVideo() {
  videoResource.value = null
}

function selectMoreCity(event) {
  const nextCity = event.target.value
  if (nextCity) loadCity(nextCity)
}

async function loadCity(nextCity = city.value) {
  const sequence = ++loadSequence
  city.value = nextCity
  loading.value = true
  resources.value = []
  errorMessage.value = ''
  selectedResource.value = null
  videoResource.value = null
  try {
    const result = await listTourismResources(nextCity)
    if (sequence !== loadSequence) return
    const apiResources = Array.isArray(result) ? result : []
    resources.value = apiResources
    if (!resources.value.length) errorMessage.value = '这座城市暂时没有可展示的文旅资源。'
  } catch (error) {
    if (sequence !== loadSequence) return
    resources.value = []
    errorMessage.value = error.message || '旅行灵感暂时没有加载出来。'
  } finally {
    if (sequence === loadSequence) loading.value = false
  }
}

function handleKeydown(event) {
  if (event.key === 'Escape' && !videoResource.value) closeDetails()
}

onMounted(() => {
  window.addEventListener('keydown', handleKeydown)
  loadCity()
})

onBeforeUnmount(() => {
  loadSequence += 1
  window.removeEventListener('keydown', handleKeydown)
})
</script>

<template>
  <section class="inspiration-browser" aria-label="真实文旅资源灵感">
    <div class="city-rail">
      <nav v-if="activeView !== 'recommendation'" class="city-options" aria-label="灵感城市">
        <button
          v-for="option in primaryCities"
          :key="option"
          :class="{ active: city === option }"
          type="button"
          :disabled="loading && city === option"
          @click="loadCity(option)"
        >
          <b>{{ option }}</b>
        </button>
        <label class="more-city-select" :class="{ active: moreCities.includes(city) }">
          <select aria-label="选择更多城市" :value="moreCities.includes(city) ? city : ''" @change="selectMoreCity">
            <option disabled value="">更多城市</option>
            <option v-for="option in moreCities" :key="option" :value="option">{{ option }}</option>
          </select>
          <span>{{ moreCities.includes(city) ? city : '更多城市' }}</span>
          <i></i>
        </label>
      </nav>
      <div class="discovery-tabs" aria-label="灵感浏览方式" role="tablist">
        <button
          v-for="view in discoveryViews"
          :key="view.value"
          :class="{ active: activeView === view.value }"
          type="button"
          role="tab"
          :aria-selected="activeView === view.value"
          @click="activeView = view.value"
        >
          <small>{{ view.index }}</small>{{ view.label }}
        </button>
      </div>
      <div v-if="activeView !== 'recommendation'" class="catalog-proof">
        <span><b>{{ resourceCounts.total }}</b> 个地点</span>
        <i></i>
        <span>{{ resourceCounts.districts }} 个区域</span>
        <i></i>
        <span>{{ resourceCounts.verified }} 个坐标已核验</span>
      </div>
    </div>

    <SmartRecommendation
      v-if="activeView === 'recommendation'"
    />

    <div v-else-if="loading" class="inspiration-state">
      <i></i>
      <b>正在翻阅{{ city }}的地方资料</b>
      <p>高德 POI 与文旅资源库正在同步。</p>
    </div>

    <div v-else-if="errorMessage" class="inspiration-state error">
      <span>!</span>
      <b>这页灵感暂时空着</b>
      <p>{{ errorMessage }}</p>
      <button type="button" @click="loadCity(city)">重新读取</button>
    </div>

    <div v-else-if="featured && activeView === 'featured'" class="inspiration-layout">
      <article class="featured-place">
        <div class="featured-visual">
          <img :src="visualImage(0)" alt="城市旅行氛围" />
          <span class="featured-number">01</span>
          <span class="featured-city">{{ city }} · {{ featured.area || '城市精选' }}</span>
          <button class="featured-play" type="button" @click="openVideo(featured)"><i></i><span>AI 视频导览</span></button>
        </div>
        <button class="plan-bookmark featured-bookmark" type="button" aria-label="将主推荐加入规划" @click="startPlanning(featured)">
          <svg aria-hidden="true" viewBox="0 0 24 24"><path d="M7 4h10v16l-5-3-5 3z" /></svg>
          <span>加入规划</span>
        </button>
        <div class="featured-copy">
          <div class="featured-index">
            <span>EDITOR'S PICK</span>
            <i></i>
            <b>{{ sourceLabel(featured) }}</b>
          </div>
          <div class="place-flags">
            <span>{{ featured.type }}</span>
            <span v-for="tag in (featured.tags || []).slice(0, 2)" :key="tag">{{ tag }}</span>
          </div>
          <h3>{{ featured.name }}</h3>
          <p class="featured-note">{{ featured.note }}</p>
          <div class="featured-actions">
            <button type="button" @click="openDetails(featured)">查看地点资料 <b>→</b></button>
          </div>
        </div>
      </article>

      <section class="more-places">
        <header>
          <div>
            <span>MORE PLACES · {{ city }}</span>
            <h3>值得停留的地方</h3>
          </div>
          <b>{{ String(secondary.length).padStart(2, '0') }}</b>
        </header>

        <div class="place-grid">
          <article v-for="(resource, index) in secondary" :key="resource.id" class="place-card">
            <button class="place-card-main" type="button" @click="openDetails(resource)">
              <div class="place-card-visual">
                <img :src="visualImage(index + 1)" alt="" />
                <span>{{ String(index + 2).padStart(2, '0') }}</span>
                <mark>{{ resource.type }}</mark>
              </div>
              <div class="place-card-copy">
                <small>{{ resource.area || city }}</small>
                <h4>{{ resource.name }}</h4>
                <p>{{ resource.note }}</p>
                <div><span>{{ resource.openHours || '时间现场为准' }}</span><b>{{ formatPrice(resource) }}</b></div>
              </div>
            </button>
            <button class="plan-bookmark" type="button" :aria-label="`将${resource.name}加入规划`" @click="startPlanning(resource)">
              <svg aria-hidden="true" viewBox="0 0 24 24"><path d="M7 4h10v16l-5-3-5 3z" /></svg>
              <span>加入规划</span>
            </button>
            <button class="card-video" type="button" :aria-label="`观看${resource.name}视频导览`" @click="openVideo(resource)"><i></i><span>视频导览</span></button>
          </article>
        </div>
      </section>
    </div>

    <AttractionRanking
      v-else-if="featured && activeView === 'ranking'"
      :city="city"
      :image-for="resourceImage"
      :resources="resources"
      @details="openDetails"
      @plan="startPlanning"
      @video="openVideo"
    />

    <Teleport to="body">
      <Transition name="place-drawer">
        <div v-if="selectedResource" class="place-backdrop" @click.self="closeDetails">
          <aside class="place-detail" aria-label="景点详情">
            <button class="detail-close" type="button" aria-label="关闭景点详情" @click="closeDetails">×</button>
            <div class="detail-visual">
              <img :src="visualImage(inspirationResources.indexOf(selectedResource))" alt="城市旅行氛围" />
              <span>{{ city }} / {{ selectedResource.area }}</span>
            </div>
            <div class="detail-copy">
              <p class="detail-eyebrow">PLACE DOSSIER · {{ sourceLabel(selectedResource) }}</p>
              <h2>{{ selectedResource.name }}</h2>
              <p class="detail-summary">{{ selectedResource.note }}</p>
              <div class="detail-tags">
                <span v-for="tag in selectedResource.tags" :key="tag">{{ tag }}</span>
              </div>
              <dl>
                <div><dt>实际地点</dt><dd>{{ selectedResource.placeName || selectedResource.name }}</dd></div>
                <div><dt>地址</dt><dd>{{ selectedResource.address || `${city}${selectedResource.area || ''}` }}</dd></div>
                <div><dt>开放时间</dt><dd>{{ selectedResource.openHours || '以现场公告为准' }}</dd></div>
                <div><dt>参考花费</dt><dd>{{ formatPrice(selectedResource) }}</dd></div>
                <div><dt>预约状态</dt><dd>{{ availabilityLabel(selectedResource) }}</dd></div>
                <div><dt>坐标状态</dt><dd>{{ selectedResource.locationStatus === 'VERIFIED' ? '高德坐标已核验' : selectedResource.locationStatus === 'DEMO' ? '待接入坐标' : '近似坐标' }}</dd></div>
              </dl>
              <div class="detail-source">
                <span>数据来源</span>
                <b>{{ selectedResource.source }}</b>
              </div>
              <div class="detail-actions">
                <a :href="amapUrl(selectedResource)" target="_blank" rel="noopener noreferrer">在高德地图查看</a>
                <button type="button" @click="openVideo(selectedResource)">AI 视频导览</button>
                <button type="button" @click="startPlanning(selectedResource)">以这里开始规划 <span>→</span></button>
              </div>
            </div>
          </aside>
        </div>
      </Transition>
    </Teleport>

    <AttractionVideoModal
      v-if="videoResource"
      :city="city"
      :images="guideImages(videoResource)"
      :resource="videoResource"
      @close="closeVideo"
      @plan="startPlanning"
    />
  </section>
</template>

<style scoped>
.inspiration-browser { display: grid; gap: 20px; }
.city-rail { display: grid; grid-template-columns: 200px minmax(0, 1fr) 170px; align-items: stretch; min-height: 84px; overflow: hidden; border-top: 3px solid #315b5a; background: rgba(250, 246, 237, .82); box-shadow: 0 12px 32px rgba(62, 60, 50, .06); backdrop-filter: blur(12px); }
.city-rail nav { display: grid; grid-template-columns: repeat(5, minmax(80px, 1fr)); }
.city-rail nav button { display: grid; align-content: center; justify-items: center; gap: 5px; border: 0; border-right: 1px solid rgba(44, 36, 27, .08); background: transparent; color: #5e665f; cursor: pointer; font-family: 'Noto Serif SC', serif; transition: .25s ease; }
.city-rail nav button:hover { background: rgba(49, 91, 90, .06); color: #315b5a; }
.city-rail nav button.active { background: #315b5a; color: #fffaf1; }
.city-rail nav small { font: 700 7px/1 Georgia, serif; letter-spacing: 1.5px; opacity: .62; }
.city-rail nav b { font-size: 14px; letter-spacing: 2px; }
.catalog-proof { display: flex; align-items: center; gap: 12px; padding: 12px 17px; background: #ded1b9; }
.catalog-proof > span { color: #315b5a; font: 500 32px/1 Georgia, serif; }
.catalog-proof p { margin: 0; color: #625e54; font-size: 8px; line-height: 1.65; }

.inspiration-layout { display: grid; grid-template-columns: minmax(390px, .95fr) minmax(560px, 1.45fr); gap: 16px; }
.featured-place { position: sticky; top: 18px; min-height: 610px; overflow: hidden; background: #20362f; box-shadow: 0 18px 45px rgba(37, 49, 44, .15); }
.featured-place > img { position: absolute; inset: 0; width: 100%; height: 100%; object-fit: cover; }
.featured-shade { position: absolute; inset: 0; background: linear-gradient(180deg, rgba(21, 42, 37, .05), rgba(21, 42, 37, .32) 42%, rgba(20, 39, 35, .92)); }
.featured-number { position: absolute; top: 23px; right: 25px; color: rgba(255, 250, 241, .7); font: 500 16px/1 Georgia, serif; }
.featured-copy { position: absolute; right: 0; bottom: 0; left: 0; padding: 34px 36px 38px; color: #fffaf1; }
.place-flags { display: flex; flex-wrap: wrap; gap: 6px; margin-bottom: 19px; }
.place-flags span { padding: 5px 8px; border: 1px solid rgba(255, 250, 241, .38); font-size: 8px; letter-spacing: 1px; backdrop-filter: blur(7px); }
.featured-copy > p { margin: 0 0 9px; color: #dbb08b; font: 700 9px/1 Georgia, serif; letter-spacing: 2px; }
.featured-copy h3 { margin: 0; font-family: 'Noto Serif SC', serif; font-size: clamp(31px, 3.4vw, 48px); font-weight: 500; letter-spacing: -2px; line-height: 1.2; }
.featured-copy > strong { display: -webkit-box; margin-top: 15px; overflow: hidden; color: rgba(255, 250, 241, .78); font-size: 11px; font-weight: 400; line-height: 1.9; -webkit-box-orient: vertical; -webkit-line-clamp: 3; }
.featured-actions { display: flex; gap: 8px; margin-top: 24px; }
.featured-actions button { min-height: 40px; padding: 0 16px; border: 1px solid rgba(255, 250, 241, .6); background: rgba(255, 255, 255, .05); color: #fffaf1; cursor: pointer; font-family: inherit; font-size: 10px; }
.featured-actions button:last-child { border-color: #d59468; background: #c66d4e; }
.featured-actions b { margin-left: 14px; }

.place-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; }
.place-card { display: grid; grid-template-rows: 1fr 36px; min-height: 293px; overflow: hidden; border-top: 3px solid #315b5a; background: rgba(255, 252, 246, .9); box-shadow: 0 12px 30px rgba(62, 60, 50, .07); }
.place-card-main { display: grid; grid-template-rows: 135px 1fr; min-width: 0; padding: 0; border: 0; background: transparent; color: inherit; cursor: pointer; text-align: left; }
.place-card-visual { position: relative; overflow: hidden; }
.place-card-visual::after { position: absolute; inset: 0; content: ''; background: linear-gradient(180deg, transparent, rgba(31, 51, 45, .24)); }
.place-card-visual img { width: 100%; height: 100%; object-fit: cover; transition: transform .45s ease; }
.place-card:hover .place-card-visual img { transform: scale(1.04); }
.place-card-visual > span { position: absolute; z-index: 1; top: 13px; left: 14px; color: #fffaf1; font: 700 10px/1 Georgia, serif; text-shadow: 0 1px 8px rgba(0,0,0,.4); }
.place-card-visual mark { position: absolute; z-index: 1; right: 10px; bottom: 10px; padding: 5px 7px; background: rgba(247, 241, 229, .9); color: #315b5a; font-size: 8px; }
.place-card-copy { min-width: 0; padding: 15px 16px 13px; }
.place-card-copy > small { color: #a6533f; font-size: 7px; letter-spacing: .8px; }
.place-card-copy h4 { margin: 7px 0 6px; overflow: hidden; color: #253d36; font-family: 'Noto Serif SC', serif; font-size: 16px; font-weight: 600; text-overflow: ellipsis; white-space: nowrap; }
.place-card-copy > p { display: -webkit-box; height: 33px; margin: 0; overflow: hidden; color: #727a73; font-size: 9px; line-height: 1.7; -webkit-box-orient: vertical; -webkit-line-clamp: 2; }
.place-card-copy > div { display: flex; justify-content: space-between; gap: 8px; margin-top: 9px; color: #7e817a; font-size: 7px; }
.place-card-copy > div b { color: #315b5a; }
.inspiration-state { display: grid; min-height: 500px; place-content: center; justify-items: center; gap: 9px; background: rgba(250, 246, 237, .76); color: #315b5a; text-align: center; backdrop-filter: blur(12px); }
.inspiration-state i { width: 31px; height: 31px; border: 2px solid rgba(49, 91, 90, .17); border-top-color: #315b5a; border-radius: 50%; animation: inspiration-spin .8s linear infinite; }
.inspiration-state > span { display: grid; width: 32px; height: 32px; place-items: center; border: 1px solid #a6533f; border-radius: 50%; color: #a6533f; }
.inspiration-state b { margin-top: 5px; font-family: 'Noto Serif SC', serif; font-size: 20px; }
.inspiration-state p { margin: 0; color: #768078; font-size: 10px; }
.inspiration-state button { margin-top: 10px; padding: 8px 15px; border: 1px solid #315b5a; background: transparent; color: #315b5a; cursor: pointer; }

.place-backdrop { position: fixed; z-index: 1000; inset: 0; display: grid; justify-items: end; background: rgba(20, 31, 28, .48); backdrop-filter: blur(5px); }
.place-detail { display: grid; grid-template-columns: minmax(280px, .72fr) minmax(430px, 1fr); width: min(930px, 94vw); height: 100%; overflow: auto; background: #f7f1e5; box-shadow: -25px 0 70px rgba(15, 28, 24, .22); }
.detail-close { position: fixed; z-index: 2; top: 20px; right: 22px; display: grid; width: 35px; height: 35px; place-items: center; border: 1px solid rgba(255,255,255,.5); border-radius: 50%; background: rgba(32, 54, 47, .72); color: #fffaf1; cursor: pointer; font-size: 20px; backdrop-filter: blur(6px); }
.detail-visual { position: sticky; top: 0; height: 100vh; overflow: hidden; }
.detail-visual::after { position: absolute; inset: 0; content: ''; background: linear-gradient(180deg, transparent 50%, rgba(28, 49, 43, .65)); }
.detail-visual img { width: 100%; height: 100%; object-fit: cover; }
.detail-visual span { position: absolute; z-index: 1; bottom: 30px; left: 28px; color: #fffaf1; font: 700 10px/1 Georgia, serif; letter-spacing: 2px; }
.detail-copy { align-self: center; padding: 70px 55px 55px; }
.detail-eyebrow { margin: 0; color: #a6533f; font: 700 8px/1 Georgia, serif; letter-spacing: 2px; }
.detail-copy h2 { margin: 13px 0 15px; color: #20362f; font-family: 'Noto Serif SC', serif; font-size: clamp(30px, 4vw, 46px); font-weight: 500; letter-spacing: -2px; line-height: 1.25; }
.detail-summary { color: #66736c; font-size: 11px; line-height: 1.9; }
.detail-tags { display: flex; flex-wrap: wrap; gap: 6px; margin: 20px 0 25px; }
.detail-tags span { padding: 5px 8px; border: 1px solid rgba(49, 91, 90, .18); color: #315b5a; font-size: 8px; }
.detail-copy dl { margin: 0; border-top: 3px solid #315b5a; }
.detail-copy dl > div { display: grid; grid-template-columns: 82px 1fr; gap: 14px; padding: 12px 0; border-bottom: 1px solid rgba(44, 36, 27, .1); }
.detail-copy dt { color: #8a8e87; font-size: 8px; }
.detail-copy dd { margin: 0; color: #344a43; font-size: 10px; line-height: 1.55; }
.detail-source { display: grid; gap: 5px; margin-top: 20px; padding: 14px; background: #e9deca; }
.detail-source span { color: #9b6551; font-size: 7px; letter-spacing: 1px; }
.detail-source b { color: #5e655f; font-size: 9px; font-weight: 500; }
.detail-actions { display: grid; grid-template-columns: 1fr 1.2fr; gap: 8px; margin-top: 22px; }
.detail-actions a, .detail-actions button { display: flex; min-height: 42px; align-items: center; justify-content: center; border: 1px solid #315b5a; background: transparent; color: #315b5a; cursor: pointer; font-family: inherit; font-size: 9px; text-decoration: none; }
.detail-actions button { background: #315b5a; color: #fffaf1; }
.detail-actions span { margin-left: 10px; }

.place-drawer-enter-active, .place-drawer-leave-active { transition: opacity .35s ease; }
.place-drawer-enter-active .place-detail, .place-drawer-leave-active .place-detail { transition: transform .42s cubic-bezier(.2,.8,.2,1); }
.place-drawer-enter-from, .place-drawer-leave-to { opacity: 0; }
.place-drawer-enter-from .place-detail, .place-drawer-leave-to .place-detail { transform: translateX(100%); }
@keyframes inspiration-spin { to { transform: rotate(360deg); } }

@media (max-width: 1050px) {
  .city-rail { grid-template-columns: 1fr 150px; }
  .inspiration-layout { grid-template-columns: 1fr; }
  .featured-place { position: relative; top: 0; min-height: 520px; }
}

@media (max-width: 720px) {
  .city-rail { grid-template-columns: 1fr; }
  .city-rail nav { overflow-x: auto; grid-template-columns: repeat(5, 88px); }
  .city-rail nav button { min-height: 64px; }
  .catalog-proof { display: none; }
  .place-grid { grid-template-columns: 1fr; }
  .featured-place { min-height: 480px; }
  .featured-copy { padding: 26px 22px 28px; }
  .featured-copy h3 { font-size: 31px; letter-spacing: -1px; }
  .featured-actions { display: grid; }
  .place-detail { grid-template-columns: 1fr; width: 100%; }
  .detail-visual { position: relative; height: 270px; }
  .detail-copy { padding: 37px 23px 45px; }
  .detail-actions { grid-template-columns: 1fr; }
}

/* Editorial travel index redesign. */
.inspiration-browser {
  display: grid;
  gap: 12px;
}

.city-rail {
  display: flex;
  min-height: 40px;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  overflow: visible;
  border: 0;
  background: transparent;
  box-shadow: none;
  backdrop-filter: none;
}

.city-rail nav {
  display: flex;
  align-items: center;
  gap: 7px;
}

.city-rail nav button {
  display: block;
  min-width: 74px;
  padding: 9px 17px;
  border: 1px solid rgba(44, 36, 27, .14);
  border-radius: 999px;
  background: rgba(255, 252, 245, .52);
  color: #56615c;
  font-family: 'Noto Serif SC', serif;
  transition: color .22s ease, border-color .22s ease, background .22s ease;
}

.city-rail nav button:hover {
  border-color: rgba(49, 91, 90, .42);
  background: rgba(255, 252, 245, .88);
}

.city-rail nav button.active {
  border-color: #315b5a;
  background: #315b5a;
  color: #fffaf1;
}

.city-rail nav b {
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 1px;
}

.catalog-proof {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0;
  background: transparent;
  color: #737970;
  white-space: nowrap;
}

.catalog-proof > span {
  color: inherit;
  font: 400 9px/1.2 'Noto Serif SC', serif;
  letter-spacing: .5px;
}

.catalog-proof > span b {
  color: #315b5a;
  font: 600 14px/1 Georgia, serif;
}

.catalog-proof i {
  width: 3px;
  height: 3px;
  border-radius: 50%;
  background: #b56c53;
}

.inspiration-layout {
  display: grid;
  grid-template-columns: 1fr;
  gap: 18px;
}

.featured-place {
  position: relative;
  top: auto;
  display: block;
  height: clamp(290px, 20vw, 340px);
  min-height: 0;
  overflow: hidden;
  border: 1px solid rgba(44, 36, 27, .12);
  border-radius: 2px;
  background: #20362f;
  box-shadow: 0 18px 50px rgba(47, 55, 48, .1);
}

.featured-visual {
  position: absolute;
  inset: 0;
  height: 100%;
  min-height: 0;
  overflow: hidden;
  padding: 0;
  border: 0;
  background: #20362f;
}

.featured-visual::after {
  position: absolute;
  inset: 0;
  content: '';
  background:
    linear-gradient(90deg, rgba(19, 39, 33, .9) 0%, rgba(19, 39, 33, .66) 37%, rgba(19, 39, 33, .1) 72%),
    linear-gradient(0deg, rgba(18, 37, 31, .65), transparent 64%);
}

.featured-visual img {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform .8s cubic-bezier(.2, .7, .2, 1);
}

.featured-place:hover .featured-visual img {
  transform: scale(1.025);
}

.featured-number {
  position: absolute;
  z-index: 3;
  top: 22px;
  right: 24px;
  left: auto;
  color: #fffaf1;
  font: 600 12px/1 Georgia, serif;
  letter-spacing: 1px;
  text-shadow: 0 2px 10px rgba(0, 0, 0, .3);
}

.featured-city {
  position: absolute;
  z-index: 3;
  right: 24px;
  bottom: 22px;
  color: #fffaf1;
  font-size: 10px;
  letter-spacing: 1.5px;
}

.featured-copy {
  position: absolute;
  z-index: 2;
  inset: 0 auto 0 0;
  display: flex;
  width: min(610px, 68%);
  padding: 25px 34px 24px;
  color: #fffaf1;
  flex-direction: column;
  justify-content: flex-end;
}

.featured-index {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 11px;
  color: #e0a283;
  font: 700 8px/1 Georgia, serif;
  letter-spacing: 1.7px;
}

.featured-index i {
  width: 34px;
  height: 1px;
  background: rgba(255, 250, 241, .34);
}

.featured-index b {
  color: rgba(255, 250, 241, .68);
  font: 500 8px/1 'Noto Serif SC', serif;
  letter-spacing: .5px;
}

.place-flags {
  gap: 6px;
  margin: 0 0 8px;
}

.place-flags span {
  padding: 4px 8px;
  border: 1px solid rgba(255, 250, 241, .28);
  color: rgba(255, 250, 241, .86);
  font-size: 8px;
  letter-spacing: .5px;
}

.featured-copy h3 {
  margin: 0;
  color: #fffaf1;
  font-family: 'Noto Serif SC', serif;
  font-size: clamp(23px, 2vw, 28px);
  font-weight: 600;
  letter-spacing: -1px;
  line-height: 1.25;
}

.featured-copy > .featured-note {
  display: -webkit-box;
  margin: 8px 0 0;
  overflow: hidden;
  color: rgba(255, 250, 241, .76);
  font: 400 10px/1.7 'Noto Serif SC', serif;
  letter-spacing: .2px;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.featured-actions {
  display: block;
  margin-top: 14px;
}

.featured-actions button {
  width: 150px;
  min-height: 34px;
  padding: 0 13px;
  border: 1px solid rgba(255, 250, 241, .55);
  background: rgba(255, 250, 241, .08);
  color: #fffaf1;
  font-size: 9px;
}

.featured-actions button:last-child {
  border-color: rgba(255, 250, 241, .55);
  background: rgba(255, 250, 241, .08);
  color: #fffaf1;
}

.more-places {
  display: grid;
  gap: 10px;
}

.more-places > header {
  display: flex;
  align-items: end;
  justify-content: space-between;
  padding-bottom: 8px;
  border-bottom: 1px solid rgba(44, 36, 27, .14);
}

.more-places > header span {
  color: #a6533f;
  font: 700 8px/1 Georgia, serif;
  letter-spacing: 1.7px;
}

.more-places > header h3 {
  margin: 4px 0 0;
  color: #263c35;
  font-family: 'Noto Serif SC', serif;
  font-size: 18px;
  font-weight: 600;
  letter-spacing: 1px;
}

.more-places > header > b {
  color: rgba(49, 91, 90, .3);
  font: 500 24px/1 Georgia, serif;
}

.place-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
}

.place-card {
  position: relative;
  display: block;
  min-height: 260px;
  overflow: hidden;
  border: 1px solid rgba(44, 36, 27, .11);
  border-top: 0;
  border-radius: 2px;
  background: rgba(255, 252, 246, .91);
  box-shadow: 0 12px 28px rgba(62, 60, 50, .06);
  animation: place-card-arrive .55s both;
}

.place-card:nth-child(2) { animation-delay: .07s; }
.place-card:nth-child(3) { animation-delay: .14s; }
.place-card:nth-child(4) { animation-delay: .21s; }
.place-card:nth-child(5) { animation-delay: .28s; }
.place-card:nth-child(6) { animation-delay: .35s; }

.place-card-main {
  display: grid;
  width: 100%;
  height: 100%;
  grid-template-rows: clamp(185px, 13vw, 220px) 1fr;
}

.place-card-visual {
  border-top: 3px solid #315b5a;
}

.place-card-visual > span {
  top: 14px;
  right: 14px;
  left: auto;
}

.place-card-visual mark {
  right: 12px;
  bottom: 11px;
  padding: 5px 8px;
  background: rgba(250, 246, 237, .94);
  color: #315b5a;
}

.place-card-copy {
  padding: 12px 15px 10px;
}

.place-card-copy > small {
  color: #a6533f;
  font-size: 8px;
  letter-spacing: .8px;
}

.place-card-copy h4 {
  margin: 6px 0;
  color: #253d36;
  font-size: 15px;
}

.place-card-copy > p {
  height: 17px;
  color: #727a73;
  font-size: 9px;
  line-height: 1.7;
  -webkit-line-clamp: 1;
}

.place-card-copy > div {
  margin-top: 7px;
  color: #8a8e87;
  font-size: 8px;
}

.plan-bookmark {
  position: absolute;
  z-index: 4;
  top: 0;
  left: 15px;
  display: flex;
  width: 38px;
  height: 41px;
  align-items: center;
  gap: 7px;
  overflow: hidden;
  padding: 0 11px;
  border: 0;
  border-radius: 0 0 10px 10px;
  background: #ad5f49;
  color: #fffaf1;
  cursor: pointer;
  font-family: 'Noto Serif SC', serif;
  box-shadow: 0 7px 18px rgba(79, 47, 38, .2);
  transition: width .28s cubic-bezier(.2, .8, .2, 1), background .2s ease, box-shadow .2s ease;
}

.plan-bookmark svg {
  width: 16px;
  min-width: 16px;
  fill: none;
  stroke: currentColor;
  stroke-linejoin: round;
  stroke-width: 1.6;
}

.plan-bookmark span {
  max-width: 0;
  overflow: hidden;
  opacity: 0;
  font-size: 10px;
  font-weight: 600;
  letter-spacing: .5px;
  white-space: nowrap;
  transition: max-width .28s ease, opacity .18s ease .05s;
}

.plan-bookmark:hover,
.plan-bookmark:focus-visible {
  width: 105px;
  background: #954c39;
  box-shadow: 0 9px 22px rgba(79, 47, 38, .28);
}

.plan-bookmark:hover span,
.plan-bookmark:focus-visible span {
  max-width: 58px;
  opacity: 1;
}

.featured-bookmark {
  left: 22px;
}

.inspiration-state {
  min-height: 420px;
  border: 1px solid rgba(44, 36, 27, .1);
  background: rgba(250, 246, 237, .7);
}

@keyframes place-card-arrive {
  from { opacity: 0; transform: translateY(12px); }
  to { opacity: 1; transform: translateY(0); }
}

@media (max-width: 1050px) {
  .city-rail { display: grid; }
  .city-rail nav { overflow-x: auto; }
  .catalog-proof { justify-self: start; }
  .featured-place { grid-template-columns: 1.05fr .95fr; }
  .featured-copy { padding: 28px; }
  .place-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .place-card-main { grid-template-rows: 190px 1fr; }
}

@media (max-width: 720px) {
  .city-rail { gap: 12px; }
  .city-rail nav { display: flex; grid-template-columns: none; width: 100%; padding-bottom: 4px; }
  .city-rail nav button { min-width: 68px; min-height: auto; padding: 8px 14px; }
  .catalog-proof { display: none; }
  .featured-place { height: 390px; }
  .featured-visual { height: 100%; min-height: 0; }
  .featured-copy { width: 100%; }
  .featured-copy { padding: 25px 21px 27px; }
  .featured-copy h3 { font-size: 26px; }
  .place-grid { grid-template-columns: 1fr; }
  .place-card-main { grid-template-rows: 220px 1fr; }
  .more-places > header h3 { font-size: 19px; }
}

/* Discovery modes and video entry points. */
.city-rail {
  gap: 18px;
}

.city-options {
  flex: 0 1 auto;
}

.more-city-select {
  position: relative;
  display: flex;
  min-width: 92px;
  min-height: 36px;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 0 14px;
  border: 1px solid rgba(44, 36, 27, .14);
  border-radius: 999px;
  background: rgba(255, 252, 245, .52);
  color: #56615c;
  cursor: pointer;
  font-family: 'Noto Serif SC', serif;
  font-size: 11px;
  white-space: nowrap;
  transition: color .22s ease, border-color .22s ease, background .22s ease;
}

.more-city-select:hover,
.more-city-select:focus-within {
  border-color: rgba(49, 91, 90, .42);
  background: rgba(255, 252, 245, .88);
}

.more-city-select.active {
  border-color: #315b5a;
  background: #315b5a;
  color: #fffaf1;
}

.more-city-select select {
  position: absolute;
  inset: 0;
  width: 100%;
  opacity: 0;
  cursor: pointer;
}

.more-city-select i {
  width: 0;
  height: 0;
  border-top: 4px solid currentColor;
  border-right: 4px solid transparent;
  border-left: 4px solid transparent;
}

.discovery-tabs {
  display: flex;
  align-items: center;
  gap: 3px;
  margin-left: auto;
  padding: 3px;
  border: 1px solid rgba(49, 91, 90, .13);
  border-radius: 999px;
  background: rgba(255, 252, 245, .48);
}

.discovery-tabs button {
  display: flex;
  min-height: 31px;
  align-items: center;
  gap: 7px;
  padding: 0 13px;
  border: 0;
  border-radius: 999px;
  background: transparent;
  color: #68736e;
  cursor: pointer;
  font-family: 'Noto Serif SC', serif;
  font-size: 9px;
  white-space: nowrap;
  transition: background .2s ease, color .2s ease;
}

.discovery-tabs button small {
  color: #b1674f;
  font: 600 7px/1 Georgia, serif;
}

.discovery-tabs button.active {
  background: #315b5a;
  color: #fffaf1;
}

.discovery-tabs button.active small {
  color: #e7ad89;
}

.featured-play {
  position: absolute;
  z-index: 4;
  top: 50%;
  right: 11%;
  display: grid;
  width: 82px;
  height: 82px;
  place-content: center;
  justify-items: center;
  gap: 8px;
  border: 1px solid rgba(255, 250, 241, .58);
  border-radius: 50%;
  background: rgba(22, 46, 39, .28);
  color: #fffaf1;
  cursor: pointer;
  transform: translateY(-50%);
  backdrop-filter: blur(8px);
  transition: background .25s ease, transform .25s ease;
}

.featured-play:hover {
  background: rgba(177, 91, 67, .78);
  transform: translateY(-50%) scale(1.05);
}

.featured-play i,
.featured-actions i,
.card-video i {
  width: 0;
  height: 0;
  border-top: 6px solid transparent;
  border-bottom: 6px solid transparent;
  border-left: 10px solid currentColor;
}

.featured-play span {
  font-size: 8px;
  letter-spacing: .5px;
}

.featured-actions {
  display: flex;
  gap: 7px;
}

.featured-actions button {
  display: flex;
  width: auto;
  min-width: 138px;
  align-items: center;
  justify-content: center;
  gap: 8px;
}

.featured-actions i {
  border-top-width: 4px;
  border-bottom-width: 4px;
  border-left-width: 7px;
}

.card-video {
  position: absolute;
  z-index: 4;
  top: 145px;
  left: 13px;
  display: flex;
  min-height: 31px;
  align-items: center;
  gap: 7px;
  padding: 0 10px;
  border: 1px solid rgba(255,255,255,.55);
  background: rgba(27, 53, 46, .7);
  color: #fffaf1;
  cursor: pointer;
  font-family: inherit;
  font-size: 8px;
  backdrop-filter: blur(7px);
}

.card-video i {
  border-top-width: 4px;
  border-bottom-width: 4px;
  border-left-width: 7px;
}

.detail-actions {
  grid-template-columns: 1fr 1fr 1.2fr;
}

@media (max-width: 1220px) {
  .catalog-proof { display: none; }
}

@media (max-width: 1050px) {
  .city-rail { display: flex; overflow-x: auto; padding-bottom: 3px; }
  .city-options { overflow: visible !important; }
  .discovery-tabs { flex: 0 0 auto; }
  .featured-play { right: 7%; }
}

@media (max-width: 720px) {
  .city-rail { display: grid; overflow: visible; }
  .city-options { overflow-x: auto !important; }
  .more-city-select { min-width: 82px; min-height: 33px; padding: 0 12px; }
  .discovery-tabs { width: 100%; margin-left: 0; }
  .discovery-tabs button { flex: 1; justify-content: center; }
  .featured-play { top: 24px; right: 22px; width: 62px; height: 62px; transform: none; }
  .featured-play:hover { transform: scale(1.04); }
  .featured-play span { display: none; }
  .featured-actions { display: grid; grid-template-columns: 1fr 1fr; }
  .featured-actions button { min-width: 0; }
  .card-video { top: 174px; }
  .detail-actions { grid-template-columns: 1fr; }
}

@media (prefers-reduced-motion: reduce) {
  .featured-play,
  .featured-visual img,
  .place-card-visual img { transition: none; }
}
</style>
