import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { compileScriptSetup } from './vue-script-test-loader.mjs'

const releases = readFileSync(new URL('../src/views/ReleasesView.vue', import.meta.url), 'utf8')
const aiQuestionBank = readFileSync(new URL('../src/views/AiQuestionBankView.vue', import.meta.url), 'utf8')

function expectSource(source, pattern, message) {
  if (!pattern.test(source)) throw new Error(message)
}

expectSource(releases, /getTests\(\{ page: 1, size: 100 \}\)[\s\S]*pageCount[\s\S]*getTests\(\{ page: index \+ 2, size: 100 \}\)/, '发布页必须加载全部题型分页')
expectSource(releases, /openConfirmation\('schedule', draft\)[\s\S]*openConfirmation\('publish', draft\)[\s\S]*openConfirmation\('offline', item\)/, '发布、预约和下线必须先打开确认弹窗')
expectSource(releases, /confirmationScheduleAt\.value = action === 'schedule' \? scheduleAt\.value : ''/, '预约发布必须锁定确认时的时间快照')
expectSource(releases, /scheduleVersion\(version\.id, confirmationScheduleAt\.value\)/, '预约发布请求必须提交确认弹窗展示的时间快照')
expectSource(releases, /scheduledTime <= Date\.now\(\)/, '预约发布确认前必须再次校验生效时间')
expectSource(releases, /offlineVersion\(version\.id, offlineReason\.value\.trim\(\)\)/, '下线必须提交管理员填写的真实原因')

const closeConfirmation = releases.match(/function closeConfirmation\(\) \{([\s\S]*?)\n\}/)?.[1] ?? ''
if (/publishVersion|scheduleVersion|offlineVersion/.test(closeConfirmation)) throw new Error('取消确认不得调用发布或下线接口')

