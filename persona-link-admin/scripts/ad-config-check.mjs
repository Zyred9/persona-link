import assert from 'node:assert/strict'
import { fileURLToPath } from 'node:url'
import { loadViewScriptSetup } from './vue-script-test-loader.mjs'

const path = fileURLToPath(new URL('../src/views/AdConfigView.vue', import.meta.url))
let admin = true
let confirmed = true
let failSave = false
let finishSave
let confirmations = 0
let writes = 0
let unmount
const initial = { enabled: false, adUnitId: '', failurePolicy: 1, updatedAt: null, updatedByName: null }
const dirty = { value: false }
const view = loadViewScriptSetup(path, '{ load, save, saved, form, notice, failed, saving }', {
  vue: { onBeforeUnmount: fn => { unmount = fn } },
  modules: {
    '../api': {
      getAdConfig: async () => ({ ...initial }),
      saveAdConfig: async payload => {
        writes++
        if (failSave) throw new Error('保存失败')
        if (finishSave) await new Promise(resolve => { finishSave = resolve })
        return { ...payload, updatedAt: '2026-09-06T12:00:00', updatedByName: '管理员' }
      },
    },
    '../auth': { isAdmin: () => admin },
    '../composables/useConfirm': { confirmAction: async () => { confirmations++; return confirmed } },
    '../composables/useUnsavedChanges': { useUnsavedChanges: () => ({ dirty, markDirty: () => { dirty.value = true }, markSaved: () => { dirty.value = false } }) },
  },
})
await view.load()
view.form.value.enabled = true
await view.save()
assert.equal(writes, 0, '缺少广告位不能开启')
assert.equal(view.saved.value.enabled, false, '表单修改不影响生效卡片')
view.form.value.adUnitId = ' adunit-123abc '
confirmed = false
await view.save()
assert.equal(writes, 0, '取消确认不能保存')
assert.equal(view.saving.value, false)
confirmed = true
failSave = true
await view.save()
assert.equal(view.saved.value.enabled, false, '保存失败不能修改生效状态')
assert.equal(view.form.value.enabled, true, '保存失败保留编辑')
assert.equal(view.failed.value, true)
failSave = false
await view.save()
assert.equal(view.saved.value.enabled, true)
assert.equal(view.saved.value.adUnitId, 'adunit-123abc')
assert.equal(view.saved.value.updatedByName, '管理员')
const confirmationsBefore = confirmations
view.form.value.failurePolicy = 2
await view.save()
assert.equal(confirmations, confirmationsBefore, '非开关修改不重复确认')
view.form.value.failurePolicy = 3
const writesBefore = writes
await view.save()
assert.equal(writes, writesBefore, '拒绝无效失败策略')
view.form.value.failurePolicy = 1
admin = false
await view.save()
assert.equal(writes, writesBefore, '非管理员不发送修改请求')
admin = true
finishSave = true
const pending = view.save()
await view.save()
assert.equal(writes, writesBefore + 1, '保存期间禁止重复提交')
unmount()
finishSave()
await pending
assert.equal(view.saved.value.failurePolicy, 2, '卸载后晚响应不回写')
console.log('ad config: OK')
