import assert from 'node:assert/strict'
import { loadViewScriptSetup } from './vue-script-test-loader.mjs'

const deferred = () => { let resolve, reject; const promise = new Promise((yes, no) => { resolve = yes; reject = no }); return { promise, resolve, reject } }
const flush = async () => { for (let i = 0; i < 8; i++) await Promise.resolve() }
function setup() {
  const details = new Map(), versions = new Map()
  let unmount, writes = 0, confirmation = () => true
  const api = {
    getTest: (id) => { const task = deferred(); details.set(id, task); return task.promise },
    getTestVersions: (id) => { const task = deferred(); versions.set(id, task); return task.promise },
    saveTest: async () => { writes++; return { id: 1 } }, uploadImage: async () => { writes++; return 'image' },
  }
  const view = loadViewScriptSetup(new URL('../src/views/TypeManagementView.vue', import.meta.url),
    '{openEdit,openCreate,closeDrawer,saveItem,handleImageUpload,form,editingId,editingVersion,editorLoading,editorError,drawerOpen}', {
      vue: { onBeforeUnmount: (callback) => { unmount = callback } },
      globals: { Error },
      modules: {
        '../api': api, '../auth': { isReadOnly: () => false },
        '../composables/useUnsavedChanges': { useUnsavedChanges: () => ({ markDirty() {}, markSaved() {}, confirmDiscard: () => confirmation() }) },
        '../composables/useConfirm': { confirmAction: async () => true },
      },
    })
  const resolve = (id) => {
    details.get(id).resolve({ testName: `题型${id}`, testType: 1, status: 1, categoryId: 1 })
    versions.get(id).resolve([{ id: id * 10, versionStatus: 1, dimensions: [], coverUrl: `cover${id}` }])
  }
  return { view, api, details, resolve, unmount: () => unmount(), writes: () => writes, confirm: (callback) => { confirmation = callback } }
}

{
  const s = setup(), v = s.view
  const a = v.openEdit({ id: 1 }); await flush()
  assert.equal(v.drawerOpen.value, true)
  assert.equal(v.editorLoading.value, true)
  await v.saveItem(); await v.handleImageUpload({ target: { files: [{}] } }, 'coverUrl')
  assert.equal(s.writes(), 0)
  const b = v.openEdit({ id: 2 }); await flush()
  s.resolve(2); await b
  s.resolve(1); await a
  assert.equal(v.form.name, '题型2')
  assert.equal(v.editingVersion.value.id, 20)
  const c = v.openEdit({ id: 3 }); await flush()
  assert.equal(v.editingVersion.value, null)
  assert.equal(v.form.coverUrl, '')
  await v.openCreate(); s.details.get(3).reject(new Error('旧失败')); await c
  assert.equal(v.editingId.value, null)
  assert.equal(v.editorError.value, '')
}
for (const action of ['closeDrawer', 'openCreate', 'unmount']) {
  const s = setup(), v = s.view
  const pending = v.openEdit({ id: 1 }); await flush()
  if (action === 'unmount') s.unmount(); else await v[action]()
  s.resolve(1); await pending
  assert.equal(v.form.name, '')
  if (action === 'closeDrawer') assert.equal(v.drawerOpen.value, false)
}
{
  const s = setup(), v = s.view
  const pending = v.openEdit({ id: 1 }); await flush()
  s.details.get(1).reject(new Error('当前失败')); await pending
  assert.equal(v.editorLoading.value, false)
  assert.equal(v.editorError.value, '当前失败')
  await v.saveItem(); await v.handleImageUpload({ target: { files: [{}] } }, 'coverUrl')
  assert.equal(s.writes(), 0)
}
for (const action of ['closeDrawer', 'openCreate', 'openEdit']) {
  const s = setup(), v = s.view
  await v.openCreate()
  const decision = deferred(); s.confirm(() => decision.promise)
  const old = v[action]({ id: 1 }); await flush()
  s.confirm(() => true)
  const latest = v.openEdit({ id: 2 }); await flush(); s.resolve(2); await latest
  decision.resolve(true); await old
  assert.equal(v.editingId.value, 2)
  assert.equal(v.form.name, '题型2')
  assert.equal(v.drawerOpen.value, true)
}
{
  const s = setup(), v = s.view
  await v.openCreate()
  const uploaded = deferred(); s.api.uploadImage = () => uploaded.promise
  const pending = v.handleImageUpload({ target: { files: [{}], value: 'picked' } }, 'coverUrl')
  await v.openEdit({ id: 2 }); await v.closeDrawer(); await v.openCreate()
  assert.equal(s.details.size, 0, '上传期间不得切换编辑对象')
  assert.equal(v.drawerOpen.value, true)
  s.unmount(); uploaded.resolve('late-image'); await pending
  assert.equal(v.form.coverUrl, '', '卸载后上传结果不得写回')
}
{
  const s = setup(), v = s.view
  await v.openCreate()
  Object.assign(v.form, { name: 'valid', categoryId: 1, coverUrl: 'cover', estimatedMinutes: 1, drawQuestionCount: 1, dimensions: [{ dimensionName: 'valid' }] })
  const saved = deferred(); s.api.saveTest = () => saved.promise
  s.api.saveVersion = () => assert.fail('卸载后不得继续保存版本')
  const pending = v.saveItem()
  await v.openEdit({ id: 2 }); await v.closeDrawer(); await v.openCreate()
  assert.equal(s.details.size, 0, '保存期间不得切换编辑对象')
  assert.equal(v.drawerOpen.value, true)
  s.unmount(); saved.resolve({ id: 88 }); await pending
  assert.equal(v.editingId.value, null, '卸载后保存结果不得写回')
}
console.log('type editor race: OK')
