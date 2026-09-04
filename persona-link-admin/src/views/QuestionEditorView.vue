<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { deleteQuestion, getQuestions, getVersion, saveQuestion, type Question, type TestVersion } from '../api'
import { isReadOnly } from '../auth'

interface EditableOption {
  optionCode?: string
  optionText: string
  dimensionId: number | null
  scoreValue: number | null
}

const route = useRoute()
const router = useRouter()
const versionId = Number(route.params.id)
const version = ref<TestVersion | null>(null)
const questions = ref<Question[]>([])
const currentIndex = ref(0)
const readOnly = isReadOnly()
const currentQuestionId = ref<number | null>(null)
const question = ref('')
const questionType = ref(1)
const dimensionId = ref<number | null>(null)
const minSelectCount = ref(1)
const maxSelectCount = ref(1)
const requiredFlag = ref(1)
const options = ref<EditableOption[]>([])
const feedback = ref('')
const previewActive = ref(false)
const letters = 'ABCDEFGH'
const colorClasses = ['yellow', 'mint', 'purple', 'coral']
const previewTotal = computed(() => questions.value.length + (currentQuestionId.value ? 0 : 1))
const valid = computed(() => {
  const optionsValid = options.value.length >= 2 && options.value.every((option) => option.optionText.trim() && option.scoreValue !== null)
  if (!question.value.trim() || !optionsValid) return false
  if (questionType.value === 1) return dimensionId.value !== null
  return minSelectCount.value >= 1
    && minSelectCount.value <= maxSelectCount.value
    && maxSelectCount.value <= options.value.length
    && options.value.every((option) => option.dimensionId !== null)
})

function blankOptions(): EditableOption[] {
  return [
    { optionText: '', dimensionId: null, scoreValue: null },
    { optionText: '', dimensionId: null, scoreValue: null },
  ]
}

function showFeedback(message: string) {
  feedback.value = message
  window.setTimeout(() => { feedback.value = '' }, 2200)
}

function showQuestion(index: number) {
  const item = questions.value[index]
  currentIndex.value = index
  currentQuestionId.value = item?.id ?? null
  question.value = item?.questionText ?? ''
  questionType.value = item?.questionType ?? 1
  dimensionId.value = item?.dimensionId ?? null
  minSelectCount.value = item?.minSelectCount ?? 1
  maxSelectCount.value = item?.maxSelectCount ?? 1
  requiredFlag.value = item?.requiredFlag ?? 1
  options.value = item?.options.map((option) => ({
    optionCode: option.optionCode,
    optionText: option.optionText,
    dimensionId: option.dimensionId ?? null,
    scoreValue: option.scoreValue,
  })) ?? blankOptions()
}

function selectQuestion(event: Event) { showQuestion(Number((event.target as HTMLSelectElement).value)) }

async function load() {
  try {
    const [versionData, questionData] = await Promise.all([getVersion(versionId), getQuestions(versionId)])
    version.value = versionData
    questions.value = questionData
    showQuestion(0)
  } catch (error) { showFeedback(error instanceof Error ? error.message : '题目加载失败') }
}

function addOption() {
  if (options.value.length >= letters.length) return showFeedback('最多添加 8 个选项')
  options.value.push({ optionText: '', dimensionId: null, scoreValue: null })
}

function removeOption(index: number) {
  if (options.value.length <= 2) return showFeedback('至少保留 2 个选项')
  options.value.splice(index, 1)
}

