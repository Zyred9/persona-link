<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getResultConfig, getVersion, saveResultConfig, type ResultTemplate, type TestVersion } from '../api'
import { isReadOnly } from '../auth'

interface ResultRule {
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
const editingRuleId = ref<number | null>(null)
const feedback = ref('')
const dimensions = ref<Dimension[]>([])
const form = reactive({ min: 0, max: 100, name: '', copy: '', deepCopy: '', shareCopy: '' })
const selected = computed<Dimension>(() => dimensions.value.find(({ id }) => id === selectedId.value) ?? dimensions.value[0] ?? { id: 0, name: '加载中', icon: '♡', rules: [] })
const coverage = computed(() => {
  const rules = [...selected.value.rules].sort((a, b) => a.min - b.min)
  const overlap = rules.some((rule, index) => index > 0 && rule.min < rules[index - 1].max)
  const complete = rules.length > 0
    && !overlap
    && rules[0].min === 0
    && rules[rules.length - 1].max === 100
    && rules.every((rule, index) => index === 0 || rule.min === rules[index - 1].max)
  return { complete, overlap }
})

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
  } catch (error) { showFeedback(error instanceof Error ? error.message : '结果规则加载失败') }
}

function openCreate() {
  editingRuleId.value = null
  Object.assign(form, { min: 0, max: 100, name: '', copy: '', deepCopy: '', shareCopy: '' })
  drawerOpen.value = true
}

function openEdit(rule: ResultRule) {
  editingRuleId.value = rule.id ?? null
  Object.assign(form, rule)
  drawerOpen.value = true
}

function changeDimension(event: Event) {
  selectedId.value = Number((event.target as HTMLSelectElement).value)
}

function saveRule() {
  if (!form.name.trim() || !form.copy.trim()) return showFeedback('请填写结果名称和基础文案')
  if (form.min < 0 || form.max > 100 || form.min >= form.max) return showFeedback('分数区间需满足 0 ≤ 最小值 < 最大值 ≤ 100')
  const overlapsExistingRule = selected.value.rules.some((rule) => rule.id !== editingRuleId.value && form.min < rule.max && form.max > rule.min)
  if (overlapsExistingRule) return showFeedback('分数区间与已有规则重叠，请调整后再保存')
  if (editingRuleId.value) {
    const rule = selected.value.rules.find(({ id }) => id === editingRuleId.value)
    if (!rule) return showFeedback('未找到待编辑规则，请关闭后重试')
    Object.assign(rule, { ...form, name: form.name.trim(), copy: form.copy.trim() })
  } else {
    selected.value.rules.push({ ...form, name: form.name.trim(), copy: form.copy.trim() })
    selected.value.rules.sort((a, b) => a.min - b.min)
  }
  drawerOpen.value = false
  showFeedback('规则已更新，请点击保存草稿')
}

function removeRule() {
  if (!editingRuleId.value) return
  const index = selected.value.rules.findIndex(({ id }) => id === editingRuleId.value)
  if (index < 0) return
  selected.value.rules.splice(index, 1)
  drawerOpen.value = false
  editingRuleId.value = null
  showFeedback('规则已删除，请点击保存草稿')
}

async function saveConfig() {
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
  try { await saveResultConfig(versionId, templates); await load(); showFeedback('结果规则已保存到服务端') }
  catch (error) { showFeedback(error instanceof Error ? error.message : '结果规则保存失败') }
}

onMounted(load)
</script>