expectSource(aiQuestionBank, /v-if="currentQuestion\.type === 1"[^>]*><span>计分维度/, '仅单选题展示题目计分维度')
expectSource(aiQuestionBank, /v-if="currentQuestion\.type === 2" v-model\.number="option\.dimensionId"/, '仅多选题展示选项计分维度')
expectSource(aiQuestionBank, /dimensionId: currentQuestion\.value\.type === 1 \? currentQuestion\.value\.dimensionId : null/, '题目保存必须按单选计分契约提交维度')
expectSource(aiQuestionBank, /dimensionId: currentQuestion\.value\.type === 2 \? option\.dimensionId : null/, '选项保存必须按多选计分契约提交维度')
expectSource(aiQuestionBank, /function removeRule\(index: number\)/, 'AI 结果规则必须提供删除入口')
expectSource(aiQuestionBank, /const canSubmit = computed\([\s\S]*!resultConfigDirty\.value[\s\S]*allRulesCovered\.value\)/, '提交状态必须跟踪结果规则覆盖和未保存修改')
expectSource(aiQuestionBank, /if \(!allRulesCovered\.value\)[\s\S]*checkPublish/, '提交前必须先阻止不完整的 0—100 结果规则')
expectSource(aiQuestionBank, /useUnsavedChanges\('AI 题库还有未保存的修改/, 'AI 题库必须启用未保存离开保护')

function localInputTime(offsetMs) {
  const value = new Date(Date.now() + offsetMs)
  return new Date(value.getTime() - value.getTimezoneOffset() * 60_000).toISOString().slice(0, 16)
}

async function verifyReleaseBehavior() {
  const calls = []
  let registeredBusy
  const api = {
    scheduleVersion: async (versionId, scheduledAt) => { calls.push(['schedule', versionId, scheduledAt]) },
    publishVersion: async (versionId) => { calls.push(['publish', versionId]) },
    offlineVersion: async (versionId, reason) => { calls.push(['offline', versionId, reason]) },
  }
  const test = compileScriptSetup(releases,
    '{ scheduleAt, confirmationAction, confirmationScheduleAt, offlineReason, actionLoading, openConfirmation, closeConfirmation, confirmOperation }', {
      modules: {
        '../api': api,
        '../auth': { isReadOnly: () => false },
        '../composables/useConfirm': { confirmAction: async () => true },
        '../composables/useUnsavedChanges': {
          useUnsavedChanges: (_message, busy) => {
            registeredBusy = busy
            return { markDirty: () => {}, markSaved: () => {}, confirmDiscard: async () => true }
          },
        },
      },
      globals: { window: { setTimeout: () => 0, clearTimeout: () => {} } },
    })
  const version = { id: 7, versionNo: 3, versionStatus: 1 }
  assert.strictEqual(registeredBusy, test.actionLoading, '发布页必须将操作中状态接入离开保护')
  test.actionLoading.value = true
  assert.strictEqual(registeredBusy.value, true, '发布请求进行中必须激活离开保护')
  test.actionLoading.value = false

  test.scheduleAt.value = localInputTime(60 * 60_000)
  test.openConfirmation('schedule', version)
  test.closeConfirmation()
  assert.strictEqual(calls.length, 0, '取消预约确认不得发请求')
  assert.strictEqual(test.confirmationAction.value, null, '取消后必须关闭确认状态')

  const scheduledSnapshot = localInputTime(2 * 60 * 60_000)
  test.scheduleAt.value = scheduledSnapshot
  test.openConfirmation('schedule', version)
  test.scheduleAt.value = localInputTime(3 * 60 * 60_000)
  await test.confirmOperation()
  assert.deepStrictEqual(Array.from(calls[0]), ['schedule', 7, scheduledSnapshot], '预约请求必须使用弹窗打开时锁定的时间')

  test.scheduleAt.value = localInputTime(60 * 60_000)
  test.openConfirmation('schedule', version)
  test.confirmationScheduleAt.value = '2000-01-01T00:00'
  await test.confirmOperation()
  assert.strictEqual(calls.length, 1, '确认时已过期的预约不得发请求')

  test.closeConfirmation()
  test.openConfirmation('offline', { ...version, versionStatus: 4 })
  test.offlineReason.value = '  内容风险下线  '
  await test.confirmOperation()
  assert.deepStrictEqual(Array.from(calls[1]), ['offline', 7, '内容风险下线'], '下线请求必须提交真实原因并去除首尾空格')
}

async function verifyAiBehavior() {
  const questionPayloads = []
  let publishCheckCalls = 0
  let dirtyMarks = 0
  let registeredBusy
  const api = {
    saveQuestion: async (versionId, payload, questionId) => {
      questionPayloads.push(payload)
      return {
        id: questionId,
        versionId,
        questionType: payload.questionType,
        dimensionId: payload.dimensionId ?? undefined,
        minSelectCount: payload.minSelectCount,
        maxSelectCount: payload.maxSelectCount,
        questionNo: payload.questionNo,
        questionText: payload.questionText,
        requiredFlag: payload.requiredFlag,
        sortNo: payload.sortNo,
        options: payload.options.map((option, index) => ({ id: index + 1, ...option, dimensionId: option.dimensionId ?? undefined })),
      }
    },
    checkPublish: async () => { publishCheckCalls += 1; return { passed: true, errors: [] } },
    submitAiGenerationTask: async () => ({ taskStatus: 5 }),
    createAiGenerationTask: async (payload) => {
      assert.strictEqual(payload.detailImageUrl, '/detail.png', 'AI 创建请求必须携带详情图')
      assert.strictEqual(payload.coverUrl, '/cover.png', 'AI 创建请求必须独立携带首页封面')
      return { id: 9, ...payload }
    },
    getAiGenerationTasks: async () => ({ records: [], total: 0 }),
  }
  const test = compileScriptSetup(aiQuestionBank,
    '{ task, questions, dimensions, dimensionIndex, ruleIndex, ruleDraft, resultConfigDirty, savingQuestion, editorBusy, allRulesCovered, canSubmit, saveQuestion, removeRule, submitLibrary, form, applyTaskToForm, startGeneration, notice }', {
      modules: {
        '../api': api,
        '../auth': { isReadOnly: () => false },
        '../composables/useConfirm': { confirmAction: async () => true },
        '../composables/useUnsavedChanges': {
          useUnsavedChanges: (_message, busy) => {
            registeredBusy = busy
            return { markDirty: () => { dirtyMarks += 1 }, markSaved: () => {}, confirmDiscard: async () => true }
          },
        },
      },
      globals: {
        sessionStorage: { getItem: () => 'request-id', setItem: () => {}, removeItem: () => {} },
        window: { setTimeout: () => 0, clearTimeout: () => {} },
      },
    })

  assert.strictEqual(registeredBusy.value, false, 'AI 页必须将写操作状态接入离开保护')
  test.savingQuestion.value = true
  assert.strictEqual(test.editorBusy.value, true, 'AI 题目写请求进行中必须激活离开保护')
  test.savingQuestion.value = false

  test.applyTaskToForm({ testName: '题型', testType: 1, categoryId: 1, coverUrl: '/cover.png', detailImageUrl: '/detail.png' })
  assert.strictEqual(test.form.detailImageUrl, '/detail.png', '恢复 AI 任务时需保留独立详情图')
  assert.strictEqual(test.form.coverUrl, '/cover.png', '恢复详情图不得替换首页封面')
  test.applyTaskToForm({ testName: '旧题型', testType: 1, categoryId: 1, coverUrl: '/old-cover.png' })
  assert.strictEqual(test.form.detailImageUrl, '', '旧任务无详情图时必须清空上一任务的图')
  Object.assign(test.form, { coverUrl: '/cover.png', detailImageUrl: '/detail.png', estimatedMinutes: 3, bankCount: 10, drawCount: 5 })
  await test.startGeneration()
  assert.strictEqual(test.notice.value, '', '携带详情图创建 AI 任务应成功')

  test.task.value = { id: 9, versionId: 12 }
  test.dimensions.splice(0, test.dimensions.length, { id: 1, code: 'D1', name: '维度', icon: '♡', rules: [] })
  test.questions.splice(0, test.questions.length, {
    id: 21,
    questionNo: 1,
    text: '单选题',
    type: 1,
    dimensionId: 1,
    minSelect: 1,
    maxSelect: 1,
    options: [{ text: 'A', dimensionId: 1, score: 2 }, { text: 'B', dimensionId: 1, score: 1 }],
  })
  await test.saveQuestion()
  assert.strictEqual(questionPayloads[0].dimensionId, 1, '单选题必须提交题目维度')
  assert.ok(questionPayloads[0].options.every((option) => option.dimensionId === null), '单选题选项不得提交无效维度')

  Object.assign(test.questions[0], { type: 2, dimensionId: 1, minSelect: 1, maxSelect: 2 })
  test.questions[0].options.forEach((option) => { option.dimensionId = 1 })
  await test.saveQuestion()
  assert.strictEqual(questionPayloads[1].dimensionId, null, '多选题不得提交无效题目维度')
  assert.ok(questionPayloads[1].options.every((option) => option.dimensionId === 1), '多选题每个选项必须提交计分维度')

  test.dimensions.splice(0, test.dimensions.length, {
    id: 1,
    code: 'D1',
    name: '维度',
    icon: '♡',
    rules: [
      { id: 1, dimensionId: 1, min: 0, max: 50, name: '低', content: '低分', deepContent: '', shareText: '' },
      { id: 2, dimensionId: 1, min: 50, max: 100, name: '高', content: '高分', deepContent: '', shareText: '' },
    ],
  })
  test.dimensionIndex.value = 0
  test.ruleIndex.value = 0
  Object.assign(test.ruleDraft, test.dimensions[0].rules[0])
  await test.removeRule(1)
  assert.strictEqual(test.resultConfigDirty.value, true, '删除结果规则后必须保持未保存状态')
  assert.strictEqual(test.allRulesCovered.value, false, '删除造成的区间空档必须被识别')
  assert.strictEqual(test.canSubmit.value, false, '结果区间未完整覆盖时不得提交')
  await test.submitLibrary()
  assert.strictEqual(publishCheckCalls, 0, '结果区间未完整覆盖时不得调用发布检查或提交接口')
  assert.ok(dirtyMarks > 0, '删除结果规则必须触发离开保护')
}

await verifyReleaseBehavior()
await verifyAiBehavior()
console.log('发布排期与 AI 题库源码及关键行为回归检查通过')
