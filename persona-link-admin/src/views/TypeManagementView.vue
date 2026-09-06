<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  assetUrl, copyVersionAsDraft, deleteTests, getCategories, getTest, getTests, getTestVersions, saveTest, saveVersion,
  updateTestStatus, uploadImage, type Category, type TestItem as TestDto, type TestVersion,
} from '../api'
import { isReadOnly } from '../auth'
import { useUnsavedChanges } from '../composables/useUnsavedChanges'
import { confirmAction } from '../composables/useConfirm'
import AiImageGenerationDialog from '../components/AiImageGenerationDialog.vue'

type TestType = '单人测试' | '双人测试'
type TestStatus = '启用' | '停用'
interface TestItem { id: number; name: string; coverUrl: string; drawQuestionCount: number | null; estimatedMinutes: number | null; type: TestType; version: string; status: TestStatus; home: '推荐位' | '焦点位' | '普通列表'; updatedAt: string }
interface EditableDimension { id?: number; dimensionCode?: string; dimensionName: string; sortNo: number }
interface TestForm {
  name: string
  coverUrl: string
  detailImageUrl: string
  description: string
  type: TestType
  status: TestStatus
  categoryId: number
  estimatedMinutes: number | null
  drawQuestionCount: number | null
  dimensions: EditableDimension[]
}
const readOnly = isReadOnly()

const router = useRouter()
let disposed = false
onBeforeUnmount(() => { disposed = true })
const keyword = ref('')
const typeFilter = ref('全部类型')
const statusFilter = ref('全部状态')
const drawerOpen = ref(false)
const imageTest = ref<TestItem | null>(null)
const editingId = ref<number | null>(null)
const editingVersion = ref<TestVersion | null>(null)
const uploadingCover = ref(false)
const saving = ref(false)
const feedback = ref('')
const items = ref<TestItem[]>([])
const categories = ref<Category[]>([])
const page = ref(1)
const pageSize = ref(10)
let loadSequence = 0
const total = ref(0)
const selectedIds = ref<number[]>([])
const form = reactive<TestForm>({ name: '', coverUrl: '', detailImageUrl: '', description: '', type: '单人测试', status: '启用', categoryId: 0, estimatedMinutes: null, drawQuestionCount: null, dimensions: [] })
const editorBusy = computed(() => saving.value || uploadingCover.value)
const { markDirty, markSaved, confirmDiscard } = useUnsavedChanges('题型内容尚未保存，确认放弃修改并离开吗？', editorBusy)

const pageCount = computed(() => Math.max(1, Math.ceil(total.value / pageSize.value)))
const allSelected = computed(() => items.value.length > 0 && items.value.every((item) => selectedIds.value.includes(item.id)))

function toView(item: TestDto): TestItem {
  return { id: item.id, name: item.testName, coverUrl: item.coverUrl ?? '', drawQuestionCount: item.drawQuestionCount ?? null, estimatedMinutes: item.estimatedMinutes ?? null, type: item.testType === 2 ? '双人测试' : '单人测试', version: item.currentVersionNo ? `V${item.currentVersionNo}` : '暂无', status: item.status === 1 ? '启用' : '停用', home: item.homeDisplay === 1 ? '焦点位' : item.homeDisplay === 2 ? '推荐位' : '普通列表', updatedAt: item.updateDate?.replace('T', ' ').slice(0, 16) ?? '—' }
}

function showFeedback(message: string) { feedback.value = message; window.setTimeout(() => { feedback.value = '' }, 2600) }

async function closeDrawer() {
  if (uploadingCover.value || saving.value || !await confirmDiscard('题型内容尚未保存，确认放弃修改吗？')) return
  drawerOpen.value = false
  markSaved()
}

