import assert from 'node:assert/strict'
import test from 'node:test'
import { buildFortunePlanRequest, fortunePlanError, localDate, splitCities } from './fortunePlanning.js'

const input = { city: ' 苏州 ', date: '2099-04-20', days: '3', budget: '4200', travelers: '亲子出行', pace: '轻松一些', preferences: { interests: ['亲子友好', '自然风景'], visitedCities: ['杭州'] } }

test('fortune builds a complete itinerary request with selected constraints', () => {
  const request = buildFortunePlanRequest(input, 0)
  assert.equal(request.destination, undefined)
  assert.equal(request.city, undefined)
  assert.equal(request.days, 3)
  assert.equal(request.budget, 4200)
  assert.equal(request.date, input.date)
  assert.equal(request.travelers, input.travelers)
  assert.equal(request.pace, input.pace)
  assert.ok(request.preferences.interests.includes('亲子友好'))
  assert.deepEqual(request.preferences.visitedCities, ['杭州'])
  assert.equal(request.exploreNew, true)
})

test('city browsing cannot change the fortune request', () => {
  assert.deepEqual(buildFortunePlanRequest(input), buildFortunePlanRequest({ ...input, city: '北京' }))
  assert.equal(buildFortunePlanRequest({ ...input, exploreNew: false, previousDestination: '南京' }).previousDestination, '南京')
  assert.deepEqual(splitCities('杭州、上海,杭州； 北京\n南京'), ['杭州', '上海', '北京', '南京'])
})

test('invalid days, missing dates and insufficient budgets do not submit', () => {
  assert.equal(fortunePlanError(input), '')
  assert.notEqual(fortunePlanError({ ...input, preferences: { interests: [], visitedCities: [] } }), '')
  for (const days of ['', 0, 1.5, 8]) assert.notEqual(fortunePlanError({ ...input, days }), '')
  for (const budget of ['', 1799, 200001, 'invalid']) assert.notEqual(fortunePlanError({ ...input, budget }), '')
  assert.notEqual(fortunePlanError({ ...input, date: '' }), '')
  assert.notEqual(fortunePlanError({ ...input, date: '2000-01-01' }), '')
  assert.equal(localDate(new Date(2026, 8, 22)), '2026-09-22')
})
