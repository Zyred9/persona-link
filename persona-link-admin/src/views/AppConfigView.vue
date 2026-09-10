<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { deleteAppConfigs, getAppConfig, getAppConfigs, saveAppConfig, type AppConfig, type AppConfigInput } from '../api'
import { isAdmin } from '../auth'
import { confirmAction } from '../composables/useConfirm'
import { useUnsavedChanges } from '../composables/useUnsavedChanges'

const records = ref<AppConfig[]>([])
const total = ref(0)
const page = ref(1)
const size = 20
const keyword = ref('')
const selected = ref<number[]>([])
const busy = ref(false)
const notice = ref('')
const showEditor = ref(false)
const editingId = ref<number>()
const form = ref<AppConfigInput>({ configKey: '', configValue: '', valueType: 1, configName: '', remark: '' })
const typeLabels: Record<number, string> = { 1: '字符串', 2: '数字', 3: '布尔', 4: 'JSON' }
const { markDirty, markSaved, confirmDiscard } = useUnsavedChanges('通用配置尚未保存，确认放弃修改并离开吗？', busy)
let disposed = false

async function load(targetPage = page.value) {
  if (busy.value || disposed || !isAdmin()) return
  busy.value = true
  try {
    const result = await getAppConfigs({ page: targetPage, size, keyword: keyword.value.trim() })
    if (disposed) return
    records.value = result.records
    total.value = result.total
    page.value = targetPage
    selected.value = []
  } catch (error) { notice.value = error instanceof Error ? error.message : '配置加载失败，请重试' }
  finally { busy.value = false }
}

async function openEditor(id?: number) {
  if (busy.value || disposed || !isAdmin() || !await confirmDiscard()) return
  if (busy.value || disposed) return
  busy.value = true
  try {
    const item = id ? await getAppConfig(id) : { configKey: '', configValue: '', valueType: 1, configName: '', remark: '' }
    if (disposed) return
    form.value = { configKey: item.configKey, configValue: item.configValue ?? '', valueType: item.valueType, configName: item.configName, remark: item.remark ?? '' }
    editingId.value = id
    showEditor.value = true
    notice.value = ''
    markSaved()
  } catch (error) { notice.value = error instanceof Error ? error.message : '配置详情加载失败' }
  finally { busy.value = false }
}

async function closeEditor() {
  if (!await confirmDiscard()) return
  showEditor.value = false
  markSaved()
}

async function save() {
  if (busy.value || disposed || !isAdmin() || !showEditor.value) return
  busy.value = true
  notice.value = ''
  let saved = false
  try {
    await saveAppConfig({ ...form.value, configKey: form.value.configKey.trim(), configName: form.value.configName.trim() }, editingId.value)
    if (disposed) return
    markSaved()
    showEditor.value = false
    notice.value = '配置已保存'
    saved = true
  } catch (error) { notice.value = error instanceof Error ? error.message : '配置保存失败' }
  finally { busy.value = false }
  if (saved) await load()
}

async function removeSelected() {
  if (busy.value || disposed || !isAdmin() || !selected.value.length || showEditor.value) return
  const ids = [...selected.value]
  busy.value = true
  let deleted = false
  try {
    if (!await confirmAction(`确认删除选中的 ${ids.length} 项配置？删除后对应功能将使用默认值或不再展示。`, { title: '删除配置', confirmText: '确认删除', danger: true }) || disposed) return
    await deleteAppConfigs(ids)
    notice.value = '配置已删除'
    deleted = true
  } catch (error) { notice.value = error instanceof Error ? error.message : '配置删除失败' }
  finally { busy.value = false }
  if (deleted) await load(Math.min(page.value, Math.max(1, Math.ceil((total.value - ids.length) / size))))
}

onMounted(() => load())
onBeforeUnmount(() => { disposed = true })
</script>

