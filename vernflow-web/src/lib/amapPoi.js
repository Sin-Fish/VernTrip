import AMapLoader from '@amap/amap-jsapi-loader'

const apiKey = (import.meta.env.VITE_AMAP_JS_KEY ?? '').trim()
const securityCode = (import.meta.env.VITE_AMAP_SECURITY_CODE ?? '').trim()

export const amapConfigured = Boolean(apiKey && securityCode)

let loaderPromise = null
const searchCache = new Map()
const detailCache = new Map()

export function loadAmap() {
  if (!amapConfigured) {
    return Promise.reject(new Error('尚未配置高德地图密钥'))
  }
  if (!loaderPromise) {
    window._AMapSecurityConfig = { securityJsCode: securityCode }
    loaderPromise = AMapLoader.load({
      key: apiKey,
      version: '2.0',
      plugins: ['AMap.Driving', 'AMap.PlaceSearch', 'AMap.Scale'],
    })
  }
  return loaderPromise
}

function readLocation(location) {
  const longitude = Number(location?.getLng?.() ?? location?.lng)
  const latitude = Number(location?.getLat?.() ?? location?.lat)
  if (!Number.isFinite(longitude) || !Number.isFinite(latitude)) return null
  return { longitude, latitude }
}

function readText(value) {
  if (Array.isArray(value)) return value.filter(Boolean).join('')
  return typeof value === 'string' ? value : ''
}

function toPlainPoi(poi) {
  const location = readLocation(poi?.location)
  if (!poi?.id || !poi?.name || !location) return null
  const district = readText(poi.adname || poi.district)
  const address = readText(poi.address)
  return {
    id: poi.id,
    name: poi.name,
    type: readText(poi.type),
    district,
    address: [district, address].filter((part, index, values) => (
      part && values.findIndex((value) => value.includes(part) || part.includes(value)) === index
    )).join(' '),
    ...location,
  }
}

function resultPois(result) {
  return result?.poiList?.pois ?? result?.pois ?? []
}

export function searchAmapPois(AMap, { keyword, city = '杭州', pageSize = 8 }) {
  const query = keyword.trim()
  if (!query) return Promise.resolve([])
  const cacheKey = `${city}|${query}|${pageSize}`
  if (searchCache.has(cacheKey)) return searchCache.get(cacheKey)

  const request = new Promise((resolve, reject) => {
    const placeSearch = new AMap.PlaceSearch({
      city,
      citylimit: true,
      extensions: 'all',
      pageIndex: 1,
      pageSize,
    })
    placeSearch.search(query, (status, result) => {
      if (status === 'complete') {
        resolve(resultPois(result).map(toPlainPoi).filter(Boolean))
        return
      }
      if (status === 'no_data') {
        resolve([])
        return
      }
      reject(new Error(result?.info || '高德地点搜索暂不可用'))
    })
  })
  searchCache.set(cacheKey, request)
  request.catch(() => searchCache.delete(cacheKey))
  return request
}

export function getAmapPoi(AMap, poiId, city = '杭州') {
  if (!poiId) return Promise.resolve(null)
  const cacheKey = `${city}|${poiId}`
  if (detailCache.has(cacheKey)) return detailCache.get(cacheKey)

  const request = new Promise((resolve, reject) => {
    const placeSearch = new AMap.PlaceSearch({ city, citylimit: true, extensions: 'all' })
    placeSearch.getDetails(poiId, (status, result) => {
      if (status === 'complete') {
        resolve(toPlainPoi(resultPois(result)[0]))
        return
      }
      if (status === 'no_data') {
        resolve(null)
        return
      }
      reject(new Error(result?.info || '高德地点详情暂不可用'))
    })
  })
  detailCache.set(cacheKey, request)
  request.catch(() => detailCache.delete(cacheKey))
  return request
}

function normalizedName(value) {
  return (value ?? '')
    .toLowerCase()
    .replace(/[（(][^）)]*[）)]/g, '')
    .replace(/[\s·•\-—_，,。.!！?？、]/g, '')
}

function nameScore(expectedName, actualName) {
  const expected = normalizedName(expectedName)
  const actual = normalizedName(actualName)
  if (!expected || !actual) return 0
  if (expected === actual) return 100
  if (expected.includes(actual) || actual.includes(expected)) {
    const ratio = Math.min(expected.length, actual.length) / Math.max(expected.length, actual.length)
    return 50 + ratio * 50
  }
  const overlap = [...new Set(expected)].filter((character) => actual.includes(character)).length
  return overlap / Math.max(new Set(expected).size, new Set(actual).size) * 70
}

export function selectReliablePoi(expectedName, candidates) {
  const ranked = candidates
    .map((poi) => ({ poi, score: nameScore(expectedName, poi.name) }))
    .sort((left, right) => right.score - left.score)
  return ranked[0]?.score >= 84 ? ranked[0].poi : null
}
