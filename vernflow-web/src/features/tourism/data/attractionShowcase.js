export const discoveryViews = [
  { value: 'featured', label: '城市精选', index: '01' },
  { value: 'recommendation', label: 'AI 推荐', index: '02' },
  { value: 'ranking', label: '景点排行', index: '03' },
]

export const recommendationProfiles = [
  { value: 'culture', label: '文化深度', keywords: ['文化', '历史', '非遗', '博物馆', '古镇'] },
  { value: 'slow', label: '轻松慢游', keywords: ['漫步', '休闲', '园林', '自然', '湖', '街区'] },
  { value: 'family', label: '亲子友好', keywords: ['亲子', '手作', '体验', '室内', '研学'] },
]

export const rankingCategories = [
  { value: 'all', label: '综合' },
  { value: 'culture', label: '文化', keywords: ['文化', '历史', '非遗', '博物馆'] },
  { value: 'nature', label: '自然', keywords: ['自然', '园林', '湖', '山', '公园', '徒步'] },
  { value: 'family', label: '亲子', keywords: ['亲子', '手作', '研学', '室内'] },
  { value: 'food', label: '美食', keywords: ['餐饮', '美食', '小吃', '茶'] },
]

const cityDemoCatalog = {
  北京: [
    ['故宫博物院', '东城区', '文化场馆', ['人文历史', '建筑美学']],
    ['颐和园', '海淀区', '景点', ['皇家园林', '湖景']],
    ['天坛公园', '东城区', '景点', ['古建', '城市漫步']],
    ['什刹海', '西城区', '休闲', ['胡同', '夜游体验']],
    ['中国国家博物馆', '东城区', '文化场馆', ['人文历史', '室内']],
    ['慕田峪长城', '怀柔区', '景点', ['自然风景', '轻徒步']],
  ],
  上海: [
    ['外滩历史建筑群', '黄浦区', '景点', ['城市漫步', '建筑美学']],
    ['豫园', '黄浦区', '景点', ['园林', '人文历史']],
    ['上海博物馆', '黄浦区', '文化场馆', ['文化深度', '室内']],
    ['武康路历史文化街区', '徐汇区', '休闲', ['街区漫步', '建筑美学']],
    ['朱家角古镇', '青浦区', '景点', ['江南水乡', '古镇']],
    ['浦东美术馆', '浦东新区', '文化场馆', ['艺术展览', '城市景观']],
  ],
  西安: [
    ['秦始皇帝陵博物院', '临潼区', '文化场馆', ['人文历史', '考古']],
    ['西安城墙', '碑林区', '景点', ['古城', '城市漫步']],
    ['大雁塔文化休闲景区', '雁塔区', '景点', ['唐风', '夜游体验']],
    ['陕西历史博物馆', '雁塔区', '文化场馆', ['人文历史', '室内']],
    ['大唐不夜城', '雁塔区', '休闲', ['夜游体验', '文化街区']],
    ['华清宫', '临潼区', '景点', ['园林', '历史故事']],
  ],
  重庆: [
    ['洪崖洞民俗风貌区', '渝中区', '景点', ['夜游体验', '山城']],
    ['李子坝观景平台', '渝中区', '景点', ['城市景观', '轨道交通']],
    ['磁器口古镇', '沙坪坝区', '景点', ['古镇', '本地美食']],
    ['三峡博物馆', '渝中区', '文化场馆', ['人文历史', '室内']],
    ['南山一棵树观景台', '南岸区', '景点', ['城市夜景', '山景']],
    ['鹅岭二厂文创公园', '渝中区', '休闲', ['文创', '城市漫步']],
  ],
  广州: [
    ['陈家祠', '荔湾区', '文化场馆', ['岭南建筑', '非遗']],
    ['沙面岛', '荔湾区', '景点', ['建筑美学', '城市漫步']],
    ['广东省博物馆', '天河区', '文化场馆', ['文化深度', '室内']],
    ['广州塔', '海珠区', '景点', ['城市景观', '夜游体验']],
    ['永庆坊', '荔湾区', '休闲', ['老城街区', '非遗']],
    ['白云山', '白云区', '景点', ['自然风景', '轻徒步']],
  ],
  青岛: [
    ['八大关风景区', '市南区', '景点', ['建筑美学', '海滨漫步']],
    ['栈桥', '市南区', '景点', ['海景', '城市地标']],
    ['青岛啤酒博物馆', '市北区', '文化场馆', ['工业文化', '体验']],
    ['小鱼山公园', '市南区', '景点', ['城市景观', '轻徒步']],
    ['奥帆中心', '市南区', '休闲', ['海滨', '夜游体验']],
    ['崂山风景区', '崂山区', '景点', ['自然风景', '山海']],
  ],
  长沙: [
    ['岳麓山', '岳麓区', '景点', ['自然风景', '人文历史']],
    ['湖南博物院', '开福区', '文化场馆', ['人文历史', '室内']],
    ['橘子洲', '岳麓区', '景点', ['江景', '城市漫步']],
    ['太平老街', '天心区', '休闲', ['老城街区', '本地美食']],
    ['杜甫江阁', '天心区', '景点', ['夜游体验', '古建']],
    ['谢子龙影像艺术馆', '岳麓区', '文化场馆', ['艺术展览', '建筑美学']],
  ],
  武汉: [
    ['湖北省博物馆', '武昌区', '文化场馆', ['人文历史', '室内']],
    ['黄鹤楼', '武昌区', '景点', ['古建', '城市景观']],
    ['东湖生态旅游风景区', '武昌区', '景点', ['湖景', '骑行']],
    ['江汉路步行街', '江汉区', '休闲', ['城市漫步', '建筑美学']],
    ['昙华林', '武昌区', '休闲', ['文创', '老城街区']],
    ['武汉美术馆琴台馆', '汉阳区', '文化场馆', ['艺术展览', '建筑美学']],
  ],
  昆明: [
    ['云南省博物馆', '官渡区', '文化场馆', ['民族文化', '室内']],
    ['滇池海埂公园', '西山区', '景点', ['湖景', '自然风景']],
    ['翠湖公园', '五华区', '景点', ['城市漫步', '生态']],
    ['云南民族村', '西山区', '景点', ['民族文化', '体验']],
    ['昆明老街', '五华区', '休闲', ['老城街区', '本地美食']],
    ['西山风景区', '西山区', '景点', ['轻徒步', '城市景观']],
  ],
  大理: [
    ['大理古城', '大理市', '景点', ['古城', '城市漫步']],
    ['洱海生态廊道', '大理市', '景点', ['湖景', '骑行']],
    ['崇圣寺三塔', '大理市', '景点', ['人文历史', '古建']],
    ['喜洲古镇', '大理市', '景点', ['白族文化', '古镇']],
    ['苍山感通索道', '大理市', '景点', ['自然风景', '山景']],
    ['大理非遗博物馆', '大理市', '文化场馆', ['非遗', '室内']],
  ],
  泉州: [
    ['开元寺', '鲤城区', '景点', ['古建', '世界遗产']],
    ['泉州海外交通史博物馆', '丰泽区', '文化场馆', ['海丝文化', '室内']],
    ['西街历史文化街区', '鲤城区', '休闲', ['老城街区', '本地美食']],
    ['蟳埔村', '丰泽区', '景点', ['民俗', '非遗']],
    ['清净寺', '鲤城区', '景点', ['世界遗产', '建筑美学']],
    ['洛阳桥', '洛江区', '景点', ['古桥', '人文历史']],
  ],
  洛阳: [
    ['龙门石窟', '洛龙区', '景点', ['世界遗产', '石窟艺术']],
    ['洛阳博物馆', '洛龙区', '文化场馆', ['人文历史', '室内']],
    ['隋唐洛阳城国家遗址公园', '老城区', '景点', ['唐风', '夜游体验']],
    ['白马寺', '瀍河区', '景点', ['古建', '人文历史']],
    ['洛邑古城', '老城区', '休闲', ['古城', '文化街区']],
    ['老君山景区', '栾川县', '景点', ['自然风景', '山景']],
  ],
}

