<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { archiveVersion, assetUrl, cancelVersionSchedule, checkPublish, getQuestions, getResultConfig, getTests, getTestVersions, offlineVersion, publishVersion, scheduleVersion, type Question, type ResultTemplate, type TestItem, type TestVersion } from '../api'
import { isReadOnly } from '../auth'

const tests = ref<TestItem[]>([])
const selectedTestId = ref<number | null>(null)
const versions = ref<TestVersion[]>([])
const check = ref<{ passed: boolean; errors: string[] } | null>(null)
const notice = ref('')
const showConfirm = ref(false)
const scheduleAt = ref('')
const previewVersion = ref<TestVersion | null>(null)
const previewQuestions = ref<Question[]>([])
const previewResults = ref<ResultTemplate[]>([])
const previewLoading = ref(false)
const loading = ref(false)
const readOnly = isReadOnly()
let loadRequestId = 0
const draft = computed(() => versions.value.find((item) => item.versionStatus === 1) ?? null)
const pending = computed(() => versions.value.find((item) => item.versionStatus === 3) ?? null)
const published = computed(() => versions.value.find((item) => item.versionStatus === 4) ?? null)

function statusText(status: number) {
  return status === 1 ? '草稿' : status === 3 ? '待发布' : status === 4 ? '当前发布版' : status === 5 ? '已下线' : status === 6 ? '已归档' : `状态 ${status}`
}

function showNotice(message: string) {
  notice.value = message
  window.setTimeout(() => { notice.value = '' }, 2600)
}

async function loadVersions() {
  if (!selectedTestId.value) return
  const testId = selectedTestId.value
  const requestId = ++loadRequestId
  loading.value = true
  try {
    const nextVersions = await getTestVersions(testId)
    if (requestId !== loadRequestId || testId !== selectedTestId.value) return
    versions.value = nextVersions
    check.value = null
    const nextDraft = nextVersions.find((item) => item.versionStatus === 1)
    const nextCheck = nextDraft ? await checkPublish(nextDraft.id) : null
    if (requestId !== loadRequestId || testId !== selectedTestId.value) return
    check.value = nextCheck
  } catch (error) {
    if (requestId === loadRequestId) showNotice(error instanceof Error ? error.message : '版本加载失败')
  } finally {
    if (requestId === loadRequestId) loading.value = false
  }
}

async function publishDraft() {
  if (!draft.value) return
  try {
    await publishVersion(draft.value.id)
    showConfirm.value = false
    await loadVersions()
    showNotice('草稿已发布')
  } catch (error) {
    showNotice(error instanceof Error ? error.message : '发布失败')
  }
}

async function scheduleDraft() {
  if (!draft.value || !scheduleAt.value) return showNotice('请选择计划发布时间')
  try {
    await scheduleVersion(draft.value.id, scheduleAt.value)
    scheduleAt.value = ''
    await loadVersions()
    showNotice('发布排期已保存')
  } catch (error) { showNotice(error instanceof Error ? error.message : '排期保存失败') }
}

async function cancelSchedule(version: TestVersion) {
  try { await cancelVersionSchedule(version.id); await loadVersions(); showNotice(`V${version.versionNo} 已取消排期`) }
  catch (error) { showNotice(error instanceof Error ? error.message : '取消排期失败') }
}

async function offline(version: TestVersion) {
  try {
    await offlineVersion(version.id, '后台手动下线')
    await loadVersions()
    showNotice(`V${version.versionNo} 已下线`)
  } catch (error) {
    showNotice(error instanceof Error ? error.message : '下线失败')
  }
}

async function archive(version: TestVersion) {
  try { await archiveVersion(version.id); await loadVersions(); showNotice(`V${version.versionNo} 已归档`) }
  catch (error) { showNotice(error instanceof Error ? error.message : '归档失败') }
}

async function openPreview(version: TestVersion) {
  previewVersion.value = version
  previewQuestions.value = []
  previewResults.value = []
  previewLoading.value = true
  try {
    const [questions, resultConfig] = await Promise.all([getQuestions(version.id), getResultConfig(version.id)])
    previewQuestions.value = questions
    previewResults.value = resultConfig.templates
  } catch (error) { showNotice(error instanceof Error ? error.message : '预览内容加载失败') }
  finally { previewLoading.value = false }
}

function resultText(value: unknown): string {
  if (typeof value === 'string') return value
  if (value && typeof value === 'object' && 'text' in value) return String((value as { text: unknown }).text ?? '')
  return value ? JSON.stringify(value) : '—'
}

watch(selectedTestId, loadVersions)
onMounted(async () => {
  try {
    tests.value = (await getTests()).records
    selectedTestId.value = tests.value[0]?.id ?? null
  } catch (error) {
    showNotice(error instanceof Error ? error.message : '题型加载失败')
  }
})
</script>

