<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { assetUrl, getCategories, getHomeDraft, getTests, publishHome, saveHomeDraft, type TestItem } from '../api'
import { isReadOnly } from '../auth'

interface TestCard extends TestItem { coverUrl: string; recommended: boolean }
interface CategoryCard { id: number; name: string; selected: boolean }

const tests = ref<TestCard[]>([])
const categories = ref<CategoryCard[]>([])
const focusId = ref<number | null>(null)
const versionNote = ref('')
const message = ref('')
const loading = ref(true)
const saving = ref(false)
const readOnly = isReadOnly()
const route = useRoute()
const focusTest = computed(() => tests.value.find((item) => item.id === focusId.value) ?? null)
const recommendedTests = computed(() => tests.value.filter((item) => item.recommended && item.id !== focusId.value))
const selectedCategories = computed(() => categories.value.filter((item) => item.selected))
const targetTest = computed(() => tests.value.find((item) => item.id === Number(route.query.testId)) ?? null)
const targetDisplay = computed(() => targetTest.value?.id === focusId.value ? '焦点位' : targetTest.value?.recommended ? '推荐位' : '未展示')

function move<T>(items: T[], index: number, offset: number) {
  const target = index + offset
  if (target < 0 || target >= items.length) return
  ;[items[index], items[target]] = [items[target], items[index]]
}

function showMessage(value: string) {
  message.value = value
  window.setTimeout(() => { message.value = '' }, 2600)
}

function chooseFocus() {
  const candidates = tests.value.filter((item) => item.status === 1)
  if (!candidates.length) return showMessage('没有可配置的启用题型')
  const index = candidates.findIndex((item) => item.id === focusId.value)
  focusId.value = candidates[(index + 1) % candidates.length].id
}

function addRecommendation() {
  const candidate = tests.value.find((item) => item.status === 1 && item.id !== focusId.value && !item.recommended)
  if (candidate) candidate.recommended = true
  else showMessage('没有可添加的题型')
}

function setTargetDisplay(display: 0 | 1 | 2) {
  const target = targetTest.value
  if (!target) return
  if (focusId.value === target.id) focusId.value = null
  target.recommended = display === 2
  if (display === 1) focusId.value = target.id
}

async function load() {
  loading.value = true
  try {
    const [testPage, categoryPage, draft] = await Promise.all([getTests(), getCategories(), getHomeDraft()])
    const recommendedIds = new Set((draft?.slots ?? []).filter((slot) => slot.slotType === 2).sort((a, b) => Number(a.sortNo) - Number(b.sortNo)).map((slot) => slot.testId))
    const order = new Map(Array.from(recommendedIds).map((id, index) => [id, index]))
    tests.value = testPage.records.map((test) => ({ ...test, coverUrl: test.coverUrl ?? '', recommended: recommendedIds.has(test.id) }))
      .sort((left, right) => (order.get(left.id) ?? 999) - (order.get(right.id) ?? 999))
    focusId.value = draft?.slots.find((slot) => slot.slotType === 1)?.testId ?? null
    versionNote.value = draft?.versionNote ?? ''
    const categoryIds = new Set((draft?.categories ?? []).sort((a, b) => Number(a.sortNo) - Number(b.sortNo)).map((item) => item.categoryId))
    const categoryOrder = new Map(Array.from(categoryIds).map((id, index) => [id, index]))
    categories.value = categoryPage.records.map((item) => ({ id: item.id, name: item.categoryName, selected: categoryIds.has(item.id) }))
      .sort((left, right) => (categoryOrder.get(left.id) ?? 999) - (categoryOrder.get(right.id) ?? 999))
  } catch (error) {
    showMessage(error instanceof Error ? error.message : '首页配置加载失败')
  } finally {
    loading.value = false
  }
}

