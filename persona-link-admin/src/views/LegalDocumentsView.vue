<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { getLegalDocuments, saveLegalDocument, type LegalDocument } from '../api'
import { isAdmin } from '../auth'
import { confirmAction } from '../composables/useConfirm'
import { useUnsavedChanges } from '../composables/useUnsavedChanges'

const documents = ref<LegalDocument[]>([])
const selectedType = ref<LegalDocument['type']>(1)
const form = ref({ title: '', content: '' })
const loading = ref(false)
const saving = ref(false)
const switching = ref(false)
const notice = ref('')
const failed = ref(false)
const labels = { 1: '用户协议', 2: '隐私保护指引', 3: '娱乐免责声明' }
const { dirty, markDirty, markSaved, confirmDiscard } = useUnsavedChanges('协议尚未保存，确认放弃修改吗？', saving)
let disposed = false

function fill(type: LegalDocument['type']) {
  const document = documents.value.find(item => item.type === type)
  selectedType.value = type
  form.value = { title: document?.title || labels[type], content: document?.content || '' }
  markSaved()
}
async function load() {
  if (loading.value || saving.value || disposed) return
  loading.value = true
  notice.value = ''
  failed.value = false
  try {
    const result = await getLegalDocuments()
    if (disposed) return
    documents.value = result
    fill(selectedType.value)
  } catch (error) {
    if (!disposed) { failed.value = true; notice.value = error instanceof Error ? error.message : '协议加载失败，请重试' }
  } finally { if (!disposed) loading.value = false }
}
async function select(type: LegalDocument['type']) {
  if (type === selectedType.value || loading.value || saving.value || switching.value || disposed) return
  switching.value = true
  try {
    if (!await confirmDiscard() || disposed) return
    fill(type)
    notice.value = ''
    failed.value = false
  } finally { if (!disposed) switching.value = false }
}
async function save() {
  if (loading.value || saving.value || switching.value || disposed || !documents.value.length || !isAdmin()) return
  const payload = { title: form.value.title.trim(), content: form.value.content.trim() }
  notice.value = ''
  failed.value = false
  if (!payload.title || payload.title.length > 100 || !payload.content || payload.content.length > 30000) {
    failed.value = true
    notice.value = '请填写 1–100 字的标题及 1–30000 字的正文，不能仅为空格。'
    return
  }
  const type = selectedType.value
  saving.value = true
  try {
    if (!await confirmAction('保存后立即向小程序用户公开生效，请确认内容符合实际业务，运营主体和联系方式真实完整。', { title: `发布${labels[type]}？`, confirmText: '确认发布' }) || disposed) return
    const result = await saveLegalDocument(type, payload)
    if (disposed) return
    documents.value = documents.value.map(item => item.type === type ? result : item)
    fill(type)
    notice.value = '协议已发布，小程序下次查询时将读取新内容。'
  } catch (error) {
    if (!disposed) { failed.value = true; notice.value = error instanceof Error ? error.message : '协议保存失败，请重试' }
  } finally { if (!disposed) saving.value = false }
}
onMounted(load)
onBeforeUnmount(() => { disposed = true })
</script>

<template>
  <section class="legal-view">
    <header><h1>协议管理</h1><p>配置小程序协议正文，保存即公开生效。支持 Markdown 标题、列表、加粗、引用和表格，小程序按排版后的内容展示。</p></header>
    <p class="warning">未配置的协议在小程序中无法阅读，全部三份配置完成后用户才能同意并进入。内容需按实际业务审核，系统不代填运营主体或联系方式。</p>
    <p v-if="notice" class="notice" :class="{ error: failed }" role="status">{{ notice }}</p>
    <p v-if="loading" role="status">正在加载协议…</p>
    <button v-else-if="!documents.length" @click="load">重新加载</button>
    <template v-else>
      <nav class="document-tabs" aria-label="选择协议"><button v-for="item in documents" :key="item.type" :aria-pressed="selectedType === item.type" :disabled="saving || switching" @click="select(item.type)"><strong>{{ labels[item.type] }}</strong><small>{{ item.version ? `已发布 · V${item.version}` : '尚未配置' }}</small></button></nav>
      <form class="editor" @submit.prevent="save">
        <fieldset :disabled="saving || switching || !isAdmin()"><label>协议标题<input v-model="form.title" maxlength="100" required @input="markDirty" /></label><label>协议正文（Markdown）<textarea v-model="form.content" maxlength="30000" rows="22" required placeholder="填写 Markdown 正文，例如：## 一、服务说明" @input="markDirty"></textarea></label></fieldset>
        <footer><span>{{ form.content.length }} / 30000 字 · {{ dirty ? '有未保存的修改' : '与服务器同步' }}</span><button type="submit" :disabled="saving || switching || !dirty || !isAdmin()">{{ saving ? '正在确认 / 发布…' : '保存并发布' }}</button></footer>
      </form>
    </template>
  </section>
</template>

<style scoped>
.legal-view{max-width:1200px;display:grid;gap:18px;color:#27222b}h1{margin:0;font-size:38px}header p{color:#77707f;line-height:1.7}.warning,.notice{padding:14px 18px;margin:0;border-radius:10px;line-height:1.7}.warning{background:#fff5d4;color:#795d25}.notice{background:#edf6ef}.notice.error{background:#fff0ea;color:#a83c30}.document-tabs{display:flex;gap:12px;flex-wrap:wrap}.document-tabs button{flex:1;min-width:170px;text-align:left}.document-tabs small{display:block;color:#77707f;margin-top:8px}.document-tabs button[aria-pressed=true]{background:#f3eaff;border-color:#8257bc}.editor{border:1.5px solid #27222b;border-radius:20px;background:#fffdf8;padding:28px}fieldset{border:0;margin:0;padding:0;min-width:0}label{display:grid;gap:10px;margin-bottom:24px;font-weight:700}input,textarea{box-sizing:border-box;width:100%;border:1px solid #cfc5d4;border-radius:10px;padding:12px;font:inherit;line-height:1.7;color:inherit;background:#fff}textarea{resize:vertical;min-height:350px}footer{display:flex;align-items:center;justify-content:space-between;gap:16px;flex-wrap:wrap}footer span{color:#77707f;font-size:13px}button{min-height:44px;border:1.5px solid #27222b;border-radius:10px;padding:12px 20px;background:#fffdf8;color:inherit;font:inherit;cursor:pointer}footer button{background:#c9a7ff;font-weight:700}button:disabled,fieldset:disabled{opacity:.6}button:disabled{cursor:not-allowed}button:focus-visible,input:focus-visible,textarea:focus-visible{outline:3px solid #8257bc;outline-offset:3px}
</style>
