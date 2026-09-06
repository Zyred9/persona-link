<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { applyAiImageTask, assetUrl, createAiImageTask, getAiImageTask, getLatestAiImageTask, retryAiImageTask, type AiImageTask, type AiImageResolution } from '../api'
import { isReadOnly } from '../auth'
import { confirmAction } from '../composables/useConfirm'

const props = defineProps<{ testId: number; testName: string }>()
const emit = defineEmits<{ close: []; applied: [] }>()
const prompt = ref(props.testName)
const resolution = ref<AiImageResolution>({ coverWidth: 800, coverHeight: 800, detailWidth: 1100, detailHeight: 500 })
const task = ref<AiImageTask | null>(null)
const loading = ref(true)
const busy = ref(false)
const error = ref('')
const readOnly = isReadOnly()
const active = computed(() => task.value?.taskStatus === 1 || task.value?.taskStatus === 2)
const statusText = computed(() => ({ 1: '排队中', 2: '生成中', 3: '生成完成，待采用', 4: '生成失败', 5: '已保存至草稿，发布后生效' })[task.value?.taskStatus ?? 0] ?? '')
const controller = new AbortController()
let disposed = false
let timer: ReturnType<typeof setTimeout> | undefined
let pendingRequest: ({ requestId: string; promptText: string } & AiImageResolution) | null = null
const requestStorageKey = `persona-link-image-request-${props.testId}`
try {
  const saved = JSON.parse(sessionStorage.getItem(requestStorageKey) ?? 'null')
  if (saved && typeof saved.requestId === 'string' && typeof saved.promptText === 'string') pendingRequest = { ...resolution.value, ...saved }
} catch { /* 损坏的本地请求记录不影响查询服务端任务。 */ }

function schedulePoll() {
  clearTimeout(timer)
  if (!disposed && active.value) timer = setTimeout(() => { void refresh() }, 2000)
}

async function refresh() {
  if (disposed || busy.value || loading.value) return
  loading.value = true
  error.value = ''
  try {
    const result = task.value ? await getAiImageTask(task.value.id, controller.signal) : await getLatestAiImageTask(props.testId, controller.signal)
    if (disposed) return
    task.value = result
  } catch (failure) {
    if (!disposed) error.value = failure instanceof Error ? failure.message : '状态查询失败，请重新查询'
  } finally {
    if (!disposed) { loading.value = false; schedulePoll() }
  }
}

async function generate(retry = false) {
  if (readOnly || busy.value || loading.value || active.value) return
  const promptText = prompt.value.trim()
  if (!retry && !promptText) { error.value = '请填写生图关键词或提示词'; return }
  if (!retry) {
    for (const [name, width, height] of [['封面图', resolution.value.coverWidth, resolution.value.coverHeight], ['详情图', resolution.value.detailWidth, resolution.value.detailHeight]] as const) {
      if (!Number.isInteger(width) || !Number.isInteger(height) || width < 256 || height < 256 || width * height < 262144 || width * height > 4194304 || width / height < 1 / 3 || width / height > 3) {
        error.value = `${name}宽高须为不小于 256 的整数，总像素须在 262144–4194304 之间，宽高比在 1:3 至 3:1 之间`
        return
      }
    }
  }
  if (retry && task.value?.taskStatus !== 4) return
  busy.value = true
  error.value = ''
  try {
    if (retry && !await confirmAction('将重试失败的图片，已成功的图片会保留。若上次提交结果不确定，重复提交可能计费，服务端会拒绝不安全的重试；确认继续吗？', { title: '重试图片生成', confirmText: '确认重试' })) return
    if (disposed) return
    // 请求失败时保留幂等键，避免网络重试重复计费；成功后再次生成使用新键。
    if (!retry && (!pendingRequest || pendingRequest.promptText !== promptText || (Object.keys(resolution.value) as Array<keyof AiImageResolution>).some(key => pendingRequest![key] !== resolution.value[key]))) {
      pendingRequest = { requestId: crypto.randomUUID(), promptText, ...resolution.value }
    }
    const submittedRequest = JSON.stringify(pendingRequest)
    if (!retry) sessionStorage.setItem(requestStorageKey, submittedRequest)
    const result = retry ? await retryAiImageTask(task.value!.id) : await createAiImageTask(props.testId, pendingRequest!)
    if (!retry && sessionStorage.getItem(requestStorageKey) === submittedRequest) sessionStorage.removeItem(requestStorageKey)
    if (disposed) return
    task.value = result
    pendingRequest = null
    schedulePoll()
  } catch (failure) {
    if (!disposed) error.value = failure instanceof Error ? failure.message : '生图任务提交失败'
  } finally { if (!disposed) busy.value = false }
}