<template>
  <section class="config-view">
    <header><div><h1>通用配置</h1><p>管理小程序及系统的公共配置项</p></div><button :disabled="busy" @click="openEditor()">＋ 新增配置</button></header>
    <p class="help">小程序「设置 → 版本信息」读取 <code>miniapp.version</code>（字符串，如 1.0.0），保存后下次进入设置生效。首页标题图也可在「首页配置」上传修改。</p>
    <p v-if="notice && !showEditor" class="notice" role="status">{{ notice }}</p>
    <form class="filters" @submit.prevent="load(1)"><input v-model="keyword" :disabled="busy" aria-label="搜索配置键或名称" placeholder="搜索配置键或名称" /><button :disabled="busy">查询</button><button type="button" :disabled="busy || !selected.length" @click="removeSelected">删除选中（{{ selected.length }}）</button></form>
    <div class="table-card" :aria-busy="busy">
      <table><thead><tr><th>选择</th><th>名称 / 配置键</th><th>类型</th><th>配置值</th><th>说明</th><th>操作</th></tr></thead><tbody>
        <tr v-for="item in records" :key="item.id"><td><input v-model="selected" type="checkbox" :value="item.id" :disabled="busy" :aria-label="`选择${item.configName}`" /></td><td><b>{{ item.configName }}</b><code>{{ item.configKey }}</code></td><td>{{ typeLabels[item.valueType] }}</td><td class="config-value"><span :title="item.configValue">{{ item.configValue || '（空）' }}</span></td><td>{{ item.remark || '—' }}</td><td><button :disabled="busy" @click="openEditor(item.id)">编辑</button></td></tr>
        <tr v-if="!records.length"><td colspan="6">{{ busy ? '加载中…' : '暂无配置，可新增配置项或调整搜索条件' }}</td></tr>
      </tbody></table>
    </div>
    <footer><span>共 {{ total }} 项 · 第 {{ page }} / {{ Math.max(1, Math.ceil(total / size)) }} 页</span><div><button :disabled="busy || page <= 1" @click="load(page - 1)">上一页</button><button :disabled="busy || page * size >= total" @click="load(page + 1)">下一页</button></div></footer>
    <div v-if="showEditor" class="admin-modal-backdrop" @click.self="closeEditor" @keydown.esc="closeEditor">
      <form class="admin-editor-dialog" role="dialog" aria-modal="true" aria-label="配置编辑" @submit.prevent="save">
        <header class="admin-dialog-header"><div><h2>{{ editingId ? '编辑配置' : '新增配置' }}</h2><p>配置键保存后不可修改；填写值需符合所选类型。</p></div><button type="button" :disabled="busy" aria-label="关闭" @click="closeEditor">×</button></header>
        <div class="drawer-body"><p v-if="notice" role="status">{{ notice }}</p><fieldset :disabled="busy" @input="markDirty" @change="markDirty"><div class="admin-form-grid">
          <label class="admin-field"><span>配置名称</span><input v-model="form.configName" required maxlength="100" /></label>
          <label class="admin-field"><span>配置键</span><input v-model="form.configKey" :readonly="!!editingId" required maxlength="128" pattern="[a-z][a-z0-9._\-]*" placeholder="如 miniapp.version" /></label>
          <label class="admin-field"><span>值类型</span><select v-model.number="form.valueType"><option v-for="(label, type) in typeLabels" :key="type" :value="Number(type)">{{ label }}</option></select></label>
          <label class="admin-field admin-field-wide"><span>配置值</span><textarea v-model="form.configValue" rows="5" maxlength="65535" :placeholder="form.valueType === 3 ? '填写 true 或 false' : form.valueType === 4 ? '填写合法 JSON' : '填写配置值；字符串可留空'" /></label>
          <label class="admin-field admin-field-wide"><span>配置说明</span><textarea v-model="form.remark" rows="2" maxlength="500" /></label>
        </div></fieldset></div>
        <footer class="admin-dialog-footer"><span class="admin-footer-note">保存后生效</span><div class="admin-footer-actions"><button class="secondary-action" type="button" :disabled="busy" @click="closeEditor">取消</button><button class="primary" :disabled="busy">{{ busy ? '保存中…' : '保存配置' }}</button></div></footer>
      </form>
    </div>
  </section>
</template>

<style scoped>
.config-view{display:grid;gap:18px}.config-view>header,.config-view>footer{display:flex;align-items:center;justify-content:space-between;gap:12px}h1{margin:0;font-size:38px}.config-view>header p,.help{margin:6px 0;color:#777}.help{line-height:1.7}button{border:1.5px solid #222;border-radius:9px;background:#fff;padding:8px 14px;cursor:pointer}.config-view>header button,.primary{background:#ff6652;color:#fff}button:disabled{opacity:.45;cursor:not-allowed}.filters{display:flex;gap:10px;padding:16px;border:1.5px solid #aaa;border-radius:14px;background:#fff}.filters input{min-width:240px;border:1px solid #bbb;border-radius:9px;padding:9px;font:inherit}.table-card{overflow:auto;border:1.5px solid #aaa;border-radius:14px;background:#fff}table{width:100%;min-width:800px;border-collapse:collapse;text-align:left}th,td{padding:14px;border-bottom:1px solid #ddd;overflow-wrap:anywhere}th{background:#f5effe}td code{display:block;margin-top:5px;font-size:12px;color:#777}.config-value{max-width:320px}.config-value span{display:-webkit-box;-webkit-line-clamp:3;-webkit-box-orient:vertical;overflow:hidden;white-space:pre-wrap}.config-view>footer div{display:flex;gap:8px}fieldset{border:0;margin:0;padding:0;min-width:0}.admin-editor-dialog{--editor-width:720px}@media(max-width:720px){.config-view>header,.filters{align-items:stretch;flex-direction:column}.filters input{min-width:0}h1{font-size:30px}}
</style>
