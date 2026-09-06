import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { compileScriptSetup, loadTypeScriptModule } from './vue-script-test-loader.mjs'

const source = (path) => readFileSync(new URL(`../${path}`, import.meta.url), 'utf8')
const questionEditor = source('src/views/QuestionEditorView.vue')
const typeManagement = source('src/views/TypeManagementView.vue')
const resultRules = source('src/views/ResultRulesView.vue')
const unsavedChanges = source('src/composables/useUnsavedChanges.ts')

const guardModule = {
  useUnsavedChanges: () => ({ markDirty: () => {}, markSaved: () => {}, confirmDiscard: async () => true }),
}
const routerModule = {
  useRoute: () => ({ params: { id: '1' }, query: {} }),
  useRouter: () => ({ push: () => {}, replace: () => {} }),
}
const commonModules = (api) => ({
  '../api': api,
  '../auth': { isReadOnly: () => false },
  '../composables/useUnsavedChanges': guardModule,
  '../composables/useConfirm': { confirmAction: async () => true },
  'vue-router': routerModule,
})

assert.match(questionEditor, /sortNo:\s*options\.value\.length\s*-\s*index/,
  '题目选项必须按列表长度倒序提交 sortNo')
assert.match(questionEditor, /selectQuestion[\s\S]*confirmDiscard\([\s\S]*showQuestion\(index\)/,
  '切换题目前必须支持取消未保存内容')

const coverValidation = typeManagement.indexOf('if (!form.coverUrl) return showFeedback(')
const saveTestCall = typeManagement.indexOf('const saved = await saveTest(')
const saveVersionCall = typeManagement.indexOf('await saveVersion(')
assert.ok(coverValidation >= 0 && coverValidation < saveTestCall && saveTestCall < saveVersionCall,
  '封面校验必须发生在题型写入前，且题型写入后必须保存版本')
const saveDraftConfirmation = typeManagement.indexOf("await confirmAction('当前版本不是草稿")
assert.ok(saveDraftConfirmation >= 0 && saveDraftConfirmation < saveTestCall,
  '保存已发布版本时必须在首个写请求前确认复制草稿')
const editorCopyConfirmation = typeManagement.indexOf("await confirmAction('当前没有草稿版本")
const editorVersionCopy = typeManagement.indexOf('draft = await copyVersionAsDraft(', editorCopyConfirmation)
assert.ok(editorCopyConfirmation >= 0 && editorCopyConfirmation < editorVersionCopy,
  '进入编辑器时复制版本必须先明确确认')

assert.match(resultRules, /savedRuleKey\s*=\s*crypto\.randomUUID\(\)/,
  '新增结果规则必须生成客户端稳定键')
assert.match(resultRules, /findIndex\(\(\{ clientKey \}\) => clientKey === editingRuleKey\.value\)/,
  '编辑或删除结果规则必须按客户端稳定键定位')
assert.match(resultRules, /function reorderRule\([\s\S]*rules\.splice\(sourceIndex, 1\)[\s\S]*rules\.splice\(targetIndex, 0, rule\)/,
  '数字顺序和拖拽必须复用实际数组重排逻辑')
assert.match(resultRules, /@dragstart\.stop="startDrag\(/, '规则拖拽手柄必须连接拖动行为')
assert.match(resultRules, /@drop\.prevent\.stop="dropRule\(/, '规则卡片必须连接真实放置行为')
assert.match(resultRules, /return rule\.max === 100 \? '≤' : '<'/,
  '区间右边界闭合只能由 max 是否为 100 决定')

assert.match(unsavedChanges, /onBeforeRouteLeave\(\(\) => confirmDiscard\(\)\)/,
  '未保存保护必须覆盖路由离开')
assert.match(unsavedChanges, /export async function confirmPendingChanges/,
  '布局退出登录必须能够先确认全局未保存内容')
assert.match(unsavedChanges, /export function discardPendingChanges/,
  '明确退出后必须能够清理未保存状态，避免重复提示')

async function verifySavingRouteGuard() {
  let routeGuard
  let alertCount = 0
  const module = loadTypeScriptModule(new URL('../src/composables/useUnsavedChanges.ts', import.meta.url), {
    modules: {
      'vue-router': { onBeforeRouteLeave: (guard) => { routeGuard = guard } },
      './useConfirm': { confirmAction: async () => { alertCount += 1; return false } },
    },
    globals: {
      window: {
        addEventListener: () => {},
        removeEventListener: () => {},
        alert: () => { alertCount += 1 },
        confirm: () => false,
      },
    },
  })
  const saving = { value: true }
  const changes = module.useUnsavedChanges('尚未保存', saving)
  assert.strictEqual(await routeGuard(), false, '保存进行中必须阻止路由离开')
  assert.strictEqual(alertCount, 1)
  saving.value = false
  changes.markDirty()
  assert.strictEqual(await routeGuard(), false, '有未保存内容且取消确认时必须留在当前页')
}

async function verifyChangedWhileConfirming() {
  let finishConfirmation
  const module = loadTypeScriptModule(new URL('../src/composables/useUnsavedChanges.ts', import.meta.url), {
    modules: { './useConfirm': { confirmAction: () => new Promise(resolve => { finishConfirmation = resolve }) } },
  })
  const changes = module.useUnsavedChanges()
  changes.markDirty()
  const pending = changes.confirmDiscard()
  changes.markDirty()
  finishConfirmation(true)
  assert.equal(await pending, false, '确认期间内容已改变，不得凭旧确认放弃新内容')
}

async function verifyLateVersionResponse() {
  let unmount, finishVersions
  let confirmations = 0, copies = 0, navigations = 0
  const test = compileScriptSetup(typeManagement, '{ openVersionEditor }', {
    vue: { onBeforeUnmount: callback => { unmount = callback } },
    modules: {
      ...commonModules({
        getTestVersions: () => new Promise(resolve => { finishVersions = resolve }),
        copyVersionAsDraft: async () => { copies++; return { id: 2 } },
      }),
      '../composables/useConfirm': { confirmAction: async () => { confirmations++; return true } },
      'vue-router': { useRouter: () => ({ push: async () => { navigations++ } }) },
    },
  })
  const opening = test.openVersionEditor({ id: 1 }, 'questions')
  unmount()
  finishVersions([{ id: 1, versionStatus: 4 }])
  await opening
  assert.equal(confirmations, 0, '已卸载页面的版本响应不得再次弹确认')
  assert.equal(copies, 0, '已卸载页面不得复制草稿')
  assert.equal(navigations, 0, '已卸载页面不得触发旧导航')
}

function validTypeForm(test) {
  Object.assign(test.form, {
    name: '测试题型',
    coverUrl: '/cover.png',
    detailImageUrl: '/detail.png',
    description: '描述',
    type: '单人测试',
    status: '启用',
    categoryId: 1,
    estimatedMinutes: 3,
    drawQuestionCount: 2,
    dimensions: [{ id: 1, dimensionCode: 'STYLE', dimensionName: '维度', sortNo: 1 }],
  })
}

async function verifyTypeSaveBehavior() {
  let confirmResult = false
  let saveTestCount = 0
  let copyCount = 0
  let delaySaveTest = false
  let finishSaveTest
  let failVersion = false
  let savedVersionPayload
  let failUpload = false
  let finishUpload
  const api = {
    saveTest: async (_payload, id) => {
      assert.ok(!('iconUrl' in _payload), '题型保存不得提交已移除的 Icon 字段')
      saveTestCount += 1
      if (delaySaveTest) return new Promise((resolve) => { finishSaveTest = () => resolve({ id: id || 10 }) })
      return { id: id || 10 }
    },
    copyVersionAsDraft: async () => { copyCount += 1; return { id: 20, versionStatus: 1, versionNote: '', dimensions: [{ id: 21, dimensionCode: 'STYLE', dimensionName: '原名称' }] } },
    saveVersion: async (_id, payload) => { savedVersionPayload = payload; if (failVersion) throw new Error('version failed') },
    getTests: async () => ({ records: [], total: 0 }),
    getCategories: async () => ({ records: [] }),
    uploadImage: async () => {
      if (failUpload) throw new Error('upload failed')
      return new Promise((resolve) => { finishUpload = () => resolve('/uploaded-detail.png') })
    },
  }
  const test = compileScriptSetup(typeManagement,
    '{ form, editingId, editingVersion, saving, saveItem, handleImageUpload, editorBusy, drawerOpen, closeDrawer }', {
      modules: { ...commonModules(api), '../composables/useConfirm': { confirmAction: async () => confirmResult } },
      globals: { window: { confirm: () => confirmResult, setTimeout: () => {} } },
    })
  validTypeForm(test)

  test.editingId.value = 9
  test.editingVersion.value = { id: 8, versionStatus: 4, versionNote: '' }
  await test.saveItem()
  assert.strictEqual(saveTestCount, 0, '取消复制草稿必须保持零写请求')

  confirmResult = true
  test.editingId.value = null
  test.editingVersion.value = null
  delaySaveTest = true
  const firstCreate = test.saveItem()
  const duplicateCreate = test.saveItem()
  assert.strictEqual(saveTestCount, 1, '双击新建保存必须共用单次写入')
  finishSaveTest()
  await Promise.all([firstCreate, duplicateCreate])
  assert.strictEqual(test.saving.value, false)
  assert.strictEqual(savedVersionPayload.detailImageUrl, '/detail.png', '新建版本需保存独立详情图')
  assert.strictEqual(savedVersionPayload.coverUrl, '/cover.png', '详情图不得覆盖首页封面')

  delaySaveTest = false
  test.editingId.value = 9
  test.editingVersion.value = { id: 8, versionStatus: 4, versionNote: '' }
  failVersion = true
  await test.saveItem()
  assert.strictEqual(test.editingVersion.value.id, 20, '复制出的草稿必须立即缓存供失败重试')
  failVersion = false
  await test.saveItem()
  assert.strictEqual(copyCount, 1, '版本保存失败重试不得重复复制草稿')
  assert.strictEqual(savedVersionPayload.dimensions[0].id, 21, '复制已发布版本后需提交新草稿维度 ID')
  assert.strictEqual(savedVersionPayload.dimensions[0].dimensionName, '维度', '复制草稿不得覆盖用户修改的维度名称')
  assert.strictEqual(savedVersionPayload.detailImageUrl, '/detail.png', '编辑已发布题型后新草稿需保存详情图')
  test.form.detailImageUrl = ''
  await test.saveItem()
  assert.strictEqual(savedVersionPayload.detailImageUrl, null, '清空详情图必须显式发送 null')
  test.drawerOpen.value = true
  const input = { files: [{}], value: 'picked.png' }
  const upload = test.handleImageUpload({ target: input }, 'detailImageUrl')
  assert.strictEqual(test.editorBusy.value, true, '图片上传期间需阻止离开')
  test.closeDrawer()
  assert.strictEqual(test.drawerOpen.value, true, '图片上传期间不能关闭抽屉')
  const priorSaves = saveTestCount
  await test.saveItem()
  assert.strictEqual(saveTestCount, priorSaves, '图片上传期间不能保存版本')
  finishUpload()
  await upload
  assert.strictEqual(test.form.detailImageUrl, '/uploaded-detail.png')
  assert.strictEqual(test.form.coverUrl, '/cover.png', '上传详情图不能覆盖首页封面')
  failUpload = true
  await test.handleImageUpload({ target: input }, 'detailImageUrl')
  assert.strictEqual(test.form.detailImageUrl, '/uploaded-detail.png', '上传失败需保留原详情图')
}

async function verifyTypeListDisplay() {
  let versionRequests = 0
  const test = compileScriptSetup(typeManagement, '{ load, items }', {
    modules: commonModules({
      getTests: async () => ({ records: [
        { id: 1, testName: '已有版本', coverUrl: '/cover.png', drawQuestionCount: 5, estimatedMinutes: 3 },
        { id: 2, testName: '暂无版本' },
      ], total: 2 }),
      getTestVersions: async () => { versionRequests += 1; return [] },
    }),
    globals: { window: { setTimeout: () => {} } },
  })
  await test.load()
  assert.equal(test.items.value[0].coverUrl, '/cover.png')
  assert.equal(test.items.value[0].drawQuestionCount, 5)
  assert.equal(test.items.value[0].estimatedMinutes, 3)
  assert.equal(test.items.value[1].coverUrl, '')
  assert.equal(test.items.value[1].drawQuestionCount, null)
  assert.equal(test.items.value[1].estimatedMinutes, null)
  assert.equal(versionRequests, 0, '列表展示不得逐行请求版本详情')
}

async function verifyQuestionSaveBehavior() {
  let saveCount = 0
  let savedPayload
  let finishSave
  const api = {
    saveQuestion: async (_versionId, payload) => {
      saveCount += 1
      savedPayload = payload
      return new Promise((resolve) => {
        finishSave = () => resolve({ id: 1, ...payload, options: payload.options })
      })
    },
  }
  const test = compileScriptSetup(questionEditor,
    '{ version, question, questionType, dimensionId, options, saving, save, removeOption, addOption }', {
      modules: commonModules(api),
      globals: { window: { confirm: () => true, setTimeout: () => {} } },
    })
  test.version.value = { versionStatus: 1, dimensions: [{ id: 1, dimensionName: '维度' }] }
  test.question.value = '题干'
  test.questionType.value = 1
  test.dimensionId.value = 1
  test.options.value = [
    { optionCode: 'D', optionText: 'A', dimensionId: null, scoreValue: 3 },
    { optionCode: 'C', optionText: 'B', dimensionId: null, scoreValue: 2 },
    { optionCode: 'B', optionText: 'C', dimensionId: null, scoreValue: 1 },
    { optionCode: 'A', optionText: 'D', dimensionId: null, scoreValue: 0 },
  ]
  test.removeOption(1)
  test.addOption()
  Object.assign(test.options.value[3], { optionText: '新增', scoreValue: 4 })
  const firstSave = test.save('保存题目')
  const duplicateSave = test.save('保存题目')
  assert.strictEqual(saveCount, 1, '双击保存题目不得产生并发写入')
  finishSave()
  await Promise.all([firstSave, duplicateSave])
  assert.deepStrictEqual(Array.from(savedPayload.options, (option) => option.sortNo), [4, 3, 2, 1],
    '题目保存 payload 必须按显示顺序生成倒序 sortNo')
  assert.deepStrictEqual(Array.from(savedPayload.options, (option) => option.optionCode), ['A', 'B', 'C', 'D'],
    '历史乱序选项删除中间项再新增后，保存编码必须与显示一致且无重复')
  assert.deepStrictEqual(Array.from(savedPayload.options, (option) => [option.optionText, option.scoreValue]),
    [['A', 3], ['C', 1], ['D', 0], ['新增', 4]], '重编号不得改变选项内容与分值绑定')
  test.version.value = { versionStatus: 2, dimensions: [] }
  await test.save('保存题目')
  assert.strictEqual(saveCount, 1, '已发布版本直达页不得发题目写请求')
}

async function verifyResultRuleBehavior() {
  const savedPayloads = []
  let saveCount = 0
  let finishSave
  let keySequence = 0
  const api = {
    getVersion: async () => ({ versionStatus: 1 }),
    getResultConfig: async () => ({ dimensions: [], templates: [] }),
    saveResultConfig: async (_versionId, templates) => {
      saveCount += 1
      savedPayloads.push(templates)
      return new Promise((resolve) => { finishSave = resolve })
    },
  }
  const test = compileScriptSetup(resultRules,
    '{ version, dimensions, selectedId, form, saving, openCreate, openEdit, saveRule, saveConfig, reorderRule, scoreUpperBoundSymbol }', {
      modules: commonModules(api),
      globals: { crypto: { randomUUID: () => `test-key-${++keySequence}` }, window: { setTimeout: () => {} } },
    })
  test.version.value = { versionStatus: 1 }
  test.dimensions.value = [{ id: 1, name: '维度', icon: '♡', rules: [] }]
  test.selectedId.value = 1

  test.openCreate()
  Object.assign(test.form, { min: 0, max: 50, name: '第一条', copy: '第一条文案', position: 1 })
  test.saveRule()
  assert.strictEqual(test.dimensions.value[0].rules.length, 1, '新增规则后应只有一条记录')

  test.openEdit(test.dimensions.value[0].rules[0])
  test.form.name = '编辑后第一条'
  test.saveRule()
  assert.strictEqual(test.dimensions.value[0].rules.length, 1, '无服务端 id 的新规则再次编辑不得复制出第二条')
  assert.strictEqual(test.dimensions.value[0].rules[0].name, '编辑后第一条')

  test.openCreate()
  Object.assign(test.form, { min: 50, max: 100, name: '第二条', copy: '第二条文案', position: 1 })
  test.saveRule()
  assert.deepStrictEqual(Array.from(test.dimensions.value[0].rules, (rule) => rule.name), ['第二条', '编辑后第一条'],
    '数字匹配顺序必须实际调整数组位置')
  assert.strictEqual(test.scoreUpperBoundSymbol(test.dimensions.value[0].rules[0]), '≤', 'max=100 的规则必须闭合右边界')
  assert.strictEqual(test.scoreUpperBoundSymbol(test.dimensions.value[0].rules[1]), '<', '非 100 的规则必须使用开区间右边界')

  const firstSave = test.saveConfig()
  const duplicateSave = test.saveConfig()
  assert.strictEqual(saveCount, 1, '双击保存结果规则不得产生并发写入')
  assert.deepStrictEqual(Array.from(savedPayloads[0], (template) => template.sortNo), [1, 2],
    '保存 payload 必须按当前数组位置连续生成 sortNo')
  finishSave()
  await Promise.all([firstSave, duplicateSave])

  test.version.value = { versionStatus: 1 }
  test.dimensions.value = [{ id: 2, name: '未覆盖维度', icon: '♡', rules: [{ clientKey: 'gap', min: 20, max: 100, name: '缺口', copy: '缺口', deepCopy: '', shareCopy: '' }] }]
  await test.saveConfig()
  assert.strictEqual(saveCount, 1, '维度未完整覆盖 0—100 时不得发送保存请求')
}

await verifySavingRouteGuard()
await verifyChangedWhileConfirming()
await verifyLateVersionResponse()
await verifyTypeSaveBehavior()
await verifyTypeListDisplay()
await verifyQuestionSaveBehavior()
await verifyResultRuleBehavior()
console.log('Editor safety checks passed.')
