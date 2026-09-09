import assert from 'node:assert/strict'
import { fileURLToPath } from 'node:url'
import { loadViewScriptSetup } from './vue-script-test-loader.mjs'

let unmount
let confirmed = true
let discard = true
let rejectSave = false
let writes = 0
let finishSave
const dirty = { value: false }
const initial = [1, 2, 3].map(type => ({ type, title: `协议${type}`, content: '', version: 0 }))
const legal = loadViewScriptSetup(fileURLToPath(new URL('../src/views/LegalDocumentsView.vue', import.meta.url)), '{ load, select, save, form, documents, selectedType, saving, failed }', {
  vue: { onBeforeUnmount: fn => { unmount = fn } },
  modules: {
    '../api': { getLegalDocuments: async () => initial, saveLegalDocument: async (type, payload) => {
      writes++
      if (rejectSave) throw new Error('失败')
      if (finishSave) await new Promise(resolve => { finishSave = resolve })
      return { type, ...payload, version: 1 }
    } },
    '../auth': { isAdmin: () => true },
    '../composables/useConfirm': { confirmAction: async () => confirmed },
    '../composables/useUnsavedChanges': { useUnsavedChanges: () => ({ dirty, markDirty: () => { dirty.value = true }, markSaved: () => { dirty.value = false }, confirmDiscard: async () => discard }) },
  },
})
await legal.load()
legal.form.value.content = '   '
await legal.save()
assert.equal(writes, 0, '空白正文不能发布')
legal.form.value.content = ' 正文 '
confirmed = false
await legal.save()
assert.equal(writes, 0, '取消不发布')
discard = false
await legal.select(2)
assert.equal(legal.selectedType.value, 1, '取消放弃编辑不得切换')
confirmed = true
rejectSave = true
await legal.save()
assert.equal(legal.form.value.content, ' 正文 ', '保存失败保留编辑')
assert.equal(legal.documents.value[0].version, 0)
rejectSave = false
await legal.save()
assert.equal(legal.documents.value[0].content, '正文')
assert.equal(legal.documents.value[0].version, 1)
finishSave = true
const before = writes
const pending = legal.save()
await Promise.resolve()
await legal.save()
await legal.select(2)
assert.equal(writes, before + 1, '保存锁阻止重复提交')
assert.equal(legal.selectedType.value, 1, '保存时禁止切换')
unmount()
finishSave()
await pending

let finishLoad
let calls = 0
const feedback = loadViewScriptSetup(fileURLToPath(new URL('../src/views/FeedbackView.vue', import.meta.url)), '{ load, page, size, records }', {
  vue: { onBeforeUnmount: fn => { unmount = fn } },
  modules: { '../api': { getFeedbacks: async (page, size) => {
    calls++
    if (finishLoad) await new Promise(resolve => { finishLoad = resolve })
    if (page === 3) throw new Error('失败')
    return { records: [{ id: '1', openId: 'test-user-openid', content: '反馈' }], page, size, total: 50 }
  } } },
})
await feedback.load(2, 10)
assert.equal(feedback.page.value, 2)
assert.equal(feedback.size.value, 10)
assert.equal(feedback.records.value[0].openId, 'test-user-openid', '列表保留提交用户标识')
await feedback.load(3, 20)
assert.equal(feedback.page.value, 2, '失败不改变已显示数据的页码')
assert.equal(feedback.size.value, 10)
finishLoad = true
const late = feedback.load(1, 50)
await feedback.load(1, 100)
assert.equal(calls, 3, '加载锁阻止并发')
unmount()
finishLoad()
await late
assert.equal(feedback.size.value, 10, '卸载后不回写')
console.log('account support: OK')
