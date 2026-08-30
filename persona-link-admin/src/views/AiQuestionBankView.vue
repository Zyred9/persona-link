<script setup lang="ts">
import { computed, onUnmounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'

type ReviewState = 'pending' | 'passed' | 'returned'

interface Question {
  content: string
  type: '单选题' | '多选题'
  dimension: string
  options: string[]
  review: ReviewState
}

interface ResultRule {
  range: string
  title: string
  description: string
}

interface Dimension {
  name: string
  icon: string
  review: ReviewState
  rules: ResultRule[]
}

const router = useRouter()
const step = ref(1)
const formTouched = ref(false)
const proposalVersion = ref(1)
const progress = ref(0)
const reviewTab = ref<'questions' | 'results'>('questions')
const currentQuestionIndex = ref(0)
const currentDimensionIndex = ref(0)
const reviewMessage = ref('')
let generationTimer: number | undefined

const form = reactive({
  name: '情侣沟通方式测试',
  type: '双人测试',
  prompt: '生成一套适合情侣的沟通方式测试，语气轻松，不贴标签。',
  bankCount: 100,
  drawCount: 30,
})

const questions = reactive<Question[]>([
  {
    content: '发生分歧时，你更常先做什么？',
    type: '单选题',
    dimension: '冲突处理',
    options: ['先冷静一下，再继续沟通', '马上把自己的想法说清楚', '先听听对方怎么想', '暂时不谈，等情绪过去'],
    review: 'pending',
  },
  {
    content: '你希望对方怎样回应你的情绪？',
    type: '多选题',
    dimension: '情感表达',
    options: ['认真听我说完', '给我一个拥抱', '一起想解决办法', '先给我一点空间'],
    review: 'pending',
  },
  {
    content: '沟通中你更在意哪件事？',
    type: '单选题',
    dimension: '倾听习惯',
    options: ['被准确理解', '快速解决问题', '保持气氛轻松', '双方都有表达机会'],
    review: 'pending',
  },
  {
    content: '讨论重要决定时，你通常会？',
    type: '单选题',
    dimension: '关系主动',
    options: ['主动提出计划', '先了解对方期待', '列出选项一起选', '等更合适的时机再聊'],
    review: 'pending',
  },
])

const dimensions = reactive<Dimension[]>([
  {
    name: '冲突处理',
    icon: '⚡',
    review: 'pending',
    rules: [
      { range: '0 ≤ 分数 < 40', title: '回避观察者', description: '你倾向于回避冲突，避免直接面对问题。' },
      { range: '40 ≤ 分数 < 70', title: '温和协调者', description: '你倾向于通过协商与妥协，寻求双方都能接受的方案。' },
      { range: '70 ≤ 分数 ≤ 100', title: '坦诚沟通者', description: '你倾向于直接表达观点，积极沟通解决问题。' },
    ],
  },
  {
    name: '情感表达',
    icon: '💗',
    review: 'pending',
    rules: [
      { range: '0 ≤ 分数 < 40', title: '含蓄感受者', description: '你更习惯把感受留在心里，用行动表达在意。' },
      { range: '40 ≤ 分数 < 70', title: '自然表达者', description: '你会在合适的时候说出感受，也尊重彼此节奏。' },
      { range: '70 ≤ 分数 ≤ 100', title: '热情分享者', description: '你乐于直接分享情绪，让关系保持充分连接。' },
    ],
  },
  {
    name: '倾听习惯',
    icon: '☁',
    review: 'pending',
    rules: [
      { range: '0 ≤ 分数 < 40', title: '快速回应者', description: '你习惯尽快回应，偶尔会错过对方话里的细节。' },
      { range: '40 ≤ 分数 < 70', title: '平衡倾听者', description: '你能在表达自己与理解对方之间找到平衡。' },
      { range: '70 ≤ 分数 ≤ 100', title: '耐心共情者', description: '你愿意先听完整，再回应对方真正的需要。' },
    ],
  },
  {
    name: '关系主动',
    icon: '♧',
    review: 'pending',
    rules: [
      { range: '0 ≤ 分数 < 40', title: '从容跟随者', description: '你习惯观察关系节奏，在明确后再行动。' },
      { range: '40 ≤ 分数 < 70', title: '默契协作者', description: '你会根据情境主动，也愿意配合对方。' },
      { range: '70 ≤ 分数 ≤ 100', title: '积极推动者', description: '你乐于主动发起沟通，为关系创造更多可能。' },
    ],
  },
])

const stepLabels = ['填写题型信息', '生成题库', '审核题目与结果', '提交题型库']
const formError = computed(() => {
  if (!form.name.trim()) return '请填写题型名称'
  if (!form.prompt.trim()) return '请填写生成要求'
  if (!Number.isInteger(form.bankCount) || form.bankCount < 10 || form.bankCount > 500) return '题库题数需为 10—500 的整数'
  if (!Number.isInteger(form.drawCount) || form.drawCount < 1 || form.drawCount > form.bankCount) return '单次答题数需为 1—题库题数的整数'
  return ''
})
const generatedCount = computed(() => Math.round((form.bankCount * progress.value) / 100))
const generatedSingleCount = computed(() => Math.round(generatedCount.value * 0.72))
const generatedMultipleCount = computed(() => generatedCount.value - generatedSingleCount.value)
const currentPreview = computed(() => questions[Math.min(Math.floor(progress.value / 25), questions.length - 1)])
const currentQuestion = computed(() => questions[currentQuestionIndex.value])
const currentDimension = computed(() => dimensions[currentDimensionIndex.value])
const questionReviewDone = computed(() => questions.every((question) => question.review === 'passed'))
const resultReviewDone = computed(() => dimensions.every((dimension) => dimension.review === 'passed'))
const reviewDone = computed(() => questionReviewDone.value && resultReviewDone.value)
const passedQuestionCount = computed(() => questions.filter((question) => question.review === 'passed').length)
const returnedQuestionCount = computed(() => questions.filter((question) => question.review === 'returned').length)

function refreshProposal() {
  formTouched.value = true
  if (formError.value) return
  proposalVersion.value += 1
}

function startGeneration() {
  formTouched.value = true
  if (formError.value) return
  step.value = 2
  progress.value = 8
  generationTimer = window.setInterval(() => {
    progress.value = Math.min(progress.value + 8, 100)
    if (progress.value === 100) stopGenerationTimer()
  }, 260)
}

function stopGenerationTimer() {
  if (generationTimer === undefined) return
  window.clearInterval(generationTimer)
  generationTimer = undefined
}

function enterReview() {
  stopGenerationTimer()
  progress.value = 100
  step.value = 3
}

function setQuestionReview(review: ReviewState) {
  currentQuestion.value.review = review
  reviewMessage.value = review === 'passed' ? '当前题目已通过' : '当前题目已退回修改'
  if (currentQuestionIndex.value < questions.length - 1) currentQuestionIndex.value += 1
}

function setAllQuestionReview(review: ReviewState) {
  questions.forEach((question) => {
    question.review = review
  })
  reviewMessage.value = review === 'passed' ? '所有示例题目已通过' : '所有示例题目已退回'
}

function setDimensionReview(review: ReviewState) {
  currentDimension.value.review = review
  reviewMessage.value = review === 'passed' ? '当前结果规则已通过' : '当前结果规则已退回修改'
  if (currentDimensionIndex.value < dimensions.length - 1) currentDimensionIndex.value += 1
}

function submitLibrary() {
  const invalidQuestionIndex = questions.findIndex((question) => !question.content.trim() || question.options.some((option) => !option.trim()))
  if (invalidQuestionIndex >= 0) {
    questions[invalidQuestionIndex].review = 'pending'
    currentQuestionIndex.value = invalidQuestionIndex
    reviewTab.value = 'questions'
    reviewMessage.value = `第 ${invalidQuestionIndex + 1} 题内容或选项不完整，请补全后重新审核`
    return
  }
  const invalidDimensionIndex = dimensions.findIndex((dimension) => dimension.rules.some((rule) => !rule.title.trim() || !rule.description.trim()))
  if (invalidDimensionIndex >= 0) {
    dimensions[invalidDimensionIndex].review = 'pending'
    currentDimensionIndex.value = invalidDimensionIndex
    reviewTab.value = 'results'
    reviewMessage.value = `${dimensions[invalidDimensionIndex].name}存在不完整的结果规则，请补全后重新审核`
    return
  }
  if (!reviewDone.value) {
    reviewMessage.value = returnedQuestionCount.value > 0 || dimensions.some((dimension) => dimension.review === 'returned')
      ? '请先修改并通过所有退回项'
      : '请先审核全部题目与结果规则'
    return
  }
  step.value = 4
}

function goToTypeLibrary() {
  void router.push('/types')
}

onUnmounted(stopGenerationTimer)
</script>

<template>
  <section class="ai-page">
    <div class="demo-banner" role="status">
      <span>演示模式</span>
      本页交互仅保存在当前页面，尚未接入 AI 生成与后台保存接口。
    </div>

    <header class="page-heading">
      <div>
        <p class="eyebrow">AI QUESTION BANK</p>
        <h1>AI 题库助手 <span aria-hidden="true">♡</span></h1>
        <p>{{ step === 1 ? '填写题型信息，确认方案后生成可审核题库' : step === 2 ? 'AI 正在生成题库，完成后进入人工审核' : step === 3 ? '逐项审核题目、选项、计分维度和结果规则' : '题型草稿已经提交到题型库' }}</p>
      </div>
      <span class="draft-chip">AI 草稿</span>
    </header>

    <section class="steps" aria-label="创建进度">
      <div v-for="(label, index) in stepLabels" :key="label" class="step-item" :class="{ active: step === index + 1, done: step > index + 1 }">
        <span class="step-number">{{ step > index + 1 ? '✓' : index + 1 }}</span>
        <span>{{ label }}</span>
      </div>
    </section>

    <section v-if="step === 1" class="stage-grid form-stage">
      <form class="panel form-panel" @submit.prevent="startGeneration">
        <div class="panel-title">
          <div>
            <span class="section-icon">✎</span>
            <h2>填写题型信息</h2>
          </div>
          <span>第 1 步</span>
        </div>

        <label>
          <span>题型名称</span>
          <input v-model="form.name" type="text" maxlength="40" placeholder="例如：情侣沟通方式测试" />
        </label>
        <label>
          <span>测试类型</span>
          <select v-model="form.type">
            <option>双人测试</option>
            <option>单人测试</option>
          </select>
        </label>
        <label>
          <span>生成要求</span>
          <textarea v-model="form.prompt" rows="5" maxlength="300" placeholder="描述测试主题、语气和需要避免的内容"></textarea>
          <small>{{ form.prompt.length }}/300</small>
        </label>
        <div class="number-grid">
          <label>
            <span>题库题数</span>
            <input v-model.number="form.bankCount" type="number" min="10" max="500" />
          </label>
          <label>
            <span>单次答题</span>
            <input v-model.number="form.drawCount" type="number" min="1" :max="form.bankCount" />
          </label>
        </div>
        <p v-if="formTouched && formError" class="form-error" role="alert">{{ formError }}</p>
      </form>

      <aside class="panel proposal-panel">
        <div class="panel-title">
          <div>
            <span class="section-icon">✦</span>
            <h2>生成方案预览</h2>
          </div>
          <span>方案 V{{ proposalVersion }}</span>
        </div>
        <div class="proposal-card">
          <span class="status-chip waiting">待确认</span>
          <h3>{{ form.name || '未命名题型' }}</h3>
          <p>{{ form.prompt || '填写生成要求后，这里会显示方案摘要。' }}</p>
          <div class="dimension-tags">
            <span v-for="dimension in dimensions" :key="dimension.name">{{ dimension.name }}</span>
          </div>
          <dl>
            <div><dt>测试类型</dt><dd>{{ form.type }}</dd></div>
            <div><dt>题库题数</dt><dd>{{ form.bankCount }} 题</dd></div>
            <div><dt>单次答题</dt><dd>{{ form.drawCount }} 题</dd></div>
            <div><dt>题型构成</dt><dd>单选 + 多选</dd></div>
          </dl>
        </div>
        <div class="actions">
          <button class="secondary-button" type="button" @click="refreshProposal">↻ 刷新方案</button>
          <button class="primary-button" type="button" @click="startGeneration">✦ 确认并开始生成</button>
        </div>
      </aside>
    </section>

    <section v-else-if="step === 2" class="stage-grid generation-stage">
      <div class="panel generation-panel">
        <div class="panel-title">
          <div><span class="section-icon">✦</span><h2>正在生成题库</h2></div>
          <span>{{ progress === 100 ? '已完成' : '生成中' }}</span>
        </div>
        <div class="generation-card">
          <div class="generation-head">
            <h3>{{ form.name }}</h3>
            <span class="status-chip">{{ progress === 100 ? '等待审核' : '生成中' }}</span>
          </div>
          <strong>{{ generatedCount }} <small>/ {{ form.bankCount }} 题</small></strong>
          <div class="progress-track" :aria-label="`生成进度 ${progress}%`">
            <span :style="{ width: `${progress}%` }">{{ progress }}%</span>
          </div>
          <p>当前批次　第 {{ Math.max(1, Math.ceil(progress / 20)) }} 批 / 共 5 批</p>
          <div class="generation-note">♡ {{ progress === 100 ? '题库生成完成，请进入审核。' : 'AI 正在生成题目，你可以查看右侧当前内容。' }}</div>
        </div>
        <div class="stat-grid">
          <div><span>▣</span><p>已生成<strong>{{ generatedCount }}</strong></p></div>
          <div><span>◎</span><p>单选题<strong>{{ generatedSingleCount }}</strong></p></div>
          <div><span>☑</span><p>多选题<strong>{{ generatedMultipleCount }}</strong></p></div>
        </div>
      </div>

      <aside class="panel preview-panel">
        <div class="panel-title">
          <div><span class="section-icon">⌁</span><h2>当前生成内容</h2></div>
          <span>第 {{ Math.max(1, generatedCount) }} 题</span>
        </div>
        <div class="question-tags">
          <span>{{ currentPreview.type }}</span>
          <span>{{ currentPreview.dimension }}</span>
        </div>
        <h3>{{ currentPreview.content }}</h3>
        <ol class="preview-options">
          <li v-for="(option, index) in currentPreview.options" :key="option"><span>{{ String.fromCharCode(65 + index) }}</span>{{ option }}</li>
        </ol>
        <ul class="quality-list">
          <li><span>♢ 维度覆盖</span><strong>通过</strong></li>
          <li><span>▤ 计分规则</span><strong>通过</strong></li>
          <li><span>⌕ 重复题目</span><em>未发现</em></li>
        </ul>
        <button class="primary-button full-button" type="button" :disabled="progress < 100" @click="enterReview">
          {{ progress < 100 ? `生成中 ${progress}%` : '进入人工审核 →' }}
        </button>
      </aside>
    </section>

    <section v-else-if="step === 3" class="review-stage">
      <div class="panel test-summary">
        <div>
          <span class="section-icon">▣</span>
          <div><h2>{{ form.name }}</h2><span class="status-chip">AI 草稿</span></div>
        </div>
        <dl>
          <div><dt>题库</dt><dd>{{ form.bankCount }}<small> 题</small></dd></div>
          <div><dt>单次答题</dt><dd>{{ form.drawCount }}<small> 题</small></dd></div>
          <div><dt>计分维度</dt><dd>{{ dimensions.length }}<small> 个</small></dd></div>
        </dl>
      </div>

      <div class="review-tabs" role="tablist">
        <button id="questions-tab" :class="{ active: reviewTab === 'questions' }" type="button" role="tab" aria-controls="questions-panel" :aria-selected="reviewTab === 'questions'" @click="reviewTab = 'questions'">
          题目与选项 <span>{{ questions.length }}</span>
        </button>
        <button id="results-tab" :class="{ active: reviewTab === 'results' }" type="button" role="tab" aria-controls="results-panel" :aria-selected="reviewTab === 'results'" @click="reviewTab = 'results'">
          结果规则 <span>{{ dimensions.reduce((total, item) => total + item.rules.length, 0) }}</span>
        </button>
      </div>

      <div v-if="reviewTab === 'questions'" id="questions-panel" class="review-grid" role="tabpanel" aria-labelledby="questions-tab">
        <aside class="panel review-list-panel">
          <div class="panel-title">
            <div><h2>题目列表</h2></div>
            <button class="text-button" type="button" @click="setAllQuestionReview('passed')">全部通过</button>
          </div>
          <div class="filter-row">
            <span>全部 {{ questions.length }}</span>
            <span class="passed">已通过 {{ passedQuestionCount }}</span>
            <span class="returned">已退回 {{ returnedQuestionCount }}</span>
          </div>
          <button
            v-for="(question, index) in questions"
            :key="question.content"
            class="question-list-item"
            :class="{ active: currentQuestionIndex === index }"
            type="button"
            @click="currentQuestionIndex = index"
          >
            <span class="question-index">{{ index + 1 }}</span>
            <span><strong>{{ question.content }}</strong><small>{{ question.type }} · {{ question.dimension }}</small></span>
            <em :class="question.review">{{ question.review === 'passed' ? '已通过' : question.review === 'returned' ? '已退回' : '待审核' }}</em>
          </button>
        </aside>

        <div class="panel question-editor">
          <div class="panel-title">
            <div><h2>题目与选项</h2></div>
            <span>第 {{ currentQuestionIndex + 1 }} / {{ questions.length }} 题</span>
          </div>
          <label>
            <span>题目内容</span>
            <input v-model="currentQuestion.content" type="text" maxlength="80" />
          </label>
          <div class="number-grid">
            <label><span>题型</span><select v-model="currentQuestion.type"><option>单选题</option><option>多选题</option></select></label>
            <label><span>计分维度</span><select v-model="currentQuestion.dimension"><option v-for="dimension in dimensions" :key="dimension.name">{{ dimension.name }}</option></select></label>
          </div>
          <div class="option-editor">
            <span>选项</span>
            <label v-for="(option, index) in currentQuestion.options" :key="index">
              <b>{{ String.fromCharCode(65 + index) }}</b>
              <input v-model="currentQuestion.options[index]" type="text" maxlength="50" />
              <em>分值 {{ currentQuestion.options.length - index }}</em>
            </label>
          </div>
          <div class="editor-actions">
            <button class="secondary-button return-button" type="button" @click="setQuestionReview('returned')">↩ 退回修改</button>
            <button class="secondary-button" type="button" :disabled="currentQuestionIndex === 0" @click="currentQuestionIndex--">← 上一题</button>
            <button class="secondary-button" type="button" :disabled="currentQuestionIndex === questions.length - 1" @click="currentQuestionIndex++">下一题 →</button>
            <button class="approve-button" type="button" @click="setQuestionReview('passed')">✓ 通过题目</button>
          </div>
        </div>
      </div>

      <div v-else id="results-panel" class="result-grid" role="tabpanel" aria-labelledby="results-tab">
        <aside class="panel dimension-list">
          <div class="panel-title"><div><h2>计分维度</h2></div><span>{{ dimensions.length }} 个</span></div>
          <button
            v-for="(dimension, index) in dimensions"
            :key="dimension.name"
            :class="{ active: currentDimensionIndex === index }"
            type="button"
            @click="currentDimensionIndex = index"
          >
            <span>{{ dimension.icon }}</span>
            <span><strong>{{ dimension.name }}</strong><small>{{ dimension.rules.length }} 条规则</small></span>
            <em :class="dimension.review">{{ dimension.review === 'passed' ? '✓' : dimension.review === 'returned' ? '↩' : '·' }}</em>
          </button>
        </aside>

        <div class="panel rule-panel">
          <div class="panel-title">
            <div><h2>{{ currentDimension.name }} · 结果规则</h2></div>
            <span>0—100 已完整覆盖</span>
          </div>
          <article v-for="rule in currentDimension.rules" :key="rule.range" class="rule-card">
            <span>{{ rule.range }}</span>
            <label><small>结果名称</small><input v-model="rule.title" type="text" maxlength="20" /></label>
            <label><small>基础结果文案</small><textarea v-model="rule.description" rows="2" maxlength="120"></textarea></label>
          </article>
          <div class="editor-actions">
            <button class="secondary-button return-button" type="button" @click="setDimensionReview('returned')">↩ 退回修改</button>
            <button class="approve-button" type="button" @click="setDimensionReview('passed')">✓ 通过本维度</button>
          </div>
        </div>
      </div>

      <p v-if="reviewMessage" class="review-message" role="status">{{ reviewMessage }}</p>
      <div class="submit-bar">
        <p><strong>题目 {{ questionReviewDone ? '已完成' : '待审核' }}</strong><span>结果规则 {{ resultReviewDone ? '已完成' : '待审核' }}</span></p>
        <button class="primary-button" type="button" @click="submitLibrary">完成审核并提交 →</button>
      </div>
    </section>

    <section v-else class="success-stage">
      <div class="success-banner">✓ 已提交题型库，当前首页展示状态为“未展示”</div>
      <div class="panel success-card">
        <span class="success-icon">✓</span>
        <p class="eyebrow">SUBMITTED</p>
        <h2>{{ form.name }}</h2>
        <p>题型草稿已进入题型库。发布、版本控制和首页展示仍需在题型管理中完成。</p>
        <dl>
          <div><dt>题库</dt><dd>{{ form.bankCount }} 题</dd></div>
          <div><dt>单次答题</dt><dd>{{ form.drawCount }} 题</dd></div>
          <div><dt>审核结果</dt><dd>{{ passedQuestionCount }} 题通过 / {{ returnedQuestionCount }} 题退回</dd></div>
          <div><dt>首页状态</dt><dd>未展示</dd></div>
        </dl>
        <button class="primary-button" type="button" @click="goToTypeLibrary">返回题型列表 →</button>
      </div>
    </section>
  </section>
</template>

<style scoped>
.ai-page {
  --ink: #16151a;
  --muted: #6f7280;
  --line: #d9d9df;
  --purple: #7656df;
  --purple-soft: #f3edff;
  --coral: #ff5838;
  --green: #24915d;
  color: var(--ink);
  max-width: 1420px;
  margin: 0 auto;
}

button,
input,
select,
textarea {
  font: inherit;
}

button {
  cursor: pointer;
}

button:disabled {
  cursor: not-allowed;
  opacity: 0.5;
}

.demo-banner,
.success-banner {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 20px;
  padding: 11px 16px;
  border: 1px solid #e2b84b;
  border-radius: 10px;
  background: #fff8dc;
  color: #6f5510;
  font-size: 14px;
}

.demo-banner span {
  padding: 3px 9px;
  border-radius: 999px;
  background: #ffdf7e;
  color: #443000;
  font-weight: 800;
}

.page-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  margin-bottom: 22px;
}