async function load() {
  const sequence = ++loadSequence
  try {
    const testPage = await getTests({
      page: page.value,
      size: pageSize.value,
      keyword: keyword.value.trim(),
      testType: typeFilter.value === '全部类型' ? undefined : typeFilter.value === '双人测试' ? 2 : 1,
      status: statusFilter.value === '全部状态' ? undefined : statusFilter.value === '启用' ? 1 : 0,
    })
    if (disposed || sequence !== loadSequence) return
    items.value = testPage.records.map(toView)
    total.value = testPage.total
    selectedIds.value = []
  } catch (error) { if (!disposed && sequence === loadSequence) showFeedback(error instanceof Error ? error.message : '题型加载失败') }
}

function changePageSize() {
  page.value = 1
  selectedIds.value = []
  void load()
}

function search() {
  page.value = 1
  void load()
}

function changePage(offset: number) {
  const next = page.value + offset
  if (next < 1 || next > pageCount.value) return
  page.value = next
  void load()
}

function togglePageSelection() {
  selectedIds.value = allSelected.value ? [] : items.value.map((item) => item.id)
}

async function removeSelected() {
  const ids = [...selectedIds.value]
  if (readOnly || !ids.length || !await confirmAction(`确认删除选中的 ${ids.length} 个题型？`, { title: '删除题型', confirmText: '确认删除', danger: true })) return
  try {
    await deleteTests(ids)
    if (items.value.length === ids.length && page.value > 1) page.value -= 1
    await load()
    showFeedback('题型已删除')
  } catch (error) { showFeedback(error instanceof Error ? error.message : '题型删除失败') }
}

function resetForm() {
  Object.assign(form, { name: '', coverUrl: '', detailImageUrl: '', description: '', type: '单人测试', status: '启用', categoryId: categories.value[0]?.id ?? 0, estimatedMinutes: null, drawQuestionCount: null, dimensions: [{ dimensionName: '', sortNo: 1 }] })
}

function openCreate() { editingId.value = null; editingVersion.value = null; resetForm(); markSaved(); drawerOpen.value = true }

async function openEdit(item: TestItem) {
  editingId.value = item.id
  resetForm()
  try {
    const [detail, versions] = await Promise.all([getTest(item.id), getTestVersions(item.id)])
    const version = versions.find((candidate) => candidate.versionStatus === 1) ?? versions[0] ?? null
    editingVersion.value = version
    Object.assign(form, {
      name: detail.testName, type: detail.testType === 2 ? '双人测试' : '单人测试', status: detail.status === 1 ? '启用' : '停用', categoryId: detail.categoryId,
      coverUrl: version?.coverUrl ?? '', description: version?.description ?? '', estimatedMinutes: version?.estimatedMinutes ?? null, drawQuestionCount: version?.drawQuestionCount ?? null,
      detailImageUrl: version?.detailImageUrl ?? '',
      dimensions: version?.dimensions.map((item) => ({ id: item.id, dimensionCode: item.dimensionCode, dimensionName: item.dimensionName, sortNo: item.sortNo })) ?? [{ dimensionName: '', sortNo: 1 }],
    })
    markSaved()
    drawerOpen.value = true
  } catch (error) { showFeedback(error instanceof Error ? error.message : '题型详情加载失败') }
}

async function handleImageUpload(event: Event, field: 'coverUrl' | 'detailImageUrl') {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file || uploadingCover.value) return
  const label = { coverUrl: '版本封面', detailImageUrl: '详情图' }[field]
  uploadingCover.value = true
  try { form[field] = await uploadImage(file); markDirty(); showFeedback(`${label}上传成功`) }
  catch (error) { showFeedback(error instanceof Error ? error.message : `${label}上传失败`) }
  finally { uploadingCover.value = false; input.value = '' }
}

function addDimension() {
  form.dimensions.push({ dimensionName: '', sortNo: form.dimensions.length + 1 })
  markDirty()
}

function removeDimension(index: number) {
  if (form.dimensions.length <= 1) return showFeedback('至少保留 1 个计分维度')
  form.dimensions.splice(index, 1)
  form.dimensions.forEach((item, itemIndex) => { item.sortNo = itemIndex + 1 })
  markDirty()
}

