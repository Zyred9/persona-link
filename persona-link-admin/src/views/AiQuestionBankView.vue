<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  assetUrl,
  checkPublish,
  createAiGenerationTask,
  deleteQuestion,
  getAiGenerationTask,
  getAiGenerationTasks,
  getCategories,
  getQuestions,
  getResultConfig,
  retryAiGenerationTask,
  saveQuestion as saveQuestionRequest,
  saveResultConfig,
  submitAiGenerationTask,
  uploadImage,
  type AiGenerationTask,
  type AiGenerationTaskSummary,
  type Category,
  type Question,
  type ResultTemplate,
} from '../api'
import { isReadOnly } from '../auth'
import { useUnsavedChanges } from '../composables/useUnsavedChanges'
import { confirmAction } from '../composables/useConfirm'

interface AiOption {
  id?: number
  text: string
  dimensionId: number
  score: number
}

interface AiQuestion {
  id: number
  questionNo: number
  sortNo?: number
  text: string
  type: 1 | 2
  dimensionId: number
  minSelect: number
  maxSelect: number
  options: AiOption[]
}

interface AiRule {
  id?: number
  dimensionId: number
  resultCode?: string
  sortNo?: number
  min: number
  max: number
  name: string
  content: string
  deepContent: string
  shareText: string
}

interface AiDimension {
  id: number
  code: string
  name: string
  icon: string
  rules: AiRule[]
}

const router = useRouter()
const route = useRoute()
const step = ref(1)
const touched = ref(false)
const reviewTab = ref<'questions' | 'results'>('questions')
const questionIndex = ref(0)
const dimensionIndex = ref(0)
const ruleIndex = ref(0)
const questionQuery = ref('')
const questionFilter = ref<'all' | 'single' | 'multiple'>('all')
const pendingRule = ref<{ dimensionIndex: number; ruleIndex: number; previousMax: number | null; previousResultConfigDirty: boolean } | null>(null)
const notice = ref('')
const task = ref<AiGenerationTask | null>(null)
const categories = ref<Category[]>([])
const taskHistory = ref<AiGenerationTaskSummary[]>([])
const historyStatus = ref('')
const historyPage = ref(1)
const historyTotal = ref(0)
const historySize = 5
const creating = ref(false)
const uploading = ref(false)
const loadingReview = ref(false)
const savingQuestion = ref(false)
const savingRule = ref(false)
const submitting = ref(false)
const formDirty = ref(false)
const ruleDirty = ref(false)
const resultConfigDirty = ref(false)
const dirtyQuestionIds = reactive(new Set<number>())
const requestStorageKey = 'ai-question-bank-request-id'
const requestId = ref(loadRequestId())
let timer: number | undefined
let disposed = false
const taskStatusLabels: Record<number, string> = { 1: '待生成', 2: '生成中', 3: '待审核', 4: '生成失败', 5: '已提交' }
const readOnly = isReadOnly()
const editorBusy = computed(() => creating.value || uploading.value || savingQuestion.value || savingRule.value || submitting.value)
const { markDirty, markSaved, confirmDiscard } = useUnsavedChanges('AI 题库还有未保存的修改，确认放弃并离开吗？', editorBusy)

function loadRequestId(): string {
  const stored = sessionStorage.getItem(requestStorageKey)
  if (stored) return stored
  const created = crypto.randomUUID()
  sessionStorage.setItem(requestStorageKey, created)
  return created
}

const form = reactive({
  name: '情侣沟通方式测试',
  type: 2,
  categoryId: 0,
  coverUrl: '',
  detailImageUrl: '',
  description: '',
  estimatedMinutes: 5,
  prompt: '生成一套适合情侣的沟通方式测试，语气轻松，不贴标签。',
  bankCount: 100,
  drawCount: 30,
})

const questions = reactive<AiQuestion[]>([])

const dimensions = reactive<AiDimension[]>([])

const ruleDraft = reactive<AiRule>({ dimensionId: 0, min: 0, max: 100, name: '', content: '', deepContent: '', shareText: '' })

const formError = computed(() => {
  if (!form.name.trim()) return '请填写题型名称'
  if (!form.categoryId) return '请选择分类标签'
  if (!form.coverUrl.trim()) return '请上传封面'
  if (form.detailImageUrl && !/^(https?:\/\/|\/).+/.test(form.detailImageUrl)) return '详情图地址无效，请重新上传或清除详情图'
  if (!Number.isInteger(form.estimatedMinutes) || form.estimatedMinutes < 1) return '预计用时需为正整数'
  if (!form.prompt.trim()) return '请填写生成要求'
  if (!Number.isInteger(form.bankCount) || form.bankCount < 10 || form.bankCount > 500) return '题库题数需为 10—500 的整数'
  if (!Number.isInteger(form.drawCount) || form.drawCount < 1 || form.drawCount > form.bankCount) return '单次答题数不能超过题库题数'
  return ''
})
const generatedCount = computed(() => task.value?.generatedQuestionCount ?? 0)
const progress = computed(() => task.value ? Math.round(task.value.generatedQuestionCount * 100 / task.value.targetQuestionCount) : 0)
const currentQuestion = computed(() => questions[questionIndex.value]!)
const currentDimension = computed(() => dimensions[dimensionIndex.value]!)
const ruleCount = computed(() => dimensions.reduce((total, item) => total + item.rules.length, 0))
const generationFailed = computed(() => task.value?.taskStatus === 4)
const generationComplete = computed(() => task.value?.taskStatus === 3 || task.value?.taskStatus === 5)
const latestQuestion = computed(() => questions[questions.length - 1])
const singleCount = computed(() => questions.filter((item) => item.type === 1).length)
const multipleCount = computed(() => questions.filter((item) => item.type === 2).length)
const filteredQuestions = computed(() => questions
  .map((question, index) => ({ question, index }))
  .filter(({ question }) => question.text.includes(questionQuery.value.trim()))
  .filter(({ question }) => questionFilter.value === 'all' || question.type === (questionFilter.value === 'single' ? 1 : 2)))
const rulesCovered = computed(() => currentDimension.value ? isCoverageComplete(currentDimension.value.rules) : false)
const allRulesCovered = computed(() => dimensions.length > 0 && dimensions.every((dimension) => isCoverageComplete(dimension.rules)))
const canSubmit = computed(() => !readOnly
  && !savingQuestion.value
  && !savingRule.value
  && !submitting.value
  && dirtyQuestionIds.size === 0
  && !ruleDirty.value
  && !resultConfigDirty.value
  && !pendingRule.value
  && allRulesCovered.value)

function isCoverageComplete(sourceRules: AiRule[]): boolean {
  const rules = [...sourceRules].sort((left, right) => left.min - right.min)
  return rules.length > 0
    && rules[0]!.min === 0
    && rules[rules.length - 1]!.max === 100
    && rules.every((rule, index) => rule.min < rule.max && (index === rules.length - 1 || rule.max === rules[index + 1]!.min))
}

function hasUnsavedChanges(): boolean {
  return formDirty.value || dirtyQuestionIds.size > 0 || ruleDirty.value || resultConfigDirty.value || Boolean(pendingRule.value)
}

function markSavedIfClean() {
  if (!hasUnsavedChanges()) markSaved()
}

function markFormDirty() {
  formDirty.value = true
  markDirty()
}

function markRuleDirty() {
  ruleDirty.value = true
  markDirty()
}

watch([questionQuery, questionFilter], () => {
  if (filteredQuestions.value.some(({ index }) => index === questionIndex.value)) return
  if (filteredQuestions.value[0]) questionIndex.value = filteredQuestions.value[0].index
})

