import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { nextTick, ref, vModelSelect, watch } from 'vue'
import { loadTypeScriptModule, loadViewScriptSetup } from './vue-script-test-loader.mjs'

const homePath = fileURLToPath(new URL('../src/views/HomeConfigView.vue', import.meta.url))
const dashboardPath = fileURLToPath(new URL('../src/views/DashboardView.vue', import.meta.url))
const analyticsPath = fileURLToPath(new URL('../src/views/AnalyticsView.vue', import.meta.url))
const chartScalePath = fileURLToPath(new URL('../src/utils/chartScale.ts', import.meta.url))
const { niceCeiling } = loadTypeScriptModule(chartScalePath)

// 使用真实 Vue select 指令，覆盖浏览器 input 与 change 之间发生响应式刷新的情况。
const selectMarkup = readFileSync(homePath, 'utf8').match(/<select\b[^>]*v-model.number="item.homeDisplay"[^>]*>/)[0]
const dirtyEvent = selectMarkup.match(/@(input|change)="markRowDirty\(item.id\)"/)[1]
const selection = loadViewScriptSetup(homePath, '{ tests, dirtyIds, markRowDirty }', {
  vue: { ref },
  modules: {
    '../api': {},
    '../auth': { isReadOnly: () => false },
    '../composables/useUnsavedChanges': {
      useUnsavedChanges: () => ({ markDirty: () => {}, markSaved: () => {} }),
    },
  },
})
selection.tests.value = [{ id: 1, homeDisplay: 0 }]
const row = selection.tests.value[0]
const select = new EventTarget()
select.multiple = false
select.options = ['普通列表', '焦点位', '推荐位'].map((text, value) => ({ value: String(value), text, selected: false }))
Object.defineProperty(select, 'selectedIndex', {
  get: () => select.options.findIndex((option) => option.selected),
  set: (index) => select.options.forEach((option, current) => { option.selected = current === index }),
})
const binding = () => ({ value: row.homeDisplay, modifiers: { number: true } })
const vnode = { props: { 'onUpdate:modelValue': (value) => { row.homeDisplay = value } } }
vModelSelect.created(select, binding(), vnode)
select.addEventListener(dirtyEvent, () => selection.markRowDirty(row.id))
vModelSelect.mounted(select, binding())
const stopSelectionWatch = watch(() => [row.homeDisplay, selection.dirtyIds.value], () => {
  vModelSelect.beforeUpdate(select, binding(), vnode)
  vModelSelect.updated(select, binding())
})
for (const value of [1, 2, 0]) {
  select.selectedIndex = value
  select.dispatchEvent(new Event('input'))
  await nextTick()
  select.dispatchEvent(new Event('change'))
  await nextTick()
  assert.equal(row.homeDisplay, value, '选择后模型必须保留新首页位置')
  assert.equal(select.selectedIndex, value, '选择后下拉菜单必须显示新位置名称')
  assert.ok(selection.dirtyIds.value.has(row.id), '选择后必须标记本行未保存')
}
stopSelectionWatch()

let updateArguments
let updateCalls = 0
let resolveUpdate
const updateGate = new Promise((resolve) => { resolveUpdate = resolve })
let dirtyMarks = 0
let savedMarks = 0
const latestRows = [
  { id: 1, testName: '旧焦点', homeDisplay: 0, homeSort: 90 },
  { id: 2, testName: '新焦点', homeDisplay: 1, homeSort: 80 },
  { id: 3, testName: '未保存推荐', homeDisplay: 2, homeSort: 70 },
]
const miniappHome = { focusTest: latestRows[1], recommendedTests: [], categories: [], allTests: latestRows }
const home = loadViewScriptSetup(homePath, '{ save, tests, dirtyIds, preview, savingId, markRowDirty }', {
  modules: {
    '../api': {
      assetUrl: (value) => value,
      getMiniappHome: async () => miniappHome,
      getTests: async () => ({ records: latestRows, total: latestRows.length, size: 100 }),
      updateTestHomeDisplay: async (...args) => {
        updateCalls += 1
        updateArguments = args
        await updateGate
      },
    },
    '../auth': { isReadOnly: () => false },
    '../composables/useUnsavedChanges': {
      useUnsavedChanges: () => ({
        markDirty: () => { dirtyMarks += 1 },
        markSaved: () => { savedMarks += 1 },
      }),
    },
  },
})

