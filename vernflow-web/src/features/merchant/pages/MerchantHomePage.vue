<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useUserAuth } from '@/composables/useUserAuth'
import {
  createMerchantResource,
  listMerchantResources,
  updateMerchantResource,
  updateMerchantResourceStatus,
} from '@/lib/merchantApi'
import { loadAmap, searchAmapPois } from '@/lib/amapPoi'

const router = useRouter()
const { currentUser, hydrateUserProfile, signOutUser } = useUserAuth()
const activeSection = ref('overview')
const resourceFilter = ref('全部')
const resourceModalOpen = ref(false)
const resourceEditorOpen = ref(false)
const contentGenerating = ref(false)
const toast = ref('')
const resourceLoading = ref(true)
const resourceSaving = ref(false)
const resourceEditing = ref(false)
const resourceError = ref('')
const updatingResourceId = ref(null)
const poiSearching = ref(false)
const poiResults = ref([])
const poiSearchError = ref('')

const sectionTitles = {
  overview: '经营总览',
  resources: '文旅资源管理',
  products: '产品与套餐',
  content: '智能内容工作室',
  insight: '游客洞察',
}

const todayLabel = new Intl.DateTimeFormat('zh-CN', { month: 'long', day: 'numeric' }).format(new Date())

const resources = ref([])

const filteredResources = computed(() => resourceFilter.value === '全部'
  ? resources.value
  : resources.value.filter((resource) => resource.status === resourceFilter.value))
const publishedCount = computed(() => resources.value.filter((resource) => resource.status === '已上线').length)
const recommendableCount = computed(() => resources.value.filter((resource) => (
  resource.statusCode === 'PUBLISHED' && resource.availabilityStatus !== 'SOLD_OUT'
)).length)

const tasks = [
  { title: '补充雨天接待方案', detail: '运河夜游讲解服务', level: '今日处理' },
  { title: '核对暑期开放时间', detail: '3 项资源将在 7 天内过期', level: '信息校验' },
  { title: '完善亲子服务标签', detail: '预计提升游客匹配准确率', level: '内容优化' },
]

const products = [
  { name: '亲子非遗慢游 · 两日', price: 299, conversion: '18.6%', status: '热卖', description: '手作体验、运河文化与西湖慢行组合' },
  { name: '宋韵夜游 · 半日', price: 168, conversion: '12.4%', status: '推广中', description: '清河坊街区、杭帮菜与夜间讲解服务' },
  { name: '龙井茶事 · 一日', price: 239, conversion: '9.8%', status: '待优化', description: '茶园采制、茶文化讲解与轻徒步' },
]

const trends = [
  { label: '非遗手作', value: 92, change: '+26%' },
  { label: '亲子研学', value: 81, change: '+18%' },
  { label: '雨天室内', value: 68, change: '+15%' },
  { label: '宋韵夜游', value: 55, change: '+9%' },
]

const contentForm = reactive({ resource: '', channel: '小红书', tone: '温暖故事感' })
const contentResult = ref('带孩子认识一座城，不妨先从亲手完成一件小作品开始。沿着运河走到拱宸桥畔，在老师的带领下触摸杭州传统手艺。九十分钟，不赶行程，只把一段城市记忆留在手心。')
const newResource = reactive({
  name: '',
  category: 'EXPERIENCE',
  district: '拱墅区',
  placeQuery: '',
  placeName: '',
  amapPoiId: '',
  address: '',
  longitude: null,
  latitude: null,
  summary: '',
  tags: '非遗体验、需预约',
  openHours: '09:00-17:00',
  priceMin: 0,
  priceMax: 0,
  reservationRequired: true,
  dailyCapacity: 30,
  availableCapacity: 18,
})

const editingResource = reactive({
  id: null,
  name: '',
  category: 'EXPERIENCE',
  summary: '',
  tags: '',
  openHours: '',
  priceMin: 0,
  priceMax: 0,
  reservationRequired: false,
  dailyCapacity: null,
  availableCapacity: null,
})

const categoryOptions = [
  { value: 'EXPERIENCE', label: '非遗体验' },
  { value: 'CULTURE', label: '文化场馆' },
  { value: 'FOOD', label: '本地餐饮' },
  { value: 'LEISURE', label: '城市休闲' },
  { value: 'SCENIC', label: '自然景观' },
  { value: 'LODGING', label: '住宿' },
]

function showToast(message) {
  toast.value = message
  window.setTimeout(() => { if (toast.value === message) toast.value = '' }, 2200)
}

function toViewResource(resource) {
  return {
    id: resource.id,
    name: resource.name,
    type: resource.categoryLabel,
    category: resource.category,
    area: resource.district,
    status: resource.statusLabel,
    statusCode: resource.status,
    score: resource.qualityScore,
    tags: resource.tags ?? [],
    placeName: resource.placeName,
    locationStatus: resource.locationStatus,
    amapPoiId: resource.amapPoiId,
    address: resource.address,
    summary: resource.summary,
    openHours: resource.openHours,
    priceMin: Number(resource.priceMin ?? 0),
    priceMax: Number(resource.priceMax ?? 0),
    reservationRequired: resource.reservationRequired,
    dailyCapacity: resource.dailyCapacity,
    availableCapacity: resource.availableCapacity,
    availabilityStatus: resource.availabilityStatus,
    availabilityLabel: resource.availabilityLabel,
  }
}

function operatingPayload(form) {
  const reservationRequired = Boolean(form.reservationRequired)
  return {
    openHours: form.openHours?.trim() || null,
    priceMin: Number(form.priceMin ?? 0),
    priceMax: Number(form.priceMax ?? 0),
    reservationRequired,
    dailyCapacity: reservationRequired ? Number(form.dailyCapacity) : null,
    availableCapacity: reservationRequired ? Number(form.availableCapacity) : null,
  }
}

function validateOperatingForm(form) {
  if (Number(form.priceMax) < Number(form.priceMin)) {
    showToast('最高价格不能低于最低价格')
    return false
  }
  if (form.reservationRequired
      && (!Number.isFinite(Number(form.dailyCapacity))
        || Number(form.dailyCapacity) <= 0
        || !Number.isFinite(Number(form.availableCapacity))
        || Number(form.availableCapacity) < 0
        || Number(form.availableCapacity) > Number(form.dailyCapacity))) {
    showToast('请检查每日容量和当前余量')
    return false
  }
  return true
}

function openNewResourceModal() {
  resourceModalOpen.value = true
  poiResults.value = []
  poiSearchError.value = ''
}

function openResourceEditor(resource) {
  Object.assign(editingResource, {
    id: resource.id,
    name: resource.name,
    category: resource.category,
    summary: resource.summary,
    tags: resource.tags.join('、'),
    openHours: resource.openHours ?? '',
    priceMin: resource.priceMin,
    priceMax: resource.priceMax,
    reservationRequired: resource.reservationRequired,
    dailyCapacity: resource.dailyCapacity ?? 30,
    availableCapacity: resource.availableCapacity ?? 18,
  })
  resourceEditorOpen.value = true
}

