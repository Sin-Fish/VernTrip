export const interestOptions = ['人文历史', '自然风景', '本地美食', '非遗体验', '城市漫步', '博物馆', '亲子友好', '摄影', '徒步', '海滨休闲']

export function localDate(date = new Date()) {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
}

export function fortunePlanError(input) {
  const preferenceError = fortuneProfileError(input.preferences)
  if (preferenceError) return preferenceError
  if (!/^\d{4}-\d{2}-\d{2}$/.test(input.date ?? '') || input.date < localDate()) return '请选择今天或之后的出发日期。'
  const days = Number(input.days)
  if (!Number.isInteger(days) || days < 1 || days > 7) return '出行天数应为 1–7 天。'
  const budget = Number(input.budget)
  if (!Number.isFinite(budget) || budget < days * 600 || budget > 200000) return `这次 ${days} 天旅行的总预算应在 ￥${days * 600}–￥200000 之间。`
  return ''
}

export function splitCities(value = '') {
  return [...new Set(value.split(/[,，、;；\n]+/).map((city) => city.trim()).filter(Boolean))]
}

export function fortuneProfileError(preferences) {
  if (!preferences.interests.length) return '请至少选择一项旅行兴趣。'
  if (preferences.interests.length > 12 || preferences.interests.some((item) => !item.trim() || item.length > 30)) return '最多选择 12 项兴趣，每项不超过 30 字。'
  if (preferences.visitedCities.length > 40 || preferences.visitedCities.some((city) => city.length > 40)) return '最多记录 40 座去过的城市，每项不超过 40 字。'
  return ''
}

export function buildFortunePlanRequest(input) {
  return {
    preferences: { interests: [...input.preferences.interests], visitedCities: [...input.preferences.visitedCities] },
    departure: input.departure?.trim() ?? '',
    date: input.date,
    days: Number(input.days),
    budget: Number(input.budget),
    travelers: input.travelers,
    pace: input.pace,
    exploreNew: input.exploreNew !== false,
    previousDestination: input.previousDestination || '',
  }
}
