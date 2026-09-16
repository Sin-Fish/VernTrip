<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useUserAuth } from '@/composables/useUserAuth'
import {
  auditMerchantResource,
  createMerchantProduct,
  createMerchantResource,
  generateMerchantContent,
  getMerchantDashboard,
  listMerchantProducts,
  listMerchantResources,
  updateMerchantProduct,
  updateMerchantProductStatus,
  updateMerchantResource,
  updateMerchantResourceStatus,
} from '@/lib/merchantApi'
import { loadAmap, searchAmapPois } from '@/lib/amapPoi'

const router = useRouter()
const { currentUser, hydrateUserProfile, signOutUser } = useUserAuth()
const activeSection = ref('overview')
const resourceFilter = ref('全部')
const resourceSearch = ref('')
const resourcePage = ref(1)
const productResourceSearch = ref('')
const resourceModalOpen = ref(false)
const resourceEditorOpen = ref(false)
const productModalOpen = ref(false)
const contentGenerating = ref(false)
const toast = ref('')
const resourceLoading = ref(true)
const dashboardLoading = ref(true)
const productLoading = ref(true)
const resourceSaving = ref(false)
const resourceEditing = ref(false)
const productSaving = ref(false)
const resourceError = ref('')
const dashboardError = ref('')
const productError = ref('')
const updatingResourceId = ref(null)
const updatingProductId = ref(null)
const poiSearching = ref(false)
const poiResults = ref([])
const poiSearchError = ref('')
const resourceAuditing = ref(false)
const resourceAudit = ref(null)
const resourceAuditSignature = ref('')
const publishAfterCreate = ref(true)

const sectionTitles = {
  overview: '经营总览',
  resources: '文旅资源管理',
  products: '产品与套餐',
  content: '智能内容工作室',
  insight: '游客洞察',
}

const todayLabel = new Intl.DateTimeFormat('zh-CN', { month: 'long', day: 'numeric' }).format(new Date())

const resources = ref([])
const products = ref([])
const dashboard = ref(null)

const filteredResources = computed(() => resources.value.filter((resource) => {
  const matchesStatus = resourceFilter.value === '全部' || resource.status === resourceFilter.value
  const keyword = resourceSearch.value.trim().toLocaleLowerCase('zh-CN')
  const matchesSearch = !keyword || [resource.name, resource.type, resource.area, ...resource.tags]
    .some((value) => String(value ?? '').toLocaleLowerCase('zh-CN').includes(keyword))
  return matchesStatus && matchesSearch
}))
const resourcesPerPage = 12
const resourcePageCount = computed(() => Math.max(1, Math.ceil(filteredResources.value.length / resourcesPerPage)))
const visibleResources = computed(() => {
  const start = (resourcePage.value - 1) * resourcesPerPage
  return filteredResources.value.slice(start, start + resourcesPerPage)
})
const resourceResultRange = computed(() => {
  if (!filteredResources.value.length) return '0 项'
  const start = (resourcePage.value - 1) * resourcesPerPage + 1
  const end = Math.min(resourcePage.value * resourcesPerPage, filteredResources.value.length)
  return `${start}–${end} / ${filteredResources.value.length} 项`
})
const publishedCount = computed(() => resources.value.filter((resource) => resource.status === '已上线').length)
const recommendableCount = computed(() => resources.value.filter((resource) => (
  resource.statusCode === 'PUBLISHED' && resource.availabilityStatus !== 'SOLD_OUT'
)).length)
const qualityScore = computed(() => dashboard.value?.averageQualityScore ?? (
  resources.value.length
    ? Math.round(resources.value.reduce((sum, item) => sum + item.score, 0) / resources.value.length)
    : 0
))
const tasks = computed(() => dashboard.value?.tasks ?? [])
const trends = computed(() => dashboard.value?.interestTrends ?? [])
const audienceSegments = computed(() => dashboard.value?.audienceSegments ?? [])
const resourceCitations = computed(() => dashboard.value?.resourceCitations ?? [])
const topInterest = computed(() => trends.value[0]?.label ?? '暂无数据')
const topAudience = computed(() => audienceSegments.value[0]?.label ?? '暂无数据')
const maxTrendCount = computed(() => Math.max(1, ...trends.value.map((item) => Number(item.count) || 0)))
const maxCitationCount = computed(() => Math.max(1, ...resourceCitations.value.map((item) => Number(item.count) || 0)))
const topInterestShare = computed(() => Number(trends.value[0]?.share ?? 0))
const audiencePalette = ['#d1785e', '#d7bd8c', '#86a092', '#a9b7ad', '#efe4ce', '#7b9388']
const audienceLegend = computed(() => audienceSegments.value.slice(0, 6).map((segment, index) => ({
  ...segment,
  color: audiencePalette[index % audiencePalette.length],
})))
const audienceDonutStyle = computed(() => {
  if (!audienceLegend.value.length) return { background: 'rgba(255, 255, 255, .08)' }
  let cursor = 0
  const stops = audienceLegend.value.map((segment) => {
    const start = cursor
    cursor = Math.min(100, cursor + Number(segment.share || 0))
    return `${segment.color} ${start}% ${cursor}%`
  })
  if (cursor < 100) stops.push(`rgba(255, 255, 255, .1) ${cursor}% 100%`)
  return { background: `conic-gradient(${stops.join(', ')})` }
})
const contentSources = computed(() => (contentForm.sourceType === 'PRODUCT'
  ? products.value.map((product) => ({ id: product.id, label: product.name }))
  : resources.value.map((resource) => ({ id: resource.id, label: resource.name }))))

