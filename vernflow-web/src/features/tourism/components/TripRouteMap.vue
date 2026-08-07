<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'

const props = defineProps({
  day: { type: Object, required: true },
  city: { type: String, default: '杭州' },
})

const mapContainer = ref(null)
const status = ref('idle')
const errorMessage = ref('')
const routeDistance = ref('')
const routeDuration = ref('')

const apiKey = (import.meta.env.VITE_AMAP_JS_KEY ?? '').trim()
const securityCode = (import.meta.env.VITE_AMAP_SECURITY_CODE ?? '').trim()
const configured = Boolean(apiKey && securityCode)
const locatedItems = computed(() => (props.day?.items ?? []).filter((item) => (
  Number.isFinite(Number(item.longitude)) && Number.isFinite(Number(item.latitude))
)))
const missingLocationCount = computed(() => (props.day?.items?.length ?? 0) - locatedItems.value.length)

let map = null
let driving = null
let renderSequence = 0

function destroyMap() {
  driving?.clear?.()
  driving = null
  map?.destroy?.()
  map = null
}

function markerContent(index) {
  return `<span style="display:grid;width:30px;height:30px;place-items:center;border:3px solid #f5efe3;border-radius:50%;background:#276d62;color:#fffaf1;font:700 13px/1 Georgia,serif;box-shadow:0 7px 18px rgba(38,72,64,.28)">${index + 1}</span>`
}

function formatDistance(meters) {
  if (!Number.isFinite(meters)) return ''
  return meters >= 1000 ? `${(meters / 1000).toFixed(1)} 公里` : `${Math.round(meters)} 米`
}

function formatDuration(seconds) {
  if (!Number.isFinite(seconds)) return ''
  const minutes = Math.max(1, Math.round(seconds / 60))
  if (minutes < 60) return `${minutes} 分钟`
  return `${Math.floor(minutes / 60)} 小时 ${minutes % 60} 分钟`
}

async function renderRoute() {
  const sequence = ++renderSequence
  destroyMap()
  routeDistance.value = ''
  routeDuration.value = ''
  errorMessage.value = ''

  if (!configured) {
    status.value = 'configuration-required'
    return
  }
  if (locatedItems.value.length < 2) {
    status.value = 'insufficient-points'
    return
  }

  status.value = 'loading'
  await nextTick()

  try {
    window._AMapSecurityConfig = { securityJsCode: securityCode }
    const { default: AMapLoader } = await import('@amap/amap-jsapi-loader')
    const AMap = await AMapLoader.load({
      key: apiKey,
      version: '2.0',
      plugins: ['AMap.Driving', 'AMap.Scale'],
    })
    if (sequence !== renderSequence) return
    const points = locatedItems.value.map((item) => new AMap.LngLat(
      Number(item.longitude),
      Number(item.latitude),
    ))

    map = new AMap.Map(mapContainer.value, {
      viewMode: '2D',
      zoom: 12,
      center: points[0],
      mapStyle: 'amap://styles/whitesmoke',
    })
    map.addControl(new AMap.Scale())
    map.add(points.map((position, index) => new AMap.Marker({
      position,
      anchor: 'center',
      content: markerContent(index),
      title: locatedItems.value[index].title,
      zIndex: 120 + index,
    })))

    driving = new AMap.Driving({
      map,
      hideMarkers: true,
      showTraffic: false,
      autoFitView: true,
      outlineColor: '#f5efe3',
    })

    const result = await new Promise((resolve, reject) => {
      driving.search(
        points[0],
        points.at(-1),
        { waypoints: points.slice(1, -1) },
        (routeStatus, routeResult) => {
          if (routeStatus === 'complete' && routeResult?.routes?.length) {
            resolve(routeResult.routes[0])
            return
          }
          reject(new Error(routeResult?.info || '地图服务暂未返回可用路线'))
        },
      )
    })
    if (sequence !== renderSequence) return

    routeDistance.value = formatDistance(Number(result.distance))
    routeDuration.value = formatDuration(Number(result.time))
    status.value = 'ready'
  } catch (error) {
    if (sequence !== renderSequence) return
    errorMessage.value = error?.message || '地图加载失败，请检查高德地图配置。'
    status.value = 'error'
  }
}

onMounted(renderRoute)
watch(() => props.day?.day, renderRoute)
onBeforeUnmount(() => {
  renderSequence += 1
  destroyMap()
})
</script>

<template>
  <section class="trip-route-card" aria-label="当日地图路线">
    <header class="trip-route-head">
      <div>
        <p>ROUTE MAP / DAY {{ day.day }}</p>
        <h3>把一天的路，放到地图上</h3>
      </div>
      <span v-if="status === 'ready'">
        约 {{ routeDistance }}<i></i>驾车 {{ routeDuration }}
      </span>
      <span v-else>{{ locatedItems.length }} 个已定位节点</span>
    </header>

    <div v-if="configured && locatedItems.length >= 2" class="trip-map-shell">
      <div ref="mapContainer" class="trip-map-canvas"></div>
      <div v-if="status === 'loading'" class="trip-map-state">
        <b>正在计算真实道路路线</b>
        <small>高德地图正在连接当天的行程节点</small>
      </div>
      <div v-else-if="status === 'error'" class="trip-map-state error">
        <b>路线暂时没有加载出来</b>
        <small>{{ errorMessage }}</small>
      </div>
    </div>

    <div v-else class="trip-map-placeholder">
      <span>MAP</span>
      <div v-if="!configured">
        <b>配置高德地图后显示真实道路路线</b>
        <p>在项目环境变量中填写 <code>VITE_AMAP_JS_KEY</code> 和 <code>VITE_AMAP_SECURITY_CODE</code>，重新启动前端即可启用。</p>
      </div>
      <div v-else>
        <b>可定位节点不足</b>
        <p>至少需要两个带经纬度的行程节点才能规划路线。</p>
      </div>
    </div>

    <ol class="trip-route-stops">
      <li v-for="(item, index) in day.items" :key="`${day.day}-${item.resourceId}-${item.time}`" :class="{ missing: item.longitude == null || item.latitude == null }">
        <span>{{ index + 1 }}</span>
        <div><small>{{ item.time }}</small><b>{{ item.title }}</b></div>
        <em>{{ item.longitude == null || item.latitude == null ? '待定位' : item.address || city }}</em>
      </li>
    </ol>
    <p v-if="missingLocationCount" class="trip-route-warning">
      {{ missingLocationCount }} 个节点尚未维护坐标，本次路线未包含这些节点。
    </p>
  </section>
