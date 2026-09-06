import assert from 'node:assert/strict'
import { loadViewScriptSetup } from './vue-script-test-loader.mjs'

const pending = []
const test = loadViewScriptSetup(new URL('../src/views/ReleasesView.vue', import.meta.url),
  '{ openPreview, previewVersion, previewQuestions, previewLoading, notice }', {
    globals: { Error },
    modules: {
      '../api': {
        getVersion: (id) => new Promise((resolve, reject) => pending.push({ id, resolve, reject })),
        getQuestions: async (id) => [{ id }],
        getResultConfig: async () => ({ templates: [] }),
      },
      '../auth': { isReadOnly: () => false },
      '../composables/useUnsavedChanges': { useUnsavedChanges: () => {} },
    },
  })

const first = test.openPreview({ id: 1, estimatedMinutes: 5 })
assert.equal(pending[0].id, 1)
pending[0].resolve({ id: 1, estimatedMinutes: 12 })
await first
assert.equal(test.previewVersion.value.estimatedMinutes, 12, '预览必须读取最新保存的预计分钟')

const oldRequest = test.openPreview({ id: 1, estimatedMinutes: 5 })
const newRequest = test.openPreview({ id: 2, estimatedMinutes: 8 })
pending[2].resolve({ id: 2, estimatedMinutes: 20 })
await newRequest
pending[1].resolve({ id: 1, estimatedMinutes: 12 })
await oldRequest
assert.equal(test.previewVersion.value.id, 2, '旧预览响应不能覆盖当前版本')
assert.equal(test.previewQuestions.value[0].id, 2)

const closedRequest = test.openPreview({ id: 1, estimatedMinutes: 5 })
test.previewVersion.value = null
pending[3].resolve({ id: 1, estimatedMinutes: 12 })
await closedRequest
assert.equal(test.previewVersion.value, null, '关闭后不能被异步响应重新打开')

const failedRequest = test.openPreview({ id: 1, estimatedMinutes: 5 })
pending[4].reject(new Error('读取失败'))
await failedRequest
assert.equal(test.previewVersion.value, null, '读取失败不能保留旧数据冒充最新预览')
assert.equal(test.previewLoading.value, false)
assert.equal(test.notice.value, '读取失败')
console.log('Release preview checks passed.')