async function searchResourcePoi() {
  if (!newResource.placeQuery.trim()) {
    showToast('请输入景点或商户名称后再搜索')
    return
  }
  poiSearching.value = true
  poiSearchError.value = ''
  poiResults.value = []
  newResource.amapPoiId = ''
  try {
    const AMap = await loadAmap()
    poiResults.value = await searchAmapPois(AMap, {
      keyword: newResource.placeQuery,
      city: '杭州',
      pageSize: 6,
    })
    if (!poiResults.value.length) {
      poiSearchError.value = '没有找到对应地点，请尝试使用高德地图中的完整名称。'
    }
  } catch (error) {
    poiSearchError.value = error.message || '高德地点搜索暂不可用'
  } finally {
    poiSearching.value = false
  }
}

function selectResourcePoi(poi) {
  newResource.placeQuery = poi.name
  newResource.placeName = poi.name
  newResource.amapPoiId = poi.id
  newResource.address = poi.address
  newResource.longitude = poi.longitude
  newResource.latitude = poi.latitude
  if (poi.district) newResource.district = poi.district
  poiResults.value = []
  poiSearchError.value = ''
}

async function loadResources() {
  resourceLoading.value = true
  resourceError.value = ''
  try {
    resources.value = (await listMerchantResources()).map(toViewResource)
    if (!resources.value.some((resource) => resource.name === contentForm.resource)) {
      contentForm.resource = resources.value[0]?.name ?? ''
    }
  } catch (error) {
    resourceError.value = error.message || '资源数据加载失败'
  } finally {
    resourceLoading.value = false
  }
}

async function generateContent() {
  contentGenerating.value = true
  await new Promise((resolve) => window.setTimeout(resolve, 720))
  contentResult.value = `${contentForm.resource}，不是匆匆打卡的一站，而是一段可以慢下来参与的杭州故事。我们为${contentForm.channel}准备了这份${contentForm.tone}内容：从在地文化、体验过程到预约提示，把游客真正关心的信息一次讲清。`
  contentGenerating.value = false
  showToast('AI 文案已生成，可继续修改')
}

async function addResource() {
  if (!newResource.name.trim()) {
    showToast('请先填写资源名称')
    return
  }
  if (!newResource.amapPoiId) {
    showToast('请先从高德搜索结果中选择真实地点')
    return
  }
  if (!validateOperatingForm(newResource)) return
  resourceSaving.value = true
  try {
    const created = await createMerchantResource({
      city: '杭州',
      name: newResource.name.trim(),
      category: newResource.category,
      district: newResource.district,
      placeName: newResource.placeName,
      amapPoiId: newResource.amapPoiId,
      address: newResource.address,
      longitude: newResource.longitude,
      latitude: newResource.latitude,
      summary: newResource.summary.trim(),
      tags: newResource.tags.split(/[、,，]/).map((tag) => tag.trim()).filter(Boolean),
      ...operatingPayload(newResource),
    })
    resources.value.unshift(toViewResource(created))
    newResource.name = ''
    newResource.placeQuery = ''
    newResource.placeName = ''
    newResource.amapPoiId = ''
    newResource.address = ''
    newResource.longitude = null
    newResource.latitude = null
    newResource.summary = ''
    newResource.openHours = '09:00-17:00'
    newResource.priceMin = 0
    newResource.priceMax = 0
    newResource.reservationRequired = true
    newResource.dailyCapacity = 30
    newResource.availableCapacity = 18
    poiResults.value = []
    resourceModalOpen.value = false
    activeSection.value = 'resources'
    showToast('资源已写入数据库，完善后即可上线')
  } catch (error) {
    showToast(error.message || '资源创建失败')
  } finally {
    resourceSaving.value = false
  }
}

async function saveResource() {
  if (!editingResource.name.trim() || !editingResource.summary.trim()) {
    showToast('资源名称和体验简介不能为空')
    return
  }
  if (!validateOperatingForm(editingResource)) return
  resourceEditing.value = true
  try {
    const updated = toViewResource(await updateMerchantResource(editingResource.id, {
      name: editingResource.name.trim(),
      category: editingResource.category,
      summary: editingResource.summary.trim(),
      tags: editingResource.tags.split(/[、,，]/).map((tag) => tag.trim()).filter(Boolean),
      ...operatingPayload(editingResource),
    }))
    const index = resources.value.findIndex((item) => item.id === editingResource.id)
    if (index >= 0) resources.value.splice(index, 1, updated)
    resourceEditorOpen.value = false
    showToast(updated.availabilityStatus === 'SOLD_OUT'
      ? '资源已保存；因当前约满，已退出游客推荐池'
      : '资源已保存，OpenClaw 推荐目录已同步')
  } catch (error) {
    showToast(error.message || '资源保存失败')
  } finally {
    resourceEditing.value = false
  }
}

async function toggleResourceStatus(resource) {
  const targetStatus = resource.statusCode === 'PUBLISHED' ? 'INACTIVE' : 'PUBLISHED'
  updatingResourceId.value = resource.id
  try {
    const updated = toViewResource(await updateMerchantResourceStatus(resource.id, targetStatus))
    const index = resources.value.findIndex((item) => item.id === resource.id)
    if (index >= 0) {
      resources.value.splice(index, 1, updated)
    }
    showToast(targetStatus === 'PUBLISHED' ? '资源已上线并进入游客推荐池' : '资源已下线')
  } catch (error) {
    showToast(error.message || '资源状态更新失败')
  } finally {
    updatingResourceId.value = null
  }
}

async function logout() {
  await signOutUser()
  await router.replace({ name: 'login' })
}

onMounted(async () => {
  try {
    await hydrateUserProfile()
    await loadResources()
  } catch {
    // The shared request layer redirects invalid sessions to login.
  }
})
</script>