.eyebrow {
  margin: 0 0 4px;
  color: var(--purple);
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0.14em;
}

.page-heading h1 {
  margin: 0;
  font-size: clamp(30px, 4vw, 46px);
  letter-spacing: 0.03em;
}

.page-heading h1 span {
  color: var(--purple);
  font-family: cursive;
}

.page-heading p:not(.eyebrow) {
  margin: 8px 0 0;
  color: var(--muted);
}

.draft-chip,
.status-chip,
.question-tags span,
.dimension-tags span {
  display: inline-flex;
  align-items: center;
  width: fit-content;
  padding: 5px 11px;
  border-radius: 999px;
  background: var(--purple-soft);
  color: var(--purple);
  font-size: 13px;
  font-style: normal;
  font-weight: 700;
}

.steps {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 10px;
  margin-bottom: 20px;
  padding: 12px;
  border: 1px solid var(--line);
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.76);
}

.step-item {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  min-height: 62px;
  border: 1px solid transparent;
  border-radius: 12px;
  color: #777984;
  font-weight: 700;
}

.step-item.active {
  border-color: #b99cff;
  background: linear-gradient(135deg, #fbf8ff, #f0e8ff);
  color: var(--purple);
}

.step-item.done {
  color: #477b62;
}

.step-number {
  display: grid;
  width: 34px;
  height: 34px;
  place-items: center;
  border: 1px solid #d7d7dd;
  border-radius: 50%;
  background: #fff;
  color: var(--ink);
}

.active .step-number {
  border-color: var(--purple);
  background: var(--purple);
  color: #fff;
}

.done .step-number {
  border-color: #b8e2c6;
  background: #edf9f0;
  color: var(--green);
}

.stage-grid,
.review-grid,
.result-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(390px, 0.85fr);
  gap: 16px;
}

