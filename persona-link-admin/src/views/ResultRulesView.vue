<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getResultConfig, getVersion, saveResultConfig, type ResultTemplate, type TestVersion } from '../api'
import { isReadOnly } from '../auth'
import { useUnsavedChanges } from '../composables/useUnsavedChanges'

interface ResultRule {
  clientKey: string
  id?: number
  min: number
  max: number
  name: string
  copy: string
  deepCopy: string
  shareCopy: string
  resultCode?: string
}
interface Dimension { id: number; name: string; icon: string; rules: ResultRule[] }
const readOnly = isReadOnly()

const route = useRoute()
const router = useRouter()
const versionId = Number(route.params.id)
const version = ref<TestVersion | null>(null)
const selectedId = ref(0)
const drawerOpen = ref(false)
const editingRuleKey = ref('')
const draggingRuleKey = ref('')
const drawerDirty = ref(false)
const configDirty = ref(false)
const saving = ref(false)
const feedback = ref('')
const dimensions = ref<Dimension[]>([])
const form = reactive({ min: 0, max: 100, name: '', copy: '', deepCopy: '', shareCopy: '', position: 1 })
const { markDirty, markSaved, confirmDiscard } = useUnsavedChanges('结果规则尚未保存，确认放弃修改并离开吗？', saving)
const editorReadOnly = computed(() => readOnly || version.value?.versionStatus !== 1)
const editorLocked = computed(() => editorReadOnly.value || saving.value)
const selected = computed<Dimension>(() => dimensions.value.find(({ id }) => id === selectedId.value) ?? dimensions.value[0] ?? { id: 0, name: '加载中', icon: '♡', rules: [] })
function coverageOf(sourceRules: ResultRule[]) {
  const rules = [...sourceRules].sort((a, b) => a.min - b.min)
  const overlap = rules.some((rule, index) => index > 0 && rule.min < rules[index - 1].max)
  const complete = rules.length > 0
    && !overlap
    && rules[0].min === 0
    && rules[rules.length - 1].max === 100
    && rules.every((rule, index) => index === 0 || rule.min === rules[index - 1].max)
  return { complete, overlap }
}
const coverage = computed(() => coverageOf(selected.value.rules))

function showFeedback(message: string) {
  feedback.value = message
  window.setTimeout(() => { feedback.value = '' }, 2200)
}

function textOf(value: unknown): string {
  if (value && typeof value === 'object' && 'text' in value) return String((value as { text?: unknown }).text ?? '')
  return typeof value === 'string' ? value : ''
}

async function load() {
  try {
    const [versionData, config] = await Promise.all([getVersion(versionId), getResultConfig(versionId)])
    version.value = versionData
    dimensions.value = config.dimensions.map((dimension, index) => ({
      id: dimension.id,
      name: dimension.dimensionName,
      icon: ['♙', '♡', '♧'][index % 3],
      rules: config.templates.filter((template) => template.dimensionId === dimension.id).map((template) => ({
        clientKey: template.id ? `saved-${template.id}` : crypto.randomUUID(),
        id: template.id,
        min: Number(template.scoreMin),
        max: Number(template.scoreMax),
        name: template.resultName,
        copy: textOf(template.basicResultJson),
        deepCopy: textOf(template.deepResultJson),
        shareCopy: textOf(template.shareCopyJson),
        resultCode: template.resultCode,
      })),
    }))
    selectedId.value = dimensions.value[0]?.id ?? 0
    drawerDirty.value = false
    configDirty.value = false
    markSaved()
  } catch (error) { showFeedback(error instanceof Error ? error.message : '结果规则加载失败') }
}

function openCreate() {
  if (editorLocked.value) return
  editingRuleKey.value = ''
  Object.assign(form, { min: 0, max: 100, name: '', copy: '', deepCopy: '', shareCopy: '', position: selected.value.rules.length + 1 })
  drawerDirty.value = false
  drawerOpen.value = true
}