<template>
  <main class="merchant-app">
    <aside class="merchant-sidebar">
      <RouterLink class="merchant-brand" :to="{ name: 'merchant-home' }">
        <span>V</span>
        <div><strong>山水灵</strong><small>商家工作台</small></div>
      </RouterLink>

      <nav>
        <button :class="{ active: activeSection === 'overview' }" type="button" @click="activeSection = 'overview'"><i>总</i>经营总览</button>
        <button :class="{ active: activeSection === 'resources' }" type="button" @click="activeSection = 'resources'"><i>资</i>文旅资源</button>
        <button :class="{ active: activeSection === 'products' }" type="button" @click="activeSection = 'products'"><i>品</i>产品与套餐</button>
        <button :class="{ active: activeSection === 'content' }" type="button" @click="activeSection = 'content'"><i>文</i>智能内容</button>
        <button :class="{ active: activeSection === 'insight' }" type="button" @click="activeSection = 'insight'"><i>析</i>游客洞察</button>
      </nav>

      <div class="merchant-user">
        <span>{{ currentUser?.displayName?.slice(0, 1) || '商' }}</span>
        <div><strong>{{ currentUser?.displayName || '商家演示账号' }}</strong><small>机构运营人员</small></div>
        <button type="button" title="退出登录" @click="logout">退出</button>
      </div>
    </aside>

    <section class="merchant-main">
      <header class="merchant-topbar">
        <div>
          <span>VERNTRIP MERCHANT CONSOLE</span>
          <h1>{{ sectionTitles[activeSection] }}</h1>
        </div>
        <div class="topbar-actions">
          <RouterLink :to="{ name: 'travel-home' }">查看游客端</RouterLink>
          <button type="button" @click="openNewResourceModal">+ 新增文旅资源</button>
        </div>
      </header>

      <div v-if="activeSection === 'overview'" class="dashboard-content">
        <section class="merchant-welcome">
          <div>
            <span>{{ todayLabel }} · 杭州</span>
            <h2>上午好，先把最影响游客体验的事处理好。</h2>
            <p>系统汇总了资源完整度、游客咨询和近期需要校验的信息，帮助经营人员明确今天的优先事项。</p>
          </div>
          <div class="quality-ring">
            <strong>86<small>%</small></strong>
            <span>资源健康度</span>
          </div>
        </section>

        <section class="metric-grid">
          <article><span>有效文旅资源</span><strong>{{ resources.length }}</strong><small>其中 {{ publishedCount }} 项已进入推荐池</small></article>
          <article><span>近 7 日方案引用</span><strong>1,286</strong><small class="positive">较上周 +18.6%</small></article>
          <article><span>待校验信息</span><strong>3</strong><small class="warning">开放时间与价格即将过期</small></article>
          <article><span>游客兴趣热度</span><strong>非遗</strong><small>亲子手作关注增长最快</small></article>
        </section>

        <div class="dashboard-grid">
          <section class="resource-card">
            <div class="section-title"><div><span>RESOURCE QUALITY</span><h3>重点资源质量</h3></div><button type="button" @click="activeSection = 'resources'">查看全部</button></div>
            <div class="resource-table">
              <article v-for="resource in resources.slice(0, 5)" :key="resource.id">
                <div><strong>{{ resource.name }}</strong><small>{{ resource.type }}</small></div>
                <span :class="{ pending: resource.status !== '已上线' }">{{ resource.status }}</span>
                <b>{{ resource.score }}<small>分</small></b>
              </article>
            </div>
          </section>

          <section class="task-card">
            <div class="section-title"><div><span>TODAY'S FOCUS</span><h3>今日待办</h3></div><em>{{ tasks.length }} 项</em></div>
            <article v-for="(task, index) in tasks" :key="task.title">
              <span>0{{ index + 1 }}</span>
              <div><strong>{{ task.title }}</strong><small>{{ task.detail }}</small></div>
              <em>{{ task.level }}</em>
            </article>
          </section>
        </div>

        <section class="insight-strip">
          <span>AI 经营提示</span>
          <p>近一周游客对“非遗体验 + 雨天可参与”的组合咨询明显增加，建议优先补充室内体验的年龄限制、预约方式和接待容量。</p>
          <button type="button" @click="activeSection = 'content'">生成优化建议 →</button>
        </section>
      </div>

      <section v-else-if="activeSection === 'resources'" class="module-content resources-module">
        <div class="module-intro">
          <div><span>RESOURCE LIBRARY</span><h2>让每条资源都可被 AI 准确理解</h2><p>统一维护开放状态、服务标签和来源信息，决定资源是否进入游客推荐池。</p></div>
          <div class="module-stat"><strong>{{ recommendableCount }}/{{ publishedCount }}</strong><small>可推荐 / 已上线</small></div>
        </div>
        <div class="module-toolbar">
          <div class="filter-tabs">
            <button v-for="filter in ['全部', '已上线', '待完善', '已停用']" :key="filter" :class="{ active: resourceFilter === filter }" type="button" @click="resourceFilter = filter">{{ filter }}</button>
          </div>
          <button type="button" @click="openNewResourceModal">新增资源 +</button>
        </div>
        <div v-if="resourceLoading" class="resource-feedback">正在读取商家资源库...</div>
        <div v-else-if="resourceError" class="resource-feedback error">
          <span>{{ resourceError }}</span>
          <button type="button" @click="loadResources">重新加载</button>
        </div>
        <div class="resource-management-list">
          <article v-for="resource in filteredResources" :key="resource.id">
            <div class="resource-score"><strong>{{ resource.score }}</strong><small>质量分</small></div>
            <div class="resource-copy">
              <span>{{ resource.type }} · {{ resource.area }}</span><h3>{{ resource.name }}</h3>
              <small>{{ resource.locationStatus === 'VERIFIED' ? '高德真实地点' : '待核验地点' }} · {{ resource.placeName }}</small>
              <div><b v-for="tag in resource.tags" :key="tag">{{ tag }}</b></div>
              <div class="resource-operating-meta">
                <span>{{ resource.openHours || '开放时间待补充' }}</span>
                <span>¥{{ resource.priceMin }}–{{ resource.priceMax }}</span>
                <strong :class="{ alert: resource.availabilityStatus === 'SOLD_OUT' }">
                  {{ resource.availabilityLabel }}<template v-if="resource.reservationRequired"> · 余 {{ resource.availableCapacity }}/{{ resource.dailyCapacity }}</template>
                </strong>
              </div>
            </div>
            <span :class="['resource-status', { pending: resource.status !== '已上线' }]">{{ resource.status }}</span>
            <div class="resource-actions">
              <button type="button" @click="openResourceEditor(resource)">编辑</button>
              <button type="button" :disabled="updatingResourceId === resource.id" @click="toggleResourceStatus(resource)">
                {{ updatingResourceId === resource.id ? '处理中...' : resource.statusCode === 'PUBLISHED' ? '下线' : '上线' }}
              </button>
            </div>
          </article>
        </div>
      </section>

      <section v-else-if="activeSection === 'products'" class="module-content">
        <div class="module-intro">
          <div><span>TOURISM PRODUCTS</span><h2>把分散资源组合成可售卖体验</h2><p>产品与游客画像、行程场景关联，OpenClaw 可在规划时选择合适组合。</p></div>
          <button class="primary-module-action" type="button" @click="showToast('套餐创建向导已打开')">创建新套餐</button>
        </div>
        <div class="product-grid">
          <article v-for="product in products" :key="product.name">
            <span>{{ product.status }}</span><h3>{{ product.name }}</h3><p>{{ product.description }}</p>
            <div><strong>¥{{ product.price }}</strong><small>近 7 日转化 {{ product.conversion }}</small></div>
            <button type="button" @click="showToast(`正在编辑「${product.name}」`) ">管理套餐 →</button>
          </article>
          <button class="new-product-card" type="button" @click="showToast('套餐创建向导已打开')"><b>＋</b><span>组合新的文旅体验</span></button>
        </div>
      </section>

      <section v-else-if="activeSection === 'content'" class="module-content content-module">
        <div class="module-intro"><div><span>AI CONTENT STUDIO</span><h2>从真实资源，生成可发布内容</h2><p>生成过程只引用已审核资源信息，减少商家重复写作和内容失真。</p></div><span class="openclaw-badge">OPENCLAW ENABLED</span></div>
        <div class="content-studio-grid">
          <form @submit.prevent="generateContent">
            <label><span>选择资源</span><select v-model="contentForm.resource"><option v-for="resource in resources" :key="resource.id">{{ resource.name }}</option></select></label>
            <label><span>发布渠道</span><select v-model="contentForm.channel"><option>小红书</option><option>微信公众号</option><option>短视频口播</option><option>景区详情页</option></select></label>
            <label><span>表达风格</span><select v-model="contentForm.tone"><option>温暖故事感</option><option>简洁实用</option><option>年轻有趣</option><option>文化深度</option></select></label>
            <button type="submit" :disabled="contentGenerating">{{ contentGenerating ? 'OpenClaw 正在生成…' : '生成运营文案 →' }}</button>
          </form>
          <article class="content-result"><div><span>AI DRAFT</span><button type="button" @click="showToast('文案已复制')">复制文案</button></div><h3>{{ contentForm.channel }}内容初稿</h3><p>{{ contentResult }}</p><footer><span>基于：{{ contentForm.resource }}</span><b>可编辑 · 未发布</b></footer></article>
        </div>
      </section>

      <section v-else class="module-content insight-module">
        <div class="module-intro"><div><span>VISITOR INSIGHT</span><h2>看见游客正在期待什么</h2><p>聚合规划偏好、咨询主题和资源引用趋势，为产品设计提供依据。</p></div><div class="module-stat"><strong>1,286</strong><small>近 7 日规划</small></div></div>
        <div class="insight-grid">
          <section class="trend-card"><div class="section-title"><div><span>INTEREST TREND</span><h3>兴趣增长趋势</h3></div><em>近 7 日</em></div><article v-for="trend in trends" :key="trend.label"><span>{{ trend.label }}</span><i><b :style="{ width: `${trend.value}%` }"></b></i><strong>{{ trend.change }}</strong></article></section>
          <section class="portrait-card"><span>高潜游客画像</span><h3>长三角 · 亲子家庭 · 周末两日</h3><p>更关注参与感、雨天备选与节奏舒适度，愿意为可信的在地体验支付合理溢价。</p><div><b>非遗体验</b><b>本地美食</b><b>室内友好</b><b>预算 2k–4k</b></div><button type="button" @click="activeSection = 'products'">据此设计套餐 →</button></section>
        </div>
      </section>
    </section>

    <Transition name="merchant-toast"><div v-if="toast" class="merchant-toast">{{ toast }}</div></Transition>
    <div v-if="resourceModalOpen" class="resource-modal-backdrop" @click.self="resourceModalOpen = false">
      <form class="resource-modal" @submit.prevent="addResource">
        <button type="button" @click="resourceModalOpen = false">×</button>
        <span>NEW RESOURCE</span>
        <h2>新增文旅资源</h2>
        <p>基础信息将直接写入资源库，审核上线后即可参与游客行程推荐。</p>
        <label><span>资源或体验名称</span><input v-model.trim="newResource.name" placeholder="例如：运河蓝染亲子体验" /></label>
        <label>
          <span>高德真实地点</span>
          <div class="poi-search-row">
            <input v-model.trim="newResource.placeQuery" placeholder="搜索体验实际发生的场馆或门店" @keyup.enter.prevent="searchResourcePoi" />
            <button type="button" :disabled="poiSearching" @click="searchResourcePoi">{{ poiSearching ? '搜索中' : '搜索' }}</button>
          </div>
        </label>
        <div v-if="poiResults.length" class="poi-result-list">
          <button v-for="poi in poiResults" :key="poi.id" type="button" @click="selectResourcePoi(poi)">
            <strong>{{ poi.name }}</strong><small>{{ poi.address || poi.district || '杭州' }}</small>
          </button>
        </div>
        <p v-if="poiSearchError" class="poi-search-error">{{ poiSearchError }}</p>
        <div v-if="newResource.amapPoiId" class="selected-poi">
          <span>已绑定高德 POI</span><strong>{{ newResource.placeName }}</strong>
          <small>{{ newResource.address || newResource.district }} · {{ newResource.longitude.toFixed(6) }}, {{ newResource.latitude.toFixed(6) }}</small>
          <small>POI ID · {{ newResource.amapPoiId }}</small>
        </div>
        <label><span>资源类型</span><select v-model="newResource.category"><option v-for="option in categoryOptions" :key="option.value" :value="option.value">{{ option.label }}</option></select></label>
        <label><span>所在区域</span><input v-model.trim="newResource.district" placeholder="选择地点后自动填写" /></label>
        <label><span>体验简介</span><textarea v-model.trim="newResource.summary" rows="3" placeholder="说明体验内容、适合人群和建议停留时间"></textarea></label>
        <label><span>资源标签</span><input v-model.trim="newResource.tags" placeholder="使用顿号分隔，例如：亲子友好、需预约" /></label>
        <div class="modal-grid">
          <label><span>开放时间</span><input v-model.trim="newResource.openHours" placeholder="例如：09:00-17:00" /></label>
          <label><span>价格区间（元）</span><div class="price-row"><input v-model.number="newResource.priceMin" min="0" type="number" /><i>至</i><input v-model.number="newResource.priceMax" min="0" type="number" /></div></label>
        </div>
        <label class="reservation-switch"><input v-model="newResource.reservationRequired" type="checkbox" /><span>该资源需要预约，并按当前余量参与推荐</span></label>
        <div v-if="newResource.reservationRequired" class="modal-grid">
          <label><span>每日接待容量</span><input v-model.number="newResource.dailyCapacity" min="1" type="number" /></label>
          <label><span>当前可预约余量</span><input v-model.number="newResource.availableCapacity" min="0" type="number" /></label>
        </div>
        <button class="modal-submit" type="submit" :disabled="resourceSaving">{{ resourceSaving ? '正在写入资源库...' : '创建并继续完善 →' }}</button>
      </form>
    </div>
    <div v-if="resourceEditorOpen" class="resource-modal-backdrop" @click.self="resourceEditorOpen = false">
      <form class="resource-modal resource-editor" @submit.prevent="saveResource">
        <button type="button" @click="resourceEditorOpen = false">×</button>
        <span>RESOURCE OPERATIONS</span>
        <h2>编辑经营信息</h2>
        <p>保存后会同步到游客推荐池和 OpenClaw 资源目录；余量为 0 时自动停止推荐。</p>
        <label><span>资源或体验名称</span><input v-model.trim="editingResource.name" /></label>
        <label><span>资源类型</span><select v-model="editingResource.category"><option v-for="option in categoryOptions" :key="option.value" :value="option.value">{{ option.label }}</option></select></label>
        <label><span>体验简介</span><textarea v-model.trim="editingResource.summary" rows="4"></textarea></label>
        <label><span>资源标签</span><input v-model.trim="editingResource.tags" placeholder="使用顿号分隔" /></label>
        <div class="modal-grid">
          <label><span>开放时间</span><input v-model.trim="editingResource.openHours" placeholder="例如：09:00-17:00" /></label>
          <label><span>价格区间（元）</span><div class="price-row"><input v-model.number="editingResource.priceMin" min="0" type="number" /><i>至</i><input v-model.number="editingResource.priceMax" min="0" type="number" /></div></label>
        </div>
        <label class="reservation-switch"><input v-model="editingResource.reservationRequired" type="checkbox" /><span>需要预约，并按当前余量参与游客推荐</span></label>
        <div v-if="editingResource.reservationRequired" class="capacity-panel">
          <div class="modal-grid">
            <label><span>每日接待容量</span><input v-model.number="editingResource.dailyCapacity" min="1" type="number" /></label>
            <label><span>当前可预约余量</span><input v-model.number="editingResource.availableCapacity" min="0" type="number" /></label>
          </div>
          <p :class="{ alert: Number(editingResource.availableCapacity) === 0 }">
            {{ Number(editingResource.availableCapacity) === 0 ? '保存后该资源会立即退出游客推荐池。' : '保存后余量会同步给 OpenClaw 规划目录。' }}
          </p>
        </div>
        <button class="modal-submit" type="submit" :disabled="resourceEditing">{{ resourceEditing ? '正在同步...' : '保存并同步推荐目录 →' }}</button>
      </form>
    </div>
  </main>