.panel {
  border: 1.5px solid var(--ink);
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.9);
  box-shadow: 0 3px 0 rgba(30, 22, 52, 0.05);
}

.form-panel,
.proposal-panel,
.generation-panel,
.preview-panel,
.review-list-panel,
.question-editor,
.dimension-list,
.rule-panel {
  padding: 20px;
}

.panel-title,
.panel-title > div {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.panel-title {
  margin-bottom: 18px;
}

.panel-title h2 {
  margin: 0;
  font-size: 20px;
}

.panel-title > span {
  color: var(--purple);
  font-size: 13px;
  font-weight: 700;
}

.section-icon {
  display: grid;
  width: 40px;
  height: 40px;
  flex: 0 0 auto;
  place-items: center;
  border-radius: 50%;
  background: var(--purple-soft);
  color: var(--purple);
  font-size: 22px;
}

label {
  display: grid;
  gap: 7px;
  margin-bottom: 15px;
  color: #383741;
  font-size: 14px;
  font-weight: 700;
}

input,
select,
textarea {
  width: 100%;
  box-sizing: border-box;
  border: 1px solid #cfd0d8;
  border-radius: 9px;
  outline: none;
  background: #fff;
  color: var(--ink);
}

input,
select {
  min-height: 44px;
  padding: 0 13px;
}

textarea {
  resize: vertical;
  padding: 12px 13px;
}

input:focus,
select:focus,
textarea:focus {
  border-color: var(--purple);
  box-shadow: 0 0 0 3px rgba(118, 86, 223, 0.1);
}

label small {
  justify-self: end;
  color: #92939d;
  font-weight: 500;
}

.number-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
}