</template>

<style scoped>
.trip-route-card { margin-top: 20px; border: 1px solid rgba(32, 54, 48, .13); background: #fbf8f1; }
.trip-route-head { display: flex; align-items: end; justify-content: space-between; gap: 20px; padding: 24px 27px 21px; border-bottom: 1px solid rgba(32, 54, 48, .1); }
.trip-route-head p { margin: 0 0 7px; color: #b65c49; font: 700 11px/1 Georgia, serif; letter-spacing: 2px; }
.trip-route-head h3 { margin: 0; color: #20362f; font-family: Georgia, 'Noto Serif SC', serif; font-size: clamp(22px, 3vw, 34px); font-weight: 500; }
.trip-route-head > span { display: flex; align-items: center; gap: 9px; color: #276d62; font-size: 12px; font-weight: 700; white-space: nowrap; }
.trip-route-head i { width: 3px; height: 3px; border-radius: 50%; background: #b65c49; }
.trip-map-shell { position: relative; min-height: 430px; overflow: hidden; background: #ece9e1; }
.trip-map-canvas { width: 100%; min-height: 430px; }
.trip-map-state { position: absolute; inset: 0; display: grid; place-content: center; justify-items: center; gap: 8px; background: rgba(247, 243, 234, .88); color: #276d62; backdrop-filter: blur(5px); }
.trip-map-state b { font-family: Georgia, 'Noto Serif SC', serif; font-size: 20px; }
.trip-map-state small { color: #758078; }
.trip-map-state.error { color: #a8493a; }
.trip-map-placeholder { display: grid; grid-template-columns: 100px minmax(0, 1fr); align-items: center; gap: 23px; min-height: 250px; padding: 35px; overflow: hidden; background: radial-gradient(circle at 15% 15%, rgba(58, 112, 97, .15), transparent 34%), repeating-linear-gradient(120deg, transparent 0 38px, rgba(39, 109, 98, .045) 39px 40px), #eeece5; }
.trip-map-placeholder > span { display: grid; width: 92px; height: 92px; place-items: center; border: 1px solid rgba(39, 109, 98, .25); border-radius: 50%; color: #276d62; font: 700 13px/1 Georgia, serif; letter-spacing: 3px; box-shadow: inset 0 0 0 10px rgba(255, 255, 255, .32); }
.trip-map-placeholder b { color: #20362f; font-family: Georgia, 'Noto Serif SC', serif; font-size: 22px; }
.trip-map-placeholder p { max-width: 620px; margin: 10px 0 0; color: #66736c; font-size: 12px; line-height: 1.8; }
.trip-map-placeholder code { padding: 2px 5px; background: rgba(39, 109, 98, .08); color: #276d62; font-size: 11px; }
.trip-route-stops { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); margin: 0; padding: 0; border-top: 1px solid rgba(32, 54, 48, .08); list-style: none; }
.trip-route-stops li { display: grid; grid-template-columns: 30px 1fr; gap: 10px; min-width: 0; padding: 17px 16px; border-right: 1px solid rgba(32, 54, 48, .08); }
.trip-route-stops li:last-child { border-right: 0; }
.trip-route-stops li > span { display: grid; width: 26px; height: 26px; place-items: center; border-radius: 50%; background: #276d62; color: #fffaf1; font: 700 11px/1 Georgia, serif; }
.trip-route-stops li > div { display: grid; min-width: 0; gap: 2px; }
.trip-route-stops small { color: #b65c49; font-size: 9px; }
.trip-route-stops b { overflow: hidden; color: #2d4039; font-size: 11px; text-overflow: ellipsis; white-space: nowrap; }
.trip-route-stops em { grid-column: 2; overflow: hidden; color: #8a908a; font-size: 8px; font-style: normal; text-overflow: ellipsis; white-space: nowrap; }
.trip-route-stops li.missing { opacity: .55; }
.trip-route-stops li.missing > span { background: #938c82; }
.trip-route-warning { margin: 0; padding: 10px 17px; border-top: 1px solid rgba(182, 92, 73, .12); background: rgba(182, 92, 73, .05); color: #9c5547; font-size: 9px; }
@media (max-width: 760px) {
  .trip-route-head { align-items: start; padding: 20px; }
  .trip-route-head > span { display: none; }
  .trip-map-shell, .trip-map-canvas { min-height: 350px; }
  .trip-map-placeholder { grid-template-columns: 1fr; padding: 28px 20px; }
  .trip-map-placeholder > span { width: 72px; height: 72px; }
  .trip-route-stops { grid-template-columns: 1fr 1fr; }
  .trip-route-stops li:nth-child(2) { border-right: 0; }
  .trip-route-stops li:nth-child(n + 3) { border-top: 1px solid rgba(32, 54, 48, .08); }
}
</style>