home.tests.value = [
  { id: 1, testName: '旧焦点', homeDisplay: 1, homeSort: 90 },
  { id: 2, testName: '新焦点', homeDisplay: 1, homeSort: 80 },
  { id: 3, testName: '未保存推荐', homeDisplay: 2, homeSort: 70 },
]
home.markRowDirty(2)
const savePromise = home.save(home.tests.value[1])
const duplicateSavePromise = home.save(home.tests.value[1])
await Promise.resolve()
assert.equal(home.savingId.value, 2, 'A 请求未完成时必须保持当前行锁定')
assert.equal(updateCalls, 1, '快速二次保存只能发起一次更新请求')
home.tests.value[2].homeSort = 777
home.markRowDirty(3)
resolveUpdate()
await Promise.all([savePromise, duplicateSavePromise])

assert.deepEqual(updateArguments, [2, 1, 80], '必须提交 A 行保存时的真实值')
assert.equal(home.tests.value.find((item) => item.id === 1).homeDisplay, 0, 'A 保存后必须同步服务端清除的旧焦点')
assert.equal(home.tests.value.find((item) => item.id === 2).homeDisplay, 1, 'A 保存后必须采用服务端最新值')
assert.equal(home.tests.value.find((item) => item.id === 3).homeSort, 777, 'A 保存后的刷新不能覆盖 B 行未保存值')
assert.deepEqual([...home.dirtyIds.value], [3], 'A 保存后只能清除 A 的脏标记')
assert.equal(home.preview.value, miniappHome, '保存后必须刷新真实小程序预览')
assert.equal(home.savingId.value, null, '保存结束后必须解除当前行锁定')
assert.equal(dirtyMarks, 2, '两行编辑必须分别记录脏状态')
assert.equal(savedMarks, 0, '仍有 B 行未保存时不能把整页标记为已保存')

assert.equal(niceCeiling(0), 1, '空数据比例尺必须保持可计算')
assert.equal(niceCeiling(201), 500, '生产比例尺必须按 1/2/5 档向上取整')
assert.equal(niceCeiling(843), 1000, '生产比例尺必须覆盖真实最大值')

const chartModules = {
  '../api': { getDashboard: async () => null, getHealth: async () => 'UP', getAnalytics: async () => null },
  '../utils/chartScale': { niceCeiling },
}
const dashboard = loadViewScriptSetup(dashboardPath, '{ overview, chartMaximum, chartPoints }', { modules: chartModules })
const analytics = loadViewScriptSetup(analyticsPath, '{ overview, trendMaximum, trendPoints }', { modules: chartModules })
const overview = {
  trend: [
    { statDate: '2026-09-04', pv: 843, startedCount: 510, completedCount: 267 },
    { statDate: '2026-09-05', pv: 500, startedCount: 250, completedCount: 100 },
  ],
}

dashboard.overview.value = overview
analytics.overview.value = overview
assert.equal(dashboard.chartMaximum.value, 1000, 'Dashboard computed 必须调用生产比例尺')
assert.equal(analytics.trendMaximum.value, 1000, 'Analytics computed 必须调用生产比例尺')
assert.equal(dashboard.chartPoints('pv'), '0,54 700,105', 'Dashboard 折线必须按同一真实上限缩放')
assert.equal(analytics.trendPoints('completedCount'), '0,140 700,165', 'Analytics 折线必须按同一真实上限缩放')

console.log('home-analytics regression: OK')