<template>
  <section class="rules-page">
    <div class="breadcrumb">题型管理　/　{{ version?.title ?? '加载中' }}　/　V{{ version?.versionNo ?? '-' }}</div>
    <header class="rules-header">
      <div><h1>结果规则 <span>♡</span></h1><p>为每个计分维度配置分数区间与结果文案。</p></div>
      <div class="header-actions"><button class="secondary-action" type="button" @click="router.push('/types')">‹ 返回题型</button><button class="primary-action" type="button" :disabled="readOnly" @click="saveConfig">保存草稿</button></div>
    </header>

    <div class="rules-layout">
      <aside class="dimension-panel">
        <h2>计分维度 <small>ⓘ</small></h2>
        <button v-for="dimension in dimensions" :key="dimension.id" type="button" :class="{ active: selectedId === dimension.id }" @click="selectedId = dimension.id">
          <span>{{ dimension.icon }}</span><div><strong>{{ dimension.name }}</strong><small>{{ dimension.rules.length }} 条规则</small></div>
        </button>
      </aside>

      <section class="rule-panel">
        <header><h2>{{ selected.name }} · 分数区间</h2><span class="coverage" :class="{ invalid: !coverage.complete }">{{ coverage.complete ? '✓ 0—100 已完整覆盖' : '⚠ 覆盖不完整' }}</span><small>{{ coverage.overlap ? '存在重叠区间' : coverage.complete ? '区间无重叠' : '存在断档区间' }}</small></header>
        <button v-for="(rule, index) in selected.rules" :key="rule.id" class="rule-card" :class="{ selected: editingRuleId === rule.id && drawerOpen }" type="button" @click="openEdit(rule)">
          <span class="drag">⠿</span>
          <div><span class="range" :class="index === 1 ? 'yellow' : index === 2 ? 'purple' : ''">{{ rule.min }} ≤ 分数 {{ index === selected.rules.length - 1 ? '≤' : '<' }} {{ rule.max }}</span><h3>{{ rule.name }}</h3><p>{{ rule.copy }}</p></div>
          <span class="more">⋮</span>
        </button>
        <button class="add-rule" type="button" :disabled="readOnly" @click="openCreate">＋ 添加结果规则</button>
      </section>
    </div>

    <div v-if="drawerOpen" class="drawer-backdrop" @click.self="drawerOpen = false">
      <aside class="rule-drawer" role="dialog" aria-modal="true" aria-label="结果规则编辑抽屉">
        <header><h2>{{ editingRuleId ? '编辑结果规则' : '新增结果规则' }}</h2><button type="button" aria-label="关闭" @click="drawerOpen = false">×</button></header>
        <div class="drawer-body">
          <label>计分维度<select :value="selectedId" :disabled="editingRuleId !== null" @change="changeDimension"><option v-for="dimension in dimensions" :key="dimension.id" :value="dimension.id">{{ dimension.name }}</option></select></label>
          <label>分数区间<div class="score-range"><input v-model.number="form.min" type="number" min="0" max="100" /><span>至</span><input v-model.number="form.max" type="number" min="0" max="100" /></div></label>
          <label>结果名称<input v-model="form.name" maxlength="30" placeholder="请输入结果名称" /></label>
          <label>基础结果文案<textarea v-model="form.copy" maxlength="200" rows="6" placeholder="请输入结果文案" /><small>{{ form.copy.length }}/200</small></label>
          <label>深度解读文案<textarea v-model="form.deepCopy" maxlength="2000" rows="6" placeholder="请输入深度解读文案" /></label>
          <label>分享文案<textarea v-model="form.shareCopy" maxlength="500" rows="4" placeholder="请输入分享文案" /></label>
          <label>匹配顺序<input :value="selected.rules.findIndex(({ id }) => id === editingRuleId) + 1 || selected.rules.length + 1" type="number" readonly /></label>
        </div>
        <footer><button v-if="editingRuleId" class="danger-action" type="button" :disabled="readOnly" @click="removeRule">删除</button><button class="secondary-action" type="button" @click="drawerOpen = false">取消</button><button class="primary-action" type="button" :disabled="readOnly" @click="saveRule">保存规则</button></footer>
      </aside>
    </div>
    <div v-if="feedback" class="toast" role="status">{{ feedback }}</div>
  </section>
</template>