async function apply() {
  if (readOnly || busy.value || loading.value || task.value?.taskStatus !== 3 || !task.value.coverUrl || !task.value.detailImageUrl) return
  busy.value = true
  error.value = ''
  try {
    if (!await confirmAction('确认将这两张图片写入草稿？会覆盖草稿原有的封面图和详情图；没有草稿时会创建草稿，不会直接发布。', { title: '采用生成的图片', confirmText: '采用并替换', danger: true }) || disposed) return
    const result = await applyAiImageTask(task.value.id)
    if (disposed) return
    task.value = result
    emit('applied')
  } catch (failure) {
    if (!disposed) error.value = failure instanceof Error ? failure.message : '采用图片失败'
  } finally { if (!disposed) busy.value = false }
}

onMounted(async () => {
  try {
    const result = await getLatestAiImageTask(props.testId, controller.signal)
    if (disposed) return
    task.value = result
    prompt.value = pendingRequest?.promptText ?? result?.promptText ?? props.testName
    const savedResolution = pendingRequest ?? result
    if (savedResolution) {
      for (const key of Object.keys(resolution.value) as Array<keyof AiImageResolution>) {
        resolution.value[key] = savedResolution[key] ?? resolution.value[key]
      }
    }
    schedulePoll()
  } catch (failure) {
    if (!disposed) error.value = failure instanceof Error ? failure.message : '任务加载失败，请重新查询'
  } finally { if (!disposed) loading.value = false }
})
onUnmounted(() => { disposed = true; clearTimeout(timer); controller.abort() })
</script>