async function save(label: string) {
  if (!valid.value) return showFeedback('请填写完整题目、计分维度、选项和分值')
  try {
    const singleChoice = questionType.value === 1
    const saved = await saveQuestion(versionId, {
      questionType: questionType.value,
      dimensionId: singleChoice ? dimensionId.value ?? undefined : undefined,
      minSelectCount: singleChoice ? 1 : minSelectCount.value,
      maxSelectCount: singleChoice ? 1 : maxSelectCount.value,
      questionNo: currentQuestionId.value ? questions.value[currentIndex.value].questionNo : questions.value.length + 1,
      questionText: question.value.trim(),
      requiredFlag: requiredFlag.value,
      sortNo: currentQuestionId.value ? questions.value[currentIndex.value].sortNo : questions.value.length + 1,
      options: options.value.map((option, index) => ({
        optionCode: option.optionCode || letters[index],
        optionText: option.optionText.trim(),
        dimensionId: singleChoice ? undefined : option.dimensionId ?? undefined,
        scoreValue: Number(option.scoreValue),
        sortNo: index + 1,
      })),
    }, currentQuestionId.value ?? undefined)
    const index = questions.value.findIndex((item) => item.id === saved.id)
    if (index >= 0) questions.value[index] = saved
    else questions.value.push(saved)
    showQuestion(index >= 0 ? index : questions.value.length - 1)
    showFeedback(`${label}成功`)
  } catch (error) { showFeedback(error instanceof Error ? error.message : '题目保存失败') }
}

function createNew() { showQuestion(questions.value.length) }

async function removeCurrent() {
  if (!currentQuestionId.value) return
  try { await deleteQuestion(currentQuestionId.value); questions.value.splice(currentIndex.value, 1); showQuestion(Math.max(0, currentIndex.value - 1)); showFeedback('题目已删除') }
  catch (error) { showFeedback(error instanceof Error ? error.message : '删除失败') }
}

onMounted(load)
</script>

<template>
  <section class="question-page">
    <header class="editor-header">
      <div><button class="back-link" type="button" @click="router.push('/types')">‹ 返回题型</button><h1>题目编辑 <span>✦</span></h1></div>
      <div class="header-actions">
        <button class="secondary-action" type="button" :disabled="readOnly" @click="save('保存题目')">保存题目</button>
        <button class="secondary-action" type="button" @click="previewActive = !previewActive">{{ previewActive ? '关闭高亮' : '预览' }}</button>
        <button class="primary-action" type="button" :disabled="readOnly" @click="createNew">新增题目</button>
      </div>
    </header>

    <ol class="steps" aria-label="题型配置进度">
      <li class="active"><b>1</b><span>基础信息</span></li><li class="active"><b>2</b><span>题目配置</span></li><li><b>3</b><span>计分规则</span></li><li><b>4</b><span>结果内容</span></li><li><b>5</b><span>发布设置</span></li>
    </ol>

    <div class="question-layout">
      <article class="question-card">
        <div class="question-index">第 {{ currentIndex + 1 }} 题</div>
        <label v-if="questions.length">选择题目<select :value="currentIndex" @change="selectQuestion"><option v-for="(item, index) in questions" :key="item.id" :value="index">第 {{ item.questionNo }} 题</option></select></label>
        <div class="question-settings">
          <label>题目类型<select v-model.number="questionType"><option :value="1">单选题</option><option :value="2">多选题</option></select></label>
          <label v-if="questionType === 1">计分维度<select v-model.number="dimensionId"><option :value="null" disabled>请选择计分维度</option><option v-for="item in version?.dimensions ?? []" :key="item.id" :value="item.id">{{ item.dimensionName }}</option></select></label>
          <label>是否必答<select v-model.number="requiredFlag"><option :value="1">是</option><option :value="0">否</option></select></label>
          <template v-if="questionType === 2">
            <label>最少选择<input v-model.number="minSelectCount" type="number" min="1" :max="options.length" /></label>
            <label>最多选择<input v-model.number="maxSelectCount" type="number" min="1" :max="options.length" /></label>
          </template>
        </div>
        <label>题干<textarea v-model="question" maxlength="120" rows="3" /></label>
        <div class="option-list">
          <div v-for="(option, index) in options" :key="index" class="option-editor">
            <span class="letter" :class="colorClasses[index % colorClasses.length]">{{ letters[index] }}</span>
            <input v-model="option.optionText" :aria-label="`选项 ${letters[index]}`" maxlength="60" placeholder="请输入选项内容" />
            <select v-if="questionType === 2" v-model.number="option.dimensionId" :aria-label="`选项 ${letters[index]} 计分维度`"><option :value="null" disabled>计分维度</option><option v-for="item in version?.dimensions ?? []" :key="item.id" :value="item.id">{{ item.dimensionName }}</option></select>
            <input v-model.number="option.scoreValue" class="option-score" type="number" :aria-label="`选项 ${letters[index]} 分值`" placeholder="分值" />
            <button type="button" :disabled="readOnly" :aria-label="`删除选项 ${letters[index]}`" @click="removeOption(index)">♲</button>
          </div>
        </div>
        <button class="add-option" type="button" :disabled="readOnly" @click="addOption">＋ 添加选项</button>
        <button v-if="currentQuestionId" class="add-option" type="button" :disabled="readOnly" @click="removeCurrent">删除当前题目</button>
      </article>

      <aside class="preview-card" :class="{ highlighted: previewActive }">
        <h2>移动端预览 <span>✦</span></h2>
        <div class="phone">
          <div class="mini-head"><span>‹</span><span>第 {{ currentIndex + 1 }} 题 / 共 {{ previewTotal }} 题</span><span>•••</span></div>
          <div class="mini-body">
            <h3>{{ question || '请填写题干' }} <i>♡</i></h3>
            <div v-for="(option, index) in options" :key="index" class="preview-option">
              <span class="letter" :class="colorClasses[index % colorClasses.length]">{{ letters[index] }}</span>
              <p>{{ option.optionText || '待填写选项' }}</p>
            </div>
          </div>
          <div class="home-bar"></div>
        </div>
      </aside>
    </div>
    <div v-if="feedback" class="toast" role="status">{{ feedback }}</div>
  </section>
