import assert from 'node:assert/strict'
import { fileURLToPath } from 'node:url'
import { loadViewScriptSetup } from './vue-script-test-loader.mjs'

const requests = []
let unmountAnalytics
const analytics = loadViewScriptSetup(fileURLToPath(new URL('../src/views/AnalyticsView.vue', import.meta.url)), '{ load, range, overview }', {
  vue: { onBeforeUnmount: callback => { unmountAnalytics = callback } },
  modules: {
    '../api': { getAnalytics: days => new Promise(resolve => requests.push({ days, resolve })) },
    '../utils/chartScale': { niceCeiling: value => value },
  },
})
analytics.range.value = '30'
const oldRange = analytics.load()
analytics.range.value = '1'
const currentRange = analytics.load()
requests[1].resolve({ days: 1 })
await currentRange
requests[0].resolve({ days: 30 })
await oldRange
assert.equal(analytics.overview.value.days, 1, '旧周期响应不得覆盖当前选择')
const closingRange = analytics.load()
unmountAnalytics()
requests[2].resolve({ days: 7 })
await closingRange
assert.equal(analytics.overview.value.days, 1, '卸载后不得写入统计状态')

let finishOldPoll
let finishQuestions
let finishResults
let unmountAi
let savedCount = 0
const ai = loadViewScriptSetup(fileURLToPath(new URL('../src/views/AiQuestionBankView.vue', import.meta.url)), '{ pollTask, restoreTask, loadReview, markQuestionDirty, task, form, questions, dirtyQuestionIds }', {
  vue: { onUnmounted: callback => { unmountAi = callback } },
  modules: {
    '../api': {
      getAiGenerationTask: id => id === 1
        ? new Promise(resolve => { finishOldPoll = resolve })
        : Promise.resolve({ id: 2, versionId: 22, testName: '任务 B', taskStatus: 5 }),
      getQuestions: () => new Promise(resolve => { finishQuestions = resolve }),
      getResultConfig: () => new Promise(resolve => { finishResults = resolve }),
    },
    '../auth': { isReadOnly: () => false },
    '../composables/useUnsavedChanges': { useUnsavedChanges: () => ({ markDirty() {}, markSaved() { savedCount++ }, confirmDiscard: async () => true }) },
    '../composables/useConfirm': { confirmAction: async () => true },
  },
  globals: { sessionStorage: { getItem: () => 'request', removeItem() {} }, window: { setTimeout() {}, clearTimeout() {} } },
})
ai.task.value = { id: 1, versionId: 11 }
const oldPoll = ai.pollTask()
await ai.restoreTask(2)
finishOldPoll({ id: 1, versionId: 11, testName: '任务 A', taskStatus: 2 })
await oldPoll
assert.equal(ai.task.value.id, 2, '旧轮询不得将任务切回 A')
assert.equal(ai.form.name, '任务 B')

const review = ai.loadReview()
ai.questions.push({ id: 99, text: '用户刚修改' })
ai.dirtyQuestionIds.add(99)
const priorSaves = savedCount
finishQuestions([])
finishResults({ dimensions: [], templates: [] })
assert.equal(await review, false)
assert.equal(ai.questions[0].text, '用户刚修改', '晚到审核响应不得清空用户修改')
assert.equal(ai.dirtyQuestionIds.has(99), true)
assert.equal(savedCount, priorSaves, '晚到审核响应不得把未保存状态标为已保存')

ai.dirtyQuestionIds.clear()
const savedDuringRead = ai.loadReview()
ai.markQuestionDirty()
ai.questions[0].text = '修改已经保存'
ai.dirtyQuestionIds.clear()
finishQuestions([])
finishResults({ dimensions: [], templates: [] })
assert.equal(await savedDuringRead, false)
assert.equal(ai.questions[0].text, '修改已经保存', '修改后即使已保存清除 dirty，旧读也不得覆盖')

const closedReview = ai.loadReview()
unmountAi()
finishQuestions([])
finishResults({ dimensions: [], templates: [] })
assert.equal(await closedReview, false)
assert.equal(ai.questions[0].id, 99, '卸载后审核响应不应修改数组')
console.log('audit race checks: analytics latest request, AI task switch, dirty edits and unmount: OK')