</template>

<style scoped>
.merchant-app {
  --merchant-ink: #26332e;
  --merchant-paper: #f1ede4;
  --merchant-green: #315b50;
  --merchant-red: #aa4738;
  display: grid;
  grid-template-columns: 236px 1fr;
  min-height: 100vh;
  background:
    linear-gradient(rgba(49, 91, 80, .025) 1px, transparent 1px),
    linear-gradient(90deg, rgba(49, 91, 80, .025) 1px, transparent 1px),
    var(--merchant-paper);
  background-size: 28px 28px;
  color: var(--merchant-ink);
  font-family: "Noto Serif SC", "STSong", serif;
}

.merchant-sidebar { position: sticky; top: 0; display: flex; flex-direction: column; height: 100vh; padding: 24px 16px 18px; border-right: 1px solid rgba(38, 51, 46, .1); background: rgba(248, 245, 237, .9); backdrop-filter: blur(12px); }
.merchant-brand { display: flex; align-items: center; gap: 11px; margin: 0 8px 42px; color: inherit; text-decoration: none; }
.merchant-brand > span { display: grid; width: 38px; height: 38px; place-items: center; border-radius: 50% 50% 46% 54%; background: var(--merchant-red); color: #fff9ef; font-size: 17px; }
.merchant-brand div { display: grid; }
.merchant-brand strong { font-size: 19px; letter-spacing: 2px; }
.merchant-brand small { color: #777b75; font-size: 9px; letter-spacing: 2px; }
.merchant-sidebar nav { display: grid; gap: 7px; }
.merchant-sidebar nav button { display: flex; align-items: center; gap: 12px; padding: 10px 12px; border: 0; border-radius: 9px; background: transparent; color: #687069; cursor: pointer; font-family: inherit; font-size: 12px; letter-spacing: .5px; text-align: left; }
.merchant-sidebar nav button i { display: grid; width: 26px; height: 26px; place-items: center; border: 1px solid rgba(38, 51, 46, .12); border-radius: 8px; font-style: normal; font-size: 10px; }
.merchant-sidebar nav button:hover, .merchant-sidebar nav button.active { background: var(--merchant-green); color: #fffaf1; }
.merchant-user { display: grid; grid-template-columns: auto 1fr auto; align-items: center; gap: 9px; margin-top: auto; padding: 12px 8px 0; border-top: 1px solid rgba(38, 51, 46, .09); }
.merchant-user > span { display: grid; width: 31px; height: 31px; place-items: center; border-radius: 50%; background: #e3d9c7; color: var(--merchant-red); }
.merchant-user div { display: grid; }
.merchant-user strong { font-size: 10px; }
.merchant-user small { color: #85877f; font-size: 8px; }
.merchant-user button { border: 0; background: transparent; color: #8b8177; cursor: pointer; font-size: 9px; }

.merchant-main { min-width: 0; }
.merchant-topbar { display: flex; align-items: center; justify-content: space-between; height: 92px; padding: 0 clamp(24px, 4vw, 56px); border-bottom: 1px solid rgba(38, 51, 46, .08); background: rgba(244, 240, 232, .72); }
.merchant-topbar span, .section-title span { color: var(--merchant-green); font-size: 8px; letter-spacing: 3px; }
.merchant-topbar h1 { margin-top: 3px; font-size: 25px; font-weight: 600; letter-spacing: 2px; }
.topbar-actions { display: flex; gap: 9px; }
.topbar-actions a, .topbar-actions button, .coming-soon button { padding: 9px 14px; border: 1px solid rgba(38, 51, 46, .16); border-radius: 8px; background: rgba(255, 255, 255, .48); color: inherit; cursor: pointer; font-family: inherit; font-size: 10px; text-decoration: none; }
.topbar-actions button { border-color: var(--merchant-green); background: var(--merchant-green); color: #fffaf1; }
.dashboard-content { display: grid; gap: 18px; padding: 24px clamp(24px, 4vw, 56px) 42px; }
.merchant-welcome { display: flex; align-items: center; justify-content: space-between; padding: 24px 28px; overflow: hidden; border-radius: 16px; background: linear-gradient(108deg, #315b50, #496e61); color: #fffaf1; box-shadow: 0 16px 38px rgba(49, 91, 80, .15); }
.merchant-welcome > div:first-child > span { font-size: 9px; letter-spacing: 2px; opacity: .68; }
.merchant-welcome h2 { margin: 7px 0; font-size: clamp(20px, 2.5vw, 30px); font-weight: 500; letter-spacing: 1px; }
.merchant-welcome p { max-width: 710px; font-size: 10px; line-height: 1.7; opacity: .7; }
.quality-ring { display: grid; width: 108px; height: 108px; place-items: center; flex-shrink: 0; border: 1px solid rgba(255, 255, 255, .25); border-radius: 50%; box-shadow: inset 0 0 0 7px rgba(255, 255, 255, .07); }
.quality-ring strong { align-self: end; font-size: 29px; font-weight: 500; }
.quality-ring strong small { font-size: 11px; }
.quality-ring span { align-self: start; font-size: 8px; opacity: .65; }
.metric-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 12px; }
.metric-grid article { display: grid; min-height: 120px; padding: 17px; border: 1px solid rgba(38, 51, 46, .09); border-radius: 13px; background: rgba(252, 250, 245, .76); }
.metric-grid article > span { color: #747b74; font-size: 10px; }
.metric-grid strong { margin: 5px 0; font-size: 27px; font-weight: 500; }
.metric-grid small { align-self: end; color: #878982; font-size: 8px; }
.metric-grid .positive { color: #4f8268; }
.metric-grid .warning { color: var(--merchant-red); }
.dashboard-grid { display: grid; grid-template-columns: minmax(0, 1.4fr) minmax(300px, .6fr); gap: 14px; }
.resource-card, .task-card { padding: 20px; border: 1px solid rgba(38, 51, 46, .09); border-radius: 14px; background: rgba(252, 250, 245, .8); }
.section-title { display: flex; align-items: center; justify-content: space-between; margin-bottom: 15px; }
.section-title h3 { margin-top: 3px; font-size: 17px; }
.section-title button { border: 0; background: transparent; color: var(--merchant-green); cursor: pointer; font-family: inherit; font-size: 9px; }
.section-title em { color: var(--merchant-red); font-size: 10px; font-style: normal; }
.resource-table article { display: grid; grid-template-columns: 1fr 80px 48px; align-items: center; gap: 12px; padding: 13px 4px; border-top: 1px solid rgba(38, 51, 46, .07); }
.resource-table article div, .task-card article div { display: grid; gap: 3px; }
.resource-table strong, .task-card strong { font-size: 11px; }
.resource-table small, .task-card small { color: #888a83; font-size: 8px; }
.resource-table article > span { justify-self: start; padding: 4px 7px; border-radius: 999px; background: #e7f0e8; color: #44705a; font-size: 8px; }
.resource-table article > span.pending { background: #f3e5db; color: #9a5542; }
.resource-table article > b { justify-self: end; color: var(--merchant-green); font-size: 18px; }
.resource-table article > b small { font-size: 8px; }
.task-card article { display: grid; grid-template-columns: 25px 1fr auto; align-items: center; gap: 9px; padding: 11px 0; border-top: 1px solid rgba(38, 51, 46, .07); }
.task-card article > span { color: #b2aa9c; font-size: 10px; }
.task-card article > em { color: var(--merchant-red); font-size: 8px; font-style: normal; }
.insight-strip { display: grid; grid-template-columns: 95px 1fr auto; align-items: center; gap: 18px; padding: 14px 18px; border-left: 3px solid var(--merchant-red); background: rgba(255, 251, 244, .66); }
.insight-strip > span { color: var(--merchant-red); font-size: 10px; letter-spacing: 1px; }
.insight-strip p { color: #646c66; font-size: 10px; line-height: 1.6; }
.insight-strip button { border: 0; background: transparent; color: var(--merchant-green); cursor: pointer; font-family: inherit; font-size: 9px; }

.module-content { display: grid; gap: 18px; padding: 28px clamp(24px, 4vw, 56px) 48px; }
.module-intro { display: flex; align-items: center; justify-content: space-between; gap: 28px; padding: 24px 28px; border: 1px solid rgba(38, 51, 46, .09); border-radius: 16px; background: rgba(252, 250, 245, .78); }
.module-intro > div:first-child > span, .content-result > div > span, .portrait-card > span { color: var(--merchant-red); font-size: 9px; letter-spacing: 3px; }
.module-intro h2 { margin: 5px 0 7px; font-size: clamp(22px, 2.5vw, 32px); font-weight: 500; letter-spacing: 1px; }
.module-intro p { max-width: 720px; color: #717871; font-size: 11px; line-height: 1.75; }
.module-stat { display: grid; min-width: 112px; padding: 16px; border-left: 1px solid rgba(38, 51, 46, .12); text-align: right; }
.module-stat strong { color: var(--merchant-green); font-size: 28px; font-weight: 500; }
.module-stat small { color: #858980; font-size: 9px; }
.primary-module-action, .module-toolbar > button { padding: 10px 15px; border: 0; border-radius: 8px; background: var(--merchant-green); color: #fffaf1; cursor: pointer; font-family: inherit; font-size: 10px; }

.module-toolbar { display: flex; align-items: center; justify-content: space-between; }
.filter-tabs { display: flex; gap: 7px; }
.filter-tabs button { padding: 7px 12px; border: 1px solid rgba(38, 51, 46, .12); border-radius: 999px; background: rgba(255, 255, 255, .4); color: #697069; cursor: pointer; font-family: inherit; font-size: 9px; }
.filter-tabs button.active { border-color: var(--merchant-green); background: var(--merchant-green); color: #fffaf1; }
.resource-management-list { display: grid; gap: 9px; }
.resource-management-list article { display: grid; grid-template-columns: 64px minmax(0, 1fr) 72px 86px; align-items: center; gap: 15px; padding: 15px 18px; border: 1px solid rgba(38, 51, 46, .08); border-radius: 12px; background: rgba(252, 250, 245, .76); transition: transform .2s, box-shadow .2s; }
.resource-management-list article:hover { transform: translateY(-2px); box-shadow: 0 12px 26px rgba(38, 51, 46, .07); }
.resource-score { display: grid; width: 52px; height: 52px; place-content: center; border: 1px solid rgba(49, 91, 80, .2); border-radius: 50%; text-align: center; }
.resource-score strong { color: var(--merchant-green); font-size: 18px; }
.resource-score small { color: #8b8d86; font-size: 7px; }
.resource-copy > span { color: #8b8177; font-size: 8px; letter-spacing: 1px; }
.resource-copy h3 { margin: 3px 0 7px; font-size: 14px; }
.resource-copy > small { display: block; margin: -3px 0 7px; color: #747d76; font-size: 8px; }
.resource-copy div { display: flex; flex-wrap: wrap; gap: 5px; }
.resource-copy b { padding: 3px 6px; border-radius: 999px; background: #ede7db; color: #70766f; font-size: 7px; font-weight: 500; }
.resource-copy .resource-operating-meta { align-items: center; gap: 8px; margin-top: 9px; color: #747b74; font-size: 8px; }
.resource-operating-meta span { padding-right: 8px; border-right: 1px solid rgba(38, 51, 46, .12); }
.resource-operating-meta strong { color: var(--merchant-green); font-size: 8px; font-weight: 600; }
.resource-operating-meta strong.alert { color: var(--merchant-red); }
.resource-status { justify-self: start; padding: 5px 8px; border-radius: 999px; background: #e6efe7; color: #44705a; font-size: 8px; }
.resource-status.pending { background: #f3e5db; color: #9a5542; }
.resource-actions { display: grid; grid-template-columns: 1fr 1fr; gap: 5px; }
.resource-actions button { padding: 6px 4px; border: 1px solid rgba(49, 91, 80, .18); border-radius: 6px; background: rgba(255, 255, 255, .38); color: var(--merchant-green); cursor: pointer; font-family: inherit; font-size: 8px; }
.resource-actions button:last-child { border-color: transparent; background: var(--merchant-green); color: #fffaf1; }
.resource-actions button:disabled { cursor: wait; opacity: .55; }
.resource-feedback { display: flex; align-items: center; justify-content: space-between; min-height: 68px; padding: 16px 18px; border: 1px dashed rgba(49, 91, 80, .2); border-radius: 12px; color: #70766f; font-size: 10px; }
.resource-feedback.error { border-color: rgba(170, 71, 56, .25); color: var(--merchant-red); }
.resource-feedback button { padding: 7px 11px; border: 0; border-radius: 7px; background: var(--merchant-green); color: #fffaf1; cursor: pointer; font-family: inherit; font-size: 9px; }

.product-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 14px; }
.product-grid article, .new-product-card { min-height: 240px; padding: 21px; border: 1px solid rgba(38, 51, 46, .09); border-radius: 15px; background: rgba(252, 250, 245, .82); }
.product-grid article { display: flex; flex-direction: column; }
.product-grid article > span { align-self: flex-start; padding: 4px 7px; border-radius: 999px; background: #e8eee5; color: var(--merchant-green); font-size: 8px; }
.product-grid h3 { margin: 18px 0 8px; font-size: 18px; }
.product-grid p { color: #747a73; font-size: 10px; line-height: 1.7; }
.product-grid article > div { display: flex; align-items: end; justify-content: space-between; margin-top: auto; padding-top: 22px; }
.product-grid article > div strong { color: var(--merchant-red); font-size: 25px; font-weight: 500; }
.product-grid article > div small { color: #81857f; font-size: 8px; }
.product-grid article > button { margin-top: 14px; padding-top: 10px; border: 0; border-top: 1px solid rgba(38, 51, 46, .08); background: transparent; color: var(--merchant-green); cursor: pointer; font-family: inherit; font-size: 9px; text-align: left; }
.new-product-card { display: none; place-content: center; justify-items: center; gap: 9px; border-style: dashed; color: #777d76; cursor: pointer; font-family: inherit; }
.new-product-card b { color: var(--merchant-green); font-size: 27px; font-weight: 300; }

.openclaw-badge { padding: 7px 10px; border: 1px solid rgba(49, 91, 80, .2); border-radius: 999px; color: var(--merchant-green); font-size: 8px; letter-spacing: 1px; }
.content-studio-grid { display: grid; grid-template-columns: minmax(280px, .62fr) minmax(0, 1.38fr); gap: 14px; }
.content-studio-grid form, .content-result { padding: 22px; border: 1px solid rgba(38, 51, 46, .09); border-radius: 14px; background: rgba(252, 250, 245, .82); }
.content-studio-grid form { display: grid; align-content: start; gap: 15px; }
.content-studio-grid label { display: grid; gap: 6px; }
.content-studio-grid label > span { color: #70766f; font-size: 9px; }
.content-studio-grid select, .resource-modal input, .resource-modal select, .resource-modal textarea { width: 100%; padding: 10px; border: 1px solid rgba(38, 51, 46, .13); border-radius: 8px; outline: 0; background: rgba(255, 255, 255, .6); color: var(--merchant-ink); font-family: inherit; font-size: 10px; }
.resource-modal textarea { resize: vertical; line-height: 1.6; }
.content-studio-grid form > button { margin-top: 6px; padding: 11px; border: 0; border-radius: 8px; background: var(--merchant-green); color: #fffaf1; cursor: pointer; font-family: inherit; font-size: 10px; }
.content-studio-grid form > button:disabled { cursor: wait; opacity: .6; }
.content-result { position: relative; min-height: 340px; overflow: hidden; background: linear-gradient(145deg, rgba(255, 252, 246, .94), rgba(235, 226, 210, .75)); }
.content-result::after { position: absolute; right: -55px; bottom: -70px; width: 180px; height: 180px; content: ''; border: 1px solid rgba(170, 71, 56, .1); border-radius: 50%; box-shadow: inset 0 0 0 24px rgba(170, 71, 56, .025); }
.content-result > div { display: flex; align-items: center; justify-content: space-between; }
.content-result > div button { border: 0; background: transparent; color: var(--merchant-green); cursor: pointer; font-family: inherit; font-size: 9px; }
.content-result h3 { margin: 34px 0 13px; font-size: 23px; font-weight: 500; }
.content-result > p { max-width: 650px; color: #4d5953; font-size: 13px; line-height: 2; }
.content-result footer { position: absolute; right: 22px; bottom: 20px; left: 22px; display: flex; justify-content: space-between; padding-top: 12px; border-top: 1px solid rgba(38, 51, 46, .08); color: #858a83; font-size: 8px; }
.content-result footer b { color: var(--merchant-red); font-weight: 500; }

.insight-grid { display: grid; grid-template-columns: minmax(0, 1.2fr) minmax(310px, .8fr); gap: 14px; }
.trend-card, .portrait-card { padding: 22px; border: 1px solid rgba(38, 51, 46, .09); border-radius: 14px; background: rgba(252, 250, 245, .82); }
.trend-card > article { display: grid; grid-template-columns: 78px 1fr 42px; align-items: center; gap: 12px; padding: 13px 0; border-top: 1px solid rgba(38, 51, 46, .07); font-size: 10px; }
.trend-card article i { height: 8px; overflow: hidden; border-radius: 999px; background: #e5dfd4; }
.trend-card article i b { display: block; height: 100%; border-radius: inherit; background: linear-gradient(90deg, var(--merchant-green), #759080); }
.trend-card article strong { color: var(--merchant-red); font-size: 9px; }
.portrait-card { display: flex; flex-direction: column; background: linear-gradient(145deg, #315b50, #496e61); color: #fffaf1; }
.portrait-card > span { color: #e8b49e; }
.portrait-card h3 { margin: 18px 0 10px; font-size: 21px; font-weight: 500; line-height: 1.45; }
.portrait-card p { color: rgba(255, 250, 241, .7); font-size: 10px; line-height: 1.8; }
.portrait-card div { display: flex; flex-wrap: wrap; gap: 6px; margin-top: 18px; }
.portrait-card div b { padding: 5px 7px; border: 1px solid rgba(255, 255, 255, .18); border-radius: 999px; font-size: 8px; font-weight: 500; }
.portrait-card button { align-self: flex-start; margin-top: auto; padding: 18px 0 4px; border: 0; border-bottom: 1px solid currentColor; background: transparent; color: #fffaf1; cursor: pointer; font-family: inherit; font-size: 9px; }

.merchant-toast { position: fixed; right: 28px; bottom: 28px; z-index: 80; padding: 12px 18px; border-radius: 10px; background: #26332e; color: #fffaf1; font-size: 10px; box-shadow: 0 15px 35px rgba(38, 51, 46, .22); }
.merchant-toast-enter-active, .merchant-toast-leave-active { transition: opacity .25s, transform .25s; }
.merchant-toast-enter-from, .merchant-toast-leave-to { opacity: 0; transform: translateY(8px); }
.resource-modal-backdrop { position: fixed; inset: 0; z-index: 70; display: grid; place-items: center; padding: 20px; background: rgba(28, 35, 32, .42); backdrop-filter: blur(6px); }
.resource-modal { position: relative; display: grid; gap: 14px; width: min(520px, 100%); max-height: calc(100vh - 40px); overflow-y: auto; padding: 29px; border-radius: 16px; background: #f7f2e8; box-shadow: 0 24px 70px rgba(28, 35, 32, .25); }
.resource-modal > button:first-child { position: absolute; top: 13px; right: 15px; border: 0; background: transparent; color: #7d817b; cursor: pointer; font-size: 18px; }
.resource-modal > span { color: var(--merchant-red); font-size: 8px; letter-spacing: 3px; }
.resource-modal h2 { margin-top: -7px; font-size: 24px; }
.resource-modal > p { color: #737a73; font-size: 10px; line-height: 1.7; }
.resource-modal label { display: grid; gap: 6px; }
.resource-modal label > span { color: #6e756e; font-size: 9px; }
.poi-search-row { display: grid; grid-template-columns: 1fr auto; gap: 7px; }
.poi-search-row button { padding: 0 16px; border: 0; border-radius: 8px; background: var(--merchant-green); color: #fffaf1; cursor: pointer; font-family: inherit; font-size: 10px; }
.poi-search-row button:disabled { cursor: wait; opacity: .6; }
.poi-result-list { display: grid; gap: 5px; max-height: 190px; overflow-y: auto; padding: 5px; border: 1px solid rgba(49, 91, 80, .12); border-radius: 9px; background: rgba(255, 255, 255, .44); }
.poi-result-list button { display: grid; gap: 3px; padding: 9px 10px; border: 0; border-radius: 7px; background: transparent; color: var(--merchant-ink); cursor: pointer; font-family: inherit; text-align: left; }
.poi-result-list button:hover { background: rgba(49, 91, 80, .08); }
.poi-result-list strong { font-size: 10px; }
.poi-result-list small { color: #7b817a; font-size: 8px; }
.poi-search-error { margin: -6px 0 0 !important; color: var(--merchant-red) !important; font-size: 9px !important; }
.selected-poi { display: grid; gap: 3px; padding: 11px 12px; border-left: 3px solid var(--merchant-green); border-radius: 6px; background: rgba(49, 91, 80, .07); }
.selected-poi span { color: var(--merchant-green); font-size: 8px; letter-spacing: 1px; }
.selected-poi strong { font-size: 11px; }
.selected-poi small { color: #727a73; font-size: 8px; }
.resource-modal .modal-submit { padding: 11px; border: 0; border-radius: 8px; background: var(--merchant-green); color: #fffaf1; cursor: pointer; font-family: inherit; font-size: 10px; }
.resource-modal .modal-submit:disabled { cursor: wait; opacity: .6; }
.modal-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; }
.price-row { display: grid; grid-template-columns: 1fr auto 1fr; align-items: center; gap: 6px; }
.price-row i { color: #8a8e88; font-size: 8px; font-style: normal; }
.resource-modal .reservation-switch { display: flex; align-items: center; gap: 9px; padding: 11px 12px; border: 1px solid rgba(49, 91, 80, .12); border-radius: 8px; background: rgba(49, 91, 80, .05); cursor: pointer; }
.resource-modal .reservation-switch input { width: 14px; height: 14px; margin: 0; accent-color: var(--merchant-green); }
.resource-modal .reservation-switch span { color: var(--merchant-green); }
.capacity-panel { display: grid; gap: 8px; padding: 12px; border-left: 3px solid var(--merchant-green); border-radius: 7px; background: rgba(49, 91, 80, .05); }
.capacity-panel > p { margin: 0; color: #66716a; font-size: 8px; }
.capacity-panel > p.alert { color: var(--merchant-red); font-weight: 600; }

@media (max-width: 1050px) {
  .merchant-app { grid-template-columns: 78px 1fr; }
  .merchant-sidebar { padding-inline: 10px; }
  .merchant-brand div, .merchant-sidebar nav button:not(.active) { font-size: 0; }
  .merchant-sidebar nav button { justify-content: center; }
  .merchant-sidebar nav button.active { font-size: 0; }
  .merchant-user div { display: none; }
  .merchant-user { grid-template-columns: 1fr; justify-items: center; }
  .merchant-user button { width: 100%; padding-top: 5px; font-size: 8px; }
  .metric-grid { grid-template-columns: 1fr 1fr; }
  .dashboard-grid { grid-template-columns: 1fr; }
  .product-grid { grid-template-columns: 1fr 1fr; }
  .content-studio-grid, .insight-grid { grid-template-columns: 1fr; }
}

@media (max-width: 700px) {
  .merchant-app { display: block; }
  .merchant-sidebar { position: relative; width: 100%; height: auto; padding: 12px 14px; }
  .merchant-brand { margin: 0 0 12px; }
  .merchant-sidebar nav { display: flex; overflow-x: auto; }
  .merchant-sidebar nav button, .merchant-sidebar nav button.active, .merchant-sidebar nav button:not(.active) { flex-shrink: 0; font-size: 10px; }
  .merchant-sidebar nav button i { display: none; }
  .merchant-user { display: none; }
  .merchant-topbar { height: auto; padding: 18px; }
  .topbar-actions a { display: none; }
  .dashboard-content { padding: 14px; }
  .merchant-welcome { padding: 20px; }
  .quality-ring { display: none; }
  .metric-grid { grid-template-columns: 1fr 1fr; }
  .insight-strip { grid-template-columns: 1fr; }
  .module-content { padding: 14px; }
  .module-intro { align-items: flex-start; padding: 20px; }
  .module-stat, .openclaw-badge { display: none; }
  .module-toolbar { align-items: flex-start; gap: 10px; }
  .filter-tabs { flex-wrap: wrap; }
  .resource-management-list article { grid-template-columns: 48px 1fr auto; }
  .resource-status { display: none; }
  .resource-actions { grid-column: 2 / -1; grid-template-columns: 1fr 1fr; }
  .resource-score { width: 42px; height: 42px; }
  .modal-grid { grid-template-columns: 1fr; }
  .product-grid { grid-template-columns: 1fr; }
}
</style>