async function startGeneration() {
  if (readOnly) return
  touched.value = true
  if (formError.value || creating.value || uploading.value) return
  creating.value = true
  notice.value = ''
  try {
    task.value = await createAiGenerationTask({
      requestId: requestId.value,
      testName: form.name.trim(),
      testType: form.type,
      categoryId: form.categoryId,
      coverUrl: form.coverUrl.trim(),
      detailImageUrl: form.detailImageUrl.trim() || null,
      description: form.description.trim() || undefined,
      estimatedMinutes: form.estimatedMinutes,
      promptText: form.prompt.trim(),
      targetQuestionCount: form.bankCount,
      drawQuestionCount: form.drawCount,
    })
    applyTaskToForm(task.value)
    formDirty.value = false
    markSavedIfClean()
    step.value = 2
    await router.replace({ query: { ...route.query, taskId: String(task.value.id) } })
    sessionStorage.removeItem(requestStorageKey)
    schedulePoll(0)
    await loadTaskHistory()
  } catch (error) {
    notice.value = error instanceof Error ? error.message : 'AI 生成任务创建失败'
  } finally {
    creating.value = false
  }
}

async function loadTaskHistory() {
  try {
    const result = await getAiGenerationTasks({ page: historyPage.value, size: historySize, taskStatus: historyStatus.value })
    taskHistory.value = result.records
    historyTotal.value = result.total
  } catch (error) { notice.value = error instanceof Error ? error.message : '任务历史加载失败' }
}

async function changeHistoryPage(offset: number) {
  historyPage.value += offset
  await loadTaskHistory()
}

async function restoreTask(taskId: number) {
  try {
    task.value = await getAiGenerationTask(taskId)
    sessionStorage.removeItem(requestStorageKey)
    applyTaskToForm(task.value)
    questions.splice(0, questions.length)
    dimensions.splice(0, dimensions.length)
    dirtyQuestionIds.clear()
    formDirty.value = false
    ruleDirty.value = false
    resultConfigDirty.value = false
    pendingRule.value = null
    markSaved()
    if (task.value.taskStatus === 5) step.value = 4
    else if (task.value.taskStatus === 3) { step.value = 2; if (await loadReview()) step.value = 3 }
    else { step.value = 2; if (!generationFailed.value) schedulePoll() }
  } catch (error) {
    notice.value = error instanceof Error ? error.message : '生成任务恢复失败'
  }
}

async function resumeTask(taskId: number) {
  if (!await confirmDiscard('当前 AI 题库还有未保存的修改，确认放弃并切换任务吗？') || disposed) return
  await router.replace({ query: { ...route.query, taskId: String(taskId) } })
  await restoreTask(taskId)
}

function schedulePoll(delay = 1500) {
  if (disposed) return
  stopTimer()
  timer = window.setTimeout(pollTask, delay)
}

async function pollTask() {
  if (!task.value) return
  try {
    task.value = await getAiGenerationTask(task.value.id)
    if (task.value.taskStatus === 1 || task.value.taskStatus === 2) {
      schedulePoll()
    } else if (task.value.taskStatus === 3) {
      await loadReview()
    } else if (task.value.taskStatus === 4) {
      notice.value = task.value.errorMessage || 'DeepSeek 生成失败，可重试当前批次。'
    } else if (task.value.taskStatus === 5) {
      step.value = 4
    }
  } catch (error) {
    notice.value = error instanceof Error ? error.message : '生成进度查询失败'
    schedulePoll(3000)
  }
}

function stopTimer() {
  if (timer === undefined) return
  window.clearTimeout(timer)
  timer = undefined
}

async function retryGeneration() {
  if (!task.value) return
  try {
    notice.value = ''
    task.value = await retryAiGenerationTask(task.value.id)
    schedulePoll(0)
  } catch (error) {
    notice.value = error instanceof Error ? error.message : '重试失败'
  }
}

async function enterReview() {
  if (!generationComplete.value) return
  if (questions.length || await loadReview()) step.value = 3
}

async function removeQuestion(index: number) {
  if (readOnly || savingQuestion.value) return
  const question = questions[index]
  if (!question || questions.length <= 1) return
  savingQuestion.value = true
  try {
    await deleteQuestion(question.id)
    dirtyQuestionIds.delete(question.id)
    questions.splice(index, 1)
    questionIndex.value = Math.min(questionIndex.value, questions.length - 1)
    markSavedIfClean()
    notice.value = `第 ${question.questionNo} 题已删除。`
  } catch (error) {
    notice.value = error instanceof Error ? error.message : '题目删除失败'
  } finally {
    savingQuestion.value = false
  }
}

function addOption() {
  if (readOnly || savingQuestion.value) return
  if (currentQuestion.value.options.length < 8) {
    currentQuestion.value.options.push({ text: '', dimensionId: currentQuestion.value.dimensionId, score: 0 })
    markQuestionDirty()
  }
}

function removeOption(index: number) {
  if (readOnly || savingQuestion.value) return
  if (currentQuestion.value.options.length > 2) {
    currentQuestion.value.options.splice(index, 1)
    markQuestionDirty()
  }
}

function markQuestionDirty() {
  if (!currentQuestion.value) return
  dirtyQuestionIds.add(currentQuestion.value.id)
  markDirty()
}

function changeQuestionType() {
  if (currentQuestion.value.type === 1) {
    currentQuestion.value.dimensionId ||= currentQuestion.value.options[0]?.dimensionId || dimensions[0]?.id || 0
    currentQuestion.value.minSelect = 1
    currentQuestion.value.maxSelect = 1
  } else {
    const defaultDimensionId = currentQuestion.value.dimensionId || dimensions[0]?.id || 0
    currentQuestion.value.options.forEach((option) => { option.dimensionId ||= defaultDimensionId })
    currentQuestion.value.minSelect = Math.max(1, currentQuestion.value.minSelect)
    currentQuestion.value.maxSelect = Math.min(currentQuestion.value.options.length, Math.max(currentQuestion.value.minSelect, currentQuestion.value.maxSelect))
  }
  markQuestionDirty()
}

async function saveQuestion() {
  if (readOnly || savingQuestion.value) return
  if (!currentQuestion.value.text.trim() || currentQuestion.value.options.some((option) => !option.text.trim())) {
    notice.value = '请补全题目和选项内容。'
    return
  }
  if (currentQuestion.value.type === 2 && (currentQuestion.value.minSelect < 1 || currentQuestion.value.maxSelect > currentQuestion.value.options.length || currentQuestion.value.minSelect > currentQuestion.value.maxSelect)) {
    notice.value = '多选题的选择数量范围无效。'
    return
  }
  if (currentQuestion.value.type === 1 && !dimensions.some((dimension) => dimension.id === currentQuestion.value.dimensionId)) {
    notice.value = '单选题必须选择有效的计分维度。'
    return
  }
  if (currentQuestion.value.type === 2 && currentQuestion.value.options.some((option) => !dimensions.some((dimension) => dimension.id === option.dimensionId))) {
    notice.value = '多选题的每个选项都必须选择有效的计分维度。'
    return
  }
  if (currentQuestion.value.options.some((option) => !Number.isInteger(option.score))) {
    notice.value = '选项分值必须为整数。'
    return
  }
  savingQuestion.value = true
  try {
    const saved = await saveQuestionRequest(task.value!.versionId, {
      questionType: currentQuestion.value.type,
      dimensionId: currentQuestion.value.type === 1 ? currentQuestion.value.dimensionId : null,
      minSelectCount: currentQuestion.value.type === 1 ? 1 : currentQuestion.value.minSelect,
      maxSelectCount: currentQuestion.value.type === 1 ? 1 : currentQuestion.value.maxSelect,
      questionNo: currentQuestion.value.questionNo,
      questionText: currentQuestion.value.text.trim(),
      requiredFlag: 1,
      sortNo: currentQuestion.value.sortNo,
      options: currentQuestion.value.options.map((option, index) => ({
        optionCode: String.fromCharCode(65 + index),
        optionText: option.text.trim(),
        dimensionId: currentQuestion.value.type === 2 ? option.dimensionId : null,
        scoreValue: option.score,
        sortNo: currentQuestion.value.options.length - index,
      })),
    }, currentQuestion.value.id)
    const savedIndex = questions.findIndex((question) => question.id === saved.id)
    if (savedIndex >= 0) questions[savedIndex] = mapQuestion(saved)
    dirtyQuestionIds.delete(saved.id)
    markSavedIfClean()
    notice.value = `第 ${saved.questionNo} 题已保存。`
  } catch (error) {
    notice.value = error instanceof Error ? error.message : '题目保存失败'
  } finally {
    savingQuestion.value = false
  }
}