function moveDimension(index: number, offset: number) {
  const target = index + offset
  if (target < 0 || target >= form.dimensions.length) return
  ;[form.dimensions[index], form.dimensions[target]] = [form.dimensions[target], form.dimensions[index]]
  form.dimensions.forEach((item, itemIndex) => { item.sortNo = itemIndex + 1 })
  markDirty()
}

function versionPayload(version?: TestVersion | null) {
  const dimensions = form.dimensions.map((item, index) => ({ id: item.id, dimensionCode: item.dimensionCode, dimensionName: item.dimensionName.trim(), sortNo: index + 1 }))
  return { title: form.name.trim(), coverUrl: form.coverUrl, detailImageUrl: form.detailImageUrl.trim() || null, description: form.description, estimatedMinutes: Number(form.estimatedMinutes), drawQuestionCount: Number(form.drawQuestionCount), versionNote: version?.versionNote ?? '', dimensions }
}

async function saveItem() {
  if (uploadingCover.value || saving.value) return
  if (!form.name.trim() || !form.categoryId) return showFeedback('请填写题型名称并选择分类')
  if (!form.coverUrl) return showFeedback('请上传版本封面')
  if (!form.dimensions.length || form.dimensions.some((item) => !item.dimensionName.trim())) return showFeedback('请填写完整的计分维度')
  if (!Number.isInteger(form.estimatedMinutes) || Number(form.estimatedMinutes) < 1 || !Number.isInteger(form.drawQuestionCount) || Number(form.drawQuestionCount) < 1) return showFeedback('请填写有效的抽题数量和预计分钟')
  const creating = editingId.value === null
  const needsDraftCopy = Boolean(editingVersion.value && editingVersion.value.versionStatus !== 1)
  let testSaved = false
  saving.value = true
  try {
    if (needsDraftCopy && !await confirmAction('当前版本不是草稿，保存将先复制为新草稿。确认继续吗？', { title: '创建草稿并保存', confirmText: '继续保存' })) return
    const saved = await saveTest({ testName: form.name.trim(), testType: form.type === '双人测试' ? 2 : 1, categoryId: Number(form.categoryId), status: form.status === '启用' ? 1 : 0 }, editingId.value ?? undefined)
    testSaved = true
    editingId.value = saved.id
    let draft = editingVersion.value
    if (draft && draft.versionStatus !== 1) {
      draft = await copyVersionAsDraft(draft.id)
      editingVersion.value = draft
      form.dimensions.forEach((dimension) => {
        dimension.id = draft!.dimensions.find((copied) => copied.dimensionCode === dimension.dimensionCode)?.id
      })
    }
    await saveVersion(saved.id, versionPayload(draft), draft?.id)
    markSaved()
    drawerOpen.value = false
    showFeedback(creating ? '题型及草稿版本已新建' : '题型已更新')
    await load()
  } catch (error) {
    const message = error instanceof Error ? error.message : '保存失败'
    showFeedback(testSaved ? `题型基础信息已保存，但版本保存失败：${message}` : message)
  }
  finally { saving.value = false }
}

async function toggleStatus(item: TestItem) {
  try { await updateTestStatus(item.id, item.status === '启用' ? 0 : 1); await load(); showFeedback(`${item.name} 状态已更新`) }
  catch (error) { showFeedback(error instanceof Error ? error.message : '状态更新失败') }
}

function configureHome(item: TestItem) {
  void router.push({ name: 'home-config', query: { testId: item.id } })
}

async function openVersionEditor(item: TestItem, target: 'questions' | 'results') {
  if (disposed) return
  try {
    const versions = await getTestVersions(item.id)
    if (disposed) return
    let draft = versions.find((version) => version.versionStatus === 1)
    if (!draft && versions[0]) {
      if (readOnly) {
        draft = versions[0]
      } else {
        if (!await confirmAction('当前没有草稿版本，继续将复制现有版本并创建新草稿。确认继续吗？', { title: '创建草稿', confirmText: '创建并继续' }) || disposed) return
        draft = await copyVersionAsDraft(versions[0].id)
      }
    }
    if (disposed) return
    if (!draft) return showFeedback('该题型暂无版本，请先编辑题型并创建草稿版本')
    await router.push(`/types/${draft.id}/${target}`)
  } catch (error) { if (!disposed) showFeedback(error instanceof Error ? error.message : '草稿版本加载失败') }
}