</template>

<style scoped>
.question-page { color: var(--ink, #211d22); }
.editor-header { display: flex; align-items: flex-end; justify-content: space-between; gap: 22px; }.editor-header h1 { margin: 5px 0 0; font: 700 clamp(34px, 4vw, 46px) "STKaiti", "KaiTi", serif; }.editor-header h1 span { color: #a473df; }.back-link { padding: 0; border: 0; background: transparent; color: #766f78; cursor: pointer; }
.header-actions { display: flex; flex-wrap: wrap; gap: 13px; }.primary-action, .secondary-action { padding: 11px 22px; border: 1.5px solid #211d22; border-radius: 12px; background: white; font: inherit; font-weight: 800; cursor: pointer; }.primary-action { background: #ff654f; color: white; box-shadow: 2px 3px 0 #211d22; }
.steps { display: grid; grid-template-columns: repeat(5, 1fr); max-width: 760px; margin: 26px 0 22px; padding: 0; list-style: none; }.steps li { position: relative; display: grid; justify-items: center; gap: 7px; color: #7e7780; font-size: 13px; }.steps li:not(:last-child)::after { content: ""; position: absolute; z-index: 0; top: 18px; left: calc(50% + 24px); width: calc(100% - 48px); border-top: 1.5px solid #aaa2aa; }.steps b { z-index: 1; display: grid; place-items: center; width: 37px; height: 37px; border: 1.5px solid #aaa2aa; border-radius: 50%; background: #fffdf8; font-size: 16px; }.steps .active { color: #211d22; font-weight: 800; }.steps .active b { border-color: #8e58dc; background: #caa1ff; }.steps li:first-child:not(:last-child)::after { border-color: #8e58dc; }
.question-layout { display: grid; grid-template-columns: minmax(0, 1.55fr) minmax(330px, .8fr); gap: 28px; align-items: start; }.question-card, .preview-card { padding: 26px; border: 1.5px solid #211d22; border-radius: 19px; background: #fffdf9; }.question-index { display: inline-block; padding: 11px 24px; border: 1.5px solid #211d22; border-radius: 12px; background: #d7b3ff; font-size: 19px; font-weight: 800; }.question-card > label { display: grid; gap: 10px; margin-top: 26px; font-weight: 800; }.question-card textarea { width: 100%; resize: vertical; padding: 16px; border: 1px solid #aaa2aa; border-radius: 7px; background: white; font: inherit; font-size: 17px; line-height: 1.7; }
.question-settings { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 14px; margin-top: 22px; }.question-settings label { display: grid; gap: 7px; font-weight: 800; }.question-settings input, .question-settings select, .option-editor select { min-height: 42px; padding: 0 10px; border: 1px solid #aaa2aa; border-radius: 8px; background: white; font: inherit; }
.option-list { display: grid; gap: 13px; margin-top: 20px; }.option-editor, .preview-option { display: flex; align-items: center; gap: 12px; min-height: 64px; padding: 10px 14px; border: 1.5px solid #211d22; border-radius: 12px; background: white; }.option-editor input { flex: 1; min-width: 0; border: 0; outline: 0; background: transparent; font: inherit; }.option-editor .option-score { flex: none; width: 72px; padding: 9px; border: 1px solid #aaa2aa; border-radius: 8px; }.option-editor button { width: 34px; border: 0; background: transparent; color: #736d75; font-size: 20px; cursor: pointer; }.letter { flex: none; display: grid; place-items: center; width: 39px; height: 39px; border: 1.5px solid #211d22; border-radius: 50%; font-size: 21px; font-weight: 800; }.letter.yellow { background: #ffda65; }.letter.mint { background: #a9e3c8; }.letter.purple { background: #d0adff; }.letter.coral { background: #ffa39e; }.add-option { width: 100%; margin-top: 15px; padding: 15px; border: 1.5px dashed #8d858e; border-radius: 12px; background: transparent; font: inherit; font-weight: 700; cursor: pointer; }
.preview-card { background: linear-gradient(145deg, #f1e6ff, #d7c1ff); transition: transform .2s, box-shadow .2s; }.preview-card.highlighted { transform: translateY(-4px); box-shadow: 7px 9px 0 #211d22; }.preview-card h2 { margin: 0 0 13px; font: 700 24px "STKaiti", "KaiTi", serif; }.preview-card h2 span { color: #f3c640; }.phone { position: relative; min-height: 600px; overflow: hidden; border: 2px solid #211d22; border-radius: 35px; background-color: #fffdf9; background-image: linear-gradient(#efe7de 1px, transparent 1px), linear-gradient(90deg, #efe7de 1px, transparent 1px); background-size: 26px 26px; }.phone-status, .mini-head { display: flex; justify-content: space-between; gap: 10px; padding: 11px 22px; font-size: 12px; }.mini-head { align-items: center; padding-top: 4px; font-size: 13px; }.mini-head span:last-child { padding: 4px 10px; border: 1px solid #211d22; border-radius: 999px; }.mini-body { padding: 28px 15px 48px; }.mini-body h3 { position: relative; margin: 0 10px 25px; font: 700 23px/1.65 "STKaiti", "KaiTi", serif; }.mini-body h3 i { color: #b276ed; font-size: 34px; font-style: normal; }.preview-option { min-height: 59px; margin-bottom: 13px; padding: 9px 11px; }.preview-option p { margin: 0; font-size: 14px; line-height: 1.5; }.preview-option .letter { width: 35px; height: 35px; font-size: 18px; }.home-bar { position: absolute; left: 50%; bottom: 13px; width: 115px; height: 5px; border-radius: 999px; background: #211d22; transform: translateX(-50%); }
.toast { position: fixed; z-index: 60; right: 28px; bottom: 28px; padding: 13px 18px; border: 1.5px solid #211d22; border-radius: 12px; background: #211d22; color: white; box-shadow: 4px 5px 0 #d9bdff; }
@media (max-width: 1100px) { .question-layout { grid-template-columns: 1fr; }.preview-card { max-width: 480px; }.phone { min-height: 560px; } }
@media (max-width: 760px) { .editor-header { align-items: stretch; flex-direction: column; }.header-actions { display: grid; grid-template-columns: 1fr 1fr; }.header-actions .primary-action { grid-column: 1 / -1; }.steps { overflow-x: auto; grid-template-columns: repeat(5, 115px); padding-bottom: 8px; }.question-card, .preview-card { padding: 17px; }.question-settings { grid-template-columns: 1fr; }.option-editor { flex-wrap: wrap; }.option-editor > input:first-of-type { flex-basis: calc(100% - 70px); }.preview-card { max-width: 100%; }.phone { min-height: 540px; } }
</style>