export function buildCityDemoResources(city) {
  return (cityDemoCatalog[city] ?? []).map(([name, area, type, tags], index) => ({
    id: `city-demo-${city}-${index + 1}`,
    city,
    name,
    placeName: name,
    area,
    address: `${city}${area}`,
    type,
    tags,
    note: `从${name}认识${city}的${tags.slice(0, 2).join('与')}，适合加入城市精选行程。`,
    openHours: '开放时间以现场为准',
    priceMin: 0,
    priceMax: 0,
    reservationRequired: false,
    availabilityStatus: 'AVAILABLE',
    locationStatus: 'DEMO',
    source: '城市展示样例',
  }))
}

function searchableText(resource) {
  return [resource?.name, resource?.type, resource?.note, ...(resource?.tags ?? [])].filter(Boolean).join(' ')
}

export function matchesKeywords(resource, keywords = []) {
  const text = searchableText(resource)
  return keywords.some((keyword) => text.includes(keyword))
}

export function recommendationScore(resource, profile, index) {
  const tagMatches = (profile?.keywords ?? []).filter((keyword) => searchableText(resource).includes(keyword)).length
  const verifiedBonus = resource?.locationStatus === 'VERIFIED' ? 3 : 0
  const availabilityBonus = resource?.availabilityStatus === 'SOLD_OUT' ? -5 : 2
  return Math.min(98, 82 + tagMatches * 4 + verifiedBonus + availabilityBonus - (index % 3))
}

export function recommendationReason(resource, profile) {
  const matched = (profile?.keywords ?? []).filter((keyword) => searchableText(resource).includes(keyword)).slice(0, 2)
  if (matched.length) return `匹配${matched.join('、')}偏好`
  if (resource?.area) return `适合安排在${resource.area}行程中`
  return '城市体验与出行节奏匹配'
}

export function rankingScore(resource, index) {
  const verifiedBonus = resource?.locationStatus === 'VERIFIED' ? 2 : 0
  const tagBonus = Math.min(3, resource?.tags?.length ?? 0)
  return Math.min(99, Math.max(78, 96 - index * 2 + verifiedBonus + tagBonus))
}

export function buildGuideScenes(resource, city) {
  const tags = (resource?.tags ?? []).slice(0, 2)
  const place = resource?.placeName || resource?.name
  return [
    {
      kicker: `${city} · ${resource?.area || '城市精选'}`,
      title: resource?.name,
      narration: resource?.note || `从${place}开始，读懂这座城市的风景与日常。`,
    },
    {
      kicker: 'AI GUIDE · 看点',
      title: tags.length ? tags.join(' · ') : resource?.type || '在地体验',
      narration: `这里适合放慢脚步，留出时间感受${tags.join('与') || '当地文化和空间细节'}。`,
    },
    {
      kicker: 'BEFORE YOU GO · 出发前',
      title: resource?.openHours || '开放时间以现场为准',
      narration: resource?.reservationRequired ? '建议提前确认预约与余量，再把它加入当天路线。' : '可根据附近路线灵活安排到行程中。',
    },
  ]
}