async function save(showSuccess = true) {
  saving.value = true
  try {
    await saveHomeDraft({
      versionNote: versionNote.value.trim(),
      slots: [
        ...(focusId.value ? [{ slotType: 1, testId: focusId.value, sortNo: 1 }] : []),
        ...recommendedTests.value.map((item, index) => ({ slotType: 2, testId: item.id, sortNo: index + 1 })),
      ],
      categories: selectedCategories.value.map((item, index) => ({ categoryId: item.id, sortNo: index + 1 })),
    })
    if (showSuccess) showMessage('首页草稿已保存')
  } catch (error) {
    showMessage(error instanceof Error ? error.message : '首页草稿保存失败')
    throw error
  } finally {
    saving.value = false
  }
}

async function publish() {
  try {
    await save(false)
    await publishHome()
    showMessage('首页配置已发布，小程序将读取最新配置')
  } catch (error) {
    if (!message.value) showMessage(error instanceof Error ? error.message : '首页配置发布失败')
  }
}

onMounted(load)
</script>

<template>
  <section class="home-config">
    <header>
      <div><h1>首页配置 <span>♡</span></h1><p>配置焦点图、推荐题型和分类，发布后供小程序首页读取。</p></div>
      <div class="actions"><button type="button" :disabled="readOnly || saving" @click="save()">保存草稿</button><button class="primary" type="button" :disabled="readOnly || saving" @click="publish">保存并发布</button></div>
    </header>
    <p v-if="message" class="message" role="status">{{ message }}</p>
    <p v-if="loading" class="panel">首页配置加载中...</p>
    <div v-else class="layout">
      <div class="stack">
        <article v-if="targetTest" class="panel target-panel">
          <div><h2>配置“{{ targetTest.testName }}”</h2><span>当前：{{ targetDisplay }}</span></div>
          <div class="actions">
            <button type="button" :disabled="readOnly" @click="setTargetDisplay(1)">设为焦点位</button>
            <button type="button" :disabled="readOnly" @click="setTargetDisplay(2)">设为推荐位</button>
            <button type="button" :disabled="readOnly" @click="setTargetDisplay(0)">不在首页展示</button>
          </div>
        </article>
        <article class="panel">
          <div class="panel-title"><h2>焦点推荐位</h2><span>仅展示 1 个</span></div>
          <div v-if="focusTest" class="focus-row">
            <img v-if="focusTest.coverUrl" :src="assetUrl(focusTest.coverUrl)" :alt="focusTest.testName" />
            <span v-else class="no-image">无图</span>
            <div><strong>{{ focusTest.testName }}</strong><small>{{ focusTest.testType === 2 ? '双人测试' : '单人测试' }}</small></div>
            <button type="button" :disabled="readOnly" @click="chooseFocus">更换</button><button class="danger" type="button" :disabled="readOnly" @click="focusId = null">下架</button>
          </div>
          <button v-else class="empty" type="button" :disabled="readOnly" @click="chooseFocus">＋ 选择焦点题型</button>
        </article>

        <article class="panel">
          <div class="panel-title"><h2>推荐题型</h2><button type="button" :disabled="readOnly" @click="addRecommendation">＋ 添加推荐</button></div>
          <div class="list">
            <div v-for="(item, index) in tests" :key="item.id" class="row" :class="{ muted: !item.recommended || item.id === focusId }">
              <img v-if="item.coverUrl" :src="assetUrl(item.coverUrl)" :alt="item.testName" /><span v-else class="no-image">无图</span>
              <div><strong>{{ item.testName }}</strong><small>{{ item.testType === 2 ? '双人测试' : '单人测试' }}</small></div>
              <span>{{ item.recommended && item.id !== focusId ? '展示中' : '未展示' }}</span>
              <button type="button" :disabled="readOnly || index === 0" @click="move(tests, index, -1)">↑</button>
              <button type="button" :disabled="readOnly || index === tests.length - 1" @click="move(tests, index, 1)">↓</button>
              <button type="button" :disabled="readOnly" @click="item.recommended = !item.recommended">{{ item.recommended ? '移除' : '添加' }}</button>
            </div>
          </div>
        </article>

        <article class="panel">
          <div class="panel-title"><h2>首页分类</h2><span>“全部”由小程序固定展示</span></div>
          <div class="categories">
            <div v-for="(item, index) in categories" :key="item.id" :class="{ muted: !item.selected }">
              <strong>{{ item.name }}</strong>
              <button type="button" :disabled="readOnly || index === 0" @click="move(categories, index, -1)">←</button>
              <button type="button" :disabled="readOnly || index === categories.length - 1" @click="move(categories, index, 1)">→</button>
              <button type="button" :disabled="readOnly" @click="item.selected = !item.selected">{{ item.selected ? '移除' : '添加' }}</button>
            </div>
          </div>
        </article>

        <article class="panel"><label>版本说明<textarea v-model="versionNote" :disabled="readOnly" rows="3" maxlength="500" placeholder="可选，记录本次首页调整内容"></textarea></label></article>
      </div>

      <aside class="panel preview">
        <h2>小程序首页预览</h2>
        <strong class="all-category">全部</strong><span v-for="item in selectedCategories" :key="item.id" class="category-chip">{{ item.name }}</span>
        <img v-if="focusTest?.coverUrl" class="focus-image" :src="assetUrl(focusTest.coverUrl)" :alt="focusTest.testName" />
        <div class="preview-grid"><img v-for="item in recommendedTests" :key="item.id" :src="assetUrl(item.coverUrl)" :alt="item.testName" /></div>
      </aside>
    </div>
  </section>
