<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { assetUrl, getMiniappHome, getTests, updateTestHomeDisplay, type MiniappHome, type TestItem } from '../api'
import { isReadOnly } from '../auth'
import { useUnsavedChanges } from '../composables/useUnsavedChanges'

const tests = ref<TestItem[]>([])
const preview = ref<MiniappHome | null>(null)
const loading = ref(true)
// ponytail: 一次仅保存一行；需要并发保存时改为按题型锁。
const savingId = ref<number | null>(null)
const message = ref('')
const route = useRoute()
const readOnly = isReadOnly()
const targetId = computed(() => Number(route.query.testId) || 0)
const selectedCategoryId = ref('all')
const dirtyIds = ref<Set<number>>(new Set())
const page = ref(1)
const pageSize = ref(10)
const listScroll = ref<HTMLElement | null>(null)
const pageCount = computed(() => Math.max(1, Math.ceil(tests.value.length / pageSize.value)))
const pagedTests = computed(() => tests.value.slice((page.value - 1) * pageSize.value, page.value * pageSize.value))
const saving = computed(() => savingId.value !== null)
const { markDirty, markSaved } = useUnsavedChanges('首页配置还有未保存的题型，确认放弃修改并离开吗？', saving)
const previewCategories = computed(() => [
  { categoryId: 'all', categoryName: '全部' },
  ...(preview.value?.categories ?? []),
])
const previewTests = computed(() => {
  const items = preview.value?.allTests ?? []
  return selectedCategoryId.value === 'all'
    ? items
    : items.filter((item) => item.categoryId === selectedCategoryId.value)
})

function showMessage(value: string) {
  message.value = value
  window.setTimeout(() => { message.value = '' }, 2600)
}

function updatePreview(value: MiniappHome) {
  preview.value = value
  if (!previewCategories.value.some((item) => item.categoryId === selectedCategoryId.value)) {
    selectedCategoryId.value = 'all'
  }
}

function markRowDirty(id: number) {
  dirtyIds.value = new Set(dirtyIds.value).add(id)
  markDirty()
}

function markRowSaved(id: number) {
  const next = new Set(dirtyIds.value)
  next.delete(id)
  dirtyIds.value = next
  if (!next.size) markSaved()
}

function isRowDirty(id: number): boolean {
  return dirtyIds.value.has(id)
}

function changePage(delta: number) {
  if (saving.value) return
  page.value = Math.min(pageCount.value, Math.max(1, page.value + delta))
  listScroll.value?.scrollTo({ top: 0 })
}

function changePageSize(event: Event) {
  if (saving.value) return
  const size = Number((event.target as HTMLSelectElement).value)
  if (![10, 20, 50].includes(size)) return
  pageSize.value = size
  page.value = 1
  listScroll.value?.scrollTo({ top: 0 })
}

async function fetchTests(): Promise<TestItem[]> {
  const firstPage = await getTests({ page: 1, size: 100 })
  const remainingPages = await Promise.all(Array.from(
    { length: Math.max(0, Math.ceil(firstPage.total / firstPage.size) - 1) },
    (_, index) => getTests({ page: index + 2, size: 100 }),
  ))
  return [firstPage, ...remainingPages].flatMap((page) => page.records).map((item) => ({
    ...item,
    homeDisplay: item.homeDisplay ?? 0,
    homeSort: item.homeSort ?? 0,
  }))
}

function mergeSavedRows(latestTests: TestItem[]) {
  const localById = new Map(tests.value.map((item) => [item.id, item]))
  tests.value = latestTests.map((item) => dirtyIds.value.has(item.id) ? (localById.get(item.id) ?? item) : item)
  page.value = Math.min(page.value, pageCount.value)
}

async function load() {
  loading.value = true
  try {
    const [latestTests, home] = await Promise.all([fetchTests(), getMiniappHome()])
    tests.value = latestTests
    const targetIndex = latestTests.findIndex((item) => item.id === targetId.value)
    page.value = targetIndex < 0 ? 1 : Math.floor(targetIndex / pageSize.value) + 1
    updatePreview(home)
    dirtyIds.value = new Set()
    markSaved()
  } catch (error) {
    showMessage(error instanceof Error ? error.message : '首页题型加载失败')
  } finally {
    loading.value = false
  }
}