<template>
  <div class="admin-modal-backdrop" @click.self="!busy && emit('close')" @keydown.esc="!busy && emit('close')">
    <section class="admin-editor-dialog image-generation-dialog" role="dialog" aria-modal="true" aria-label="AI 生图">
      <header class="admin-dialog-header"><div><h2>AI 生图</h2><p>{{ testName }} · 一次生成封面图与详情图</p></div><button type="button" aria-label="关闭" :disabled="busy" @click="emit('close')">×</button></header>
      <div class="drawer-body">
        <label class="admin-field"><span>关键词 / 提示词</span><textarea v-model="prompt" rows="4" maxlength="1500" placeholder="例如：温暖手绘风、奶油黄背景、可爱职场人物；说明主题、配色与想要的画面" :disabled="readOnly || busy || active || loading" autofocus /><small class="admin-field-hint">{{ prompt.length }} / 1500 字。后端自动补充以下两张图片的尺寸、比例要求。</small></label>
        <div class="image-resolutions">
          <fieldset :disabled="readOnly || busy || active || loading"><legend>封面图分辨率（像素）</legend><div><label>宽<input v-model.number="resolution.coverWidth" type="number" min="256" step="1" /></label><span>×</span><label>高<input v-model.number="resolution.coverHeight" type="number" min="256" step="1" /></label></div></fieldset>
          <fieldset :disabled="readOnly || busy || active || loading"><legend>详情图分辨率（像素）</legend><div><label>宽<input v-model.number="resolution.detailWidth" type="number" min="256" step="1" /></label><span>×</span><label>高<input v-model.number="resolution.detailHeight" type="number" min="256" step="1" /></label></div></fieldset>
        </div>
        <p class="image-spec">默认封面 800 × 800，详情图 1100 × 500。宽高均不小于 256，总像素 262144–4194304，宽高比 1:3 至 3:1；支持 2000 × 2000 和 2200 × 1000。重试沿用原任务尺寸，修改尺寸需重新生成两张。</p>
        <p v-if="loading && !task" role="status">正在读取最近一次生图任务…</p>
        <p v-if="task" role="status">{{ statusText }} · {{ task.provider === 1 ? '千问' : '火山' }} / {{ task.modelName }}</p>
        <p v-if="active" class="image-hint">正在后台生成，可以关闭弹窗；再次打开会恢复进度。</p>
        <p v-if="task?.errorMessage" class="image-error" role="alert">{{ task.errorMessage }}</p>
        <p v-if="error" class="image-error" role="alert">{{ error }} <button type="button" :disabled="busy || loading" @click="refresh">重新查询状态</button></p>
        <div v-if="task?.coverUrl || task?.detailImageUrl" class="image-results">
          <figure><figcaption>首页封面 · {{ task.coverWidth ?? 800 }} × {{ task.coverHeight ?? 800 }}</figcaption><img v-if="task.coverUrl" :src="assetUrl(task.coverUrl)" alt="AI 生成的首页封面预览" /><p v-else>等待生成封面</p></figure>
          <figure><figcaption>详情图 · {{ task.detailWidth ?? 1100 }} × {{ task.detailHeight ?? 500 }}</figcaption><img v-if="task.detailImageUrl" :src="assetUrl(task.detailImageUrl)" alt="AI 生成的详情图预览" /><p v-else>等待生成详情图</p></figure>
        </div>
      </div>
      <footer class="admin-dialog-footer"><small class="admin-footer-note">采用仅保存草稿；发布后同步小程序。</small><div class="admin-footer-actions">
        <button type="button" :disabled="busy" @click="emit('close')">关闭</button>
        <button v-if="task?.taskStatus === 4" type="button" :disabled="readOnly || busy || loading" @click="generate(true)">重试失败图片</button>
        <button type="button" :disabled="readOnly || busy || loading || active || !prompt.trim()" @click="generate()">{{ busy ? '处理中…' : active ? '生成中…' : task ? '重新生成两张' : '生成两张图片' }}</button>
        <button v-if="task?.taskStatus === 3" class="primary-action" type="button" :disabled="readOnly || busy || loading || !task.coverUrl || !task.detailImageUrl" @click="apply">采用两张图片</button>
      </div></footer>
    </section>
  </div>
</template>

<style scoped>
.image-generation-dialog { --editor-width: 900px; }
.admin-footer-actions > button, .image-error button { min-height: 40px; padding: 8px 12px; border: 1px solid #cfc5d2; border-radius: 8px; background: white; font-size: 13px; }
.admin-footer-actions > .primary-action { border-color: var(--ink); background: var(--purple); font-weight: 700; }
.image-spec, .image-hint { color: #756777; font-size: 13px; line-height: 1.7; }
.image-resolutions { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; margin-top: 16px; }
.image-resolutions fieldset { min-width: 0; border: 1px solid #e5ddd4; border-radius: 10px; padding: 12px; }
.image-resolutions legend { font-size: 13px; font-weight: 700; }
.image-resolutions fieldset > div { display: flex; align-items: center; gap: 10px; }
.image-resolutions label { display: flex; align-items: center; gap: 8px; min-width: 0; font-size: 13px; }
.image-resolutions input { width: 100%; min-width: 0; min-height: 40px; box-sizing: border-box; border: 1px solid #cfc5d2; border-radius: 6px; padding: 8px; }
.image-error { color: #ac352c; overflow-wrap: anywhere; }
.image-results { display: grid; grid-template-columns: 1fr 1.4fr; gap: 18px; margin-top: 20px; }
.image-results figure { margin: 0; min-width: 0; padding: 12px; border: 1px solid #e5ddd4; border-radius: 12px; background: #faf7f1; }
.image-results figcaption { margin-bottom: 12px; font-size: 13px; font-weight: 700; }
.image-results img { display: block; width: 100%; height: 250px; object-fit: contain; }
@media (max-width: 640px) { .image-results, .image-resolutions { grid-template-columns: 1fr; } }
</style>