function openEdit(rule: ResultRule) {
  if (saving.value) return
  editingRuleKey.value = rule.clientKey
  Object.assign(form, {
    min: rule.min,
    max: rule.max,
    name: rule.name,
    copy: rule.copy,
    deepCopy: rule.deepCopy,
    shareCopy: rule.shareCopy,
    position: selected.value.rules.findIndex(({ clientKey }) => clientKey === rule.clientKey) + 1,
  })
  drawerDirty.value = false
  drawerOpen.value = true
}

function markRuleFormDirty() {
  drawerDirty.value = true
  markDirty()
}

function markConfigDirty() {
  drawerDirty.value = false
  configDirty.value = true
  markDirty()
}

async function closeDrawer() {
  if (saving.value) return
  if (drawerDirty.value && !await confirmDiscard('当前结果规则尚未保存，确认放弃修改吗？')) return
  drawerOpen.value = false
  drawerDirty.value = false
  editingRuleKey.value = ''
  if (!configDirty.value) markSaved()
}

function changeDimension(event: Event) {
  selectedId.value = Number((event.target as HTMLSelectElement).value)
  form.position = selected.value.rules.length + 1
}

function reorderRule(ruleKey: string, targetPosition: number): boolean {
  const rules = selected.value.rules
  const sourceIndex = rules.findIndex(({ clientKey }) => clientKey === ruleKey)
  if (sourceIndex < 0) return false
  const requestedPosition = Number.isFinite(targetPosition) ? Math.round(targetPosition) : sourceIndex + 1
  const targetIndex = Math.min(Math.max(requestedPosition - 1, 0), rules.length - 1)
  if (sourceIndex === targetIndex) return false
  const [rule] = rules.splice(sourceIndex, 1)
  rules.splice(targetIndex, 0, rule)
  return true
}

function scoreUpperBoundSymbol(rule: ResultRule): '≤' | '<' {
  return rule.max === 100 ? '≤' : '<'
}

function startDrag(event: DragEvent, ruleKey: string) {
  if (editorLocked.value) return
  draggingRuleKey.value = ruleKey
  if (event.dataTransfer) event.dataTransfer.effectAllowed = 'move'
}

function dropRule(targetRuleKey: string) {
  if (editorLocked.value || !draggingRuleKey.value) return
  const targetPosition = selected.value.rules.findIndex(({ clientKey }) => clientKey === targetRuleKey) + 1
  if (reorderRule(draggingRuleKey.value, targetPosition)) {
    markConfigDirty()
    showFeedback('规则顺序已调整，请点击保存草稿')
  }
  draggingRuleKey.value = ''
}

function saveRule() {
  if (editorLocked.value) return
  if (!form.name.trim() || !form.copy.trim()) return showFeedback('请填写结果名称和基础文案')
  if (form.min < 0 || form.max > 100 || form.min >= form.max) return showFeedback('分数区间需满足 0 ≤ 最小值 < 最大值 ≤ 100')
  const overlapsExistingRule = selected.value.rules.some((rule) => rule.clientKey !== editingRuleKey.value && form.min < rule.max && form.max > rule.min)
  if (overlapsExistingRule) return showFeedback('分数区间与已有规则重叠，请调整后再保存')
  let savedRuleKey = editingRuleKey.value
  const ruleContent = {
    min: form.min,
    max: form.max,
    name: form.name.trim(),
    copy: form.copy.trim(),
    deepCopy: form.deepCopy,
    shareCopy: form.shareCopy,
  }
  if (editingRuleKey.value) {
    const rule = selected.value.rules.find(({ clientKey }) => clientKey === editingRuleKey.value)
    if (!rule) return showFeedback('未找到待编辑规则，请关闭后重试')
    Object.assign(rule, ruleContent)
  } else {
    savedRuleKey = crypto.randomUUID()
    selected.value.rules.push({ clientKey: savedRuleKey, ...ruleContent })
  }
  reorderRule(savedRuleKey, Number(form.position))
  drawerOpen.value = false
  editingRuleKey.value = ''
  markConfigDirty()
  showFeedback('规则已更新，请点击保存草稿')
}

function removeRule() {
  if (!editingRuleKey.value || editorLocked.value) return
  const index = selected.value.rules.findIndex(({ clientKey }) => clientKey === editingRuleKey.value)
  if (index < 0) return
  selected.value.rules.splice(index, 1)
  drawerOpen.value = false
  editingRuleKey.value = ''
  markConfigDirty()
  showFeedback('规则已删除，请点击保存草稿')
}