onMounted(async () => {
  try { categories.value = (await getCategories({ status: 1 })).records }
  catch (error) { showFeedback(error instanceof Error ? error.message : '分类加载失败') }
  await load()
})
</script>

<template>
  <section class="type-page">
    <header class="type-header">
      <div>
        <p class="eyebrow">CONTENT LIBRARY</p>
        <h1>题型管理 <span>♡</span></h1>
        <p>管理所有测试题型，支持创建、编辑、启停与版本内容配置。</p>
      </div>
      <button class="primary-action" type="button" :disabled="readOnly" @click="openCreate">＋ 新建题型</button>
    </header>

    <form class="filter-panel" @submit.prevent="search">
      <article class="total-card"><span class="clipboard" aria-hidden="true">♡</span><div>题型总数<strong>{{ total }}<small> 个</small></strong></div></article>
      <label class="search-box"><span>⌕</span><input v-model="keyword" type="search" placeholder="搜索题型" /></label>
      <select v-model="typeFilter" aria-label="测试类型" @change="search">
        <option>全部类型</option><option>单人测试</option><option>双人测试</option>
      </select>
      <select v-model="statusFilter" aria-label="题型状态" @change="search">
        <option>全部状态</option><option>启用</option><option>停用</option>
      </select>
      <div class="filter-actions">
        <button class="filter-button query-button" type="submit">查询</button>
        <button class="filter-button delete-button" type="button" :disabled="readOnly || !selectedIds.length" @click="removeSelected">批量删除</button>
      </div>
    </form>

    <div class="type-table-panel">
      <p class="table-hint">封面、抽题数量、预计时长与版本号取自同一版本：优先草稿，其次已发布，最后历史版本。小程序仍以已发布版本为准。</p>
      <div class="type-table type-table-head">
        <input type="checkbox" :checked="allSelected" aria-label="选择当前页全部题型" @change="togglePageSelection" /><span>题型名称</span><span>封面图</span><span>类型</span><span>当前版本</span><span>抽题数量</span><span>预计时长</span><span>状态</span><span>首页显示</span><span>更新时间</span><span>操作</span>
      </div>
      <div v-for="item in items" :key="item.id" class="type-table type-row">
        <input v-model="selectedIds" type="checkbox" :value="item.id" :aria-label="`选择题型 ${item.name}`" />
        <strong>{{ item.name }}</strong>
        <span class="list-cover"><img v-if="item.coverUrl" :src="assetUrl(item.coverUrl)" :alt="`${item.name}封面`" loading="lazy" /><span v-else>未配置</span></span>
        <span>{{ item.type }}</span>
        <span>{{ item.version }}</span>
        <span>{{ item.drawQuestionCount == null ? '—' : `${item.drawQuestionCount} 题` }}</span>
        <span>{{ item.estimatedMinutes == null ? '—' : `${item.estimatedMinutes} 分钟` }}</span>
        <button class="status-pill" :class="{ disabled: item.status === '停用' }" type="button" :disabled="readOnly" @click="toggleStatus(item)">{{ item.status }}</button>
        <button class="home-pill" :class="item.home === '焦点位' ? 'focus' : item.home === '普通列表' ? 'regular' : ''" type="button" @click="configureHome(item)">{{ item.home }}</button>
        <span>{{ item.updatedAt }}</span>
        <div class="row-actions">
          <button type="button" :disabled="readOnly" @click="openEdit(item)">编辑</button>
          <button type="button" :disabled="readOnly" @click="imageTest = item">AI 生图</button>
          <button type="button" @click="openVersionEditor(item, 'questions')">题目</button>
          <button type="button" @click="openVersionEditor(item, 'results')">结果规则</button>
          <button type="button" @click="configureHome(item)">配置首页</button>
        </div>
      </div>
      <div v-if="!items.length" class="empty-result">没有匹配的题型，换个条件试试。</div>
      <footer class="table-footer"><span>共 {{ total }} 条</span><select v-model.number="pageSize" class="page-size" aria-label="每页条数" @change="changePageSize"><option v-for="size in [10, 20, 50, 100]" :key="size" :value="size">{{ size }} 条/页</option></select><button type="button" :disabled="page <= 1" @click="changePage(-1)">‹</button><b>{{ page }} / {{ pageCount }}</b><button type="button" :disabled="page >= pageCount" @click="changePage(1)">›</button></footer>
    </div>

    <AiImageGenerationDialog v-if="imageTest" :key="imageTest.id" :test-id="imageTest.id" :test-name="imageTest.name" @close="imageTest = null" @applied="load" />
    <div v-if="drawerOpen" class="admin-modal-backdrop" @click.self="closeDrawer">
      <form class="editor-drawer admin-editor-dialog type-editor-dialog" role="dialog" aria-modal="true" aria-label="题型编辑弹窗" @submit.prevent="saveItem" @input="markDirty" @change="markDirty">
        <header class="admin-dialog-header"><div><h2>{{ editingId ? '编辑题型' : '新建题型' }}</h2><p>先填写基本信息，再配置展示图片与答题设置。</p></div><button type="button" aria-label="关闭" :disabled="uploadingCover || saving" @click="closeDrawer">×</button></header>
        <div class="drawer-body" :inert="saving || undefined" :aria-busy="saving">
          <section class="admin-form-section">
            <h3>基本信息</h3><p>设置题型名称、分类和对外展示的介绍。</p>
            <div class="admin-form-grid">
              <label class="admin-field"><span>题型名称</span><input v-model="form.name" maxlength="30" placeholder="请输入题型名称" /><small class="admin-field-hint">{{ form.name.length }} / 30 字</small></label>
              <label class="admin-field"><span>所属分类</span><select v-model.number="form.categoryId"><option v-for="category in categories" :key="category.id" :value="category.id">{{ category.categoryName }}</option></select></label>
              <label class="admin-field"><span>测试类型</span><select v-model="form.type"><option>单人测试</option><option>双人测试</option></select></label>
              <label class="admin-field"><span>当前状态</span><select v-model="form.status"><option>启用</option><option>停用</option></select></label>
              <label class="admin-field admin-field-wide"><span>题型描述</span><textarea v-model="form.description" rows="2" maxlength="200" placeholder="用简短的文字介绍这个测试" /><small class="admin-field-hint">{{ form.description.length }} / 200 字</small></label>
            </div>
          </section>
          <section class="admin-form-section">
            <h3>展示图片</h3><p>上传图片，支持 PNG、JPG、WebP。</p>
            <div class="admin-form-grid image-card-grid">
              <div class="image-card">
                <div class="image-card-heading"><strong>首页封面</strong><label class="image-upload">{{ uploadingCover ? '上传中...' : '上传封面' }}<input type="file" aria-label="上传首页封面" accept="image/png,image/jpeg,image/webp" :disabled="uploadingCover" @change="handleImageUpload($event, 'coverUrl')" /></label></div>
                <div class="cover-preview"><img v-if="form.coverUrl" :src="assetUrl(form.coverUrl)" alt="版本封面预览" /><template v-else>上传首页展示的封面</template></div>
                <small class="admin-field-hint">用于小程序首页，随题型版本发布生效。</small>
              </div>
              <div class="image-card">
                <div class="image-card-heading"><strong>详情图 <span>选填</span></strong><label class="image-upload">{{ uploadingCover ? '上传中...' : '上传详情图' }}<input type="file" aria-label="上传详情图" accept="image/png,image/jpeg,image/webp" :disabled="uploadingCover" @change="handleImageUpload($event, 'detailImageUrl')" /></label></div>
                <div class="cover-preview"><img v-if="form.detailImageUrl" :src="assetUrl(form.detailImageUrl)" alt="详情图完整预览" /><template v-else>未配置时使用内置默认整图</template></div>
                <button v-if="form.detailImageUrl" class="secondary-button" type="button" :disabled="readOnly || uploadingCover" @click="form.detailImageUrl = ''; markDirty()">清除详情图，使用默认图</button>
                <small class="admin-field-hint">建议宽高比约 2.2:1，完整展示、不裁剪；随版本发布生效，不影响首页封面。</small>
              </div>
            </div>
          </section>
          <section class="admin-form-section">
            <h3>答题设置</h3><p>填写每次测试的抽题数量与预计完成时间。</p>
            <div class="admin-form-grid">
              <label class="admin-field"><span>抽题数量（题）</span><input v-model.number="form.drawQuestionCount" type="number" min="1" placeholder="请输入抽题数量" /></label>
              <label class="admin-field"><span>预计时长（分钟）</span><input v-model.number="form.estimatedMinutes" type="number" min="1" placeholder="请输入预计分钟" /></label>
            </div>
          </section>
          <section class="admin-form-section dimension-editor">
            <div class="dimension-title"><div><h3>计分维度</h3><p>至少保留一个维度，可调整显示顺序。</p></div><button type="button" @click="addDimension">＋ 添加维度</button></div>
            <div v-for="(dimension, index) in form.dimensions" :key="dimension.id ?? `new-${index}`" class="dimension-row">
              <span class="dimension-number">{{ index + 1 }}</span>
              <input v-model="dimension.dimensionName" maxlength="64" :aria-label="`计分维度 ${index + 1}`" placeholder="请输入计分维度名称" />
              <div class="dimension-actions"><button type="button" :aria-label="`上移计分维度 ${index + 1}`" :disabled="index === 0" @click="moveDimension(index, -1)">↑</button>
              <button type="button" :aria-label="`下移计分维度 ${index + 1}`" :disabled="index === form.dimensions.length - 1" @click="moveDimension(index, 1)">↓</button>
              <button type="button" :aria-label="`删除计分维度 ${index + 1}`" @click="removeDimension(index)">删除</button></div>
            </div>
          </section>
        </div>
        <footer class="admin-dialog-footer"><span class="admin-footer-note">版本内容保存为草稿，发布后同步至小程序。</span><div class="admin-footer-actions"><button class="secondary-action" type="button" :disabled="uploadingCover || saving" @click="closeDrawer">取消</button><button class="primary-action" type="submit" :disabled="uploadingCover || saving">{{ saving ? '保存中...' : '保存' }}</button></div></footer>
      </form>
    </div>

    <div v-if="feedback" class="toast" role="status">{{ feedback }}</div>
  </section>