function selectDimension(index: number) {
  if (savingRule.value) return
  if (index === dimensionIndex.value) return
  if (ruleDirty.value) {
    notice.value = '当前结果规则尚未保存，请先保存或取消修改。'
    return
  }
  rollbackPendingRule()
  dimensionIndex.value = index
  ruleIndex.value = 0
  resetRuleDraft()
}

function editRule(index: number) {
  if (savingRule.value) return
  if (ruleDirty.value) {
    if (index === ruleIndex.value) return
    notice.value = '当前结果规则尚未保存，请先保存或取消修改。'
    return
  }
  if (pendingRule.value?.dimensionIndex === dimensionIndex.value && pendingRule.value.ruleIndex !== index) rollbackPendingRule()
  ruleIndex.value = index
  resetRuleDraft()
}

function resetRuleDraft() {
  if (currentDimension.value?.rules[ruleIndex.value]) {
    Object.assign(ruleDraft, currentDimension.value.rules[ruleIndex.value])
  } else {
    Object.assign(ruleDraft, { id: undefined, dimensionId: currentDimension.value?.id ?? 0, resultCode: undefined, sortNo: undefined, min: 0, max: 100, name: '', content: '', deepContent: '', shareText: '' })
  }
  ruleDirty.value = false
}

function cancelRuleEdit() {
  if (pendingRule.value?.dimensionIndex === dimensionIndex.value && pendingRule.value.ruleIndex === ruleIndex.value) {
    rollbackPendingRule()
    ruleIndex.value = Math.max(0, currentDimension.value.rules.length - 1)
  }
  resetRuleDraft()
  markSavedIfClean()
  notice.value = '已取消结果规则修改。'
}

function rollbackPendingRule() {
  if (!pendingRule.value) return
  const pending = pendingRule.value
  const rules = dimensions[pending.dimensionIndex]!.rules
  rules.splice(pending.ruleIndex, 1)
  if (pending.previousMax !== null && rules[pending.ruleIndex - 1]) rules[pending.ruleIndex - 1]!.max = pending.previousMax
  resultConfigDirty.value = pending.previousResultConfigDirty
  pendingRule.value = null
  markSavedIfClean()
}

async function saveRule() {
  if (readOnly || savingRule.value) return
  if (!Number.isInteger(ruleDraft.min) || !Number.isInteger(ruleDraft.max) || ruleDraft.min < 0 || ruleDraft.min >= ruleDraft.max || ruleDraft.max > 100) {
    notice.value = '结果分数区间需为 0—100 内的有效整数区间。'
    return
  }
  if (!ruleDraft.name.trim() || !ruleDraft.content.trim()) {
    notice.value = '请补全结果名称和基础结果文案。'
    return
  }
  const candidateRules = currentDimension.value.rules.map((rule, index) => index === ruleIndex.value ? { ...ruleDraft } : rule)
  const sortedRules = [...candidateRules].sort((left, right) => left.min - right.min)
  if (!isCoverageComplete(sortedRules)) {
    notice.value = '结果规则必须无空档、无重叠地覆盖 0—100。'
    return
  }
  if (dimensions.some((dimension) => dimension.id !== currentDimension.value.id && !isCoverageComplete(dimension.rules))) {
    notice.value = '所有计分维度的结果规则都必须完整覆盖 0—100。'
    return
  }
  const templates: ResultTemplate[] = dimensions.flatMap((dimension) => {
    const rules = dimension.id === currentDimension.value.id ? candidateRules : dimension.rules
    return rules.map((rule, index) => ({
      id: rule.id,
      dimensionId: dimension.id,
      resultCode: rule.resultCode,
      resultName: rule.name.trim(),
      scoreMin: rule.min,
      scoreMax: rule.max,
      basicResultJson: { text: rule.content.trim() },
      deepResultJson: rule.deepContent ? { text: rule.deepContent } : undefined,
      shareCopyJson: rule.shareText ? { text: rule.shareText } : undefined,
      sortNo: rule.sortNo ?? index,
    }))
  })
  savingRule.value = true
  try {
    const dimensionName = currentDimension.value.name
    const config = await saveResultConfig(task.value!.versionId, templates)
    resultConfigDirty.value = false
    applyResultConfig(config.dimensions, config.templates)
    pendingRule.value = null
    markSavedIfClean()
    notice.value = `${dimensionName}结果规则已保存。`
  } catch (error) {
    notice.value = error instanceof Error ? error.message : '结果规则保存失败'
  } finally {
    savingRule.value = false
  }
}

function addRule() {
  if (readOnly || savingRule.value) return
  rollbackPendingRule()
  const lastRule = currentDimension.value.rules[currentDimension.value.rules.length - 1]
  const previousResultConfigDirty = resultConfigDirty.value
  if (!lastRule) {
    currentDimension.value.rules.push({ dimensionId: currentDimension.value.id, resultCode: `${currentDimension.value.code.slice(0, 22)}_${crypto.randomUUID().replace(/-/g, '').slice(0, 8).toUpperCase()}`, min: 0, max: 100, name: '新结果', content: '请填写结果文案。', deepContent: '', shareText: '' })
    pendingRule.value = { dimensionIndex: dimensionIndex.value, ruleIndex: 0, previousMax: null, previousResultConfigDirty }
    resultConfigDirty.value = true
    markDirty()
    editRule(0)
    notice.value = '已新增完整区间，请编辑并保存新规则。'
    return
  }
  if (lastRule.max - lastRule.min < 2) {
    notice.value = '当前最后一个区间无法继续拆分。'
    return
  }
  const previousMax = lastRule.max
  const splitScore = Math.floor((lastRule.min + lastRule.max) / 2)
  lastRule.max = splitScore
  const resultCode = `${currentDimension.value.code.slice(0, 22)}_${crypto.randomUUID().replace(/-/g, '').slice(0, 8).toUpperCase()}`
  currentDimension.value.rules.push({ dimensionId: currentDimension.value.id, resultCode, min: splitScore, max: 100, name: '新结果', content: '请填写结果文案。', deepContent: '', shareText: '' })
  pendingRule.value = { dimensionIndex: dimensionIndex.value, ruleIndex: currentDimension.value.rules.length - 1, previousMax, previousResultConfigDirty }
  resultConfigDirty.value = true
  markDirty()
  editRule(currentDimension.value.rules.length - 1)
  notice.value = '已拆分最后一个区间，请编辑并保存新规则。'
}

async function removeRule(index: number) {
  if (readOnly || savingRule.value) return
  if (ruleDirty.value) {
    notice.value = '当前结果规则尚未保存，请先保存或取消修改。'
    return
  }
  const rule = currentDimension.value.rules[index]
  if (!rule || !await confirmAction(`确认删除结果规则“${rule.name}”吗？删除后需补齐 0—100 分数区间并保存。`, { title: '删除结果规则', confirmText: '确认删除', danger: true })) return
  if (disposed || savingRule.value || ruleDirty.value || currentDimension.value.rules[index] !== rule) return
  if (pendingRule.value?.dimensionIndex === dimensionIndex.value && pendingRule.value.ruleIndex === index) {
    rollbackPendingRule()
    ruleIndex.value = Math.max(0, currentDimension.value.rules.length - 1)
    resetRuleDraft()
    notice.value = '已取消新增结果规则。'
    return
  }
  rollbackPendingRule()
  currentDimension.value.rules.splice(index, 1)
  ruleIndex.value = Math.min(index, Math.max(0, currentDimension.value.rules.length - 1))
  resetRuleDraft()
  resultConfigDirty.value = true
  markDirty()
  notice.value = isCoverageComplete(currentDimension.value.rules)
    ? '结果规则已从当前配置移除，请点击“保存规则”提交修改。'
    : '结果规则已移除，请先补齐 0—100 分数区间，再保存规则。'
}