async function save(item: TestItem) {
  if (savingId.value !== null) return
  savingId.value = item.id
  try {
    const homeDisplay = Number(item.homeDisplay)
    const homeSort = Math.max(0, Number(item.homeSort) || 0)
    await updateTestHomeDisplay(item.id, homeDisplay, homeSort)
    item.homeDisplay = homeDisplay
    item.homeSort = homeSort
    markRowSaved(item.id)
    try {
      const [latestTests, home] = await Promise.all([fetchTests(), getMiniappHome()])
      mergeSavedRows(latestTests)
      updatePreview(home)
      showMessage(`“${item.testName}”已同步到小程序首页`)
    } catch {
      showMessage(`“${item.testName}”已保存，但预览刷新失败`)
    }
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
      <div><p class="eyebrow">HOME CONTENT</p><h1>首页配置 <span>♡</span></h1><p>管理小程序首页推荐内容与展示顺序，保存后立即生效。</p></div>
    </header>
    <p v-if="message" class="message" role="status">{{ message }}</p>
    <p v-if="loading" class="panel">题型加载中...</p>
    <div v-else class="layout">
      <div class="test-list">
        <div class="list-summary"><strong>题型配置 · 共 {{ tests.length }} 条</strong><span v-if="dirtyIds.size" class="dirty-mark">共 {{ dirtyIds.size }} 项未保存，切页会保留修改</span></div>
        <div ref="listScroll" class="list-scroll" tabindex="0" aria-label="首页题型配置列表">
        <article v-for="item in pagedTests" :key="item.id" class="test-row" :class="{ target: item.id === targetId, dirty: isRowDirty(item.id) }">
          <img v-if="item.coverUrl" :src="assetUrl(item.coverUrl)" :alt="item.testName" />
          <span v-else class="no-image">无封面</span>
          <div class="test-info">
            <strong>{{ item.testName }} <small v-if="isRowDirty(item.id)" class="dirty-mark">未保存</small></strong>
            <small>{{ item.testType === 2 ? '双人测试' : '单人测试' }} · {{ item.status === 1 ? '已启用' : '已停用' }} · {{ item.currentVersionNo ? `V${item.currentVersionNo}` : '暂无版本' }}</small>
          </div>
          <label>首页位置
            <select v-model.number="item.homeDisplay" :disabled="readOnly || savingId === item.id" @change="markRowDirty(item.id)">
              <option :value="0">普通列表</option>
              <option :value="1">焦点位</option>
              <option :value="2">推荐位</option>
            </select>
          </label>
          <label>排序
            <input v-model.number="item.homeSort" type="number" min="0" :disabled="readOnly || savingId === item.id" @input="markRowDirty(item.id)" />
          </label>
          <button type="button" :disabled="readOnly || savingId !== null || !isRowDirty(item.id)" @click="save(item)">{{ savingId === item.id ? '保存中...' : '保存' }}</button>
        </article>
        <p v-if="!tests.length" class="panel">暂无题型，请先在题型管理中创建。</p>
        </div>
        <footer class="list-pagination">
          <span>共 {{ tests.length }} 条</span>
          <select :value="pageSize" aria-label="每页条数" :disabled="saving" @change="changePageSize"><option v-for="size in [10, 20, 50]" :key="size" :value="size">{{ size }} 条/页</option></select>
          <button type="button" aria-label="上一页" :disabled="saving || page <= 1" @click="changePage(-1)">‹</button>
          <b>{{ page }} / {{ pageCount }}</b>
          <button type="button" aria-label="下一页" :disabled="saving || page >= pageCount" @click="changePage(1)">›</button>
        </footer>
      </div>

      <aside class="preview panel">
        <div class="preview-title"><h2>小程序首页预览</h2><p>当前小程序实际展示内容，保存后生效</p></div>
        <div class="phone-shell">
          <div class="phone-screen">
            <div class="phone-status"><strong>9:41</strong><span>▮▮▮　⌁　▰</span></div>
            <div class="phone-nav"><strong>心动测测 <i>♡</i></strong><span>•••　◉</span></div>
            <div class="phone-copy">把此刻的你，<br />收进一张小卡片。</div>
            <div class="phone-content">
              <img v-if="preview?.focusTests[0]?.coverUrl" class="preview-focus" :src="assetUrl(preview.focusTests[0].coverUrl)" :alt="preview.focusTests[0].title" />
              <div v-if="preview?.recommendedTests.length" class="preview-grid">
                <template v-for="item in preview.recommendedTests" :key="item.testId">
                  <img v-if="item.coverUrl" :src="assetUrl(item.coverUrl)" :alt="item.title" />
                  <span v-else class="preview-no-image">暂无封面</span>
                </template>
              </div>
              <div class="preview-categories" aria-label="首页分类预览">
                <button
                  v-for="category in previewCategories"
                  :key="category.categoryId"
                  type="button"
                  :class="{ active: selectedCategoryId === category.categoryId }"
                  :aria-pressed="selectedCategoryId === category.categoryId"
                  @click="selectedCategoryId = category.categoryId"
                >{{ category.categoryName }}</button>
              </div>
              <div v-if="previewTests.length" class="preview-grid">
                <template v-for="item in previewTests" :key="item.testId">
                  <img v-if="item.coverUrl" :src="assetUrl(item.coverUrl)" :alt="item.title" />
                  <span v-else class="preview-no-image">暂无封面</span>
                </template>
              </div>
              <p v-else class="preview-empty">这个分类还在准备中</p>
            </div>
            <div class="phone-tabbar"><span class="active">⌂<b>首页</b></span><span>♙<b>我的</b></span></div>
          </div>
        </div>
      </aside>
    </div>
  </section>
</template>

<style scoped>
.home-tests,.test-list{display:grid;gap:16px}
.test-list{min-width:0}
.list-summary,.list-pagination{display:flex;align-items:center;flex-wrap:wrap;gap:10px}
.list-summary{justify-content:space-between}
.list-scroll{display:grid;align-content:start;gap:12px;max-height:clamp(320px,calc(100dvh - 320px),700px);overflow-y:auto;overscroll-behavior:contain;padding:2px 8px 2px 2px;scrollbar-gutter:stable}
.list-pagination{justify-content:flex-end;padding:12px 0;color:#777;font-size:13px}
.list-pagination select,.list-pagination button,.list-pagination b{height:34px;padding:0 10px;border:1px solid #d5c8df;border-radius:8px;background:#fff;font:inherit}
.list-pagination b{display:grid;place-items:center;background:#d8bafd;color:#352043}
.list-pagination button,.list-pagination select{cursor:pointer}
.list-pagination :disabled{opacity:.45;cursor:not-allowed}
.home-tests>header{display:flex;align-items:center;justify-content:space-between}
.eyebrow{margin:0 0 4px;color:#ff6656;font-size:12px;letter-spacing:2px}
.home-tests h1{margin:0;font-size:38px}
.home-tests h1 span{color:#9a65df}
.home-tests p{color:#777}
.message,.panel,.test-row{padding:16px;border:1.5px solid #aaa;border-radius:14px;background:#fff}
.message{border-color:#a6cf8f;background:#eff9e9;color:#3f692d}
.layout{display:grid;grid-template-columns:minmax(0,1fr) 410px;align-items:start;gap:18px}
.test-row{display:grid;grid-template-columns:96px minmax(220px,1fr) 150px 100px 88px;align-items:center;gap:16px}
.test-row.target{border-color:#9a65df;background:#fbf7ff}
.test-row.dirty{box-shadow:inset 4px 0 0 #ff705f}
.test-row img,.no-image{width:96px;height:68px;border:1px solid #ccc;border-radius:10px;background:#faf8f3;object-fit:contain}
.no-image{display:grid;place-items:center;color:#888}
.test-info{display:grid;gap:7px}
.test-info small{color:#777}
.dirty-mark{display:inline-block;margin-left:5px;padding:2px 6px;border-radius:99px;background:#fff0e8;color:#d54f3e!important;font-size:11px;font-weight:800}
.test-row label{display:grid;gap:6px;font-size:13px;font-weight:700}
.test-row select,.test-row input,.test-row button{height:38px;padding:0 10px;border:1.5px solid #222;border-radius:9px;background:#fff;font:inherit}
.test-row button{background:#ff654d;color:#fff;cursor:pointer}
.test-row button:disabled,.test-row select:disabled,.test-row input:disabled{opacity:.45;cursor:not-allowed}
.preview{position:sticky;top:80px;background:#fffaf1}
.preview-title h2{margin:0}
.preview-title p{margin:5px 0 14px;font-size:13px}
.phone-shell{width:min(100%,360px);margin:0 auto;padding:7px;border:2px solid #201b22;border-radius:42px;background:#fff;box-shadow:3px 4px 0 rgb(33 29 34 / 8%)}
.phone-screen{height:680px;overflow:hidden;border:1px solid #d5cbc2;border-radius:34px;background-color:#fffaf1;background-image:linear-gradient(rgb(62 53 63 / 5%) 1px,transparent 1px),linear-gradient(90deg,rgb(62 53 63 / 5%) 1px,transparent 1px);background-size:14px 14px;display:grid;grid-template-rows:auto auto auto minmax(0,1fr) auto}
.phone-status,.phone-nav,.phone-tabbar{display:flex;align-items:center;justify-content:space-between}
.phone-status{padding:12px 20px 4px;font-size:11px}
.phone-nav{padding:12px 18px 8px}
.phone-nav strong{font:700 21px "STKaiti","KaiTi",serif}
.phone-nav strong i{color:#9a65df;font-style:normal}
.phone-nav>span{padding:5px 9px;border:1px solid #6f696d;border-radius:99px;background:rgb(255 255 255 / 75%);font-size:11px;letter-spacing:1px}
.phone-copy{padding:12px 22px 16px;color:#201b22;font:700 26px/1.35 "STKaiti","KaiTi",serif;letter-spacing:1px;transform:rotate(-1deg)}
.phone-content{min-height:0;overflow-y:auto;padding:0 14px 16px;scrollbar-width:none}
.preview-focus,.preview-grid img{display:block;width:100%;height:auto}
.preview-focus{margin-bottom:9px}
.preview-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:8px}
.preview-categories{display:flex;gap:7px;margin:16px -14px 11px;padding:2px 14px 7px;overflow-x:auto;scrollbar-width:none}
.phone-content::-webkit-scrollbar,.preview-categories::-webkit-scrollbar{display:none}
.preview-categories button{flex:0 0 auto;padding:5px 10px;border:1.5px solid #222;border-radius:99px;background:#fffdf8;font:700 11px inherit;cursor:pointer}
.preview-categories button.active{background:#d8bafd;transform:rotate(-1deg)}
.preview-no-image{display:grid;place-items:center;min-height:92px;border:1px dashed #aaa;border-radius:10px;color:#888;font-size:12px}
.preview-empty{display:grid;place-items:center;min-height:90px;margin:0;text-align:center}
.phone-tabbar{padding:8px 54px 10px;border-top:1px solid #bbb;background:rgb(255 253 248 / 94%)}
.phone-tabbar span{display:grid;justify-items:center;gap:1px;color:#777;font-size:20px}
.phone-tabbar b{font-size:10px}
.phone-tabbar .active{color:#8f57d4}
@media(max-width:1460px){.layout{grid-template-columns:1fr}.preview{position:static}.phone-shell{width:min(100%,390px)}}
@media(max-width:900px){.test-row{grid-template-columns:80px 1fr 1fr}.test-row img,.no-image{width:80px;height:58px}.test-info{grid-column:2/-1}}
@media(max-width:560px){.home-tests h1{font-size:30px}.test-row{grid-template-columns:72px 1fr}.test-row label,.test-row button{grid-column:1/-1}.test-row img,.no-image{width:72px;height:54px}.preview{padding:12px}.phone-shell{box-sizing:border-box}.phone-screen{height:640px}.phone-copy{font-size:23px}}
</style>