</template>

<style scoped>
.home-config,.stack,.list{display:grid;gap:16px}.home-config>header,.actions,.panel-title,.focus-row,.row,.categories>div,.target-panel{display:flex;align-items:center}.home-config>header,.panel-title,.target-panel{justify-content:space-between;gap:16px}.home-config h1{margin:0;font-size:38px}.home-config h1 span{color:#9a65df}.home-config p{color:#777}.actions{gap:10px}button{padding:8px 12px;border:1.5px solid #222;border-radius:9px;background:#fff;cursor:pointer}button:disabled{opacity:.4;cursor:not-allowed}.primary{background:#ff654d;color:#fff}.danger{color:#c63f32}.message,.panel{padding:16px;border:1.5px solid #aaa;border-radius:14px;background:#fff}.message{border-color:#a6cf8f;background:#eff9e9;color:#3f692d}.target-panel{border-color:#9a65df;background:#f8f1ff}.target-panel>div:first-child{display:grid;gap:5px}.target-panel span{color:#76539e}.layout{display:grid;grid-template-columns:minmax(0,1fr) 360px;gap:16px}.panel h2{margin:0}.focus-row{gap:12px}.focus-row img,.row img,.no-image{width:88px;height:62px;object-fit:cover;border:1px solid #bbb;border-radius:9px}.no-image{display:grid;place-items:center;color:#888}.focus-row>div,.row>div{display:grid;gap:4px;flex:1}.row{gap:10px;padding:10px;border:1px solid #ddd;border-radius:10px}.row small{color:#777}.muted{opacity:.55}.empty{width:100%;padding:22px;border-style:dashed}.categories{display:grid;grid-template-columns:repeat(2,1fr);gap:10px}.categories>div{gap:7px;padding:10px;border:1px solid #ccc;border-radius:10px}.categories strong{flex:1}label{display:grid;gap:8px;font-weight:700}textarea{padding:10px;border:1px solid #bbb;border-radius:9px;font:inherit}.preview{align-self:start;position:sticky;top:84px}.category-chip,.all-category{display:inline-block;margin:12px 6px 12px 0;padding:5px 10px;border:1px solid #333;border-radius:99px;font-size:12px}.all-category{background:#e5cdfd}.focus-image{display:block;width:100%;border-radius:12px}.preview-grid{display:grid;grid-template-columns:1fr 1fr;gap:0}.preview-grid img{width:100%;height:auto}@media(max-width:1000px){.layout{grid-template-columns:1fr}.preview{position:static}}@media(max-width:650px){.home-config>header,.focus-row,.target-panel{align-items:stretch;flex-direction:column}.row{flex-wrap:wrap}.categories{grid-template-columns:1fr}}
</style>