async function submitLibrary() {
  if (!task.value || readOnly || submitting.value) return
  if (!allRulesCovered.value) {
    notice.value = '所有计分维度的结果规则都必须无空档、无重叠地覆盖 0—100。'
    return
  }
  if (dirtyQuestionIds.size || ruleDirty.value || resultConfigDirty.value) {
    notice.value = '还有未保存的人工修改，请先保存或取消后再提交。'
    return
  }
  if (pendingRule.value) {
    notice.value = '请先保存或取消新增的结果规则。'
    return
  }
  submitting.value = true
  try {
    const check = await checkPublish(task.value.versionId)
    if (!check.passed) {
      notice.value = check.errors.join('；')
      return
    }
    task.value = await submitAiGenerationTask(task.value.id)
    step.value = 4
    markSaved()
    notice.value = 'AI 题库已提交为可继续编辑的草稿版本。'
  } catch (error) {
    notice.value = error instanceof Error ? error.message : '题型库提交失败'
  } finally {
    submitting.value = false
  }
}

function textOf(value: unknown): string {
  if (value && typeof value === 'object' && 'text' in value) return String((value as { text?: unknown }).text ?? '')
  return typeof value === 'string' ? value : ''
}

function mapQuestion(question: Question): AiQuestion {
  const dimensionId = question.dimensionId ?? question.options[0]?.dimensionId ?? dimensions[0]?.id ?? 0
  return {
    id: question.id,
    questionNo: question.questionNo,
    sortNo: question.sortNo,
    text: question.questionText,
    type: question.questionType as 1 | 2,
    dimensionId,
    minSelect: question.minSelectCount,
    maxSelect: question.maxSelectCount,
    options: question.options.map((option) => ({ id: option.id, text: option.optionText, dimensionId: option.dimensionId ?? question.dimensionId ?? dimensionId, score: option.scoreValue })),
  }
}

function dimensionName(dimensionId: number): string {
  return dimensions.find((dimension) => dimension.id === dimensionId)?.name || '未关联维度'
}

function applyTaskToForm(value: AiGenerationTask) {
  form.name = value.testName
  form.type = value.testType
  form.categoryId = value.categoryId
  form.coverUrl = value.coverUrl
  form.detailImageUrl = value.detailImageUrl ?? ''
  form.description = value.description || ''
  form.estimatedMinutes = value.estimatedMinutes
  form.bankCount = value.targetQuestionCount
  form.drawCount = value.drawQuestionCount
}

function applyResultConfig(resultDimensions: Array<{ id: number; dimensionCode?: string; dimensionName: string }>, templates: ResultTemplate[]) {
  dimensions.splice(0, dimensions.length, ...resultDimensions.map((dimension, index) => ({
    id: dimension.id,
    code: dimension.dimensionCode || `D${index + 1}`,
    name: dimension.dimensionName,
    icon: ['ϟ', '♥', '☁', '♧'][index % 4]!,
    rules: templates.filter((template) => template.dimensionId === dimension.id)
      .sort((left, right) => Number(left.scoreMin) - Number(right.scoreMin)).map((template) => ({
      id: template.id,
      dimensionId: dimension.id,
      resultCode: template.resultCode,
      sortNo: template.sortNo,
      min: Number(template.scoreMin),
      max: Number(template.scoreMax),
      name: template.resultName,
      content: textOf(template.basicResultJson),
      deepContent: textOf(template.deepResultJson),
      shareText: textOf(template.shareCopyJson),
    })),
  })))
  dimensionIndex.value = Math.min(dimensionIndex.value, Math.max(0, dimensions.length - 1))
  ruleIndex.value = 0
  resetRuleDraft()
}

async function loadReview(): Promise<boolean> {
  if (!task.value || loadingReview.value) return false
  loadingReview.value = true
  try {
    const [questionData, resultConfig] = await Promise.all([getQuestions(task.value.versionId), getResultConfig(task.value.versionId)])
    applyResultConfig(resultConfig.dimensions, resultConfig.templates)
    questions.splice(0, questions.length, ...questionData.map(mapQuestion))
    dirtyQuestionIds.clear()
    ruleDirty.value = false
    resultConfigDirty.value = false
    pendingRule.value = null
    markSavedIfClean()
    questionIndex.value = 0
    if (!questions.length || !dimensions.length) throw new Error('生成结果不完整，请重试任务')
    return true
  } catch (error) {
    notice.value = error instanceof Error ? error.message : '审核数据加载失败'
    return false
  } finally {
    loadingReview.value = false
  }
}

async function uploadVersionImage(event: Event, field: 'coverUrl' | 'detailImageUrl') {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file || uploading.value) return
  const label = field === 'detailImageUrl' ? '详情图' : '封面'
  uploading.value = true
  try {
    form[field] = await uploadImage(file)
    markFormDirty()
    notice.value = `${label}上传成功。`
  } catch (error) {
    notice.value = error instanceof Error ? error.message : `${label}上传失败`
  } finally {
    uploading.value = false
    input.value = ''
  }
}

onMounted(async () => {
  try {
    categories.value = (await getCategories({ status: 1 })).records
  } catch (error) {
    notice.value = error instanceof Error ? error.message : '分类加载失败'
  }
  await loadTaskHistory()
  const taskId = Number(route.query.taskId)
  if (!Number.isInteger(taskId) || taskId <= 0) return
  await restoreTask(taskId)
})

onUnmounted(() => {
  disposed = true
  stopTimer()
})
</script>