const contentForm = reactive({
  sourceType: 'PRODUCT',
  sourceId: null,
  channel: '小红书',
  tone: '温暖故事感',
  goal: '吸引目标游客了解并咨询',
})
const contentResult = ref('')
const contentFacts = ref([])
const contentEngine = ref('')
const contentGeneratedAt = ref('')
const newResource = reactive({
  city: '杭州',
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
  tags: '',
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

const productForm = reactive({
  id: null,
  name: '',
  targetAudience: '亲子家庭',
  durationDays: 2,
  price: 699,
  description: '',
  highlights: '',
  resourceIds: [],
})

const productResourceOptions = computed(() => resources.value.filter((resource) => (
  resource.statusCode === 'PUBLISHED' || productForm.resourceIds.includes(resource.id)
)))
const visibleProductResourceOptions = computed(() => {
  const keyword = productResourceSearch.value.trim().toLocaleLowerCase('zh-CN')
  const matches = productResourceOptions.value.filter((resource) => !keyword || [
    resource.name,
    resource.type,
    resource.area,
    ...resource.tags,
  ].some((value) => String(value ?? '').toLocaleLowerCase('zh-CN').includes(keyword)))
  const selected = productResourceOptions.value.filter((resource) => productForm.resourceIds.includes(resource.id))
  const selectedIds = new Set(selected.map((resource) => resource.id))
  const unselectedMatches = matches.filter((resource) => !selectedIds.has(resource.id))
  return [...selected, ...unselectedMatches].slice(0, keyword ? 30 : 18)
})

const categoryOptions = [
  { value: 'EXPERIENCE', label: '非遗体验' },
  { value: 'CULTURE', label: '文化场馆' },
  { value: 'FOOD', label: '本地餐饮' },
  { value: 'LEISURE', label: '城市休闲' },
  { value: 'SCENIC', label: '自然景观' },
  { value: 'LODGING', label: '住宿' },
]

const selectedCategoryLabel = computed(() => (
  categoryOptions.find((option) => option.value === newResource.category)?.label ?? '文旅体验'
))
const resourceAuditStale = computed(() => Boolean(
  resourceAudit.value && resourceAuditSignature.value !== resourceDraftSignature()
))

watch(contentSources, (sources) => {
  if (!sources.some((source) => source.id === contentForm.sourceId)) {
    contentForm.sourceId = sources[0]?.id ?? null
  }
})

watch([resourceFilter, resourceSearch], () => {
  resourcePage.value = 1
})

watch(resourcePageCount, (pageCount) => {
  resourcePage.value = Math.min(resourcePage.value, pageCount)
})

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
  resourceAudit.value = null
  resourceAuditSignature.value = ''
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
      city: newResource.city.trim() || '杭州',
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
  if (!newResource.name.trim()) newResource.name = poi.name
  poiResults.value = []
  poiSearchError.value = ''
  resourceAudit.value = null
  resourceAuditSignature.value = ''
}

function resourcePayload() {
  return {
    city: newResource.city.trim(),
    name: newResource.name.trim(),
    category: newResource.category,
    district: newResource.district.trim(),
    placeName: newResource.placeName,
    amapPoiId: newResource.amapPoiId,
    address: newResource.address,
    longitude: newResource.longitude,
    latitude: newResource.latitude,
    summary: newResource.summary.trim(),
    tags: newResource.tags.split(/[、,，]/).map((tag) => tag.trim()).filter(Boolean),
    ...operatingPayload(newResource),
  }
}

function resourceDraftSignature() {
  return JSON.stringify(resourcePayload())
}

function validateNewResource() {
  if (!newResource.city.trim() || !newResource.name.trim()) {
    showToast('请先填写经营城市和资源名称')
    return false
  }
  if (!newResource.amapPoiId) {
    showToast('请先从高德搜索结果中选择真实地点')
    return false
  }
  return validateOperatingForm(newResource)
}

async function auditResourceDraft() {
  if (!validateNewResource()) return
  resourceAuditing.value = true
  resourceAudit.value = null
  try {
    const signature = resourceDraftSignature()
    resourceAudit.value = await auditMerchantResource(resourcePayload())
    resourceAuditSignature.value = signature
    showToast(resourceAudit.value.engine === 'OPENCLAW' ? '智能审核完成' : '规则预检完成')
  } catch (error) {
    showToast(error.message || '资源智能审核失败')
  } finally {
    resourceAuditing.value = false
  }
}

function applyResourceAudit() {
  if (!resourceAudit.value || resourceAuditStale.value) return
  newResource.summary = resourceAudit.value.generatedSummary
  newResource.tags = (resourceAudit.value.generatedTags ?? []).join('、')
  resourceAuditSignature.value = resourceDraftSignature()
  showToast('内容已应用')
}

function resetNewResourceForm() {
  Object.assign(newResource, {
    city: '杭州',
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
    tags: '',
    openHours: '09:00-17:00',
    priceMin: 0,
    priceMax: 0,
    reservationRequired: true,
    dailyCapacity: 30,
    availableCapacity: 18,
  })
  resourceAudit.value = null
  resourceAuditSignature.value = ''
  poiResults.value = []
}

async function loadResources() {
  resourceLoading.value = true
  resourceError.value = ''
  try {
    resources.value = (await listMerchantResources()).map(toViewResource).sort((left, right) => {
      const leftImported = left.name === left.placeName ? 1 : 0
      const rightImported = right.name === right.placeName ? 1 : 0
      return leftImported - rightImported
    })
  } catch (error) {
    resourceError.value = error.message || '资源数据加载失败'
  } finally {
    resourceLoading.value = false
  }
}

async function loadDashboard() {
  dashboardLoading.value = true
  dashboardError.value = ''
  try {
    dashboard.value = await getMerchantDashboard()
  } catch (error) {
    dashboardError.value = error.message || '经营数据加载失败'
  } finally {
    dashboardLoading.value = false
  }
}

async function loadProducts() {
  productLoading.value = true
  productError.value = ''
  try {
    products.value = await listMerchantProducts()
  } catch (error) {
    productError.value = error.message || '旅游产品加载失败'
  } finally {
    productLoading.value = false
  }
}

async function generateContent() {
  if (!contentForm.sourceId) {
    showToast('请先选择用于生成内容的资源或产品')
    return
  }
  contentGenerating.value = true
  contentResult.value = ''
  contentFacts.value = []
  contentEngine.value = ''
  contentGeneratedAt.value = ''
  try {
    const result = await generateMerchantContent({
      sourceType: contentForm.sourceType,
      sourceId: contentForm.sourceId,
      channel: contentForm.channel,
      tone: contentForm.tone,
      goal: contentForm.goal.trim() || null,
    })
    contentResult.value = result.content
    contentFacts.value = result.facts ?? []
    contentEngine.value = result.engine
    contentGeneratedAt.value = result.generatedAt
    showToast('文案已生成')
  } catch (error) {
    showToast(error.message || '营销内容生成失败')
  } finally {
    contentGenerating.value = false
  }
}

async function copyGeneratedContent() {
  if (!contentResult.value) return
  try {
    await navigator.clipboard.writeText(contentResult.value)
    showToast('文案已复制')
  } catch {
    showToast('复制失败，请手动选择文案')
  }
}

function resetProductForm() {
  Object.assign(productForm, {
    id: null,
    name: '',
    targetAudience: '亲子家庭',
    durationDays: 2,
    price: 699,
    description: '',
    highlights: '',
    resourceIds: [],
  })
}

function openNewProductModal() {
  resetProductForm()
  productResourceSearch.value = ''
  productModalOpen.value = true
}

function openProductEditor(product) {
  productResourceSearch.value = ''
  Object.assign(productForm, {
    id: product.id,
    name: product.name,
    targetAudience: product.targetAudience,
    durationDays: product.durationDays,
    price: Number(product.price),
    description: product.description,
    highlights: product.highlights.join('、'),
    resourceIds: product.resources.map((resource) => resource.id),
  })
  productModalOpen.value = true
}

function productPayload() {
  return {
    name: productForm.name.trim(),
    targetAudience: productForm.targetAudience.trim(),
    durationDays: Number(productForm.durationDays),
    price: Number(productForm.price),
    description: productForm.description.trim(),
    highlights: productForm.highlights.split(/[、,，]/).map((item) => item.trim()).filter(Boolean),
    resourceIds: productForm.resourceIds,
  }
}

async function saveProduct() {
  if (!productForm.name.trim() || !productForm.targetAudience.trim() || !productForm.description.trim()) {
    showToast('请完整填写产品名称、目标客群和产品说明')
    return
  }
  if (productForm.resourceIds.length < 2) {
    showToast('旅游产品至少需要组合两项已上线资源')
    return
  }
  productSaving.value = true
  try {
    const saved = productForm.id
      ? await updateMerchantProduct(productForm.id, productPayload())
      : await createMerchantProduct(productPayload())
    const index = products.value.findIndex((item) => item.id === saved.id)
    if (index >= 0) products.value.splice(index, 1, saved)
    else products.value.unshift(saved)
    productModalOpen.value = false
    await loadDashboard()
    showToast(productForm.id ? '旅游产品已保存' : '产品草稿已创建，可确认后发布')
  } catch (error) {
    showToast(error.message || '旅游产品保存失败')
  } finally {
    productSaving.value = false
  }
}

async function toggleProductStatus(product) {
  const targetStatus = product.status === 'PUBLISHED' ? 'INACTIVE' : 'PUBLISHED'
  updatingProductId.value = product.id
  try {
    const updated = await updateMerchantProductStatus(product.id, targetStatus)
    const index = products.value.findIndex((item) => item.id === product.id)
    if (index >= 0) products.value.splice(index, 1, updated)
    await loadDashboard()
    showToast(targetStatus === 'PUBLISHED' ? '产品已发布' : '产品已停止展示')
  } catch (error) {
    showToast(error.message || '产品状态更新失败')
  } finally {
    updatingProductId.value = null
  }
}

function handleTask(task) {
  if (!task.resourceId) return
  const resource = resources.value.find((item) => item.id === task.resourceId)
  if (resource) {
    activeSection.value = 'resources'
    openResourceEditor(resource)
  }
}

async function addResource() {
  if (!validateNewResource()) return
  if (!resourceAudit.value || resourceAuditStale.value) {
    showToast('请先完成一次最新的智能审核')
    return
  }
  resourceSaving.value = true
  try {
    applyResourceAudit()
    let saved = await createMerchantResource(resourcePayload())
    let published = false
    if (publishAfterCreate.value
        && saved.qualityScore >= 70
        && saved.availabilityStatus !== 'SOLD_OUT') {
      try {
        saved = await updateMerchantResourceStatus(saved.id, 'PUBLISHED')
        published = true
      } catch (error) {
        showToast(error.message || '资源已创建，但自动发布失败')
      }
    }
    resources.value.unshift(toViewResource(saved))
    resetNewResourceForm()
    resourceModalOpen.value = false
    activeSection.value = 'resources'
    await loadDashboard()
    showToast(published ? '资源已上线' : '资源草稿已保存')
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
    await Promise.all([loadDashboard(), loadProducts()])
    showToast(updated.availabilityStatus === 'SOLD_OUT' ? '资源已保存，当前已约满' : '资源信息已保存')
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
    await Promise.all([loadDashboard(), loadProducts()])
    showToast(targetStatus === 'PUBLISHED' ? '资源已上线' : '资源已下线')
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
    await Promise.all([loadResources(), loadDashboard(), loadProducts()])
  } catch {
    // The shared request layer redirects invalid sessions to login.
  }
})
</script>