async function saveConfig() {
  if (editorReadOnly.value || saving.value) return
  const incompleteDimension = dimensions.value.find((dimension) => !coverageOf(dimension.rules).complete)
  if (incompleteDimension) {
    selectedId.value = incompleteDimension.id
    return showFeedback(`请先补全“${incompleteDimension.name}”的 0—100 分数区间`)
  }
  const templates: ResultTemplate[] = dimensions.value.flatMap((dimension) => dimension.rules.map((rule, index) => ({
    id: rule.id,
    dimensionId: dimension.id,
    resultCode: rule.resultCode,
    resultName: rule.name,
    scoreMin: rule.min,
    scoreMax: rule.max,
    basicResultJson: { text: rule.copy },
    deepResultJson: rule.deepCopy ? { text: rule.deepCopy } : undefined,
    shareCopyJson: rule.shareCopy ? { text: rule.shareCopy } : undefined,
    sortNo: index + 1,
  })))
  saving.value = true
  try { await saveResultConfig(versionId, templates); await load(); showFeedback('结果规则已保存到服务端') }
  catch (error) { showFeedback(error instanceof Error ? error.message : '结果规则保存失败') }
  finally { saving.value = false }
}

onMounted(load)
</script>

<template>
  <section class="rules-page">
    <div class="breadcrumb">题型管理　/　{{ version?.title ?? '加载中' }}　/　V{{ version?.versionNo ?? '-' }}</div>
    <header class="rules-header">
      <div><h1>结果规则 <span>♡</span></h1><p>为每个计分维度配置分数区间与结果文案。</p></div>
      <div class="header-actions"><button class="secondary-action" type="button" :disabled="saving" @click="router.push('/types')">‹ 返回题型</button><button class="primary-action" type="button" :disabled="editorLocked" @click="saveConfig">{{ saving ? '保存中...' : '保存草稿' }}</button></div>
    </header>

    <div class="rules-layout">
      <aside class="dimension-panel">
        <h2>计分维度 <small>ⓘ</small></h2>
        <button v-for="dimension in dimensions" :key="dimension.id" type="button" :class="{ active: selectedId === dimension.id }" :disabled="saving" @click="selectedId = dimension.id">
          <span>{{ dimension.icon }}</span><div><strong>{{ dimension.name }}</strong><small>{{ dimension.rules.length }} 条规则</small></div>
        </button>
      </aside>

      <section class="rule-panel">
        <header><h2>{{ selected.name }} · 分数区间</h2><span class="coverage" :class="{ invalid: !coverage.complete }">{{ coverage.complete ? '✓ 0—100 已完整覆盖' : '⚠ 覆盖不完整' }}</span><small>{{ coverage.overlap ? '存在重叠区间' : coverage.complete ? '区间无重叠' : '存在断档区间' }}</small></header>
        <button v-for="(rule, index) in selected.rules" :key="rule.clientKey" class="rule-card" :class="{ selected: editingRuleKey === rule.clientKey && drawerOpen, dragging: draggingRuleKey === rule.clientKey }" type="button" :disabled="saving" @dragover.prevent @drop.prevent.stop="dropRule(rule.clientKey)" @click="openEdit(rule)">
          <span class="drag" :draggable="!editorLocked" title="拖动调整匹配顺序" @click.stop @dragstart.stop="startDrag($event, rule.clientKey)" @dragend.stop="draggingRuleKey = ''">⠿</span>
          <div><span class="range" :class="index === 1 ? 'yellow' : index === 2 ? 'purple' : ''">{{ rule.min }} ≤ 分数 {{ scoreUpperBoundSymbol(rule) }} {{ rule.max }}</span><h3>{{ rule.name }}</h3><p>{{ rule.copy }}</p></div>
          <span class="more">⋮</span>
        </button>
        <button class="add-rule" type="button" :disabled="editorLocked" @click="openCreate">＋ 添加结果规则</button>
      </section>
    </div>

    <div v-if="drawerOpen" class="admin-modal-backdrop" @click.self="closeDrawer">
      <aside class="rule-drawer admin-editor-dialog" role="dialog" aria-modal="true" aria-label="结果规则编辑弹窗" @input="markRuleFormDirty" @change="markRuleFormDirty">
        <header class="admin-dialog-header"><div><h2>{{ editingRuleKey ? '编辑结果规则' : '新增结果规则' }}</h2><p>先设置匹配条件，再填写用户看到的结果内容。</p></div><button type="button" aria-label="关闭" :disabled="saving" @click="closeDrawer">×</button></header>
        <div class="drawer-body">
          <section class="admin-form-section">
            <h3>匹配条件</h3><p>分数范围为 0–100，包含下限，不包含上限；上限为 100 时包含 100。</p>
            <div class="admin-form-grid">
              <label class="admin-field"><span>计分维度</span><select :value="selectedId" :disabled="editorLocked || Boolean(editingRuleKey)" @change="changeDimension"><option v-for="dimension in dimensions" :key="dimension.id" :value="dimension.id">{{ dimension.name }}</option></select></label>
              <label class="admin-field"><span>匹配顺序</span><input v-model.number="form.position" type="number" min="1" :max="selected.rules.length + (editingRuleKey ? 0 : 1)" :disabled="editorLocked" /></label>
              <label class="admin-field"><span>分数下限</span><input v-model.number="form.min" type="number" min="0" max="100" :disabled="editorLocked" /></label>
              <label class="admin-field"><span>分数上限</span><input v-model.number="form.max" type="number" min="0" max="100" :disabled="editorLocked" /></label>
            </div>
          </section>
          <section class="admin-form-section">
            <h3>结果内容</h3><p>结果名称和基础文案为必填项，将展示在测试结果中。</p>
            <div class="admin-form-grid">
              <label class="admin-field admin-field-wide"><span>结果名称</span><input v-model="form.name" maxlength="30" :disabled="editorLocked" placeholder="请输入结果名称" /><small class="admin-field-hint">{{ form.name.length }}/30 字</small></label>
              <label class="admin-field admin-field-wide"><span>基础结果文案</span><textarea v-model="form.copy" maxlength="200" rows="4" :disabled="editorLocked" placeholder="请输入结果文案" /><small class="admin-field-hint">{{ form.copy.length }}/200 字</small></label>
            </div>
          </section>
          <section class="admin-form-section">
            <h3>补充文案</h3><p>选填，可按需要补充更详细的解读和分享内容。</p>
            <div class="admin-form-grid">
              <label class="admin-field admin-field-wide"><span>深度解读文案</span><textarea v-model="form.deepCopy" maxlength="2000" rows="5" :disabled="editorLocked" placeholder="请输入深度解读文案" /><small class="admin-field-hint">{{ form.deepCopy.length }}/2000 字</small></label>
              <label class="admin-field admin-field-wide"><span>分享文案</span><textarea v-model="form.shareCopy" maxlength="500" rows="3" :disabled="editorLocked" placeholder="请输入分享文案" /><small class="admin-field-hint">{{ form.shareCopy.length }}/500 字</small></label>
            </div>
          </section>
        </div>
        <footer class="admin-dialog-footer"><button v-if="editingRuleKey" class="danger-action" type="button" :disabled="editorLocked" @click="removeRule">删除规则</button><span v-else class="admin-footer-note">保存规则后，需在页面保存草稿。</span><div class="admin-footer-actions"><button class="secondary-action" type="button" :disabled="saving" @click="closeDrawer">取消</button><button class="primary-action" type="button" :disabled="editorLocked" @click="saveRule">保存规则</button></div></footer>
      </aside>
    </div>
    <div v-if="feedback" class="toast" role="status">{{ feedback }}</div>
  </section>