<template>
  <section class="ai-page">
    <header class="ai-heading">
      <div><h1>AI题库助手 <span>♡</span></h1><p>{{ step === 1 ? '填写题型信息，由 DeepSeek 生成可审核题库' : step === 2 ? (generationFailed ? 'DeepSeek 生成失败，请查看失败原因并重试' : generationComplete ? 'DeepSeek 已完成题库生成，可进入人工审核' : 'DeepSeek 正在生成题库，页面会自动刷新真实进度') : step === 3 ? '逐题审核题目、选项、计分维度和分值' : '题库已提交，可继续在题型管理中编辑和发布' }}</p></div>
      <span class="preview-chip">DeepSeek</span>
    </header>

    <p class="integration-note"><b>真实生成</b> 模型调用和 API Key 仅在服务端执行；任务失败后可从当前批次继续重试。</p>
    <p v-if="notice" class="review-notice" role="status">{{ notice }}</p>

    <template v-if="step === 1">
    <section class="task-history">
      <header><h2>最近任务</h2><select v-model="historyStatus" @change="historyPage = 1; loadTaskHistory()"><option value="">全部状态</option><option v-for="(label, key) in taskStatusLabels" :key="key" :value="key">{{ label }}</option></select></header>
      <div v-for="item in taskHistory" :key="item.id" class="task-row"><span><b>{{ item.testName }}</b><small>{{ item.taskNo }} · {{ new Date(item.createDate).toLocaleString() }}</small></span><strong :class="`status-${item.taskStatus}`">{{ taskStatusLabels[item.taskStatus] }}</strong><span>{{ item.generatedQuestionCount }} / {{ item.targetQuestionCount }} 题</span><button type="button" @click="resumeTask(item.id)">{{ item.taskStatus === 5 ? '查看' : '继续处理' }}</button></div>
      <p v-if="!taskHistory.length" class="empty-tip">暂无生成任务</p>
      <footer><span>共 {{ historyTotal }} 条</span><div><button :disabled="historyPage <= 1" @click="changeHistoryPage(-1)">上一页</button><button :disabled="historyPage * historySize >= historyTotal" @click="changeHistoryPage(1)">下一页</button></div></footer>
    </section>
    <section class="ai-form-card">
      <label><span>题型名称</span><input v-model="form.name" maxlength="100" placeholder="输入题型名称" @input="markFormDirty" /></label>
      <label class="short-field"><span>测试类型</span><select v-model.number="form.type" @change="markFormDirty"><option :value="1">单人测试</option><option :value="2">双人测试</option></select></label>
      <label class="short-field"><span>分类标签</span><select v-model.number="form.categoryId" @change="markFormDirty"><option :value="0" disabled>请选择分类</option><option v-for="category in categories" :key="category.id" :value="category.id">{{ category.categoryName }}</option></select></label>
      <label><span>首页封面</span><div class="cover-input"><label class="upload-button"><input type="file" accept="image/png,image/jpeg,image/webp" :disabled="readOnly || uploading" @change="uploadVersionImage($event, 'coverUrl')" />{{ uploading ? '上传中…' : '上传封面' }}</label><img v-if="form.coverUrl" :src="assetUrl(form.coverUrl)" alt="题型封面预览" /></div></label>
      <label><span>详情图（选填）</span><div class="cover-input detail-image-input"><label class="upload-button"><input type="file" accept="image/png,image/jpeg,image/webp" :disabled="readOnly || uploading" @change="uploadVersionImage($event, 'detailImageUrl')" />{{ uploading ? '上传中…' : '上传详情图' }}</label><button v-if="form.detailImageUrl" class="outline-button" type="button" :disabled="readOnly || uploading" @click="form.detailImageUrl = ''; markFormDirty()">清除详情图，使用默认图</button><img v-if="form.detailImageUrl" :src="assetUrl(form.detailImageUrl)" alt="详情图完整预览" /><small>建议宽高比约 2.2:1，按比例完整展示、不裁剪；清除后使用内置默认整图。随版本发布生效，不影响首页封面。</small></div></label>
      <label><span>题型说明</span><textarea v-model="form.description" rows="3" maxlength="2000" placeholder="选填，说明题型用途和适用人群" @input="markFormDirty"></textarea></label>
      <label><span>生成要求</span><textarea v-model="form.prompt" rows="4" maxlength="500" placeholder="描述主题、语气和需要避免的内容" @input="markFormDirty"></textarea></label>
      <label class="short-field"><span>预计用时</span><input v-model.number="form.estimatedMinutes" type="number" min="1" max="120" @input="markFormDirty" /></label>
      <label class="short-field"><span>题库题数</span><input v-model.number="form.bankCount" type="number" min="10" max="500" @input="markFormDirty" /></label>
      <label class="short-field"><span>单次答题</span><input v-model.number="form.drawCount" type="number" min="1" :max="form.bankCount" @input="markFormDirty" /></label>
      <p v-if="touched && formError" class="form-error" role="alert">{{ formError }}</p>
      <button class="generate-button" type="button" :disabled="readOnly || creating || uploading" @click="startGeneration">{{ creating ? '正在创建任务…' : '✧ 开始真实生成' }}</button>
    </section>
    </template>

    <section v-else-if="step === 2" class="generation-layout">
      <article class="generation-card">
        <h2>{{ generationFailed ? '生成失败 ✦' : generationComplete ? '题库生成完成 ✦' : '正在生成题库 ✦' }}</h2>
        <div class="generation-main">
          <div class="generation-title"><h3>{{ form.name }}</h3><span>{{ generationFailed ? '生成失败' : generationComplete ? '已完成' : '生成中' }}</span></div>
          <strong>{{ generatedCount }} <small>/ {{ form.bankCount }} 题</small></strong>
          <div class="progress-track"><i :style="{ width: `${progress}%` }">{{ progress }}%</i></div>
          <p>◷ 当前批次　第 {{ task?.currentBatchNo || 0 }} 批 / 共 {{ task?.totalBatchCount || 0 }} 批</p>
          <aside :class="{ failed: generationFailed }" :role="generationFailed ? 'alert' : 'status'">♡ {{ generationFailed ? (task?.errorMessage || '生成失败') : generationComplete ? '题库生成完成，请进入审核。' : 'DeepSeek 正在生成并写入草稿，请稍候。' }}</aside>
        </div>
        <div class="stat-row"><div>▣ <span>已生成<strong>{{ generatedCount }}</strong></span></div><div>◎ <span>单选题<strong>{{ generationComplete ? singleCount : '—' }}</strong></span></div><div>☑ <span>多选题<strong>{{ generationComplete ? multipleCount : '—' }}</strong></span></div></div>
      </article>
      <article class="current-card">
        <h2>当前生成内容 ✦</h2>
        <template v-if="latestQuestion"><div class="question-meta"><b>第 {{ latestQuestion.questionNo }} 题</b><span>{{ latestQuestion.type === 1 ? '单选题' : '多选题' }}</span><span>{{ dimensionName(latestQuestion.dimensionId) }}</span></div><h3>{{ latestQuestion.text }}</h3><ol><li v-for="(option, index) in latestQuestion.options" :key="option.id || index"><b>{{ String.fromCharCode(65 + index) }}</b>{{ option.text }}</li></ol><ul><li>♢ 维度覆盖 <strong>通过</strong></li><li>▤ 计分规则 <strong>通过</strong></li><li>⌕ 数据来源 <em>服务端草稿</em></li></ul></template>
        <p v-else class="generation-placeholder">题目按批次写入服务端，生成完成后在此展示最后一道真实题目。</p>
        <button v-if="generationFailed" type="button" :disabled="readOnly" @click="retryGeneration">重试当前批次</button><button v-else type="button" :disabled="!generationComplete" @click="enterReview">{{ generationComplete ? '进入人工审核' : '生成中，请稍候' }}</button>
      </article>
    </section>

    <section v-else-if="step === 3" class="review-stage">
      <article class="review-summary">
        <div><span>▣</span><h2>{{ form.name }}</h2><i>AI 草稿</i></div>
        <dl><div><dt>题库</dt><dd>{{ questions.length }}<small> 题</small></dd></div><div><dt>单次答题</dt><dd>{{ form.drawCount }}<small> 题</small></dd></div><div><dt>计分维度</dt><dd>{{ dimensions.length }}<small> 个</small></dd></div></dl>
      </article>
      <div class="review-tabs"><button :class="{ active: reviewTab === 'questions' }" @click="reviewTab = 'questions'">题目与选项</button><button :class="{ active: reviewTab === 'results' }" @click="reviewTab = 'results'">结果规则 <b>{{ ruleCount }}</b></button></div>

      <div v-if="reviewTab === 'questions'" class="question-review-grid">
        <aside class="question-list">
          <h2>题目列表</h2>
          <input v-model="questionQuery" placeholder="⌕ 搜索题目" aria-label="搜索题目" />
          <div class="filter-row"><button :class="{ active: questionFilter === 'all' }" @click="questionFilter = 'all'">全部 {{ questions.length }}</button><button :class="{ active: questionFilter === 'single' }" @click="questionFilter = 'single'">单选题 {{ questions.filter((item) => item.type === 1).length }}</button><button :class="{ active: questionFilter === 'multiple' }" @click="questionFilter = 'multiple'">多选题 {{ questions.filter((item) => item.type === 2).length }}</button></div>
          <div class="question-list-scroll">
            <div v-for="item in filteredQuestions" :key="item.question.id" class="question-item" :class="{ active: questionIndex === item.index }"><button class="question-select" type="button" :disabled="savingQuestion" @click="questionIndex = item.index"><b>{{ item.question.questionNo }}</b><span><strong>{{ item.question.text }}</strong><small>✎ 编辑</small></span></button><button class="question-delete" type="button" :disabled="readOnly || savingQuestion" :aria-label="`删除第 ${item.question.questionNo} 题`" @click="removeQuestion(item.index)">删除</button></div>
            <p v-if="filteredQuestions.length === 0" class="empty-tip">没有匹配的题目</p>
          </div>
        </aside>
        <article class="question-editor" :class="{ saving: savingQuestion }">
          <h2>题目与选项</h2>
          <label><span>题目内容</span><input v-model="currentQuestion.text" maxlength="500" :disabled="readOnly || savingQuestion" @input="markQuestionDirty" /></label>
          <div class="editor-fields" :class="{ single: currentQuestion.type === 2 }"><label><span>题型</span><select v-model.number="currentQuestion.type" :disabled="readOnly || savingQuestion" @change="changeQuestionType"><option :value="1">单选题</option><option :value="2">多选题</option></select></label><label v-if="currentQuestion.type === 1"><span>计分维度</span><select v-model.number="currentQuestion.dimensionId" :disabled="readOnly || savingQuestion" @change="markQuestionDirty"><option v-for="item in dimensions" :key="item.id" :value="item.id">{{ item.name }}</option></select></label></div>
          <div v-if="currentQuestion.type === 2" class="selection-range"><label><span>最少选择</span><input v-model.number="currentQuestion.minSelect" type="number" min="1" :max="currentQuestion.options.length" :disabled="readOnly || savingQuestion" @input="markQuestionDirty" /></label><label><span>最多选择</span><input v-model.number="currentQuestion.maxSelect" type="number" :min="currentQuestion.minSelect" :max="currentQuestion.options.length" :disabled="readOnly || savingQuestion" @input="markQuestionDirty" /></label></div>
          <div class="options-table" :class="{ 'single-score': currentQuestion.type === 1 }"><header><span>选项</span><span>答案内容</span><span v-if="currentQuestion.type === 2">计分维度</span><span>分值</span><span>操作</span></header><div v-for="(option, index) in currentQuestion.options" :key="index"><b>{{ String.fromCharCode(65 + index) }}</b><input v-model="option.text" :disabled="readOnly || savingQuestion" @input="markQuestionDirty" /><select v-if="currentQuestion.type === 2" v-model.number="option.dimensionId" aria-label="计分维度" :disabled="readOnly || savingQuestion" @change="markQuestionDirty"><option v-for="item in dimensions" :key="item.id" :value="item.id">{{ item.name }}</option></select><input v-model.number="option.score" type="number" step="1" :disabled="readOnly || savingQuestion" @input="markQuestionDirty" /><button :disabled="readOnly || savingQuestion" @click="removeOption(index)">删除</button></div></div>
          <footer><button class="outline-button" :disabled="readOnly || savingQuestion" @click="addOption">＋ 添加选项</button><button class="save-button" :disabled="readOnly || savingQuestion" @click="saveQuestion">{{ savingQuestion ? '保存中…' : '保存题目' }}</button></footer>
        </article>
      </div>

      <div v-else class="result-review-grid">
        <aside class="dimension-list"><h2>计分维度</h2><button v-for="(item, index) in dimensions" :key="item.name" :disabled="savingRule" :class="{ active: dimensionIndex === index }" @click="selectDimension(index)"><b>{{ item.icon }}</b><span><strong>{{ item.name }}</strong><small>{{ item.rules.length }} 条规则</small></span></button></aside>
        <article class="rules-list"><h2>{{ currentDimension.name }} · 结果规则 <span :class="{ invalid: !rulesCovered }">{{ rulesCovered ? '✓ 0—100 已完整覆盖' : '! 区间存在空档或重叠' }}</span></h2><section v-for="(rule, index) in currentDimension.rules" :key="rule.id ?? rule.resultCode ?? `${rule.min}-${rule.max}`" :class="{ active: ruleIndex === index }"><div><b>{{ rule.min }} ≤ 分数 {{ rule.max === 100 ? '≤' : '<' }} {{ rule.max }}</b></div><div><strong>{{ rule.name }}</strong><p>{{ rule.content }}</p></div><div class="rule-actions"><button type="button" :disabled="savingRule" @click="editRule(index)">✎ 编辑</button><button type="button" :disabled="readOnly || savingRule" @click="removeRule(index)">删除</button></div></section><button class="outline-button" type="button" :disabled="readOnly || savingRule" @click="addRule">＋ 添加结果规则</button></article>
        <aside class="rule-editor" :class="{ saving: savingRule }"><h2>编辑结果规则</h2><label><span>计分维度</span><input :value="currentDimension.name" readonly /></label><label><span>分数区间</span><div><input v-model.number="ruleDraft.min" type="number" min="0" max="99" :disabled="readOnly || savingRule" @input="markRuleDirty" /><i>至</i><input v-model.number="ruleDraft.max" type="number" min="1" max="100" :disabled="readOnly || savingRule" @input="markRuleDirty" /></div></label><label><span>结果名称</span><input v-model="ruleDraft.name" :disabled="readOnly || savingRule" @input="markRuleDirty" /></label><label><span>基础结果文案</span><textarea v-model="ruleDraft.content" rows="4" :disabled="readOnly || savingRule" @input="markRuleDirty"></textarea></label><details><summary>深度解读文案</summary><textarea v-model="ruleDraft.deepContent" rows="3" placeholder="输入深度解读文案" :disabled="readOnly || savingRule" @input="markRuleDirty"></textarea></details><details><summary>分享文案</summary><textarea v-model="ruleDraft.shareText" rows="3" placeholder="输入分享文案" :disabled="readOnly || savingRule" @input="markRuleDirty"></textarea></details><footer><button class="outline-button" type="button" :disabled="savingRule" @click="cancelRuleEdit">取消</button><button class="save-button" type="button" :disabled="readOnly || savingRule || !currentDimension.rules.length" @click="saveRule">{{ savingRule ? '保存中…' : '保存规则' }}</button></footer></aside>
      </div>
      <button class="submit-button" type="button" :disabled="!canSubmit" @click="submitLibrary">完成审核并提交</button>
    </section>

    <section v-else class="submitted-card"><b>✓</b><h2>题库已提交</h2><p>题型和版本已保存为草稿，没有自动发布，也没有自动加入首页。</p><button type="button" @click="router.push('/types')">返回题型管理</button></section>

    <nav class="ai-steps" aria-label="AI 题库创建步骤">
      <div v-for="(label, index) in ['填写题型信息', '生成题库', '审核题目与结果', '提交题型库']" :key="label" :class="{ active: step === index + 1, done: step > index + 1 }"><b>{{ step > index + 1 ? '✓' : index + 1 }}</b><span>{{ label }}<small>{{ step > index + 1 ? '已完成' : step === index + 1 ? (step === 4 && task?.taskStatus === 5 ? '已完成' : step === 2 ? (generationFailed ? '生成失败' : generationComplete ? '已完成' : '生成中') : '进行中') : '待开始' }}</small></span></div>
    </nav>
  </section>