<template>
  <main class="merchant-app">
    <aside class="merchant-sidebar">
      <RouterLink class="merchant-brand" :to="{ name: 'merchant-home' }">
        <span>山</span>
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
          <span>MERCHANT OPERATIONS / OPENCLAW</span>
          <h1>{{ sectionTitles[activeSection] }}</h1>
        </div>
        <div class="topbar-actions">
          <RouterLink :to="{ name: 'travel-home' }">查看游客端</RouterLink>
          <button type="button" @click="openNewResourceModal">+ 新增文旅资源</button>
        </div>
      </header>

      <div v-if="activeSection === 'overview'" class="dashboard-content">
        <section class="merchant-welcome">
          <div class="welcome-copy">
            <span>{{ todayLabel }} · 杭州经营站</span>
            <h2>今日经营简报</h2>
            <div class="welcome-pulse">
              <button type="button" @click="activeSection = 'resources'"><strong>{{ recommendableCount }}</strong><small>项资源可推荐</small></button>
              <button type="button" @click="activeSection = 'resources'"><strong>{{ tasks.length }}</strong><small>项信息待处理</small></button>
              <button type="button" @click="activeSection = 'products'"><strong>{{ topInterest }}</strong><small>近期游客偏好</small></button>
            </div>
          </div>
          <div class="quality-ring">
            <strong>{{ qualityScore }}<small>%</small></strong>
            <span>资源健康度</span>
          </div>
        </section>

        <div v-if="dashboardError" class="resource-feedback error">
          <span>{{ dashboardError }}</span>
          <button type="button" @click="loadDashboard">重新加载</button>
        </div>
        <section class="metric-grid">
          <article><span>文旅资源</span><strong>{{ resources.length }}</strong><small>{{ publishedCount }} 项已上线</small></article>
          <article><span>方案生成</span><strong>{{ dashboardLoading ? '—' : dashboard?.plansLast7Days ?? 0 }}</strong><small>近 7 日</small></article>
          <article><span>旅游产品</span><strong>{{ dashboardLoading ? '—' : dashboard?.publishedProductCount ?? 0 }}</strong><small>已发布</small></article>
          <article><span>兴趣主题</span><strong>{{ topInterest }}</strong><small>近 30 日</small></article>
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
            <article v-for="(task, index) in tasks" :key="`${task.type}-${task.resourceId ?? index}`" :class="{ actionable: task.resourceId }" @click="handleTask(task)">
              <span>0{{ index + 1 }}</span>
              <div><strong>{{ task.title }}</strong></div>
              <em>{{ task.level }}</em>
            </article>
          </section>
        </div>

      </div>

      <section v-else-if="activeSection === 'resources'" class="module-content resources-module">
        <div class="module-intro">
          <div><span>RESOURCE LIBRARY</span><h2>文旅资源</h2></div>
          <div class="module-stat"><strong>{{ recommendableCount }}/{{ publishedCount }}</strong><small>可推荐 / 已上线</small></div>
        </div>
        <div class="module-toolbar">
          <div class="resource-toolbar-left">
            <div class="filter-tabs">
              <button v-for="filter in ['全部', '已上线', '待完善', '已停用']" :key="filter" :class="{ active: resourceFilter === filter }" type="button" @click="resourceFilter = filter">{{ filter }}</button>
            </div>
            <input v-model.trim="resourceSearch" aria-label="搜索资源" placeholder="搜索名称、区域或标签" />
          </div>
          <button type="button" @click="openNewResourceModal">新增资源 +</button>
        </div>
        <div v-if="resourceLoading" class="resource-feedback">正在读取商家资源库...</div>
        <div v-else-if="resourceError" class="resource-feedback error">
          <span>{{ resourceError }}</span>
          <button type="button" @click="loadResources">重新加载</button>
        </div>
        <div class="resource-management-list">
          <article v-for="resource in visibleResources" :key="resource.id">
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
          <div v-if="!resourceLoading && !resourceError && !filteredResources.length" class="module-empty">
            <strong>没有符合条件的资源</strong>
          </div>
        </div>
        <div v-if="filteredResources.length" class="resource-pagination">
          <span>{{ resourceResultRange }}</span>
          <div>
            <button type="button" :disabled="resourcePage === 1" @click="resourcePage -= 1">上一页</button>
            <b>{{ resourcePage }} / {{ resourcePageCount }}</b>
            <button type="button" :disabled="resourcePage === resourcePageCount" @click="resourcePage += 1">下一页</button>
          </div>
        </div>
      </section>

      <section v-else-if="activeSection === 'products'" class="module-content">
        <div class="module-intro">
          <div><span>TOURISM PRODUCTS</span><h2>旅游产品</h2></div>
          <button class="primary-module-action" type="button" @click="openNewProductModal">创建新产品</button>
        </div>
        <div v-if="productLoading" class="resource-feedback">正在读取旅游产品...</div>
        <div v-else-if="productError" class="resource-feedback error"><span>{{ productError }}</span><button type="button" @click="loadProducts">重新加载</button></div>
        <div v-else-if="products.length" class="product-grid">
          <article v-for="product in products" :key="product.id">
            <span :class="{ inactive: product.status !== 'PUBLISHED' }">{{ product.statusLabel }}</span>
            <h3>{{ product.name }}</h3>
            <p>{{ product.description }}</p>
            <div class="product-resources"><b v-for="resource in product.resources" :key="resource.id">{{ resource.name }}</b></div>
            <div class="product-price"><strong>¥{{ Number(product.price).toLocaleString('zh-CN') }}</strong><small>{{ product.durationDays }} 天 · {{ product.targetAudience }}</small></div>
            <footer>
              <button type="button" @click="openProductEditor(product)">编辑产品</button>
              <button type="button" :disabled="updatingProductId === product.id" @click="toggleProductStatus(product)">{{ updatingProductId === product.id ? '处理中…' : product.status === 'PUBLISHED' ? '停用' : '发布' }}</button>
            </footer>
          </article>
        </div>
        <div v-else class="module-empty product-empty"><strong>还没有旅游产品</strong><button type="button" @click="openNewProductModal">创建产品草稿</button></div>
      </section>

      <section v-else-if="activeSection === 'content'" class="module-content content-module">
        <div class="module-intro"><div><span>AI CONTENT STUDIO</span><h2>运营内容生成</h2></div><span class="openclaw-badge">OPENCLAW ENABLED</span></div>
        <div class="content-studio-grid">
          <form @submit.prevent="generateContent">
            <label><span>内容依据</span><select v-model="contentForm.sourceType"><option value="RESOURCE">单项文旅资源</option><option value="PRODUCT">旅游产品</option></select></label>
            <label><span>选择{{ contentForm.sourceType === 'PRODUCT' ? '产品' : '资源' }}</span><select v-model="contentForm.sourceId"><option v-for="source in contentSources" :key="source.id" :value="source.id">{{ source.label }}</option></select></label>
            <label><span>发布渠道</span><select v-model="contentForm.channel"><option>小红书</option><option>微信公众号</option><option>短视频口播</option><option>景区详情页</option></select></label>
            <label><span>表达风格</span><select v-model="contentForm.tone"><option>温暖故事感</option><option>简洁实用</option><option>年轻有趣</option><option>文化深度</option></select></label>
            <label><span>本次运营目标</span><textarea v-model.trim="contentForm.goal" rows="3" placeholder="例如：吸引亲子家庭预约周末场次"></textarea></label>
            <button type="submit" :disabled="contentGenerating">{{ contentGenerating ? 'OpenClaw 正在生成…' : '生成运营文案 →' }}</button>
          </form>
          <article class="content-result">
            <div><span>AI DRAFT</span><button type="button" :disabled="!contentResult" @click="copyGeneratedContent">复制文案</button></div>
            <h3>{{ contentForm.channel }}内容初稿</h3>
            <p v-if="contentResult">{{ contentResult }}</p>
            <div v-else class="content-placeholder"><strong>尚未生成内容</strong></div>
            <details v-if="contentFacts.length"><summary>查看本次使用的 {{ contentFacts.length }} 条可信事实</summary><ul><li v-for="fact in contentFacts" :key="fact">{{ fact }}</li></ul></details>
            <footer><span>{{ contentEngine || 'OPENCLAW' }}<template v-if="contentGeneratedAt"> · {{ new Date(contentGeneratedAt).toLocaleString('zh-CN') }}</template></span><b>人工确认 · 未发布</b></footer>
          </article>
        </div>
      </section>

      <section v-else class="module-content insight-module">
        <div class="module-intro insight-intro"><div><span>VISITOR INSIGHT</span><h2>游客需求数据</h2></div><div class="module-stat"><strong>{{ dashboard?.requestsLast30Days ?? 0 }}</strong><small>近 30 日需求</small></div></div>

        <div class="insight-kpi-grid">
          <article><span>需求样本</span><strong>{{ dashboard?.requestsLast30Days ?? 0 }}</strong><small>近 30 日</small></article>
          <article><span>平均预算</span><strong><i>¥</i>{{ Number(dashboard?.averageBudget ?? 0).toLocaleString('zh-CN') }}</strong><small>单次行程</small></article>
          <article><span>平均天数</span><strong>{{ dashboard?.averageDays ?? 0 }}<i>天</i></strong><small>旅行时长</small></article>
          <article><span>行程生成</span><strong>{{ dashboard?.plansLast7Days ?? 0 }}</strong><small>近 7 日</small></article>
        </div>

        <div class="insight-visual-grid">
          <section class="interest-visual-card">
            <div class="section-title"><div><span>INTEREST SIGNAL</span><h3>兴趣主题热度</h3></div><em>近 30 日</em></div>
            <div v-if="trends.length" class="interest-chart">
              <div class="interest-ring" :style="{ '--interest-share': `${topInterestShare}%` }"><div><strong>{{ topInterestShare }}<small>%</small></strong><span>{{ topInterest }}</span></div></div>
              <div class="interest-columns">
                <article v-for="(trend, index) in trends.slice(0, 6)" :key="trend.label">
                  <div><b :style="{ height: `${Math.max(12, Number(trend.count) / maxTrendCount * 100)}%` }"></b><i>{{ trend.count }}</i></div>
                  <span>{{ trend.label }}</span><small>{{ trend.share }}%</small>
                  <em>0{{ index + 1 }}</em>
                </article>
              </div>
            </div>
            <div v-else class="chart-empty">暂无需求数据</div>
          </section>

          <section class="audience-visual-card">
            <div class="section-title"><div><span>AUDIENCE MIX</span><h3>同行人画像</h3></div><em>{{ topAudience }}</em></div>
            <div v-if="audienceLegend.length" class="audience-chart">
              <div class="audience-donut" :style="audienceDonutStyle"><div><strong>{{ dashboard?.requestsLast30Days ?? 0 }}</strong><span>需求</span></div></div>
              <div class="audience-legend">
                <article v-for="segment in audienceLegend" :key="segment.label"><i :style="{ background: segment.color }"></i><span>{{ segment.label }}</span><b>{{ segment.share }}%</b><small>{{ segment.count }} 次</small></article>
              </div>
            </div>
            <div v-else class="chart-empty dark">暂无客群数据</div>
          </section>
        </div>

        <section class="citation-card citation-visual-card">
          <div class="section-title"><div><span>RESOURCE CITATION</span><h3>行程资源引用排行</h3></div><em>{{ dashboard?.plansLast7Days ?? 0 }} 份方案 · 近 7 日</em></div>
          <div v-if="resourceCitations.length" class="citation-chart">
            <article v-for="(item, index) in resourceCitations" :key="item.resourceId"><span>0{{ index + 1 }}</span><strong>{{ item.resourceName }}</strong><i><b :style="{ width: `${Number(item.count) / maxCitationCount * 100}%` }"></b></i><em>{{ item.count }} 次</em></article>
          </div>
          <div v-else class="chart-empty compact">暂无引用数据</div>
        </section>
      </section>
    </section>

    <Transition name="merchant-toast"><div v-if="toast" class="merchant-toast">{{ toast }}</div></Transition>
    <div v-if="resourceModalOpen" class="resource-modal-backdrop" @click.self="resourceModalOpen = false">
      <form class="resource-modal resource-smart-modal" @submit.prevent="addResource">
        <button class="smart-modal-close" type="button" aria-label="关闭" @click="resourceModalOpen = false">×</button>
        <header class="resource-smart-head">
          <div><span>OPENCLAW RESOURCE DESK</span><h2>新资源上架工作台</h2></div>
        </header>

        <div class="resource-smart-stages">
          <div :class="{ active: !newResource.amapPoiId, done: newResource.amapPoiId }"><b>01</b><span>绑定地点</span></div>
          <i></i>
          <div :class="{ active: newResource.amapPoiId && (!resourceAudit || resourceAuditStale), done: resourceAudit && !resourceAuditStale }"><b>02</b><span>智能审核</span></div>
          <i></i>
          <div :class="{ active: resourceAudit && !resourceAuditStale }"><b>03</b><span>确认发布</span></div>
        </div>

        <div class="resource-smart-body">
          <section class="resource-entry-pane">
            <div class="entry-section-title"><b>经营资料</b></div>
            <div class="modal-grid city-name-grid">
              <label><span>经营城市 *</span><input v-model.trim="newResource.city" placeholder="例如：苏州" /></label>
              <label><span>资源或体验名称 *</span><input v-model.trim="newResource.name" placeholder="例如：园林拓印体验" /></label>
            </div>
            <label>
              <span>高德真实地点 *</span>
              <div class="poi-search-row">
                <input v-model.trim="newResource.placeQuery" placeholder="搜索场馆、景点或门店" @keyup.enter.prevent="searchResourcePoi" />
                <button type="button" :disabled="poiSearching" @click="searchResourcePoi">{{ poiSearching ? '定位中' : '搜索 POI' }}</button>
              </div>
            </label>
            <div v-if="poiResults.length" class="poi-result-list">
              <button v-for="poi in poiResults" :key="poi.id" type="button" @click="selectResourcePoi(poi)">
                <strong>{{ poi.name }}</strong><small>{{ poi.address || poi.district || newResource.city }}</small>
              </button>
            </div>
            <p v-if="poiSearchError" class="poi-search-error">{{ poiSearchError }}</p>
            <div v-if="newResource.amapPoiId" class="selected-poi smart-selected-poi">
              <span>AMAP VERIFIED</span><strong>{{ newResource.placeName }}</strong>
              <small>{{ newResource.address || newResource.district }}</small>
              <small>{{ newResource.longitude.toFixed(6) }}, {{ newResource.latitude.toFixed(6) }} · {{ newResource.amapPoiId }}</small>
            </div>
            <div class="modal-grid">
              <label><span>资源类型</span><select v-model="newResource.category"><option v-for="option in categoryOptions" :key="option.value" :value="option.value">{{ option.label }}</option></select></label>
              <label><span>所在区域</span><input v-model.trim="newResource.district" placeholder="选择地点后自动填写" /></label>
            </div>
            <label><span>体验简介</span><textarea v-model.trim="newResource.summary" rows="3"></textarea></label>
            <label><span>资源标签</span><input v-model.trim="newResource.tags" placeholder="使用顿号分隔" /></label>
            <div class="modal-grid">
              <label><span>开放时间</span><input v-model.trim="newResource.openHours" placeholder="例如：09:00-17:00" /></label>
              <label><span>价格区间（元）</span><div class="price-row"><input v-model.number="newResource.priceMin" min="0" type="number" /><i>至</i><input v-model.number="newResource.priceMax" min="0" type="number" /></div></label>
            </div>
            <label class="reservation-switch"><input v-model="newResource.reservationRequired" type="checkbox" /><span>需要预约</span></label>
            <div v-if="newResource.reservationRequired" class="modal-grid">
              <label><span>每日接待容量</span><input v-model.number="newResource.dailyCapacity" min="1" type="number" /></label>
              <label><span>当前可预约余量</span><input v-model.number="newResource.availableCapacity" min="0" type="number" /></label>
            </div>
          </section>

          <aside class="resource-audit-pane">
            <div class="audit-pane-head">
              <span>{{ resourceAudit?.engine === 'OPENCLAW' ? 'OPENCLAW ONLINE' : 'PRE-PUBLISH REVIEW' }}</span>
              <b :class="{ live: resourceAudit?.engine === 'OPENCLAW' }">{{ resourceAudit?.engine === 'OPENCLAW' ? '主引擎审核' : resourceAudit ? '规则预检' : '等待资料' }}</b>
            </div>

            <div v-if="resourceAuditing" class="audit-scanning">
              <div class="claw-orbit"><span>灵</span><i></i></div>
              <h3>正在核验这项资源</h3>
              <div><i></i><i></i><i></i></div>
            </div>

            <div v-else-if="!resourceAudit || resourceAuditStale" class="audit-empty-state">
              <div class="claw-orbit"><span>灵</span><i></i></div>
              <h3>{{ resourceAuditStale ? '资料已发生变化' : '等待智能审核' }}</h3>
              <button type="button" :disabled="resourceAuditing" @click="auditResourceDraft">开始智能审核 <span>→</span></button>
            </div>

            <div v-else class="audit-result">
              <div class="audit-score-card">
                <div><span>资料完整度</span><b>{{ resourceAudit.currentScore }}</b><i>当前</i></div>
                <em>→</em>
                <div class="projected"><span>补全后预计</span><b>{{ resourceAudit.projectedScore }}</b><i>/ 100</i></div>
              </div>
              <div class="audit-verdict" :class="{ warning: !resourceAudit.recommendable }">
                <span>{{ resourceAudit.recommendable ? '审核通过' : '建议完善后发布' }}</span>
                <p>{{ resourceAudit.recommendationRationale }}</p>
              </div>
              <div class="audit-check-list">
                <article v-for="check in resourceAudit.checks" :key="check.code">
                  <i :class="check.status">{{ check.status === 'PASS' ? '✓' : check.status === 'IMPROVED' ? '+' : '!' }}</i>
                  <div><b>{{ check.label }}</b><small>{{ check.detail }}</small></div>
                </article>
              </div>
              <section class="traveler-preview">
                <header><span>游客侧预览</span><b>{{ newResource.city }} · {{ selectedCategoryLabel }}</b></header>
                <div><small>{{ newResource.district }} / {{ resourceAudit.generatedTags.slice(0, 3).join(' · ') }}</small><h3>{{ newResource.name }}</h3><p>{{ resourceAudit.generatedSummary }}</p></div>
              </section>
              <button class="apply-audit-button" type="button" @click="applyResourceAudit">应用简介与标签 <span>→</span></button>
            </div>
          </aside>
        </div>

        <footer class="resource-smart-footer">
          <label><input v-model="publishAfterCreate" type="checkbox" /><span>审核通过后直接发布</span></label>
          <div>
            <button type="button" :disabled="resourceAuditing" @click="auditResourceDraft">{{ resourceAudit && !resourceAuditStale ? '重新审核' : '智能审核' }}</button>
            <button type="submit" :disabled="resourceSaving || resourceAuditing || !resourceAudit || resourceAuditStale || !resourceAudit.recommendable">{{ resourceSaving ? '正在同步资源...' : publishAfterCreate ? '确认并发布 →' : '保存为草稿 →' }}</button>
          </div>
        </footer>
      </form>
    </div>
    <div v-if="resourceEditorOpen" class="resource-modal-backdrop" @click.self="resourceEditorOpen = false">
      <form class="resource-modal resource-editor" @submit.prevent="saveResource">
        <button type="button" @click="resourceEditorOpen = false">×</button>
        <span>RESOURCE OPERATIONS</span>
        <h2>编辑经营信息</h2>
        <label><span>资源或体验名称</span><input v-model.trim="editingResource.name" /></label>
        <label><span>资源类型</span><select v-model="editingResource.category"><option v-for="option in categoryOptions" :key="option.value" :value="option.value">{{ option.label }}</option></select></label>
        <label><span>体验简介</span><textarea v-model.trim="editingResource.summary" rows="4"></textarea></label>
        <label><span>资源标签</span><input v-model.trim="editingResource.tags" placeholder="使用顿号分隔" /></label>
        <div class="modal-grid">
          <label><span>开放时间</span><input v-model.trim="editingResource.openHours" placeholder="例如：09:00-17:00" /></label>
          <label><span>价格区间（元）</span><div class="price-row"><input v-model.number="editingResource.priceMin" min="0" type="number" /><i>至</i><input v-model.number="editingResource.priceMax" min="0" type="number" /></div></label>
        </div>
        <label class="reservation-switch"><input v-model="editingResource.reservationRequired" type="checkbox" /><span>需要预约</span></label>
        <div v-if="editingResource.reservationRequired" class="capacity-panel">
          <div class="modal-grid">
            <label><span>每日接待容量</span><input v-model.number="editingResource.dailyCapacity" min="1" type="number" /></label>
            <label><span>当前可预约余量</span><input v-model.number="editingResource.availableCapacity" min="0" type="number" /></label>
          </div>
        </div>
        <button class="modal-submit" type="submit" :disabled="resourceEditing">{{ resourceEditing ? '正在保存...' : '保存修改 →' }}</button>
      </form>
    </div>
    <div v-if="productModalOpen" class="resource-modal-backdrop" @click.self="productModalOpen = false">
      <form class="resource-modal product-modal" @submit.prevent="saveProduct">
        <button type="button" @click="productModalOpen = false">×</button>
        <span>TOURISM PRODUCT</span>
        <h2>{{ productForm.id ? '编辑旅游产品' : '创建产品草稿' }}</h2>
        <label><span>产品名称</span><input v-model.trim="productForm.name" placeholder="例如：运河非遗亲子慢游 · 两日" /></label>
        <div class="modal-grid">
          <label><span>目标客群</span><input v-model.trim="productForm.targetAudience" placeholder="例如：亲子家庭" /></label>
          <label><span>建议售价（元）</span><input v-model.number="productForm.price" min="0" type="number" /></label>
        </div>
        <label><span>行程天数</span><input v-model.number="productForm.durationDays" max="14" min="1" type="number" /></label>
        <label><span>产品说明</span><textarea v-model.trim="productForm.description" rows="4" placeholder="说明主题、节奏、主要体验和服务边界"></textarea></label>
        <label><span>产品亮点</span><input v-model.trim="productForm.highlights" placeholder="使用顿号分隔，例如：非遗手作、雨天友好" /></label>
        <fieldset class="product-resource-picker">
          <legend>选择组合资源 <small>已选 {{ productForm.resourceIds.length }} 项，至少选择 2 项</small></legend>
          <input v-model.trim="productResourceSearch" aria-label="搜索可组合资源" class="product-resource-search" placeholder="搜索资源名称、类型、区域或标签" />
          <label v-for="resource in visibleProductResourceOptions" :key="resource.id">
            <input v-model="productForm.resourceIds" :value="resource.id" type="checkbox" />
            <span><strong>{{ resource.name }}</strong><small>{{ resource.type }} · {{ resource.area }} · {{ resource.availabilityLabel }}</small></span>
          </label>
          <div v-if="!visibleProductResourceOptions.length" class="inline-empty">没有匹配的可用资源，请调整搜索关键词。</div>
        </fieldset>
        <button class="modal-submit" type="submit" :disabled="productSaving">{{ productSaving ? '正在保存...' : productForm.id ? '保存产品修改 →' : '创建产品草稿 →' }}</button>
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
.task-card article.actionable { cursor: pointer; transition: color .2s, transform .2s; }
.task-card article.actionable:hover { color: var(--merchant-green); transform: translateX(3px); }
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
.resource-toolbar-left { display: flex; align-items: center; gap: 10px; }
.resource-toolbar-left > input { width: min(230px, 35vw); padding: 8px 11px; border: 1px solid rgba(38, 51, 46, .12); border-radius: 999px; outline: none; background: rgba(255, 255, 255, .48); color: var(--merchant-ink); font-family: inherit; font-size: 9px; }
.resource-toolbar-left > input:focus { border-color: rgba(49, 91, 80, .45); }
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
.resource-pagination { display: flex; align-items: center; justify-content: space-between; padding: 4px 2px; color: #7b817a; font-size: 9px; }
.resource-pagination > div { display: flex; align-items: center; gap: 8px; }
.resource-pagination b { min-width: 52px; color: var(--merchant-green); font-weight: 500; text-align: center; }
.resource-pagination button { padding: 7px 11px; border: 1px solid rgba(49, 91, 80, .16); border-radius: 7px; background: rgba(255, 255, 255, .42); color: var(--merchant-green); cursor: pointer; font-family: inherit; font-size: 8px; }
.resource-pagination button:disabled { cursor: default; opacity: .35; }
.module-empty { display: grid; place-items: center; gap: 7px; min-height: 170px; padding: 28px; border: 1px dashed rgba(49, 91, 80, .18); border-radius: 14px; color: #81857f; text-align: center; }
.module-empty strong { color: var(--merchant-ink); font-size: 14px; }
.module-empty span { font-size: 9px; }
.module-empty button { margin-top: 8px; padding: 9px 14px; border: 0; border-radius: 8px; background: var(--merchant-green); color: #fffaf1; cursor: pointer; font-family: inherit; font-size: 9px; }

.product-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 14px; }
.product-grid article, .new-product-card { min-height: 310px; padding: 21px; border: 1px solid rgba(38, 51, 46, .09); border-radius: 15px; background: rgba(252, 250, 245, .82); }
.product-grid article { display: flex; flex-direction: column; }
.product-grid article > span { align-self: flex-start; padding: 4px 7px; border-radius: 999px; background: #e8eee5; color: var(--merchant-green); font-size: 8px; }
.product-grid article > span.inactive { background: #eee4d9; color: #8e6254; }
.product-grid h3 { margin: 18px 0 8px; font-size: 18px; }
.product-grid p { color: #747a73; font-size: 10px; line-height: 1.7; }
.product-resources { display: flex; flex-wrap: wrap; gap: 5px; margin-top: 14px; }
.product-resources b { padding: 4px 6px; border-radius: 999px; background: rgba(49, 91, 80, .07); color: #5f7169; font-size: 7px; font-weight: 500; }
.product-grid .product-price { display: flex; align-items: end; justify-content: space-between; margin-top: auto; padding-top: 22px; }
.product-grid .product-price strong { color: var(--merchant-red); font-size: 25px; font-weight: 500; }
.product-grid .product-price small { color: #81857f; font-size: 8px; }
.product-grid footer { display: grid; grid-template-columns: 1fr 1fr; gap: 7px; margin-top: 14px; padding-top: 10px; border-top: 1px solid rgba(38, 51, 46, .08); }
.product-grid footer button { padding: 7px; border: 1px solid rgba(49, 91, 80, .15); border-radius: 7px; background: transparent; color: var(--merchant-green); cursor: pointer; font-family: inherit; font-size: 8px; }
.product-grid footer button:last-child { background: var(--merchant-green); color: #fffaf1; }
.product-grid footer button:disabled { cursor: wait; opacity: .55; }
.new-product-card { display: none; place-content: center; justify-items: center; gap: 9px; border-style: dashed; color: #777d76; cursor: pointer; font-family: inherit; }
.new-product-card b { color: var(--merchant-green); font-size: 27px; font-weight: 300; }

.openclaw-badge { padding: 7px 10px; border: 1px solid rgba(49, 91, 80, .2); border-radius: 999px; color: var(--merchant-green); font-size: 8px; letter-spacing: 1px; }
.content-studio-grid { display: grid; grid-template-columns: minmax(280px, .62fr) minmax(0, 1.38fr); gap: 14px; }
.content-studio-grid form, .content-result { padding: 22px; border: 1px solid rgba(38, 51, 46, .09); border-radius: 14px; background: rgba(252, 250, 245, .82); }
.content-studio-grid form { display: grid; align-content: start; gap: 15px; }
.content-studio-grid label { display: grid; gap: 6px; }
.content-studio-grid label > span { color: #70766f; font-size: 9px; }
.content-studio-grid select, .content-studio-grid textarea, .resource-modal input, .resource-modal select, .resource-modal textarea { width: 100%; padding: 10px; border: 1px solid rgba(38, 51, 46, .13); border-radius: 8px; outline: 0; background: rgba(255, 255, 255, .6); color: var(--merchant-ink); font-family: inherit; font-size: 10px; }
.content-studio-grid textarea { resize: vertical; line-height: 1.6; }
.resource-modal textarea { resize: vertical; line-height: 1.6; }
.content-studio-grid form > button { margin-top: 6px; padding: 11px; border: 0; border-radius: 8px; background: var(--merchant-green); color: #fffaf1; cursor: pointer; font-family: inherit; font-size: 10px; }
.content-studio-grid form > button:disabled { cursor: wait; opacity: .6; }
.content-result { position: relative; display: flex; min-height: 420px; overflow: hidden; flex-direction: column; background: linear-gradient(145deg, rgba(255, 252, 246, .94), rgba(235, 226, 210, .75)); }
.content-result::after { position: absolute; right: -55px; bottom: -70px; width: 180px; height: 180px; content: ''; border: 1px solid rgba(170, 71, 56, .1); border-radius: 50%; box-shadow: inset 0 0 0 24px rgba(170, 71, 56, .025); }
.content-result > * { position: relative; z-index: 1; }
.content-result > div:first-child { display: flex; align-items: center; justify-content: space-between; }
.content-result > div button { border: 0; background: transparent; color: var(--merchant-green); cursor: pointer; font-family: inherit; font-size: 9px; }
.content-result > div button:disabled { cursor: default; opacity: .4; }
.content-result h3 { margin: 34px 0 13px; font-size: 23px; font-weight: 500; }
.content-result > p { max-width: 650px; color: #4d5953; font-size: 13px; line-height: 2; }
.content-placeholder { display: grid; place-items: start; gap: 8px; margin-top: 24px; padding: 22px; border: 1px dashed rgba(49, 91, 80, .15); border-radius: 10px; color: #81857f; }
.content-placeholder strong { color: var(--merchant-green); font-size: 13px; }
.content-placeholder span { font-size: 9px; }
.content-result details { margin: 20px 0; color: #70766f; font-size: 8px; }
.content-result summary { cursor: pointer; color: var(--merchant-green); }
.content-result details ul { display: grid; gap: 5px; margin: 10px 0 0; padding-left: 17px; line-height: 1.6; }
.content-result footer { display: flex; justify-content: space-between; margin-top: auto; padding-top: 12px; border-top: 1px solid rgba(38, 51, 46, .08); color: #858a83; font-size: 8px; }
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
.inline-empty { padding: 24px 4px; color: #858a83; font-size: 9px; line-height: 1.7; }
.citation-card { padding: 22px; border: 1px solid rgba(38, 51, 46, .09); border-radius: 14px; background: rgba(252, 250, 245, .82); }
.citation-list { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 8px; }
.citation-list article { display: grid; grid-template-columns: 24px 1fr auto; align-items: center; gap: 8px; padding: 12px; border: 1px solid rgba(38, 51, 46, .07); border-radius: 9px; background: rgba(255, 255, 255, .42); }
.citation-list span { color: #aaa296; font-size: 8px; }
.citation-list strong { overflow: hidden; font-size: 9px; text-overflow: ellipsis; white-space: nowrap; }
.citation-list b { color: var(--merchant-red); font-size: 9px; font-weight: 500; }

.insight-module { gap: 14px; }
.insight-intro { padding-bottom: 14px; }
.insight-kpi-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); border: 1px solid rgba(38, 51, 46, .1); background: rgba(255, 252, 246, .74); }
.insight-kpi-grid article { position: relative; display: grid; min-height: 104px; padding: 17px 19px; border-right: 1px solid rgba(38, 51, 46, .09); }
.insight-kpi-grid article:last-child { border-right: 0; }
.insight-kpi-grid article::after { position: absolute; right: 13px; top: 13px; width: 5px; height: 5px; content: ''; background: var(--merchant-red); }
.insight-kpi-grid span { color: #7a817b; font-size: 8px; letter-spacing: 1px; }
.insight-kpi-grid strong { align-self: center; color: var(--merchant-green); font-size: 28px; font-weight: 500; line-height: 1; }
.insight-kpi-grid strong i { margin-right: 3px; color: var(--merchant-red); font-size: 12px; font-style: normal; }
.insight-kpi-grid strong i:last-child { margin: 0 0 0 4px; color: #80867f; font-size: 9px; }
.insight-kpi-grid small { align-self: end; color: #a09e96; font-size: 7px; }

.insight-visual-grid { display: grid; grid-template-columns: minmax(0, 1.35fr) minmax(310px, .65fr); gap: 12px; }
.interest-visual-card,
.audience-visual-card,
.citation-visual-card { min-width: 0; padding: 19px 21px; border: 1px solid rgba(38, 51, 46, .09); background: rgba(255, 252, 246, .82); }
.interest-visual-card { border-top: 3px solid var(--merchant-green); }
.interest-chart { display: grid; grid-template-columns: 155px minmax(0, 1fr); align-items: center; gap: 23px; min-height: 218px; }
.interest-ring { display: grid; width: 142px; height: 142px; place-items: center; border-radius: 50%; background: conic-gradient(var(--merchant-red) var(--interest-share), #e8e1d5 0); transform: rotate(-90deg); }
.interest-ring::before { width: 101px; height: 101px; content: ''; border-radius: 50%; background: #f8f3e9; box-shadow: inset 0 0 0 1px rgba(38, 51, 46, .06); }
.interest-ring > div { position: absolute; display: grid; max-width: 84px; text-align: center; transform: rotate(90deg); }
.interest-ring strong { color: var(--merchant-green); font-size: 27px; font-weight: 500; line-height: 1; }
.interest-ring strong small { font-size: 9px; }
.interest-ring span { margin-top: 7px; overflow: hidden; color: #777c76; font-size: 8px; text-overflow: ellipsis; white-space: nowrap; }
.interest-columns { display: grid; grid-template-columns: repeat(6, minmax(35px, 1fr)); align-items: end; gap: 8px; min-width: 0; padding: 0 3px 5px; border-bottom: 1px solid rgba(38, 51, 46, .14); }
.interest-columns article { position: relative; display: grid; grid-template-rows: 132px 27px auto; min-width: 0; text-align: center; }
.interest-columns article > div { position: relative; display: flex; align-items: end; width: 100%; height: 126px; padding-top: 20px; }
.interest-columns article > div b { display: block; width: 100%; min-height: 12px; background: linear-gradient(180deg, #55796d, #315b50); box-shadow: inset 0 1px rgba(255, 255, 255, .22); animation: insight-bar-rise .5s ease both; transform-origin: bottom; }
.interest-columns article:nth-child(even) > div b { background: linear-gradient(180deg, #c98066, #ad5b44); }
.interest-columns article > div i { position: absolute; top: 2px; left: 0; width: 100%; color: var(--merchant-red); font-size: 8px; font-style: normal; }
.interest-columns article > span { align-self: center; overflow: hidden; color: #555f59; font-size: 8px; text-overflow: ellipsis; white-space: nowrap; }
.interest-columns article > small { color: #a09e96; font-size: 7px; }
.interest-columns article > em { position: absolute; right: 2px; top: 25px; color: rgba(255, 255, 255, .55); font-size: 6px; font-style: normal; writing-mode: vertical-rl; }

.audience-visual-card { color: #fffaf1; border: 0; background: radial-gradient(circle at 90% 0, rgba(199, 124, 90, .26), transparent 34%), #315b50; }
.audience-visual-card .section-title { border-bottom: 1px solid rgba(255, 255, 255, .12); padding-bottom: 13px; }
.audience-visual-card .section-title span { color: #e7b199; }
.audience-visual-card .section-title h3 { color: #fffaf1; }
.audience-visual-card .section-title em { color: rgba(255, 250, 241, .58); }
.audience-chart { display: grid; grid-template-columns: 142px 1fr; align-items: center; gap: 18px; min-height: 218px; }
.audience-donut { display: grid; width: 132px; height: 132px; place-items: center; border-radius: 50%; }
.audience-donut::before { width: 86px; height: 86px; content: ''; border-radius: 50%; background: #315b50; box-shadow: 0 0 0 1px rgba(255, 255, 255, .14); }
.audience-donut > div { position: absolute; display: grid; text-align: center; }
.audience-donut strong { color: #fffaf1; font-size: 25px; font-weight: 500; line-height: 1; }
.audience-donut span { margin-top: 5px; color: rgba(255, 250, 241, .55); font-size: 7px; letter-spacing: 1px; }
.audience-legend { display: grid; align-content: center; }
.audience-legend article { display: grid; grid-template-columns: 7px minmax(0, 1fr) auto; align-items: center; gap: 8px; min-width: 0; padding: 8px 0; border-bottom: 1px solid rgba(255, 255, 255, .09); }
.audience-legend article:last-child { border-bottom: 0; }
.audience-legend i { width: 7px; height: 7px; border-radius: 50%; }
.audience-legend span { overflow: hidden; color: rgba(255, 250, 241, .78); font-size: 8px; text-overflow: ellipsis; white-space: nowrap; }
.audience-legend b { color: #fffaf1; font-size: 9px; font-weight: 500; }
.audience-legend small { grid-column: 2 / -1; margin-top: -5px; color: rgba(255, 250, 241, .38); font-size: 6px; }

.citation-visual-card { padding-bottom: 21px; }
.citation-chart { display: grid; grid-template-columns: 1fr 1fr; gap: 0 30px; }
.citation-chart article { display: grid; grid-template-columns: 24px minmax(100px, .8fr) minmax(90px, 1fr) 38px; align-items: center; gap: 9px; min-width: 0; padding: 10px 0; border-top: 1px solid rgba(38, 51, 46, .07); }
.citation-chart article > span { color: #b7ada0; font-size: 7px; }
.citation-chart article > strong { overflow: hidden; font-size: 9px; font-weight: 500; text-overflow: ellipsis; white-space: nowrap; }
.citation-chart article > i { height: 5px; overflow: hidden; background: #e8e1d5; }
.citation-chart article > i b { display: block; height: 100%; min-width: 5px; background: linear-gradient(90deg, var(--merchant-green), #8ba095); animation: citation-bar-grow .55s ease both; transform-origin: left; }
.citation-chart article > em { justify-self: end; color: var(--merchant-red); font-size: 8px; font-style: normal; }
.chart-empty { display: grid; min-height: 218px; place-items: center; color: #999b94; font-size: 9px; }
.chart-empty.dark { color: rgba(255, 250, 241, .52); }
.chart-empty.compact { min-height: 90px; }

@keyframes insight-bar-rise {
  from { opacity: .25; transform: scaleY(.08); }
  to { opacity: 1; transform: scaleY(1); }
}

@keyframes citation-bar-grow {
  from { opacity: .25; transform: scaleX(.05); }
  to { opacity: 1; transform: scaleX(1); }
}

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
.product-modal { width: min(650px, 100%); }
.product-resource-picker { display: grid; gap: 7px; max-height: 260px; overflow-y: auto; padding: 12px; border: 1px solid rgba(49, 91, 80, .13); border-radius: 9px; }
.product-resource-picker legend { padding: 0 6px; color: var(--merchant-green); font-size: 9px; }
.product-resource-picker legend small { margin-left: 6px; color: #838780; font-size: 7px; }
.product-resource-picker .product-resource-search { position: sticky; top: -12px; z-index: 1; background: #fbf8f1; }
.product-resource-picker .product-resource-count { padding: 0 2px 4px; color: #888c85; font-size: 7px; }
.product-resource-picker label { display: grid; grid-template-columns: auto 1fr; align-items: center; gap: 9px; padding: 8px; border-radius: 7px; cursor: pointer; }
.product-resource-picker label:hover { background: rgba(49, 91, 80, .06); }
.product-resource-picker label input { width: 14px; height: 14px; accent-color: var(--merchant-green); }
.product-resource-picker label > span { display: grid; gap: 3px; }
.product-resource-picker label strong { font-size: 9px; }
.product-resource-picker label small { color: #7d827c; font-size: 7px; }

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
  .citation-list { grid-template-columns: 1fr 1fr; }
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
  .resource-toolbar-left { display: grid; width: 100%; }
  .resource-toolbar-left > input { width: 100%; }
  .filter-tabs { flex-wrap: wrap; }
  .resource-management-list article { grid-template-columns: 48px 1fr auto; }
  .resource-status { display: none; }
  .resource-actions { grid-column: 2 / -1; grid-template-columns: 1fr 1fr; }
  .resource-score { width: 42px; height: 42px; }
  .modal-grid { grid-template-columns: 1fr; }
  .product-grid { grid-template-columns: 1fr; }
  .citation-list { grid-template-columns: 1fr; }
}

/* Competition presentation layout: editorial operations console. */
.merchant-app {
  grid-template-columns: 218px minmax(0, 1fr);
  background:
    linear-gradient(rgba(49, 91, 80, .022) 1px, transparent 1px),
    linear-gradient(90deg, rgba(49, 91, 80, .022) 1px, transparent 1px),
    #f3eee4;
  background-size: 34px 34px;
}

.merchant-sidebar {
  z-index: 12;
  padding: 22px 14px 17px;
  border-right: 0;
  background:
    radial-gradient(circle at 10% 3%, rgba(179, 101, 75, .2), transparent 25%),
    #263f38;
  color: #f8f1e5;
  box-shadow: 10px 0 32px rgba(32, 54, 47, .08);
}

.merchant-brand {
  gap: 12px;
  margin: 0 7px 36px;
  color: #fffaf1;
}

.merchant-brand > span {
  width: 40px;
  height: 44px;
  border: 1px solid rgba(255, 250, 241, .26);
  border-radius: 0 0 13px 13px;
  background: #aa5b45;
  color: #fffaf1;
  font-size: 18px;
  box-shadow: 0 8px 18px rgba(15, 31, 26, .18);
}

.merchant-brand strong {
  font-size: 18px;
  font-weight: 600;
}

.merchant-brand small {
  color: rgba(255, 250, 241, .5);
  font-size: 8px;
}

.merchant-sidebar nav {
  gap: 5px;
}

.merchant-sidebar nav button {
  gap: 11px;
  padding: 9px 10px;
  border-radius: 2px;
  color: rgba(255, 250, 241, .68);
  font-size: 11px;
  transition: background .2s ease, color .2s ease, transform .2s ease;
}

.merchant-sidebar nav button i {
  width: 27px;
  height: 27px;
  border-color: rgba(255, 250, 241, .15);
  border-radius: 50%;
  color: rgba(255, 250, 241, .7);
}

.merchant-sidebar nav button:hover {
  background: rgba(255, 250, 241, .08);
  color: #fffaf1;
  transform: translateX(2px);
}

.merchant-sidebar nav button.active {
  background: #f0e7d8;
  color: #263f38;
}

.merchant-sidebar nav button.active i {
  border-color: rgba(38, 63, 56, .18);
  color: #aa5b45;
}

.merchant-user {
  border-top-color: rgba(255, 250, 241, .12);
}

.merchant-user > span {
  border-radius: 2px;
  background: rgba(255, 250, 241, .12);
  color: #edb092;
}

.merchant-user strong { color: #fffaf1; }
.merchant-user small { color: rgba(255, 250, 241, .46); }
.merchant-user button { color: rgba(255, 250, 241, .55); }

.merchant-main {
  min-height: 100vh;
}

.merchant-topbar {
  position: sticky;
  z-index: 10;
  top: 0;
  height: 72px;
  padding: 0 clamp(22px, 3.5vw, 48px);
  border-bottom-color: rgba(38, 51, 46, .1);
  background: rgba(247, 243, 234, .9);
  backdrop-filter: blur(15px);
}

.merchant-topbar span,
.section-title span {
  color: #a55540;
  font-size: 8px;
  font-weight: 700;
  letter-spacing: 2.1px;
}

.merchant-topbar h1 {
  margin-top: 2px;
  font-size: 21px;
  letter-spacing: 1px;
}

.topbar-actions {
  gap: 7px;
}

.topbar-actions a,
.topbar-actions button,
.coming-soon button {
  padding: 8px 13px;
  border-radius: 2px;
  font-size: 9px;
}

.dashboard-content,
.module-content {
  width: min(1460px, 100%);
  margin: 0 auto;
}

.dashboard-content {
  gap: 14px;
  padding: 18px clamp(22px, 3.5vw, 48px) 38px;
}

.merchant-welcome {
  position: relative;
  min-height: 176px;
  padding: 24px 30px;
  border: 1px solid rgba(255, 250, 241, .1);
  border-radius: 2px;
  background:
    radial-gradient(circle at 75% 20%, rgba(189, 127, 94, .2), transparent 28%),
    linear-gradient(108deg, #263f38, #365b50);
  box-shadow: 0 18px 42px rgba(38, 63, 56, .13);
}

.merchant-welcome::after {
  position: absolute;
  right: 135px;
  bottom: -72px;
  width: 190px;
  height: 190px;
  content: '';
  border: 1px solid rgba(255, 250, 241, .08);
  border-radius: 50%;
  box-shadow: inset 0 0 0 26px rgba(255, 250, 241, .025);
}

.welcome-copy {
  position: relative;
  z-index: 1;
  min-width: 0;
}

.merchant-welcome .welcome-copy > span {
  font-size: 8px;
  letter-spacing: 2px;
  opacity: .62;
}

.merchant-welcome h2 {
  margin: 6px 0 18px;
  font-size: clamp(22px, 2vw, 28px);
  letter-spacing: 1px;
}

.welcome-pulse {
  display: flex;
  align-items: stretch;
}

.welcome-pulse button {
  display: grid;
  min-width: 150px;
  gap: 2px;
  padding: 0 22px;
  border: 0;
  border-left: 1px solid rgba(255, 250, 241, .18);
  background: transparent;
  color: #fffaf1;
  cursor: pointer;
  font-family: inherit;
  text-align: left;
}

.welcome-pulse button:first-child {
  padding-left: 0;
  border-left: 0;
}

.welcome-pulse strong {
  overflow: hidden;
  font-size: 18px;
  font-weight: 500;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.welcome-pulse small {
  color: rgba(255, 250, 241, .56);
  font-size: 8px;
}

.quality-ring {
  position: relative;
  z-index: 1;
  width: 96px;
  height: 96px;
  border-color: rgba(255, 250, 241, .3);
  box-shadow: inset 0 0 0 6px rgba(255, 250, 241, .06), 0 12px 25px rgba(17, 35, 29, .12);
}

.quality-ring strong { font-size: 26px; }

.metric-grid {
  gap: 0;
  overflow: hidden;
  border: 1px solid rgba(38, 51, 46, .1);
  border-radius: 2px;
  background: rgba(255, 252, 246, .78);
  box-shadow: 0 10px 28px rgba(38, 51, 46, .04);
}

.metric-grid article {
  min-height: 100px;
  padding: 15px 18px;
  border: 0;
  border-left: 1px solid rgba(38, 51, 46, .09);
  border-radius: 0;
  background: transparent;
}

.metric-grid article:first-child { border-left: 0; }
.metric-grid article > span { font-size: 9px; }
.metric-grid strong { margin: 3px 0; font-size: 24px; }
.metric-grid small { font-size: 8px; }

.dashboard-grid {
  grid-template-columns: minmax(0, 1.35fr) minmax(300px, .65fr);
  gap: 12px;
}

.resource-card,
.task-card,
.trend-card,
.portrait-card,
.citation-card {
  border-radius: 2px;
  background: rgba(255, 252, 246, .8);
  box-shadow: 0 10px 30px rgba(38, 51, 46, .045);
}

.resource-card { border-top: 3px solid #315b50; }
.task-card { border-top: 3px solid #aa5b45; }
.section-title h3 { font-size: 16px; }
.resource-table strong, .task-card strong { font-size: 10px; }

.insight-strip {
  grid-template-columns: 100px 1fr auto;
  padding: 13px 18px;
  border: 1px solid rgba(38, 51, 46, .1);
  border-left: 3px solid #aa5b45;
  background: rgba(255, 252, 246, .7);
}

.module-content {
  gap: 15px;
  padding: 22px clamp(22px, 3.5vw, 48px) 44px;
}

.module-intro {
  padding: 3px 0 19px;
  border: 0;
  border-bottom: 1px solid rgba(38, 51, 46, .14);
  border-radius: 0;
  background: transparent;
}

.module-intro h2 {
  margin: 4px 0 5px;
  font-size: clamp(21px, 2vw, 27px);
}

.module-intro p {
  font-size: 10px;
}

.module-stat {
  padding: 8px 0 8px 24px;
}

.primary-module-action,
.module-toolbar > button {
  border-radius: 2px;
}

.resource-toolbar-left > input,
.filter-tabs button {
  border-radius: 2px;
}

.resource-management-list article {
  border-left: 3px solid rgba(49, 91, 80, .42);
  border-radius: 2px;
  background: rgba(255, 252, 246, .78);
}

.resource-score {
  width: 48px;
  height: 48px;
  border-radius: 2px;
  background: rgba(49, 91, 80, .04);
}

.resource-copy h3 { font-size: 13px; }
.resource-actions button { border-radius: 2px; }

.product-grid article,
.new-product-card {
  min-height: 286px;
  border-top: 3px solid #315b50;
  border-radius: 2px;
  background: rgba(255, 252, 246, .82);
  box-shadow: 0 12px 30px rgba(38, 51, 46, .05);
}

.product-grid h3 { margin-top: 14px; font-size: 17px; }
.product-grid footer button { border-radius: 2px; }

.openclaw-badge {
  border-radius: 2px;
  background: rgba(49, 91, 80, .05);
}

.content-studio-grid {
  grid-template-columns: minmax(280px, .58fr) minmax(0, 1.42fr);
  gap: 12px;
}

.content-studio-grid form,
.content-result {
  border-radius: 2px;
  background: rgba(255, 252, 246, .82);
}

.content-result {
  min-height: 390px;
  border-top: 3px solid #315b50;
}

.content-studio-grid select,
.content-studio-grid textarea,
.resource-modal input,
.resource-modal select,
.resource-modal textarea,
.content-studio-grid form > button {
  border-radius: 2px;
}

.portrait-card {
  border: 0;
  background:
    radial-gradient(circle at 90% 10%, rgba(181, 105, 77, .25), transparent 30%),
    #315b50;
}

.citation-list article { border-radius: 2px; }

.dashboard-content > *,
.module-content > * {
  animation: merchant-section-arrive .42s ease both;
}

.dashboard-content > :nth-child(2),
.module-content > :nth-child(2) { animation-delay: .05s; }
.dashboard-content > :nth-child(3),
.module-content > :nth-child(3) { animation-delay: .1s; }
.dashboard-content > :nth-child(4),
.module-content > :nth-child(4) { animation-delay: .15s; }

@keyframes merchant-section-arrive {
  from { opacity: 0; transform: translateY(8px); }
  to { opacity: 1; transform: translateY(0); }
}

@media (max-width: 1050px) {
  .merchant-app { grid-template-columns: 76px minmax(0, 1fr); }
  .merchant-sidebar { padding-inline: 9px; }
  .merchant-brand { justify-content: center; margin-inline: 0; }
  .merchant-brand div { display: none; }
  .merchant-sidebar nav button,
  .merchant-sidebar nav button.active,
  .merchant-sidebar nav button:not(.active) { justify-content: center; font-size: 0; }
  .merchant-user div { display: none; }
  .merchant-user { grid-template-columns: 1fr; justify-items: center; }
  .merchant-user button { width: 100%; padding-top: 5px; }
  .welcome-pulse button { min-width: 120px; padding-inline: 15px; }
  .metric-grid { grid-template-columns: 1fr 1fr; }
  .metric-grid article:nth-child(3) { border-left: 0; border-top: 1px solid rgba(38, 51, 46, .09); }
  .metric-grid article:nth-child(4) { border-top: 1px solid rgba(38, 51, 46, .09); }
  .dashboard-grid { grid-template-columns: 1fr; }
  .content-studio-grid,
  .insight-grid { grid-template-columns: 1fr; }
}

@media (max-width: 700px) {
  .merchant-app { display: block; }
  .merchant-sidebar { position: relative; width: 100%; height: auto; padding: 10px 14px; }
  .merchant-brand { display: none; }
  .merchant-sidebar nav { display: flex; gap: 5px; overflow-x: auto; }
  .merchant-sidebar nav button,
  .merchant-sidebar nav button.active,
  .merchant-sidebar nav button:not(.active) { flex-shrink: 0; padding: 8px 12px; font-size: 10px; }
  .merchant-sidebar nav button i { display: none; }
  .merchant-user { display: none; }
  .merchant-topbar { height: auto; min-height: 66px; padding: 13px 16px; }
  .topbar-actions a { display: none; }
  .dashboard-content,
  .module-content { padding: 13px 14px 34px; }
  .merchant-welcome { min-height: 0; padding: 21px; }
  .welcome-pulse { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
  .welcome-pulse button { min-width: 0; padding: 0; border: 0; }
  .welcome-pulse button:last-child { grid-column: 1 / -1; }
  .quality-ring { display: none; }
  .metric-grid { grid-template-columns: 1fr 1fr; }
  .metric-grid article { min-height: 92px; padding: 13px; }
  .insight-strip { grid-template-columns: 1fr; }
  .module-intro { align-items: flex-start; padding: 3px 0 15px; }
  .resource-management-list article { grid-template-columns: 48px 1fr auto; }
  .product-grid { grid-template-columns: 1fr; }
}

/* Resource onboarding is deliberately staged so the OpenClaw step is visible in demos. */
.resource-modal.resource-smart-modal {
  display: grid;
  grid-template-rows: auto auto minmax(0, 1fr) auto;
  gap: 0;
  width: min(1080px, calc(100vw - 36px));
  height: min(790px, calc(100vh - 32px));
  max-height: none;
  padding: 0;
  overflow: hidden;
  border: 1px solid rgba(38, 51, 46, .14);
  border-radius: 4px;
  background: #f8f4eb;
  box-shadow: 0 28px 90px rgba(24, 34, 30, .32);
}

.resource-smart-modal .smart-modal-close {
  z-index: 3;
  top: 19px;
  right: 24px;
  width: 30px;
  height: 30px;
  padding: 0;
  border: 1px solid rgba(255, 255, 255, .22);
  border-radius: 50%;
  color: rgba(255, 255, 255, .8);
  font-size: 18px;
}

.resource-smart-head {
  display: flex;
  align-items: end;
  justify-content: space-between;
  gap: 30px;
  padding: 22px 68px 20px 28px;
  color: #fffaf1;
  background:
    radial-gradient(circle at 72% -80%, rgba(199, 132, 98, .45), transparent 42%),
    linear-gradient(115deg, #213e37, #315b50 72%, #3f665a);
}

.resource-smart-head div { display: grid; gap: 4px; }
.resource-smart-head span { color: #e8b49e; font-size: 8px; letter-spacing: 3px; }
.resource-smart-head h2 { margin: 0; color: #fffaf1; font-size: 23px; font-weight: 500; letter-spacing: 2px; }
.resource-smart-head p { max-width: 390px; margin: 0; color: rgba(255, 250, 241, .65); font-size: 9px; line-height: 1.6; text-align: right; }

.resource-smart-stages {
  display: grid;
  grid-template-columns: auto 1fr auto 1fr auto;
  align-items: center;
  gap: 13px;
  padding: 11px 28px;
  border-bottom: 1px solid rgba(38, 51, 46, .1);
  background: #eee7d9;
}

.resource-smart-stages > div { display: flex; align-items: center; gap: 7px; color: #98988f; }
.resource-smart-stages b { display: grid; width: 23px; height: 23px; place-items: center; border: 1px solid currentColor; font-size: 7px; font-weight: 500; }
.resource-smart-stages span { font-size: 9px; letter-spacing: .5px; }
.resource-smart-stages > i { height: 1px; background: rgba(38, 51, 46, .12); }
.resource-smart-stages > div.active { color: var(--merchant-red); }
.resource-smart-stages > div.done { color: var(--merchant-green); }
.resource-smart-stages > div.done b { background: var(--merchant-green); color: #fffaf1; }

.resource-smart-body {
  display: grid;
  grid-template-columns: minmax(0, 1.08fr) minmax(380px, .92fr);
  min-height: 0;
}

.resource-entry-pane {
  display: grid;
  align-content: start;
  gap: 12px;
  min-width: 0;
  padding: 21px 28px 28px;
  overflow-y: auto;
  scrollbar-color: rgba(49, 91, 80, .25) transparent;
  scrollbar-width: thin;
}

.entry-section-title { display: flex; align-items: center; justify-content: space-between; padding-bottom: 4px; }
.entry-section-title b { color: var(--merchant-ink); font-size: 13px; font-weight: 600; }
.entry-section-title span { color: #96958d; font-size: 8px; }
.resource-smart-modal label > span { color: #58645e; font-size: 9px; font-weight: 600; }
.resource-smart-modal label > span small { margin-left: 5px; color: #a09a90; font-size: 7px; font-weight: 400; }
.resource-smart-modal input,
.resource-smart-modal select,
.resource-smart-modal textarea {
  min-height: 36px;
  border-color: rgba(38, 51, 46, .14);
  background: rgba(255, 253, 248, .88);
  font-size: 10px;
}
.resource-smart-modal input:focus,
.resource-smart-modal select:focus,
.resource-smart-modal textarea:focus { border-color: rgba(49, 91, 80, .6); box-shadow: 0 0 0 2px rgba(49, 91, 80, .06); }
.resource-smart-modal .poi-search-row button { min-width: 92px; border-radius: 2px; }
.resource-smart-modal .poi-result-list { position: relative; z-index: 2; margin-top: -7px; border-radius: 2px; }
.resource-smart-modal .smart-selected-poi { border-radius: 1px; background: rgba(49, 91, 80, .075); }
.resource-smart-modal .smart-selected-poi span { font-size: 7px; letter-spacing: 2px; }
.resource-smart-modal .reservation-switch { border-radius: 2px; }

.resource-audit-pane {
  min-width: 0;
  padding: 22px 26px 26px;
  overflow-y: auto;
  color: #f8f1e5;
  background:
    linear-gradient(rgba(255, 255, 255, .025) 1px, transparent 1px),
    linear-gradient(90deg, rgba(255, 255, 255, .025) 1px, transparent 1px),
    radial-gradient(circle at 90% 12%, rgba(194, 116, 83, .23), transparent 28%),
    #25463e;
  background-size: 24px 24px, 24px 24px, auto, auto;
  scrollbar-color: rgba(255, 255, 255, .22) transparent;
  scrollbar-width: thin;
}

.audit-pane-head { display: flex; align-items: center; justify-content: space-between; padding-bottom: 14px; border-bottom: 1px solid rgba(255, 255, 255, .12); }
.audit-pane-head > span { color: #e6b098; font-size: 7px; letter-spacing: 2.5px; }
.audit-pane-head > b { display: flex; align-items: center; gap: 6px; color: rgba(255, 250, 241, .58); font-size: 8px; font-weight: 500; }
.audit-pane-head > b::before { width: 6px; height: 6px; content: ''; border-radius: 50%; background: #a4a29b; }
.audit-pane-head > b.live::before { background: #87be9c; box-shadow: 0 0 0 5px rgba(135, 190, 156, .1); }

.audit-empty-state,
.audit-scanning { display: grid; justify-items: center; align-content: center; min-height: calc(100% - 38px); padding: 26px 20px; text-align: center; }
.claw-orbit { position: relative; display: grid; width: 76px; height: 76px; margin-bottom: 22px; place-items: center; border: 1px solid rgba(255, 255, 255, .18); border-radius: 50%; }
.claw-orbit::before { position: absolute; inset: 8px; content: ''; border: 1px solid rgba(232, 176, 152, .22); border-radius: 50%; }
.claw-orbit span { z-index: 1; display: grid; width: 42px; height: 42px; place-items: center; border-radius: 50%; background: #b76249; color: #fff7eb; font-size: 18px; }
.claw-orbit > i { position: absolute; top: -3px; width: 7px; height: 7px; border-radius: 50%; background: #e7b39b; box-shadow: 0 0 16px #e7b39b; }
.audit-empty-state h3,
.audit-scanning h3 { margin: 0 0 8px; color: #fffaf1; font-size: 19px; font-weight: 500; letter-spacing: 1px; }
.audit-empty-state > p,
.audit-scanning > p { max-width: 330px; color: rgba(255, 250, 241, .62); font-size: 9px; line-height: 1.8; }
.audit-empty-state ul { display: grid; grid-template-columns: 1fr 1fr; gap: 7px 20px; width: min(340px, 100%); margin: 20px 0 23px; padding: 14px 16px; border-block: 1px solid rgba(255, 255, 255, .1); list-style: none; text-align: left; }
.audit-empty-state li { color: rgba(255, 250, 241, .75); font-size: 8px; }
.audit-empty-state li::before { margin-right: 7px; color: #e7b39b; content: '·'; }
.audit-empty-state > button,
.apply-audit-button { padding: 10px 17px; border: 1px solid rgba(255, 255, 255, .28); border-radius: 2px; background: transparent; color: #fffaf1; cursor: pointer; font-family: inherit; font-size: 9px; }
.audit-empty-state > button span,
.apply-audit-button span { margin-left: 15px; color: #e7b39b; }

.audit-scanning .claw-orbit { animation: audit-pulse 1.6s ease-in-out infinite; }
.audit-scanning > div:last-child { display: flex; gap: 6px; margin-top: 24px; }
.audit-scanning > div:last-child i { width: 28px; height: 2px; background: rgba(255, 255, 255, .16); animation: audit-loading 1.2s ease-in-out infinite; }
.audit-scanning > div:last-child i:nth-child(2) { animation-delay: .18s; }
.audit-scanning > div:last-child i:nth-child(3) { animation-delay: .36s; }

.audit-result { display: grid; gap: 12px; padding-top: 16px; }
.audit-score-card { display: grid; grid-template-columns: 1fr auto 1fr; align-items: center; padding: 12px 16px; border: 1px solid rgba(255, 255, 255, .12); background: rgba(255, 255, 255, .045); }
.audit-score-card > div { display: grid; grid-template-columns: 1fr auto; align-items: end; gap: 2px 10px; }
.audit-score-card span { grid-column: 1 / -1; color: rgba(255, 250, 241, .52); font-size: 7px; letter-spacing: 1px; }
.audit-score-card b { color: #fffaf1; font-size: 27px; font-weight: 500; line-height: 1; }
.audit-score-card i { color: rgba(255, 250, 241, .45); font-size: 7px; font-style: normal; }
.audit-score-card > em { padding: 0 18px; color: #e7b39b; font-size: 16px; font-style: normal; }
.audit-score-card .projected b { color: #e7b39b; }
.audit-verdict { padding: 11px 13px; border-left: 3px solid #8fc3a3; background: rgba(143, 195, 163, .08); }
.audit-verdict.warning { border-left-color: #df9b7d; background: rgba(223, 155, 125, .08); }
.audit-verdict span { color: #b8ddc5; font-size: 9px; font-weight: 600; }
.audit-verdict.warning span { color: #efb49a; }
.audit-verdict p { margin: 4px 0 0; color: rgba(255, 250, 241, .65); font-size: 8px; line-height: 1.65; }
.audit-check-list { display: grid; grid-template-columns: 1fr 1fr; gap: 7px; }
.audit-check-list article { display: grid; grid-template-columns: 24px 1fr; gap: 8px; padding: 9px; border: 1px solid rgba(255, 255, 255, .09); background: rgba(255, 255, 255, .025); }
.audit-check-list article > i { display: grid; width: 22px; height: 22px; place-items: center; border: 1px solid #84b69a; border-radius: 50%; color: #a9d1b8; font-size: 9px; font-style: normal; }
.audit-check-list article > i.IMPROVED { border-color: #df9b7d; color: #efb49a; }
.audit-check-list article > i.WARN { border-color: #dca078; color: #f0b28a; }
.audit-check-list article div { display: grid; gap: 3px; }
.audit-check-list b { color: #fffaf1; font-size: 8px; font-weight: 500; }
.audit-check-list small { color: rgba(255, 250, 241, .5); font-size: 7px; line-height: 1.45; }
.traveler-preview { overflow: hidden; border: 1px solid rgba(255, 255, 255, .13); background: #f4eee3; color: var(--merchant-ink); }
.traveler-preview header { display: flex; align-items: center; justify-content: space-between; padding: 8px 11px; border-bottom: 1px solid rgba(38, 51, 46, .08); }
.traveler-preview header span { color: var(--merchant-red); font-size: 7px; letter-spacing: 1.5px; }
.traveler-preview header b { color: #768078; font-size: 7px; font-weight: 500; }
.traveler-preview > div { padding: 11px 13px 13px; }
.traveler-preview small { color: #9a6e5c; font-size: 7px; }
.traveler-preview h3 { margin: 4px 0 5px; font-size: 14px; font-weight: 600; }
.traveler-preview p { display: -webkit-box; overflow: hidden; margin: 0; color: #69716b; font-size: 8px; line-height: 1.6; -webkit-box-orient: vertical; -webkit-line-clamp: 2; }
.apply-audit-button { justify-self: end; }

.resource-smart-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  padding: 13px 20px 13px 28px;
  border-top: 1px solid rgba(38, 51, 46, .1);
  background: #ede6d9;
}
.resource-smart-footer > label { display: flex; align-items: center; gap: 8px; color: #69726c; cursor: pointer; font-size: 8px; }
.resource-smart-footer > label input { width: 13px; min-height: 13px; height: 13px; margin: 0; accent-color: var(--merchant-green); }
.resource-smart-footer > div { display: flex; gap: 8px; }
.resource-smart-footer button { min-width: 92px; padding: 10px 15px; border: 1px solid rgba(49, 91, 80, .22); border-radius: 2px; background: transparent; color: var(--merchant-green); cursor: pointer; font-family: inherit; font-size: 9px; }
.resource-smart-footer button[type='submit'] { min-width: 140px; border-color: var(--merchant-green); background: var(--merchant-green); color: #fffaf1; }
.resource-smart-footer button:disabled { cursor: not-allowed; opacity: .38; }

@keyframes audit-pulse {
  0%, 100% { transform: scale(1); box-shadow: 0 0 0 0 rgba(231, 179, 155, 0); }
  50% { transform: scale(1.04); box-shadow: 0 0 0 12px rgba(231, 179, 155, .04); }
}

@keyframes audit-loading {
  0%, 100% { background: rgba(255, 255, 255, .12); }
  50% { background: #e7b39b; }
}

@media (max-width: 900px) {
  .resource-modal.resource-smart-modal { height: calc(100vh - 20px); width: calc(100vw - 20px); overflow-y: auto; grid-template-rows: auto auto auto auto; }
  .resource-smart-body { grid-template-columns: 1fr; }
  .resource-entry-pane,
  .resource-audit-pane { overflow: visible; }
  .resource-audit-pane { min-height: 480px; }
}

@media (max-width: 620px) {
  .resource-modal-backdrop { padding: 0; }
  .resource-modal.resource-smart-modal { width: 100vw; height: 100vh; border: 0; }
  .resource-smart-head { align-items: start; padding: 18px 54px 17px 18px; }
  .resource-smart-head p { display: none; }
  .resource-smart-stages { gap: 7px; padding: 9px 16px; }
  .resource-smart-stages span { display: none; }
  .resource-entry-pane { padding: 18px; }
  .city-name-grid,
  .resource-smart-modal .modal-grid { grid-template-columns: 1fr; }
  .resource-audit-pane { min-height: 510px; padding: 20px 18px; }
  .resource-smart-footer { position: sticky; bottom: 0; display: grid; padding: 11px 14px; }
  .resource-smart-footer > div { width: 100%; }
  .resource-smart-footer button { flex: 1; }
}

@media (max-width: 1100px) {
  .insight-visual-grid { grid-template-columns: 1fr; }
  .audience-chart { grid-template-columns: 170px 1fr; }
}

@media (max-width: 700px) {
  .insight-kpi-grid { grid-template-columns: 1fr 1fr; }
  .insight-kpi-grid article:nth-child(2) { border-right: 0; }
  .insight-kpi-grid article:nth-child(-n + 2) { border-bottom: 1px solid rgba(38, 51, 46, .09); }
  .interest-chart { grid-template-columns: 1fr; justify-items: center; }
  .interest-columns { width: 100%; }
  .audience-chart { grid-template-columns: 135px 1fr; }
  .citation-chart { grid-template-columns: 1fr; }
}

@media (max-width: 460px) {
  .insight-kpi-grid article { min-height: 92px; padding: 14px; }
  .insight-kpi-grid strong { font-size: 23px; }
  .interest-visual-card,
  .audience-visual-card,
  .citation-visual-card { padding: 17px; }
  .interest-columns { gap: 5px; }
  .interest-columns article { grid-template-rows: 110px 25px auto; }
  .interest-columns article > div { height: 104px; }
  .audience-chart { grid-template-columns: 1fr; justify-items: center; }
  .audience-legend { width: 100%; }
  .citation-chart article { grid-template-columns: 20px minmax(90px, 1fr) 80px 34px; }
}
</style>