<style scoped>
.rules-page { color: var(--ink, #211d22); }.breadcrumb { color: #7a737b; font-size: 13px; }
.rules-header { display: flex; align-items: flex-end; justify-content: space-between; gap: 22px; margin-top: 13px; }.rules-header h1 { margin: 0; font: 700 clamp(34px, 4vw, 47px) "STKaiti", "KaiTi", serif; }.rules-header h1 span { color: #af77e9; }.rules-header p { margin: 8px 0 0; color: #79717b; }.header-actions { display: flex; flex-wrap: wrap; gap: 12px; }.primary-action, .secondary-action { padding: 11px 19px; border: 1.5px solid #211d22; border-radius: 11px; background: white; font: inherit; font-weight: 800; cursor: pointer; }.primary-action { background: #ff5d43; color: white; box-shadow: 2px 3px 0 #211d22; }
.rules-layout { display: grid; grid-template-columns: 275px minmax(0, 1fr); gap: 18px; margin-top: 24px; }.dimension-panel, .rule-panel { padding: 18px; border: 1.5px solid #c9c2ca; border-radius: 15px; background: #fffdf9; }.dimension-panel h2, .rule-panel h2 { margin: 0; font: 700 20px "STKaiti", "KaiTi", serif; }.dimension-panel h2 small { color: #898189; font: inherit; font-size: 13px; }.dimension-panel > button { display: flex; align-items: center; gap: 14px; width: 100%; margin-top: 13px; padding: 18px; border: 1.5px solid #b8b0b8; border-radius: 12px; background: white; text-align: left; cursor: pointer; }.dimension-panel > button.active { border-color: #9859e8; background: linear-gradient(120deg, white, #eee0ff); }.dimension-panel > button > span { display: grid; place-items: center; width: 49px; height: 49px; border: 1.5px solid #211d22; border-radius: 50%; background: #d6b5ff; font-size: 26px; }.dimension-panel > button:nth-of-type(2) > span { background: #ffda74; }.dimension-panel > button:nth-of-type(3) > span { background: #bfe9cd; }.dimension-panel strong, .dimension-panel small { display: block; }.dimension-panel small { margin-top: 6px; color: #6f6870; }
.rule-panel > header { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; margin: 3px 7px 18px; }.rule-panel > header small { color: #827a83; }.coverage { margin-left: auto; padding: 5px 10px; border-radius: 8px; background: #eafae4; color: #318a30; font-size: 12px; }.coverage.invalid { background: #fff0ed; color: #bd4938; }.rule-card { display: grid; grid-template-columns: 28px 1fr 20px; align-items: center; gap: 12px; width: 100%; margin-top: 13px; padding: 20px 16px; border: 1.5px solid #aaa2ab; border-radius: 13px; background: white; color: #211d22; text-align: left; cursor: pointer; }.rule-card:hover, .rule-card.selected { border-color: #8650dc; box-shadow: 0 0 0 3px #efe3ff; }.drag { font-size: 25px; }.more { align-self: start; font-size: 24px; }.range { display: inline-block; padding: 7px 15px; border: 1px solid #cbb2fa; border-radius: 9px; background: #f4edff; }.range.yellow { border-color: #f2bd55; background: #fff7e5; }.range.purple { border-color: #b997f7; background: #f2eaff; }.rule-card h3 { margin: 13px 0 5px; font-size: 17px; }.rule-card p { margin: 0; color: #6f6870; line-height: 1.65; }.add-rule { width: 100%; margin-top: 15px; padding: 16px; border: 1.5px dashed #8e878f; border-radius: 12px; background: transparent; font: inherit; font-weight: 800; cursor: pointer; }
.drawer-backdrop { position: fixed; z-index: 40; inset: 0; background: rgb(33 29 34 / 18%); }.rule-drawer { position: absolute; top: 0; right: 0; display: grid; grid-template-rows: auto 1fr auto; width: min(435px, 100%); height: 100%; border-left: 1.5px solid #211d22; background: #fffdf9; box-shadow: -12px 0 35px rgb(33 29 34 / 12%); animation: slide-in .2s ease-out; }.rule-drawer header, .rule-drawer footer { display: flex; align-items: center; justify-content: space-between; gap: 16px; padding: 20px 24px; border-bottom: 1px solid #ddd6de; }.rule-drawer footer { border-top: 1px solid #ddd6de; border-bottom: 0; }.rule-drawer footer button { flex: 1; }.rule-drawer h2 { margin: 0; font: 700 26px "STKaiti", "KaiTi", serif; }.rule-drawer header button { border: 0; background: transparent; font-size: 31px; cursor: pointer; }.drawer-body { overflow: auto; padding: 22px 24px; }.drawer-body > label { position: relative; display: grid; gap: 8px; margin-bottom: 20px; font-weight: 800; }.drawer-body input, .drawer-body select, .drawer-body textarea { width: 100%; padding: 11px 13px; border: 1px solid #cbc4cc; border-radius: 9px; background: white; font: inherit; }.drawer-body textarea { resize: vertical; line-height: 1.6; }.drawer-body label > small { position: absolute; right: 10px; bottom: 8px; color: #817983; font-weight: 500; }.score-range { display: grid; grid-template-columns: 1fr auto 1fr; align-items: center; gap: 12px; }.drawer-body details { padding: 12px 0; border-top: 1px solid #ddd6de; }.drawer-body details p { color: #827a83; font-size: 13px; }.drawer-body summary { font-weight: 800; cursor: pointer; }.toast { position: fixed; z-index: 60; right: 28px; bottom: 28px; padding: 13px 18px; border: 1.5px solid #211d22; border-radius: 12px; background: #211d22; color: white; box-shadow: 4px 5px 0 #d9bdff; }
@keyframes slide-in { from { transform: translateX(30px); opacity: .6; } }
@media (max-width: 1050px) { .rules-layout { grid-template-columns: 1fr; }.dimension-panel { display: grid; grid-template-columns: repeat(3, 1fr); gap: 10px; }.dimension-panel h2 { grid-column: 1 / -1; }.dimension-panel > button { margin-top: 0; padding: 13px; } }
@media (max-width: 760px) { .rules-header { align-items: stretch; flex-direction: column; }.header-actions { display: grid; grid-template-columns: 1fr 1fr; }.header-actions .primary-action { grid-column: 1 / -1; }.dimension-panel { grid-template-columns: 1fr; }.dimension-panel h2 { grid-column: auto; }.rule-panel { padding: 12px; }.rule-card { grid-template-columns: 22px 1fr; }.more { display: none; }.coverage { margin-left: 0; } }
</style>