<template>
  <section class="release-view">
    <header>
      <div><h1>发布排期</h1><p>按题型校验草稿，支持立即发布、预约发布、下线和归档。</p></div>
      <select v-model.number="selectedTestId" aria-label="选择题型"><option v-for="item in tests" :key="item.id" :value="item.id">{{ item.testName }}</option></select>
    </header>
    <p v-if="notice" class="notice" role="status">{{ notice }}</p>
    <p v-if="loading" class="panel">版本加载中...</p>
    <template v-else>
      <div class="summary-grid">
        <article><small>当前发布版本</small><strong>{{ published ? `V${published.versionNo}` : '暂无' }}</strong><p>{{ published?.publishedAt ?? '当前题型还没有线上版本' }}</p></article>
        <article><small>编辑中草稿</small><strong>{{ draft ? `V${draft.versionNo}` : '暂无' }}</strong><p>{{ draft?.versionNote || '版本号由系统自动生成' }}</p></article>
        <article><small>待发布版本</small><strong>{{ pending ? `V${pending.versionNo}` : '暂无' }}</strong><p>{{ pending?.scheduledAt || '尚未设置发布排期' }}</p></article>
      </div>
      <article class="validation">
        <div class="section-title"><div><h2>发布前校验</h2><p>校验结果直接来自后端发布检查接口</p></div><div class="publish-actions"><input v-model="scheduleAt" type="datetime-local" :disabled="readOnly" aria-label="计划发布时间" /><button :disabled="readOnly || !draft || !check?.passed || !scheduleAt" @click="scheduleDraft">预约发布</button><button :disabled="readOnly || !draft || !check?.passed" @click="showConfirm = true">立即发布</button></div></div>
        <div v-if="pending" class="check-row"><span class="check">⌚</span><strong>V{{ pending.versionNo }} 将于 {{ pending.scheduledAt }} 自动发布</strong><button type="button" :disabled="readOnly" @click="cancelSchedule(pending)">取消排期</button></div>
        <div v-else-if="!draft" class="check-row"><span>—</span><strong>当前题型没有草稿</strong></div>
        <div v-else-if="check?.passed" class="check-row"><span class="check">✓</span><strong>题目、选项、计分维度和结果规则校验通过</strong><b>通过</b></div>
        <div v-for="error in check?.errors ?? []" :key="error" class="check-row invalid"><span>!</span><strong>{{ error }}</strong><b>未通过</b></div>
      </article>
      <article class="history">
        <h2>版本记录</h2>
        <div class="history-head"><span>版本</span><span>状态</span><span>题目数</span><span>操作时间</span><span>操作</span></div>
        <div v-for="item in versions" :key="item.id" class="history-row">
          <strong>V{{ item.versionNo }}</strong><span class="tag" :class="item.versionStatus === 4 ? 'live' : item.versionStatus === 1 || item.versionStatus === 3 ? 'draft' : ''">{{ statusText(item.versionStatus) }}</span><span>{{ item.questionCount }}</span><span>{{ item.scheduledAt || item.publishedAt || item.offlineAt || '—' }}</span><div class="history-actions"><button type="button" @click="openPreview(item)">预览</button><button v-if="item.versionStatus === 4" type="button" :disabled="readOnly" @click="offline(item)">下线</button><button v-if="item.versionStatus === 3" type="button" :disabled="readOnly" @click="cancelSchedule(item)">取消排期</button><button v-if="item.versionStatus === 5" type="button" :disabled="readOnly" @click="archive(item)">归档</button></div>
        </div>
      </article>
    </template>

    <div v-if="showConfirm" class="modal" role="dialog" aria-modal="true" aria-labelledby="publish-dialog-title" @click.self="showConfirm = false">
      <div class="dialog"><h2 id="publish-dialog-title">确认发布 V{{ draft?.versionNo }}？</h2><p>发布后，新创建的答卷将使用该版本；进行中的答卷不受影响。</p><div><button class="cancel" @click="showConfirm = false">取消</button><button @click="publishDraft">确认发布</button></div></div>
    </div>
    <div v-if="previewVersion" class="modal" role="dialog" aria-modal="true" aria-labelledby="preview-dialog-title" @click.self="previewVersion = null">
      <div class="dialog preview-dialog"><h2 id="preview-dialog-title">V{{ previewVersion.versionNo }} · {{ previewVersion.title }}</h2><img v-if="previewVersion.coverUrl" :src="assetUrl(previewVersion.coverUrl)" :alt="previewVersion.title" /><p>{{ previewVersion.description || '暂无题型说明' }}</p><dl><div><dt>题目数</dt><dd>{{ previewVersion.questionCount }}</dd></div><div><dt>抽题数</dt><dd>{{ previewVersion.drawQuestionCount }}</dd></div><div><dt>预计用时</dt><dd>{{ previewVersion.estimatedMinutes }} 分钟</dd></div><div><dt>计分维度</dt><dd>{{ previewVersion.dimensions.map(item => item.dimensionName).join('、') }}</dd></div></dl><p v-if="previewLoading">正在加载完整预览…</p><template v-else><section class="preview-content"><h3>题目与选项</h3><article v-for="question in previewQuestions" :key="question.id"><b>{{ question.questionNo }}. {{ question.questionText }}</b><ul><li v-for="option in question.options" :key="option.id">{{ option.optionCode }}. {{ option.optionText }} <small>分值 {{ option.scoreValue }}</small></li></ul></article></section><section class="preview-content"><h3>结果规则</h3><article v-for="result in previewResults" :key="result.id"><b>{{ result.resultName }}（{{ result.scoreMin }}—{{ result.scoreMax }}）</b><p>{{ resultText(result.basicResultJson) }}</p></article></section></template><button type="button" @click="previewVersion = null">关闭预览</button></div>
    </div>
  </section>