.form-error {
  margin: -4px 0 0;
  color: #d83c29;
  font-size: 13px;
}

.proposal-panel {
  display: flex;
  flex-direction: column;
}

.proposal-card {
  flex: 1;
  padding: 22px;
  border: 1px solid #cbb7ff;
  border-radius: 14px;
  background: linear-gradient(145deg, #faf7ff, #fff 72%);
}

.proposal-card h3 {
  margin: 12px 0 8px;
  font-size: 24px;
}

.proposal-card > p {
  min-height: 42px;
  color: var(--muted);
  line-height: 1.6;
}

.dimension-tags,
.question-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.proposal-card dl,
.success-card dl {
  margin: 18px 0 0;
}

.proposal-card dl div,
.success-card dl div {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  padding: 11px 0;
  border-bottom: 1px solid #e7e3ef;
}

dt {
  color: var(--muted);
}

dd {
  margin: 0;
  font-weight: 700;
}

.actions,
.editor-actions {
  display: flex;
  gap: 10px;
  margin-top: 16px;
}

.primary-button,
.secondary-button,
.approve-button {
  min-height: 44px;
  padding: 0 18px;
  border-radius: 10px;
  font-weight: 800;
}

.primary-button {
  flex: 1;
  border: 1.5px solid var(--ink);
  background: linear-gradient(135deg, #ff744f, var(--coral));
  color: #fff;
  box-shadow: 0 2px 0 #bc351f;
}

.secondary-button {
  border: 1px solid #aeb0ba;
  background: #fff;
  color: var(--ink);
}

.approve-button {
  border: 1px solid #79c99c;
  background: #ecf9f0;
  color: #18774a;
}

.return-button {
  border-color: #ef9a87;
  color: #c63e28;
}

.full-button {
  width: 100%;
  margin-top: auto;
}

.generation-card {
  padding: 22px;
  border: 1px solid #ccb8ff;
  border-radius: 13px;
  background: linear-gradient(145deg, #f8f4ff, #fff);
}

.generation-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.generation-head h3 {
  margin: 0;
  font-size: 22px;
}

.generation-card > strong {
  display: block;
  margin: 22px 0 12px;
  color: var(--coral);
  font-size: 44px;
  text-align: center;
}

.generation-card > strong small {
  color: #40414b;
  font-size: 20px;
}

.progress-track {
  overflow: hidden;
  height: 20px;
  border-radius: 999px;
  background: #e7ddff;
}

.progress-track span {
  display: block;
  min-width: 42px;
  height: 100%;
  border-radius: inherit;
  background: linear-gradient(90deg, #ff4d2d, #ff754b);
  color: #fff;
  font-size: 12px;
  font-weight: 800;
  line-height: 20px;
  text-align: center;
  transition: width 0.2s ease;
}

.generation-card > p {
  color: #555762;
}

.generation-note {
  padding: 10px 12px;
  border-radius: 8px;
  background: var(--purple-soft);
  color: var(--purple);
  font-size: 13px;
}

.stat-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 10px;
  margin-top: 14px;
}

.stat-grid > div {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 14px;
  border: 1px solid var(--line);
  border-radius: 10px;
}

.stat-grid > div > span {
  color: var(--purple);
  font-size: 24px;
}

.stat-grid p {
  display: grid;
  gap: 3px;
  margin: 0;
  color: var(--muted);
  font-size: 12px;
}

.stat-grid strong {
  color: var(--ink);
  font-size: 22px;
}

.preview-panel {
  display: flex;
  flex-direction: column;
}

.preview-panel > h3 {
  margin: 16px 0;
  font-size: 19px;
}

.preview-options {
  display: grid;
  gap: 8px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.preview-options li {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 9px 11px;
  border: 1px solid #d3d3da;
  border-radius: 8px;
}

.preview-options li span {
  display: grid;
  width: 26px;
  height: 26px;
  place-items: center;
  border: 1px solid var(--ink);
  border-radius: 50%;
  background: #ffda72;
  font-weight: 800;
}

.preview-options li:nth-child(2) span { background: #a9e7cb; }
.preview-options li:nth-child(3) span { background: #cbb4ff; }
.preview-options li:nth-child(4) span { background: #ffaaa0; }

.quality-list {
  margin: 14px 0;
  padding: 12px 0 0;
  border-top: 1px dashed #cfd0d8;
  list-style: none;
}

.quality-list li {
  display: flex;
  justify-content: space-between;
  padding: 5px 0;
}

.quality-list strong { color: var(--green); }
.quality-list em { color: #555762; font-style: normal; }

.test-summary {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  padding: 16px 22px;
}

.test-summary > div,
.test-summary > div > div {
  display: flex;
  align-items: center;
  gap: 12px;
}

.test-summary h2 {
  margin: 0;
  font-size: 24px;
}

.test-summary dl {
  display: flex;
  margin: 0;
}

.test-summary dl div {
  min-width: 130px;
  padding: 4px 22px;
  border-left: 1px solid #dddde3;
  text-align: center;
}

.test-summary dd {
  margin-top: 6px;
  font-size: 25px;
}

.test-summary dd small {
  font-size: 12px;
}

.review-tabs {
  display: flex;
  margin: 14px 0 10px;
}

.review-tabs button {
  min-width: 210px;
  padding: 11px 18px;
  border: 1px solid #cfd0d8;
  background: #fff;
  color: #373740;
}

.review-tabs button:first-child { border-radius: 9px 0 0 9px; }
.review-tabs button:last-child { border-radius: 0 9px 9px 0; }
.review-tabs button.active { border-color: var(--purple); background: var(--purple-soft); color: var(--purple); font-weight: 800; }
.review-tabs button span { margin-left: 5px; color: inherit; }

.review-grid {
  grid-template-columns: minmax(300px, 0.72fr) minmax(0, 1.28fr);
}

.filter-row {
  display: flex;
  gap: 7px;
  margin-bottom: 10px;
  font-size: 12px;
}

.filter-row span {
  padding: 5px 8px;
  border-radius: 999px;
  background: #f2f2f5;
}

.filter-row .passed { background: #eaf8ef; color: var(--green); }
.filter-row .returned { background: #fff0ed; color: #cb462f; }

.text-button {
  border: 0;
  background: transparent;
  color: var(--purple);
  font-weight: 700;
}

.question-list-item,
.dimension-list > button {
  display: grid;
  grid-template-columns: auto 1fr auto;
  align-items: center;
  gap: 10px;
  width: 100%;
  margin-top: 9px;
  padding: 12px;
  border: 1px solid #dddde3;
  border-radius: 10px;
  background: #fff;
  color: var(--ink);
  text-align: left;
}

.question-list-item.active,
.dimension-list > button.active {
  border-color: #b99cff;
  background: #f9f6ff;
}

.question-index {
  display: grid;
  width: 30px;
  height: 30px;
  place-items: center;
  border-radius: 50%;
  background: var(--purple-soft);
  color: var(--purple);
  font-weight: 800;
}

.question-list-item > span:nth-child(2),
.dimension-list > button > span:nth-child(2) {
  display: grid;
  gap: 5px;
}

.question-list-item small,
.dimension-list small {
  color: var(--muted);
}

.question-list-item em,
.dimension-list em {
  font-size: 12px;
  font-style: normal;
}

em.passed { color: var(--green); }
em.returned { color: #cb462f; }
em.pending { color: #a46c00; }

.option-editor > span {
  display: block;
  margin-bottom: 8px;
  font-size: 14px;
  font-weight: 700;
}

.option-editor label {
  display: grid;
  grid-template-columns: 30px 1fr auto;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.option-editor b {
  display: grid;
  width: 30px;
  height: 30px;
  place-items: center;
  border: 1px solid #b8b9c0;
  border-radius: 7px;
}

.option-editor em {
  color: var(--muted);
  font-size: 12px;
  font-style: normal;
}

.editor-actions {
  justify-content: flex-end;
  flex-wrap: wrap;
}

.result-grid {
  grid-template-columns: minmax(250px, 0.42fr) minmax(0, 1fr);
}

.dimension-list > button > span:first-child {
  display: grid;
  width: 42px;
  height: 42px;
  place-items: center;
  border-radius: 50%;
  background: var(--purple-soft);
  font-size: 22px;
}

.rule-card {
  display: grid;
  grid-template-columns: 165px minmax(150px, 0.45fr) minmax(230px, 1fr);
  align-items: start;
  gap: 12px;
  margin-bottom: 10px;
  padding: 14px;
  border: 1px solid #d4d4dc;
  border-radius: 11px;
}

.rule-card > span {
  width: fit-content;
  padding: 6px 10px;
  border-radius: 7px;
  background: var(--purple-soft);
  color: var(--purple);
  font-size: 13px;
  font-weight: 700;
}

.rule-card label {
  margin: 0;
}

.rule-card small {
  justify-self: start;
}

.review-message {
  margin: 12px 0 0;
  color: var(--purple);
  font-size: 13px;
  text-align: right;
}

.submit-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  margin-top: 12px;
  padding: 14px 18px;
  border: 1px solid #dddde3;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.9);
}

.submit-bar p {
  display: flex;
  gap: 18px;
  margin: 0;
}

.submit-bar p span {
  color: var(--muted);
}

.submit-bar .primary-button {
  flex: 0 0 auto;
}

.success-banner {
  border-color: #86ce96;
  background: #effbef;
  color: #287a42;
}

.success-card {
  max-width: 650px;
  margin: 30px auto 0;
  padding: 38px;
  text-align: center;
}

.success-icon {
  display: grid;
  width: 76px;
  height: 76px;
  margin: 0 auto 18px;
  place-items: center;
  border: 2px solid #7fc792;
  border-radius: 50%;
  background: #ebf9ee;
  color: var(--green);
  font-size: 38px;
  font-weight: 900;
}

.success-card h2 {
  margin: 0 0 8px;
  font-size: 30px;
}

.success-card > p:not(.eyebrow) {
  color: var(--muted);
  line-height: 1.7;
}

.success-card dl {
  text-align: left;
}

.success-card .primary-button {
  margin-top: 22px;
}

@media (max-width: 1050px) {
  .stage-grid,
  .review-grid,
  .result-grid {
    grid-template-columns: 1fr;
  }

  .test-summary {
    align-items: flex-start;
    flex-direction: column;
  }

  .test-summary dl {
    width: 100%;
  }

  .test-summary dl div {
    flex: 1;
    min-width: 0;
  }

  .rule-card {
    grid-template-columns: 150px 1fr;
  }

  .rule-card label:last-child {
    grid-column: 1 / -1;
  }
}

@media (max-width: 720px) {
  .page-heading,
  .submit-bar {
    align-items: stretch;
    flex-direction: column;
  }

  .steps {
    grid-template-columns: 1fr 1fr;
  }

  .step-item {
    justify-content: flex-start;
    padding: 0 10px;
    font-size: 13px;
  }

  .number-grid,
  .stat-grid {
    grid-template-columns: 1fr;
  }

  .actions,
  .editor-actions {
    flex-direction: column;
  }

  .review-tabs button {
    min-width: 0;
    flex: 1;
  }

  .test-summary dl {
    flex-direction: column;
  }

  .test-summary dl div {
    display: flex;
    align-items: center;
    justify-content: space-between;
    border-top: 1px solid #dddde3;
    border-left: 0;
    text-align: left;
  }

  .rule-card {
    grid-template-columns: 1fr;
  }

  .rule-card label:last-child {
    grid-column: auto;
  }

  .submit-bar p {
    flex-direction: column;
    gap: 5px;
  }
}
</style>
