import assert from 'node:assert/strict'
import { fileURLToPath } from 'node:url'
import { loadViewScriptSetup } from './vue-script-test-loader.mjs'

let allowed = true
let discard = true
let confirmDelete = true
let saveCalls = 0
let deleteCalls = 0
let failSave = false
let finishSave
let lastPayload
const item = { id: 7, configKey: 'miniapp.version', configName: '版本号', configValue: '1.0.0', valueType: 1, remark: '' }
const view = loadViewScriptSetup(fileURLToPath(new URL('../src/views/AppConfigView.vue', import.meta.url)), '{ load, records, page, openEditor, closeEditor, save, form, showEditor, busy, selected, removeSelected, notice }', {
  globals: { Error },
  modules: {
    '../auth': { isAdmin: () => allowed },
    '../api': {
      getAppConfigs: async () => ({ records: [item], total: 1 }),
      getAppConfig: async () => item,
      saveAppConfig: async (...args) => { saveCalls++; lastPayload = args; if (failSave) throw new Error('保存失败'); await new Promise(resolve => { finishSave = resolve }) },
      deleteAppConfigs: async () => { deleteCalls++ },
    },
    '../composables/useConfirm': { confirmAction: async () => confirmDelete },
    '../composables/useUnsavedChanges': { useUnsavedChanges: (_, busy) => ({ markDirty() {}, markSaved() {}, confirmDiscard: async () => discard && !busy.value }) },
  },
})
await view.load()
assert.equal(view.records.value[0].id, 7)
await view.openEditor(7)
assert.equal(view.form.value.configValue, '1.0.0')
view.form.value.configValue = '2.0.0'
discard = false
await view.closeEditor()
assert.equal(view.showEditor.value, true, '拒绝放弃必须保留编辑器')
failSave = true
await view.save()
assert.equal(view.form.value.configValue, '2.0.0', '失败保留草稿')
assert.equal(view.showEditor.value, true)
assert.equal(view.notice.value, '保存失败')
failSave = false
const saving = view.save()
await view.save()
assert.equal(saveCalls, 2, '保存中禁止重复请求')
assert.equal(lastPayload[1], 7)
assert.equal(lastPayload[0].configKey, 'miniapp.version')
assert.equal(lastPayload[0].configValue, '2.0.0')
finishSave()
await saving
assert.equal(view.showEditor.value, false)
view.selected.value = [7]
confirmDelete = false
await view.removeSelected()
assert.equal(deleteCalls, 0, '取消确认不能删除')
confirmDelete = true
await view.removeSelected()
assert.equal(deleteCalls, 1)
assert.equal(view.selected.value.length, 0)
allowed = false
discard = true
await view.openEditor()
view.selected.value = [7]
await view.removeSelected()
assert.equal(view.showEditor.value, false)
assert.equal(deleteCalls, 1, '非管理员不能写入')
console.log('app config check: OK')