</template>

<style scoped>
.release-view{display:grid;gap:18px}.release-view header,.section-title{display:flex;align-items:center;justify-content:space-between;gap:18px}.release-view h1{margin:0;font-size:38px}.release-view p{margin:6px 0;color:#777}.release-view select{padding:9px 12px;border:1.5px solid #222;border-radius:9px;background:#fff}.notice,.panel{padding:12px 14px;border:1px solid #b4d89c;border-radius:10px;background:#f0faeb;color:#436c31}.summary-grid{display:grid;grid-template-columns:1fr 1fr;gap:14px}.summary-grid article,.validation,.history{padding:18px;border:1.5px solid #aaa;border-radius:15px;background:#fff}.summary-grid small{color:#777}.summary-grid strong{display:block;margin-top:7px;font-size:30px}.validation h2,.history h2{margin:0}.section-title button,.dialog button,.history-row button{padding:9px 14px;border:1.5px solid #222;border-radius:9px;background:#ff6652;color:#fff;cursor:pointer}.section-title button:disabled{opacity:.4}.check-row{display:grid;grid-template-columns:32px 1fr 80px;align-items:center;gap:10px;padding:14px 0;border-top:1px solid #ddd}.check{display:grid;place-items:center;width:25px;height:25px;border-radius:50%;background:#e8f9dd;color:#4b8a2e}.check-row>b{color:#4b8a2e}.check-row.invalid>b{color:#c24b3b}.history-head,.history-row{display:grid;grid-template-columns:.7fr 1fr .7fr 1.5fr .7fr;align-items:center;gap:12px;padding:13px 8px}.history-head{margin-top:12px;background:#f5effe;font-weight:800}.history-row{border-bottom:1px solid #e2e2e2}.tag{justify-self:start;padding:4px 9px;border-radius:99px;background:#eee;color:#777;font-size:12px}.tag.live{background:#e9f8df;color:#4a872e}.tag.draft{background:#fff0c9;color:#8b681d}.modal{position:fixed;inset:0;z-index:20;display:grid;place-items:center;padding:20px;background:rgba(30,23,38,.38)}.dialog{width:min(420px,100%);padding:25px;border:2px solid #222;border-radius:18px;background:#fffdf8;text-align:center}.dialog>div{display:flex;justify-content:center;gap:10px;margin-top:18px}.dialog .cancel{background:#fff;color:#222}@media(max-width:650px){.release-view header,.section-title{align-items:flex-start;flex-direction:column}.summary-grid{grid-template-columns:1fr}.history{overflow:auto}.history-head,.history-row{min-width:650px}}
.summary-grid{grid-template-columns:repeat(3,1fr)}.publish-actions,.history-actions{display:flex;align-items:center;gap:8px;flex-wrap:wrap}.publish-actions input{padding:8px;border:1px solid #aaa;border-radius:8px}.check-row{grid-template-columns:32px 1fr auto}.history-head,.history-row{grid-template-columns:.7fr 1fr .7fr 1.5fr 1.4fr}.preview-dialog{width:min(760px,100%);max-height:90vh;overflow:auto;text-align:left}.preview-dialog img{width:100%;max-height:280px;object-fit:cover;border-radius:12px}.preview-dialog dl{display:grid;grid-template-columns:1fr 1fr;gap:10px}.preview-dialog dl div{display:grid;grid-template-columns:80px 1fr}.preview-dialog dd{margin:0}.preview-content{margin-top:18px;padding-top:14px;border-top:1px solid #ddd}.preview-content h3{margin:0 0 10px}.preview-content article{padding:10px;border-radius:9px;background:#f7f3f8;margin-bottom:8px}.preview-content ul{display:grid;gap:5px;margin:8px 0 0;padding-left:20px}.preview-content small{color:#777}@media(max-width:650px){.summary-grid{grid-template-columns:1fr}.history-head,.history-row{min-width:760px}}
</style>
