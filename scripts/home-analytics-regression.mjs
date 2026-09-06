import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const projectRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..')
const readSource = (path) => readFileSync(resolve(projectRoot, path), 'utf8')

const homeSource = readSource('persona-link-admin/src/views/HomeConfigView.vue')
const dashboardSource = readSource('persona-link-admin/src/views/DashboardView.vue')
const analyticsSource = readSource('persona-link-admin/src/views/AnalyticsView.vue')

assert.match(homeSource, /dirtyIds\.value\.has\(item\.id\).*localById\.get\(item\.id\)/s)
assert.match(homeSource, /Promise\.all\(\[fetchTests\(\), getMiniappHome\(\)\]\)/)
assert.match(homeSource, /:disabled="readOnly \|\| savingId === item\.id"/)

const localRows = [
  { id: 1, homeDisplay: 1, homeSort: 10 },
  { id: 2, homeDisplay: 1, homeSort: 20 },
  { id: 3, homeDisplay: 2, homeSort: 99 },
]
const serverRows = [
  { id: 1, homeDisplay: 0, homeSort: 10 },
  { id: 2, homeDisplay: 1, homeSort: 20 },
  { id: 3, homeDisplay: 2, homeSort: 30 },
]
const dirtyIds = new Set([3])
const localById = new Map(localRows.map((item) => [item.id, item]))
const mergedRows = serverRows.map((item) => dirtyIds.has(item.id) ? localById.get(item.id) ?? item : item)
assert.equal(mergedRows.find((item) => item.id === 1)?.homeDisplay, 0, '应同步服务端清除的旧焦点')
assert.equal(mergedRows.find((item) => item.id === 3)?.homeSort, 99, '不得覆盖其他脏行')

function niceCeiling(value) {
  if (value <= 0) return 1
  const magnitude = 10 ** Math.floor(Math.log10(value))
  const normalized = value / magnitude
  const ceiling = normalized <= 1 ? 1 : normalized <= 2 ? 2 : normalized <= 5 ? 5 : 10
  return ceiling * magnitude
}

const rows = [{ pv: 1_248, startedCount: 386, completedCount: 241 }]
const maximum = niceCeiling(Math.max(...rows.flatMap((item) => [item.pv, item.startedCount, item.completedCount])))
assert.equal(maximum, 2_000)
assert.equal(Math.round(180 - rows[0].startedCount / maximum * 150), 151)

for (const [name, source, scaleName] of [
  ['Dashboard', dashboardSource, 'chartMaximum'],
  ['Analytics', analyticsSource, 'trendMaximum'],
]) {
  assert.match(source, new RegExp(`item\\.pv,[\\s\\S]*item\\.startedCount,[\\s\\S]*item\\.completedCount`), `${name} 应以三组真实数据计算共同纵轴`)
  assert.match(source, new RegExp(`item\\[field\\] \\/ ${scaleName}\\.value`), `${name} 每条线应使用共同纵轴`)
  assert.doesNotMatch(source, />4k<|>3k<|>2k<|>1k</, `${name} 不应保留固定假刻度`)
}

console.log('home-analytics regression: OK')
