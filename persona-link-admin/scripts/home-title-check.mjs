import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { loadViewScriptSetup } from './vue-script-test-loader.mjs'

const path = fileURLToPath(new URL('../src/views/HomeConfigView.vue', import.meta.url))
let calls = 0
let finishSave
let rejectSave
let submitted
let savedMarks = 0
let readOnly = false
let uploadCalls = 0
let finishUpload
let rejectUpload
let navigationBusy
const options = {
  globals: { Error },
  modules: {
    '../auth': { isReadOnly: () => readOnly },
    '../api': {
      uploadImage: () => {
        uploadCalls += 1
        return new Promise((resolve, reject) => { finishUpload = resolve; rejectUpload = reject })
      },
      getTests: async () => ({ records: [], total: 0, size: 100 }),
      getMiniappHome: async () => ({ titleImageUrl: 'https://example.com/old.png', categories: [], allTests: [] }),
      saveHomeConfig: (value) => {
        calls += 1
        submitted = value
        return new Promise((resolve, reject) => { finishSave = resolve; rejectSave = reject })
      },
    },
    '../composables/useUnsavedChanges': { useUnsavedChanges: (_, busy) => {
      navigationBusy = busy
      return { markDirty() {}, markSaved() { savedMarks += 1 } }
    } },
  },
}
const exports = '{ load, loading, titleImageUrl, titleDirty, saving, savingId, preview, markTitleDirty, saveTitle, markRowDirty, markRowSaved, message, handleTitleUpload }'
const home = loadViewScriptSetup(path, exports, options)
await home.load()
assert.equal(home.titleImageUrl.value, 'https://example.com/old.png', '初始化应回填当前配置')
home.titleImageUrl.value = ' https://example.com/new.png '
home.markTitleDirty()
home.markRowDirty(1)
const savedBefore = savedMarks
const pending = home.saveTitle()
await home.saveTitle()
assert.equal(calls, 1, '保存中不能重复提交')
assert.equal(home.saving.value, true)
assert.equal(submitted, 'https://example.com/new.png')
finishSave({ titleImageUrl: submitted })
await pending
assert.equal(home.preview.value.titleImageUrl, submitted, '保存成功更新预览')
assert.equal(home.titleDirty.value, false)
assert.equal(savedMarks, savedBefore, '保存标题不能清除题型的未保存状态')
home.titleImageUrl.value = ''
home.markTitleDirty()
home.markRowSaved(1)
assert.equal(savedMarks, savedBefore, '保存题型不能清除标题的未保存状态')
const clearing = home.saveTitle()
assert.equal(submitted, '', '清空必须提交空字符串')
finishSave({ titleImageUrl: '' })
await clearing
assert.equal(home.preview.value.titleImageUrl, '')
assert.equal(savedMarks, savedBefore + 1)
home.titleImageUrl.value = 'bad-url'
home.markTitleDirty()
const failure = home.saveTitle()
rejectSave(new Error('图片地址不合法'))
await failure
assert.equal(home.titleDirty.value, true, '保存失败保留草稿')
assert.equal(home.titleImageUrl.value, 'bad-url')
assert.equal(home.message.value, '图片地址不合法')
assert.equal(home.saving.value, false)
const fileInput = { files: [{}], value: 'title.png' }
const uploadEvent = { target: fileInput }
const uploading = home.handleTitleUpload(uploadEvent)
assert.equal(fileInput.value, '', '选完立即清空文件输入，允许重选同一文件')
assert.equal(navigationBusy.value, true, '上传期间必须阻止离开')
const savesBeforeUpload = calls
await home.saveTitle()
await home.handleTitleUpload(uploadEvent)
assert.equal(calls, savesBeforeUpload, '上传期间不能保存')
assert.equal(uploadCalls, 1, '上传期间不能重复上传')
finishUpload('https://example.com/upload.png')
await uploading
assert.equal(home.titleImageUrl.value, 'https://example.com/upload.png')
assert.equal(home.titleDirty.value, true, '上传成功仅标记草稿，等待保存')
assert.equal(home.preview.value.titleImageUrl, '', '上传成功不能提前改变已保存预览')
assert.equal(home.saving.value, false)
fileInput.value = 'title.png'
const uploadFailure = home.handleTitleUpload(uploadEvent)
rejectUpload(new Error('图片上传失败'))
await uploadFailure
assert.equal(uploadCalls, 2, '同一文件可重选上传')
assert.equal(fileInput.value, '')
assert.equal(home.titleImageUrl.value, 'https://example.com/upload.png', '上传失败保留原草稿')
assert.equal(home.titleDirty.value, true)
assert.equal(home.message.value, '图片上传失败')
assert.equal(home.saving.value, false)
await home.handleTitleUpload({ target: { files: [], value: '' } })
assert.equal(uploadCalls, 2, '取消文件选择不发请求且保留草稿')
home.savingId.value = 1
await home.handleTitleUpload(uploadEvent)
home.savingId.value = null
home.loading.value = true
await home.handleTitleUpload(uploadEvent)
home.loading.value = false
assert.equal(uploadCalls, 2, '保存题型或初始化期间禁止上传')
const titleSaving = home.saveTitle()
await home.handleTitleUpload(uploadEvent)
assert.equal(uploadCalls, 2, '保存标题期间禁止上传')
finishSave({ titleImageUrl: submitted })
await titleSaving
const template = readFileSync(path, 'utf8')
for (const marker of ['ref="titleFileInput"', 'id="home-title-url"', 'type="button" :disabled="readOnly || loading || saving" @click="titleFileInput?.click()"']) {
  const element = template.slice(template.lastIndexOf('<', template.indexOf(marker)), template.indexOf('>', template.indexOf(marker)))
  assert.ok(element.includes(':disabled="readOnly || loading || saving"'), '上传和 URL 控件必须在只读及忙状态禁用')
}
readOnly = true
const locked = loadViewScriptSetup(path, exports, options)
locked.markTitleDirty()
const callsBefore = calls
await locked.saveTitle()
locked.loading.value = false
await locked.handleTitleUpload(uploadEvent)
assert.equal(uploadCalls, 2, '只读角色不能上传图片')
assert.equal(calls, callsBefore, '只读角色不能保存配置')
console.log('home title config: OK')