</template>

<style scoped>
.type-page { color: var(--ink, #211d22); }
.type-header { display: flex; align-items: flex-end; justify-content: space-between; gap: 24px; }
.type-header h1 { margin: 0; font: 700 clamp(34px, 4vw, 48px) var(--font-ui); }
.type-header h1 span { color: #b984f2; }
.type-header p:last-child { margin: 10px 0 0; color: #756f78; }
.eyebrow { margin: 0 0 6px; color: #9674c7; font-size: 12px; font-weight: 800; letter-spacing: .14em; }
button, input, select, textarea { font: inherit; }
button { cursor: pointer; }
.primary-action, .secondary-action { padding: 12px 22px; border: 1.5px solid #211d22; border-radius: 12px; font-weight: 800; }
.primary-action { background: #ff6c57; color: white; box-shadow: 2px 3px 0 #211d22; }
.secondary-action { background: white; color: #211d22; }
.filter-panel { display: flex; align-items: center; flex-wrap: wrap; gap: 16px; margin: 28px 0 18px; padding: 20px; border: 1.5px solid #211d22; border-radius: 16px; background: #fffdf9; }
.search-box { display: flex; align-items: center; gap: 8px; width: min(330px, 100%); padding: 0 13px; border: 1px solid #cbc5cc; border-radius: 10px; background: white; }
.search-box span { font-size: 25px; transform: rotate(-20deg); }
.search-box input { width: 100%; padding: 11px 0; border: 0; outline: 0; background: transparent; }
.filter-panel select { min-height: 44px; padding: 0 14px; border: 1px solid #cbc5cc; border-radius: 9px; background: white; color: #211d22; }
.filter-actions { display: flex; gap: 10px; }
.filter-button { display: inline-flex; align-items: center; justify-content: center; min-height: 44px; padding: 0 20px; border: 1px solid transparent; border-radius: 10px; font-size: 14px; font-weight: 700; white-space: nowrap; }
.query-button { background: #c9a7ff; border-color: #b68beb; color: #352047; box-shadow: 0 2px 4px rgb(96 57 143 / 12%); }
.query-button:hover { background: #bb91f4; border-color: #a778dd; }
.delete-button { background: #fff6f3; border-color: #edb0a7; color: #a7372c; }
.delete-button:hover:not(:disabled) { background: #ffe8e1; border-color: #d77669; }
.filter-button:focus-visible { outline: 3px solid #9568c8; outline-offset: 3px; }
.filter-button:active:not(:disabled) { box-shadow: none; }
.filter-button:disabled { background: #f3f1f4; border-color: #e4e0e6; color: #928a97; cursor: not-allowed; box-shadow: none; }
@media (max-width: 760px) { .filter-actions { width: 100%; }.filter-button { flex: 1; } }
.total-card { display: flex; flex-shrink: 0; align-items: center; gap: 10px; margin: 0; padding: 8px 12px; border: 1.5px solid #211d22; border-radius: 12px; background: linear-gradient(120deg, #fffef9, #eee2ff); white-space: nowrap; box-sizing: border-box; }
.clipboard { display: grid; place-items: center; width: 36px; height: 36px; border: 1.5px solid #211d22; border-radius: 10px; background: #fff3a9; color: #9b68dc; font-size: 24px; }
.total-card strong { display: inline-block; margin-left: 10px; font-size: 24px; }.total-card small { font-size: 14px; font-weight: 500; }
.type-table-panel { overflow: auto; padding: 16px; border: 1.5px solid #211d22; border-radius: 16px; background: #fffdf9; }
.table-hint { margin: 0 0 12px; color: #756e77; font-size: 12px; }
.type-table { display: grid; grid-template-columns: 28px minmax(190px, 1.6fr) 72px 90px 80px 80px 90px 70px 90px 140px minmax(220px, 1.4fr); align-items: center; gap: 12px; min-width: 1300px; }
.list-cover { display: grid; place-items: center; width: 64px; height: 64px; overflow: hidden; border: 1px solid #e8e0d9; border-radius: 8px; background: #fff; color: #878087; font-size: 12px; }
.list-cover img { width: 100%; height: 100%; object-fit: contain; }
.type-table-head { padding: 13px 14px; border-radius: 10px 10px 0 0; background: #faf3eb; font-size: 13px; font-weight: 800; }
.type-row { min-height: 64px; padding: 9px 14px; border-bottom: 1px solid #e8e0d9; font-size: 13px; }
.status-pill { justify-self: start; padding: 5px 9px; border: 1px solid #bde7a9; border-radius: 8px; background: #ecfbe5; color: #3c9a39; }
.status-pill.disabled { border-color: #d1ccd3; background: #f3f0f3; color: #777078; }
.home-pill { justify-self: start; padding: 5px 9px; border: 1px solid #ffd076; border-radius: 8px; background: #fff8e5; color: #e98b00; }
.home-pill.focus { border-color: #d6c1ff; background: #f2ebff; color: #8a5cdd; }.home-pill.regular { border-color: #ddd; background: #fff; color: #666; }
.row-actions { display: flex; flex-wrap: wrap; gap: 5px; }.row-actions button { padding: 5px 7px; border: 0; background: transparent; color: #3c3540; font-weight: 700; }.row-actions button:hover { color: #ff543d; }
.empty-result { min-width: 1080px; padding: 46px; text-align: center; color: #878087; }
.table-footer { display: flex; justify-content: flex-end; align-items: center; gap: 10px; min-width: 1120px; padding: 18px 6px 2px; color: #756e77; font-size: 13px; }.table-footer button, .table-footer b, .page-size { min-width: 38px; padding: 8px 10px; border: 1px solid #d3ccd4; border-radius: 8px; background: white; text-align: center; }.table-footer b { border-color: #211d22; background: #d7b4ff; color: #211d22; }
.type-editor-dialog { --editor-width: 960px; }
.cover-preview { display: grid; place-items: center; overflow: hidden; border: 1px solid #ddd6ce; border-radius: 10px; background: #fff; color: #817783; text-align: center; font-size: 13px; width: 100%; height: 250px; }
.cover-preview img { width: 100%; height: 100%; object-fit: contain; }
.image-card-grid { margin-top: 20px; }.image-card { display: grid; align-content: start; gap: 12px; min-width: 0; padding: 16px; border: 1px solid #e5ddd4; border-radius: 12px; background: #faf7f1; }
.image-card-heading { display: flex; align-items: center; justify-content: space-between; gap: 12px; }.image-card-heading strong > span { margin-left: 4px; font-size: 12px; font-weight: 400; color: #817783; }
.image-upload { position: relative; display: inline-flex; align-items: center; justify-content: center; flex-shrink: 0; min-height: 34px; padding: 6px 12px; overflow: hidden; border: 1px solid #b5a3c6; border-radius: 8px; background: #f1e9ff; color: #534365; font-size: 13px; font-weight: 700; cursor: pointer; }
.image-upload input { position: absolute; inset: 0; width: 100%; height: 100%; opacity: 0; cursor: pointer; }.image-upload:focus-within { outline: 2px solid #8b62b4; outline-offset: 3px; }.image-upload:has(input:disabled) { opacity: .55; cursor: wait; }
.dimension-editor { display: grid; gap: 10px; }.dimension-title, .dimension-row, .dimension-actions { display: flex; align-items: center; gap: 8px; }.dimension-title { justify-content: space-between; margin-bottom: 4px; }.dimension-title p { margin: 6px 0 0; color: #817783; font-size: 13px; }.dimension-title button, .dimension-row button { padding: 7px 10px; border: 1px solid #cfc5d2; border-radius: 8px; background: white; white-space: nowrap; }.dimension-row input { flex: 1; min-width: 0; min-height: 42px; padding: 9px 12px; border: 1px solid #cbc5cc; border-radius: 8px; }.dimension-number { flex: 0 0 24px; color: #817783; font-size: 13px; text-align: center; }
@media (max-width: 640px) { .image-card { padding: 12px; }.dimension-title { align-items: flex-start; }.dimension-title button { font-size: 12px; }.dimension-row { flex-wrap: wrap; }.dimension-actions { margin-left: auto; }.dimension-row input { flex-basis: calc(100% - 32px); } }
.toast { position: fixed; z-index: 60; right: 28px; bottom: 28px; max-width: calc(100vw - 56px); padding: 13px 18px; border: 1.5px solid #211d22; border-radius: 12px; background: #211d22; color: white; box-shadow: 4px 5px 0 #d9bdff; }
@media (max-width: 760px) { .type-header { align-items: stretch; flex-direction: column; }.primary-action { align-self: flex-start; }.filter-panel { flex-direction: column; }.search-box { width: 100%; }.filter-panel select { width: 100%; }.total-card { width: 100%; } }
</style>
