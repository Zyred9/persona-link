<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { assetUrl, getMiniappHome, getTests, updateTestHomeDisplay, type MiniappHome, type TestItem } from '../api'
import { isReadOnly } from '../auth'

const tests = ref<TestItem[]>([])
const preview = ref<MiniappHome | null>(null)
const loading = ref(true)
const savingId = ref<number | null>(null)
const message = ref('')
const route = useRoute()
const readOnly = isReadOnly()
const targetId = Number(route.query.testId) || 0

function showMessage(value: string) {
  message.value = value
  window.setTimeout(() => { message.value = '' }, 2600)
}

async function load() {
  loading.value = true
  try {
    const [firstPage, home] = await Promise.all([getTests({ page: 1, size: 100 }), getMiniappHome()])
    const remainingPages = await Promise.all(Array.from(
      { length: Math.max(0, Math.ceil(firstPage.total / firstPage.size) - 1) },
      (_, index) => getTests({ page: index + 2, size: 100 }),
    ))
    tests.value = [firstPage, ...remainingPages].flatMap((page) => page.records).map((item) => ({
      ...item,
      homeDisplay: item.homeDisplay ?? 0,
      homeSort: item.homeSort ?? 0,
    }))
    preview.value = home
  } catch (error) {
    showMessage(error instanceof Error ? error.message : '首页题型加载失败')
  } finally {
    loading.value = false
  }
}

async function save(item: TestItem) {
  savingId.value = item.id
  try {
    await updateTestHomeDisplay(item.id, Number(item.homeDisplay), Math.max(0, Number(item.homeSort) || 0))
    showMessage(`“${item.testName}”已同步到小程序首页`)
    await load()
  } catch (error) {
    showMessage(error instanceof Error ? error.message : '首页展示保存失败')
  } finally {
    savingId.value = null
  }
}

onMounted(load)
</script>

<template>
  <section class="home-tests">
    <header>
      <div><p class="eyebrow">HOME CONTENT</p><h1>首页题型 <span>♡</span></h1><p>直接设置题型在小程序首页的位置和顺序，保存后立即生效。</p></div>
    </header>
    <p v-if="message" class="message" role="status">{{ message }}</p>
    <p v-if="loading" class="panel">题型加载中...</p>
    <div v-else class="layout">
      <div class="test-list">
        <article v-for="item in tests" :key="item.id" class="test-row" :class="{ target: item.id === targetId }">
          <img v-if="item.coverUrl" :src="assetUrl(item.coverUrl)" :alt="item.testName" />
          <span v-else class="no-image">无封面</span>
          <div class="test-info">
            <strong>{{ item.testName }}</strong>
            <small>{{ item.testType === 2 ? '双人测试' : '单人测试' }} · {{ item.status === 1 ? '已启用' : '已停用' }} · {{ item.currentVersionNo ? `V${item.currentVersionNo}` : '暂无版本' }}</small>
          </div>
          <label>首页位置
            <select v-model.number="item.homeDisplay" :disabled="readOnly">
              <option :value="0">普通列表</option>
              <option :value="1">焦点位</option>
              <option :value="2">推荐位</option>
            </select>
          </label>
          <label>排序
            <input v-model.number="item.homeSort" type="number" min="0" :disabled="readOnly" />
          </label>
          <button type="button" :disabled="readOnly || savingId === item.id" @click="save(item)">{{ savingId === item.id ? '保存中...' : '保存' }}</button>
        </article>
        <p v-if="!tests.length" class="panel">暂无题型，请先在题型管理中创建。</p>
      </div>

      <aside class="preview panel">
        <h2>小程序首页预览</h2>
        <p>保存后的小程序展示效果</p>
        <img v-if="preview?.focusTests[0]?.coverUrl" class="preview-focus" :src="assetUrl(preview.focusTests[0].coverUrl)" :alt="preview.focusTests[0].title" />
        <div class="preview-grid">
          <img v-for="item in preview?.recommendedTests" :key="item.testId" :src="assetUrl(item.coverUrl)" :alt="item.title" />
        </div>
        <div class="preview-categories">
          <strong>全部</strong><span v-for="category in preview?.categories" :key="category.categoryId">{{ category.categoryName }}</span>
        </div>
        <div class="preview-grid">
          <img v-for="item in preview?.allTests" :key="item.testId" :src="assetUrl(item.coverUrl)" :alt="item.title" />
        </div>
        <p v-if="preview && !preview.focusTests.length && !preview.recommendedTests.length && !preview.allTests.length" class="preview-empty">暂无可预览题型</p>
      </aside>
    </div>
  </section>
</template>

<style scoped>
.home-tests,.test-list{display:grid;gap:16px}.home-tests>header{display:flex;justify-content:space-between;align-items:center}.eyebrow{margin:0 0 4px;color:#ff6656;font-size:12px;letter-spacing:2px}.home-tests h1{margin:0;font-size:38px}.home-tests h1 span{color:#9a65df}.home-tests p{color:#777}.message,.panel,.test-row{padding:16px;border:1.5px solid #aaa;border-radius:14px;background:#fff}.message{border-color:#a6cf8f;background:#eff9e9;color:#3f692d}.layout{display:grid;grid-template-columns:minmax(0,1fr) 360px;gap:16px;align-items:start}.test-row{display:grid;grid-template-columns:96px minmax(220px,1fr) 150px 100px 88px;gap:16px;align-items:center}.test-row.target{border-color:#9a65df;background:#fbf7ff}.test-row img,.no-image{width:96px;height:68px;border:1px solid #ccc;border-radius:10px;object-fit:cover}.no-image{display:grid;place-items:center;color:#888}.test-info{display:grid;gap:7px}.test-info small{color:#777}.test-row label{display:grid;gap:6px;font-size:13px;font-weight:700}.test-row select,.test-row input,.test-row button{height:38px;padding:0 10px;border:1.5px solid #222;border-radius:9px;background:#fff;font:inherit}.test-row button{background:#ff654d;color:#fff;cursor:pointer}.test-row button:disabled,.test-row select:disabled,.test-row input:disabled{opacity:.45;cursor:not-allowed}.preview{position:sticky;top:80px;overflow:hidden;background:#fffaf1}.preview h2{margin:0}.preview>p{margin:5px 0 14px}.preview-focus,.preview-grid img{display:block;width:100%;height:auto}.preview-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr))}.preview-categories{display:flex;gap:8px;margin:18px 0 12px;overflow-x:auto}.preview-categories strong,.preview-categories span{flex:0 0 auto;padding:5px 10px;border:1.5px solid #222;border-radius:99px;font-size:12px}.preview-categories strong{background:#d8bafd}.preview-empty{text-align:center}@media(max-width:1460px){.layout{grid-template-columns:1fr}.preview{position:static}}@media(max-width:900px){.test-row{grid-template-columns:80px 1fr 1fr}.test-row img,.no-image{width:80px;height:58px}.test-info{grid-column:2/-1}}@media(max-width:560px){.test-row{grid-template-columns:72px 1fr}.test-row label,.test-row button{grid-column:1/-1}.test-row img,.no-image{width:72px;height:54px}}
</style>