</template>

<style scoped>
.rules-page { color: var(--ink, #211d22); }.breadcrumb { color: #7a737b; font-size: 13px; }
.rules-header { display: flex; align-items: flex-end; justify-content: space-between; gap: 22px; margin-top: 13px; }.rules-header h1 { margin: 0; font: 700 clamp(34px, 4vw, 47px) var(--font-ui); }.rules-header h1 span { color: #af77e9; }.rules-header p { margin: 8px 0 0; color: #79717b; }.header-actions { display: flex; flex-wrap: wrap; gap: 12px; }.primary-action, .secondary-action { padding: 11px 19px; border: 1.5px solid #211d22; border-radius: 11px; background: white; font: inherit; font-weight: 800; cursor: pointer; }.primary-action { background: #ff5d43; color: white; box-shadow: 2px 3px 0 #211d22; }
.rules-layout { display: grid; grid-template-columns: 275px minmax(0, 1fr); gap: 18px; margin-top: 24px; }.dimension-panel, .rule-panel { padding: 18px; border: 1.5px solid #c9c2ca; border-radius: 15px; background: #fffdf9; }.dimension-panel h2, .rule-panel h2 { margin: 0; font: 700 20px var(--font-ui); }.dimension-panel h2 small { color: #898189; font: inherit; font-size: 13px; }.dimension-panel > button { display: flex; align-items: center; gap: 14px; width: 100%; margin-top: 13px; padding: 18px; border: 1.5px solid #b8b0b8; border-radius: 12px; background: white; text-align: left; cursor: pointer; }.dimension-panel > button.active { border-color: #9859e8; background: linear-gradient(120deg, white, #eee0ff); }.dimension-panel > button > span { display: grid; place-items: center; width: 49px; height: 49px; border: 1.5px solid #211d22; border-radius: 50%; background: #d6b5ff; font-size: 26px; }.dimension-panel > button:nth-of-type(2) > span { background: #ffda74; }.dimension-panel > button:nth-of-type(3) > span { background: #bfe9cd; }.dimension-panel strong, .dimension-panel small { display: block; }.dimension-panel small { margin-top: 6px; color: #6f6870; }
.rule-panel > header { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; margin: 3px 7px 18px; }.rule-panel > header small { color: #827a83; }.coverage { margin-left: auto; padding: 5px 10px; border-radius: 8px; background: #eafae4; color: #318a30; font-size: 12px; }.coverage.invalid { background: #fff0ed; color: #bd4938; }.rule-card { display: grid; grid-template-columns: 28px 1fr 20px; align-items: center; gap: 12px; width: 100%; margin-top: 13px; padding: 20px 16px; border: 1.5px solid #aaa2ab; border-radius: 13px; background: white; color: #211d22; text-align: left; cursor: pointer; }.rule-card:hover, .rule-card.selected { border-color: #8650dc; box-shadow: 0 0 0 3px #efe3ff; }.drag { font-size: 25px; }.more { align-self: start; font-size: 24px; }.range { display: inline-block; padding: 7px 15px; border: 1px solid #cbb2fa; border-radius: 9px; background: #f4edff; }.range.yellow { border-color: #f2bd55; background: #fff7e5; }.range.purple { border-color: #b997f7; background: #f2eaff; }.rule-card h3 { margin: 13px 0 5px; font-size: 17px; }.rule-card p { margin: 0; color: #6f6870; line-height: 1.65; }.add-rule { width: 100%; margin-top: 15px; padding: 16px; border: 1.5px dashed #8e878f; border-radius: 12px; background: transparent; font: inherit; font-weight: 800; cursor: pointer; }
.toast { position: fixed; z-index: 60; right: 28px; bottom: 28px; padding: 13px 18px; border: 1.5px solid #211d22; border-radius: 12px; background: #211d22; color: white; box-shadow: 4px 5px 0 #d9bdff; }
@media (max-width: 1050px) { .rules-layout { grid-template-columns: 1fr; }.dimension-panel { display: grid; grid-template-columns: repeat(3, 1fr); gap: 10px; }.dimension-panel h2 { grid-column: 1 / -1; }.dimension-panel > button { margin-top: 0; padding: 13px; } }
@media (max-width: 760px) { .rules-header { align-items: stretch; flex-direction: column; }.header-actions { display: grid; grid-template-columns: 1fr 1fr; }.header-actions .primary-action { grid-column: 1 / -1; }.dimension-panel { grid-template-columns: 1fr; }.dimension-panel h2 { grid-column: auto; }.rule-panel { padding: 12px; }.rule-card { grid-template-columns: 22px 1fr; }.more { display: none; }.coverage { margin-left: 0; } }
</style>