</template>

<style scoped>
.cover-input.detail-image-input img { object-fit: contain; }
.detail-image-input small { grid-column: 1 / -1; color: #777; line-height: 1.6; font-weight: 400; }
.ai-page { max-width: 1420px; margin: 0 auto; color: #171419; }
.ai-heading { display: flex; align-items: end; justify-content: space-between; gap: 20px; margin-bottom: 16px; }
.ai-heading h1 { margin: 0; font: 700 clamp(38px, 4vw, 52px) var(--font-ui); letter-spacing: .04em; }
.ai-heading h1 span { color: #b27bef; }.ai-heading p { margin: 10px 0 0; color: #7e7781; }.preview-chip { padding: 6px 12px; border-radius: 999px; background: #eee2ff; color: #7449a5; font-weight: 800; font-size: 12px; }
.task-history{display:grid;gap:0;margin-bottom:16px;border:var(--line);border-radius:17px;background:rgb(255 255 255 / 88%);overflow:hidden}.task-history>header,.task-row,.task-history>footer{display:flex;align-items:center;gap:14px;padding:13px 18px}.task-history>header{justify-content:space-between;background:#f5effe}.task-history h2{margin:0;font:700 22px var(--font-ui)}.task-history select{padding:7px 10px;border:1px solid #cfc6ce;border-radius:8px;background:#fff}.task-row{border-top:1px solid #e2dbe1}.task-row>span:first-child{display:grid;flex:1;min-width:0}.task-row small{overflow:hidden;text-overflow:ellipsis;color:#837b83;white-space:nowrap}.task-row strong{min-width:82px;padding:4px 8px;border-radius:99px;background:#eee2ff;color:#7548aa;text-align:center;font-size:12px}.task-row .status-4{background:#fff0ed;color:#b33524}.task-row .status-5{background:#ecf8e9;color:#3d9448}.task-row button,.task-history footer button{padding:7px 11px;border:1px solid #8e57e1;border-radius:8px;background:#fff;color:#7545b7}.task-history>footer{justify-content:space-between;border-top:1px solid #e2dbe1}.task-history footer div{display:flex;gap:8px}.task-history button:disabled{opacity:.4}
.integration-note { margin: 0 0 18px; padding: 11px 14px; border: 1px solid #87c9a3; border-radius: 10px; background: #effaf3; color: #2e6f4d; font-size: 13px; }.integration-note b { margin-right: 8px; }
.ai-form-card,.generation-card,.current-card,.review-summary,.question-list,.question-editor,.dimension-list,.rules-list { border: var(--line); border-radius: 17px; background: rgb(255 255 255 / 88%); }
.ai-form-card { display: grid; gap: 17px; padding: 38px 34px 28px; }.ai-form-card > label,.question-editor label { display: grid; grid-template-columns: 145px 1fr; align-items: start; gap: 20px; font-weight: 800; }.ai-form-card > label > span::before { content: '•'; margin-right: 14px; color: #b997ff; }.ai-form-card input,.ai-form-card select,.ai-form-card textarea,.question-editor input,.question-editor select,.rules-list input,.rules-list textarea,.question-list > input { width: 100%; padding: 12px 14px; border: 1px solid #ccc4cc; border-radius: 9px; background: white; }.ai-form-card textarea { min-height: 90px; }.short-field input,.short-field select { max-width: 375px; }.cover-input { display:grid; grid-template-columns:1fr auto; gap:9px }.cover-input .upload-button { display:grid; place-items:center; padding:10px 16px; border:1px solid #8e57e1; border-radius:9px; color:#7545b7; cursor:pointer }.cover-input .upload-button input { display:none }.cover-input img { grid-column:1/-1; width:180px; height:105px; border:1px solid #ddd; border-radius:10px; object-fit:cover }.form-error { margin: 0 0 0 165px; color: #c44837; }.generate-button,.submit-button,.save-button,.current-card > button,.submitted-card button { justify-self: center; min-width: 405px; padding: 14px 28px; border: var(--line); border-radius: 10px; background: #ff654a; color: white; box-shadow: 2px 3px 0 #211d22; font-weight: 900; font-size: 17px; }.generate-button:disabled,.current-card > button:disabled { opacity:.5; cursor:not-allowed }.generation-placeholder { display:grid; place-items:center; min-height:260px; padding:30px; border:1px dashed #c9b9d6; border-radius:12px; color:#817486; text-align:center }.submitted-card { display:grid; justify-items:center; gap:12px; padding:70px 30px; border:var(--line); border-radius:18px; background:#fff; text-align:center }.submitted-card > b { display:grid; place-items:center; width:74px; height:74px; border:2px solid #65aa82; border-radius:50%; color:#4b946a; font-size:38px }.submitted-card h2 { margin:0; font-size:30px }.submitted-card p { color:#777 }
.generation-layout { display: grid; grid-template-columns: 1.05fr .95fr; gap: 18px; }.generation-card,.current-card { padding: 24px; }.generation-card h2,.current-card h2,.question-list h2,.question-editor h2,.dimension-list h2,.rules-list h2 { margin: 0 0 18px; font: 700 23px var(--font-ui); }.generation-main { padding: 22px; border: 1px solid #c4a8ff; border-radius: 12px; background: linear-gradient(140deg,#faf7ff,#fff); }.generation-title { display: flex; justify-content: space-between; align-items: center; }.generation-title h3 { margin: 0; font-size: 24px; }.generation-title span,.question-meta span { padding: 5px 11px; border-radius: 999px; background: #eee2ff; color: #784cb0; font-size: 12px; font-weight: 800; }.generation-main > strong { display: block; margin: 24px 0 12px; color: #ff4e25; font-size: 45px; text-align: center; }.generation-main small { color: #37323a; font-size: 20px; }.progress-track { overflow: hidden; height: 19px; border-radius: 999px; background: #e8dcff; }.progress-track i { display: block; height: 100%; border-radius: inherit; background: #ff552d; color: white; font-size: 12px; font-style: normal; font-weight: 900; line-height: 19px; text-align: center; transition: width .18s ease; }.generation-main aside { padding: 11px 13px; border-radius: 8px; background: #f4efff; color: #7854bd; }.generation-main aside.failed { border: 1px solid #f0a092; background: #fff0ed; color: #b33524; font-weight: 800; }.stat-row { display: grid; grid-template-columns: repeat(3,1fr); gap: 10px; margin-top: 15px; }.stat-row > div { display: flex; align-items: center; gap: 12px; padding: 15px; border: 1px solid #ddd4dc; border-radius: 10px; color: #8057c4; font-size: 23px; }.stat-row span,.stat-row strong { display: block; color: #211d22; font-size: 12px; }.stat-row strong { margin-top: 3px; font-size: 22px; }.question-meta { display: flex; align-items: center; gap: 8px; }.question-meta b { margin-right: auto; padding: 7px 13px; border: 1px solid #a77cf0; border-radius: 8px; }.current-card h3 { margin: 18px 0 14px; }.current-card ol,.current-card ul { display: grid; gap: 8px; margin: 0; padding: 0; list-style: none; }.current-card ol li { display: flex; align-items: center; gap: 11px; padding: 9px 12px; border: 1px solid #d8d0d7; border-radius: 9px; }.current-card ol b { display: grid; place-items: center; width: 29px; height: 29px; border: 1px solid #211d22; border-radius: 50%; background: #ffd66b; }.current-card ol li:nth-child(2) b { background:#a7dec9 }.current-card ol li:nth-child(3) b { background:#c9a7ff }.current-card ol li:nth-child(4) b { background:#ffaaa0 }.current-card ul { margin: 14px 0; padding-top: 12px; border-top: 1px dashed #d2c8d0; }.current-card ul li { display: flex; justify-content: space-between; }.current-card ul strong { color: #238357; }.current-card ul em { font-style: normal; }.current-card > button { width: 100%; min-width: 0; background: white; color: #211d22; box-shadow: none; }
.review-summary { display: flex; align-items: center; justify-content: space-between; padding: 17px 24px; }.review-summary > div { display: flex; align-items: center; gap: 12px; }.review-summary > div > span { display:grid; place-items:center; width:52px; height:52px; border-radius:50%; background:#eee2ff; font-size:27px }.review-summary h2 { margin:0; font-size:26px }.review-summary i { padding:5px 10px; border-radius:999px; background:#eee2ff; color:#7548aa; font-style:normal; font-size:12px }.review-summary dl { display:flex; margin:0 }.review-summary dl div { min-width:145px; padding:0 28px; border-left:1px solid #d9d0d8; text-align:center }.review-summary dt { font-size:13px }.review-summary dd { margin:7px 0 0; font-size:28px; font-weight:900 }.review-summary dd small { font-size:12px }.review-tabs { display:flex; margin-top:14px }.review-tabs button { min-width:190px; padding:10px 20px; border:1px solid #cfc6ce; background:white }.review-tabs button:first-child { border-radius:9px 0 0 9px }.review-tabs button:last-child { border-radius:0 9px 9px 0 }.review-tabs button.active { border-color:#9259e7; background:#f5efff; color:#7443b0; font-weight:900 }.review-tabs b { margin-left:5px; padding:2px 7px; border-radius:999px; background:#eee2ff }.question-review-grid,.result-review-grid { display:grid; grid-template-columns: .72fr 1.28fr; gap:14px; margin-top:10px }.question-list,.question-editor,.dimension-list,.rules-list { padding:17px }.filter-row { display:flex; gap:7px; margin:11px 0 }.filter-row span { padding:5px 9px; border-radius:999px; background:#f1edf2; font-size:12px }.question-list > button,.dimension-list > button { display:grid; grid-template-columns:auto 1fr; gap:11px; width:100%; margin-top:9px; padding:12px; border:1px solid #ded5dc; border-radius:10px; background:white; text-align:left }.question-list > button.active,.dimension-list > button.active { border-color:#b58cf3; background:#faf6ff }.question-list > button > b { display:grid; place-items:center; width:34px; height:34px; border-radius:50%; background:#eee2ff; color:#784db0 }.question-list > button span { display:grid; gap:7px }.question-list small { color:#746d75 }.question-list small i { color:#ff4d34; font-style:normal }.question-editor > label { grid-template-columns:1fr; gap:7px }.editor-fields { display:grid; grid-template-columns:1fr 1fr; gap:14px }.editor-fields label { display:grid; grid-template-columns:1fr; gap:7px }.options-table { overflow-x:auto; margin-top:5px }.options-table header,.options-table > div { display:grid; grid-template-columns:50px minmax(190px,1fr) 130px 75px 55px; align-items:center; gap:9px; min-width:620px; padding:8px; border-bottom:1px solid #e4dbe2 }.options-table header { background:#faf4f7; font-size:12px; font-weight:800 }.options-table > div > b { display:grid; place-items:center; width:31px; height:31px; border:1px solid #c7bdc5; border-radius:7px }.options-table button { border:0; background:transparent; color:#ff4b35 }.dimension-value { padding:11px 9px; border-radius:7px; background:#f6f2f6; color:#665e66; font-size:12px }.question-editor footer { display:flex; justify-content:space-between; margin-top:12px }.outline-button { padding:10px 14px; border:1px solid #8e57e1; border-radius:9px; background:white; color:#7545b7; font-weight:800 }.save-button { justify-self:auto; min-width:135px; padding:10px 18px; font-size:14px }.result-review-grid { grid-template-columns:245px minmax(360px,1fr) 330px }.dimension-list > button > b { display:grid; place-items:center; width:45px; height:45px; border:1px solid #211d22; border-radius:50%; background:#eee2ff; font-size:22px }.dimension-list > button span { display:grid; gap:5px }.dimension-list small { color:#766f77 }.rules-list h2 span { float:right; padding:5px 9px; border-radius:999px; background:#ecf8e9; color:#3d9448; font:700 12px sans-serif }.rules-list section { display:grid; grid-template-columns:145px 1fr auto; gap:12px; margin-bottom:10px; padding:13px; border:1px solid #d9d0d8; border-radius:10px }.rules-list section.active { border-color:#b58cf3; background:#fcf9ff }.rules-list section > div { align-self:center }.rules-list section > div b { padding:6px 10px; border-radius:7px; background:#f0e7ff; color:#65419b; font-size:12px }.rules-list section p { margin:5px 0 0; color:#746d75; font-size:12px; line-height:1.5 }.rules-list section > button { align-self:start; border:0; background:transparent; color:#7443b0; font-weight:800 }.rule-editor { padding:17px; border:1px solid #d8cfd7; border-radius:14px; background:#fff }.rule-editor h2 { margin:0 0 15px; font:700 22px var(--font-ui) }.rule-editor label { display:grid; gap:6px; margin-bottom:12px; font-weight:800; font-size:13px }.rule-editor input,.rule-editor textarea { width:100%; padding:10px 11px; border:1px solid #d0c7cf; border-radius:8px; background:white }.rule-editor label > div { display:grid; grid-template-columns:1fr auto 1fr; align-items:center; gap:7px }.rule-editor i { font-style:normal }.rule-editor details { padding:10px 0; border-top:1px solid #e1d9e0 }.rule-editor details p { color:#7e747e; font-size:12px }.rule-editor footer { display:flex; gap:8px; margin-top:10px }.rule-editor footer button { flex:1; min-width:0 }.review-notice { margin:12px 0 0; color:#b54736; text-align:right }.submit-button { display:block; min-width:0; margin:14px 0 0 auto; }
.filter-row button { padding:5px 9px; border:0; border-radius:999px; background:#f1edf2; font-size:12px }.filter-row button.active { background:#e8d9ff; color:#7141af; font-weight:800 }.question-list { display:flex; flex-direction:column; max-height:560px; overflow:hidden }.question-list-scroll { min-height:0; overflow-y:auto; padding-right:4px; scrollbar-gutter:stable }.question-item { display:grid; grid-template-columns:1fr auto; align-items:center; gap:6px; width:100%; margin-top:9px; padding:7px; border:1px solid #ded5dc; border-radius:10px; background:white }.question-item.active { border-color:#b58cf3; background:#faf6ff }.question-select { display:grid; grid-template-columns:auto 1fr; gap:11px; min-width:0; padding:5px; border:0; background:transparent; text-align:left }.question-select > b { display:grid; place-items:center; width:34px; height:34px; border-radius:50%; background:#eee2ff; color:#784db0 }.question-select span { display:grid; gap:7px; min-width:0 }.question-select strong { overflow:hidden; text-overflow:ellipsis; white-space:nowrap }.question-delete { padding:7px; border:0; background:transparent; color:#ff4d34 }.empty-tip { color:#847d85; text-align:center }.selection-range { display:grid; grid-template-columns:1fr 1fr; gap:14px; margin-bottom:9px }.selection-range label { display:grid; grid-template-columns:1fr; gap:7px }.rules-list h2 span.invalid { background:#fff0e7; color:#bd4c31 }.rule-editor details textarea { margin-top:9px }
.task-history h2,.generation-card h2,.current-card h2,.question-list h2,.question-editor h2,.dimension-list h2,.rules-list h2,.rule-editor h2{font-family:var(--font-ui)}
.editor-fields.single{grid-template-columns:1fr}.options-table.single-score header,.options-table.single-score>div{grid-template-columns:50px minmax(190px,1fr) 75px 55px;min-width:480px}.question-editor.saving,.rule-editor.saving{opacity:.7}.rule-actions{display:flex;align-items:flex-start;gap:10px}.rules-list section .rule-actions button{border:0;background:transparent;color:#7443b0;font-weight:800}.rules-list section .rule-actions button:last-child{color:#ff4b35}.rules-list section .rule-actions button:disabled{opacity:.4}.submit-button:disabled{opacity:.5;cursor:not-allowed}
.ai-steps { display:grid; grid-template-columns:repeat(4,1fr); gap:10px; margin-top:14px; padding:12px; border:1px solid #d8cfd7; border-radius:13px; background:rgb(255 255 255 / 78%) }.ai-steps > div { display:flex; align-items:center; justify-content:center; gap:12px; min-height:68px; border:1px solid transparent; border-radius:10px; color:#878087 }.ai-steps > div.active { border-color:#b690f0; background:#faf5ff; color:#7141af }.ai-steps > div.done { color:#3c8b60 }.ai-steps b { display:grid; place-items:center; width:40px; height:40px; border:1px solid #d5ccd3; border-radius:50%; background:white; color:#211d22; font-size:20px }.ai-steps .active b { border-color:#8050c4; background:#8050c4; color:white }.ai-steps .done b { border-color:#75c59a; color:#278056 }.ai-steps span { font-weight:800 }.ai-steps small { display:block; margin-top:3px; color:#948d95; font-weight:500 }
@media (max-width:1250px) { .result-review-grid { grid-template-columns:245px 1fr }.rule-editor { grid-column:1/-1 } }
@media (max-width:1100px) { .generation-layout,.question-review-grid,.result-review-grid { grid-template-columns:1fr }.review-summary { align-items:flex-start; flex-direction:column; gap:18px }.review-summary dl { width:100% }.review-summary dl div { flex:1 }.rules-list section { grid-template-columns:150px 1fr }.rules-list section > button { grid-column:1/-1 }.rule-editor { grid-column:auto } }
@media (max-width:720px) { .ai-heading { align-items:flex-start; flex-direction:column }.task-row{align-items:flex-start;flex-direction:column}.ai-form-card { padding:22px 16px }.ai-form-card > label { grid-template-columns:1fr; gap:7px }.cover-input { grid-template-columns:1fr }.cover-input img { grid-column:auto }.form-error { margin-left:0 }.generate-button,.submitted-card button { width:100%; min-width:0 }.stat-row,.ai-steps { grid-template-columns:1fr 1fr }.review-summary dl { flex-direction:column }.review-summary dl div { display:flex; justify-content:space-between; border-top:1px solid #ddd; border-left:0; padding:9px }.editor-fields { grid-template-columns:1fr }.rules-list section { grid-template-columns:1fr }.rules-list section textarea { grid-column:auto }.submit-button { width:100% } }
</style>
