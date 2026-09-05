<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  assetUrl, copyVersionAsDraft, deleteTests, getCategories, getTest, getTests, getTestVersions, saveTest, saveVersion,
  updateTestStatus, uploadImage, type Category, type TestItem as TestDto, type TestVersion,
} from '../api'
import { isReadOnly } from '../auth'

type TestType = '单人测试' | '双人测试'
type TestStatus = '启用' | '停用'
interface TestItem { id: number; name: string; icon: string; type: TestType; version: string; status: TestStatus; home: '推荐位' | '焦点位' | '普通列表'; updatedAt: string }
interface EditableDimension { id?: number; dimensionCode?: string; dimensionName: string; sortNo: number }
interface TestForm {
  name: string
  iconUrl: string
  coverUrl: string
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
const keyword = ref('')
const typeFilter = ref('全部类型')
const statusFilter = ref('全部状态')
const drawerOpen = ref(false)
const editingId = ref<number | null>(null)
const editingVersion = ref<TestVersion | null>(null)
const uploadingCover = ref(false)
const feedback = ref('')
const items = ref<TestItem[]>([])
const categories = ref<Category[]>([])
const page = ref(1)
const pageSize = 10
const total = ref(0)
const selectedIds = ref<number[]>([])
const form = reactive<TestForm>({ name: '', iconUrl: '', coverUrl: '', description: '', type: '单人测试', status: '启用', categoryId: 0, estimatedMinutes: null, drawQuestionCount: null, dimensions: [] })

const pageCount = computed(() => Math.max(1, Math.ceil(total.value / pageSize)))
const allSelected = computed(() => items.value.length > 0 && items.value.every((item) => selectedIds.value.includes(item.id)))

function toView(item: TestDto): TestItem {
  return { id: item.id, name: item.testName, icon: item.iconUrl ?? '', type: item.testType === 2 ? '双人测试' : '单人测试', version: item.currentVersionNo ? `V${item.currentVersionNo}` : '暂无', status: item.status === 1 ? '启用' : '停用', home: item.homeDisplay === 1 ? '焦点位' : item.homeDisplay === 2 ? '推荐位' : '普通列表', updatedAt: item.updateDate?.replace('T', ' ').slice(0, 16) ?? '—' }
}

function showFeedback(message: string) { feedback.value = message; window.setTimeout(() => { feedback.value = '' }, 2600) }

function closeDrawer() { if (!uploadingCover.value) drawerOpen.value = false }

async function load() {
  try {
    const testPage = await getTests({
      page: page.value,
      size: pageSize,
      keyword: keyword.value.trim(),
      testType: typeFilter.value === '全部类型' ? undefined : typeFilter.value === '双人测试' ? 2 : 1,
      status: statusFilter.value === '全部状态' ? undefined : statusFilter.value === '启用' ? 1 : 0,
    })
    items.value = testPage.records.map(toView)
    total.value = testPage.total
    selectedIds.value = []
  } catch (error) { showFeedback(error instanceof Error ? error.message : '题型加载失败') }
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
  if (!selectedIds.value.length || !window.confirm(`确认删除选中的 ${selectedIds.value.length} 个题型？`)) return
  try {
    await deleteTests(selectedIds.value)
    if (items.value.length === selectedIds.value.length && page.value > 1) page.value -= 1
    await load()
    showFeedback('题型已删除')
  } catch (error) { showFeedback(error instanceof Error ? error.message : '题型删除失败') }
}

function resetForm() {
  Object.assign(form, { name: '', iconUrl: '', coverUrl: '', description: '', type: '单人测试', status: '启用', categoryId: categories.value[0]?.id ?? 0, estimatedMinutes: null, drawQuestionCount: null, dimensions: [{ dimensionName: '', sortNo: 1 }] })
}

function openCreate() { editingId.value = null; editingVersion.value = null; resetForm(); drawerOpen.value = true }

async function openEdit(item: TestItem) {
  editingId.value = item.id
  resetForm()
  try {
    const [detail, versions] = await Promise.all([getTest(item.id), getTestVersions(item.id)])
    const version = versions.find((candidate) => candidate.versionStatus === 1) ?? versions[0] ?? null
    editingVersion.value = version
    Object.assign(form, {
      name: detail.testName, type: detail.testType === 2 ? '双人测试' : '单人测试', status: detail.status === 1 ? '启用' : '停用', categoryId: detail.categoryId,
      iconUrl: detail.iconUrl ?? '', coverUrl: version?.coverUrl ?? '', description: version?.description ?? '', estimatedMinutes: version?.estimatedMinutes ?? null, drawQuestionCount: version?.drawQuestionCount ?? null,
      dimensions: version?.dimensions.map((item) => ({ id: item.id, dimensionCode: item.dimensionCode, dimensionName: item.dimensionName, sortNo: item.sortNo })) ?? [{ dimensionName: '', sortNo: 1 }],
    })
    drawerOpen.value = true
  } catch (error) { showFeedback(error instanceof Error ? error.message : '题型详情加载失败') }
}

async function handleIconUpload(event: Event) {
  const file = (event.target as HTMLInputElement).files?.[0]
  if (!file) return
  uploadingCover.value = true
  try { form.iconUrl = await uploadImage(file); showFeedback('题型 Icon 上传成功') }
  catch (error) { showFeedback(error instanceof Error ? error.message : '题型 Icon 上传失败') }
  finally { uploadingCover.value = false }
}

async function handleCoverUpload(event: Event) {
  const file = (event.target as HTMLInputElement).files?.[0]
  if (!file) return
  uploadingCover.value = true
  try { form.coverUrl = await uploadImage(file); showFeedback('版本封面上传成功') }
  catch (error) { showFeedback(error instanceof Error ? error.message : '版本封面上传失败') }
  finally { uploadingCover.value = false }
}

function addDimension() {
  form.dimensions.push({ dimensionName: '', sortNo: form.dimensions.length + 1 })
}

function removeDimension(index: number) {
  if (form.dimensions.length <= 1) return showFeedback('至少保留 1 个计分维度')
  form.dimensions.splice(index, 1)
  form.dimensions.forEach((item, itemIndex) => { item.sortNo = itemIndex + 1 })
}

function moveDimension(index: number, offset: number) {
  const target = index + offset
  if (target < 0 || target >= form.dimensions.length) return
  ;[form.dimensions[index], form.dimensions[target]] = [form.dimensions[target], form.dimensions[index]]
  form.dimensions.forEach((item, itemIndex) => { item.sortNo = itemIndex + 1 })
}

function versionPayload(version?: TestVersion | null) {
  const dimensions = form.dimensions.map((item, index) => ({ id: item.id, dimensionCode: item.dimensionCode, dimensionName: item.dimensionName.trim(), sortNo: index + 1 }))
  return { title: form.name.trim(), coverUrl: form.coverUrl, description: form.description, estimatedMinutes: Number(form.estimatedMinutes), drawQuestionCount: Number(form.drawQuestionCount), versionNote: version?.versionNote ?? '', dimensions }
}

async function saveItem() {
  if (uploadingCover.value) return
  if (!form.name.trim() || !form.categoryId) return showFeedback('请填写题型名称并选择分类')
  if (!editingId.value && (!form.iconUrl || !form.coverUrl)) return showFeedback('新建题型请分别上传题型 Icon 和版本封面')
  if (form.coverUrl && (!form.dimensions.length || form.dimensions.some((item) => !item.dimensionName.trim()))) return showFeedback('请填写完整的计分维度')
  if (form.coverUrl && (!Number.isInteger(form.estimatedMinutes) || Number(form.estimatedMinutes) < 1 || !Number.isInteger(form.drawQuestionCount) || Number(form.drawQuestionCount) < 1)) return showFeedback('请填写有效的抽题数量和预计分钟')
  try {
    const saved = await saveTest({ testName: form.name.trim(), testType: form.type === '双人测试' ? 2 : 1, categoryId: Number(form.categoryId), iconUrl: form.iconUrl, status: form.status === '启用' ? 1 : 0 }, editingId.value ?? undefined)
    if (form.coverUrl) {
      let draft = editingVersion.value
      if (draft && draft.versionStatus !== 1) draft = await copyVersionAsDraft(draft.id)
      await saveVersion(saved.id, versionPayload(draft), draft?.id)
    }
    drawerOpen.value = false
    showFeedback(editingId.value ? '题型已更新' : '题型及草稿版本已新建')
    await load()
  } catch (error) { showFeedback(error instanceof Error ? error.message : '保存失败') }
}

async function toggleStatus(item: TestItem) {
  try { await updateTestStatus(item.id, item.status === '启用' ? 0 : 1); await load(); showFeedback(`${item.name} 状态已更新`) }
  catch (error) { showFeedback(error instanceof Error ? error.message : '状态更新失败') }
}

function configureHome(item: TestItem) {
  void router.push({ name: 'home-config', query: { testId: item.id } })
}

async function openVersionEditor(item: TestItem, target: 'questions' | 'results') {
  try {
    const versions = await getTestVersions(item.id)
    let draft = versions.find((version) => version.versionStatus === 1)
    if (!draft && versions[0]) draft = await copyVersionAsDraft(versions[0].id)
    if (!draft) return showFeedback('该题型暂无版本，请先编辑题型并创建草稿版本')
    await router.push(`/types/${draft.id}/${target}`)
  } catch (error) { showFeedback(error instanceof Error ? error.message : '草稿版本加载失败') }
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
      <label class="search-box"><span>⌕</span><input v-model="keyword" type="search" placeholder="搜索题型" /></label>
      <select v-model="typeFilter" aria-label="测试类型" @change="search">
        <option>全部类型</option><option>单人测试</option><option>双人测试</option>
      </select>
      <select v-model="statusFilter" aria-label="题型状态" @change="search">
        <option>全部状态</option><option>启用</option><option>停用</option>
      </select>
      <button type="submit">查询</button><button type="button" :disabled="readOnly || !selectedIds.length" @click="removeSelected">批量删除</button>
    </form>

    <article class="total-card"><span class="clipboard">♡</span><div>题型总数<strong>{{ total }}<small> 个</small></strong></div></article>

    <div class="type-table-panel">
      <div class="type-table type-table-head">
        <input type="checkbox" :checked="allSelected" aria-label="选择当前页全部题型" @change="togglePageSelection" /><span>题型名称</span><span>Icon</span><span>类型</span><span>当前版本</span><span>状态</span><span>首页显示</span><span>更新时间</span><span>操作</span>
      </div>
      <div v-for="item in items" :key="item.id" class="type-table type-row">
        <input v-model="selectedIds" type="checkbox" :value="item.id" :aria-label="`选择题型 ${item.name}`" />
        <strong>{{ item.name }}</strong>
        <span class="type-icon"><img v-if="item.icon" :src="assetUrl(item.icon)" :alt="item.name" /><span v-else>—</span></span>
        <span>{{ item.type }}</span>
        <span>{{ item.version }}</span>
        <button class="status-pill" :class="{ disabled: item.status === '停用' }" type="button" :disabled="readOnly" @click="toggleStatus(item)">{{ item.status }}</button>
        <button class="home-pill" :class="item.home === '焦点位' ? 'focus' : item.home === '普通列表' ? 'regular' : ''" type="button" @click="configureHome(item)">{{ item.home }}</button>
        <span>{{ item.updatedAt }}</span>
        <div class="row-actions">
          <button type="button" :disabled="readOnly" @click="openEdit(item)">编辑</button>
          <button type="button" @click="openVersionEditor(item, 'questions')">题目</button>
          <button type="button" @click="openVersionEditor(item, 'results')">结果规则</button>
          <button type="button" @click="configureHome(item)">配置首页</button>
        </div>
      </div>
      <div v-if="!items.length" class="empty-result">没有匹配的题型，换个条件试试。</div>
      <footer class="table-footer"><span>共 {{ total }} 条</span><span class="page-size">{{ pageSize }} 条/页</span><button type="button" :disabled="page <= 1" @click="changePage(-1)">‹</button><b>{{ page }} / {{ pageCount }}</b><button type="button" :disabled="page >= pageCount" @click="changePage(1)">›</button></footer>
    </div>

    <div v-if="drawerOpen" class="drawer-backdrop" @click.self="closeDrawer">
      <form class="editor-drawer" role="dialog" aria-modal="true" aria-label="题型编辑抽屉" @submit.prevent="saveItem">
        <header><h2>{{ editingId ? '编辑题型' : '新建题型' }}</h2><button type="button" aria-label="关闭" :disabled="uploadingCover" @click="closeDrawer">×</button></header>
        <div class="drawer-body">
          <label>题型 Icon</label>
          <div class="upload-row">
            <div class="icon-preview"><img v-if="form.iconUrl" :src="assetUrl(form.iconUrl)" alt="题型 Icon 预览" /><template v-else>♡</template></div>
            <label class="upload-box">⇧<span>{{ uploadingCover ? '上传中...' : '上传 Icon' }}</span><input type="file" accept="image/png,image/jpeg,image/webp" :disabled="uploadingCover" @change="handleIconUpload" /></label>
          </div>
          <label>Icon 链接（URL）<input v-model.trim="form.iconUrl" inputmode="url" maxlength="500" pattern="https?://.+|/.+" title="请输入 HTTP(S) 图片地址或站内图片路径" :disabled="uploadingCover" placeholder="也可直接填写 Icon URL" /></label>
          <label>题型版本封面</label>
          <div class="upload-row">
            <div class="cover-preview"><img v-if="form.coverUrl" :src="assetUrl(form.coverUrl)" alt="版本封面预览" /><template v-else>无封面</template></div>
            <label class="upload-box">⇧<span>{{ uploadingCover ? '上传中...' : '上传封面' }}</span><input type="file" accept="image/png,image/jpeg,image/webp" :disabled="uploadingCover" @change="handleCoverUpload" /></label>
          </div>
          <small>支持 PNG、JPG、WebP，上传成功后自动回填对应链接。</small>
          <label>封面链接（URL）<input v-model.trim="form.coverUrl" inputmode="url" maxlength="500" pattern="https?://.+|/.+" title="请输入 HTTP(S) 图片地址或站内图片路径" :disabled="uploadingCover" placeholder="也可直接填写封面 URL" /></label>
          <label>题型名称<input v-model="form.name" maxlength="30" placeholder="请输入题型名称" /></label>
          <label>所属分类<select v-model.number="form.categoryId"><option v-for="category in categories" :key="category.id" :value="category.id">{{ category.categoryName }}</option></select></label>
          <label>测试类型<select v-model="form.type"><option>单人测试</option><option>双人测试</option></select></label>
          <label>当前状态<select v-model="form.status"><option>启用</option><option>停用</option></select></label>
          <label>题型描述<input v-model="form.description" maxlength="200" placeholder="请输入题型描述" /></label>
          <label>抽题数量<input v-model.number="form.drawQuestionCount" type="number" min="1" /></label>
          <label>预计分钟<input v-model.number="form.estimatedMinutes" type="number" min="1" /></label>
          <div class="dimension-editor">
            <div class="dimension-title"><strong>计分维度</strong><button type="button" @click="addDimension">＋ 添加维度</button></div>
            <div v-for="(dimension, index) in form.dimensions" :key="dimension.id ?? `new-${index}`" class="dimension-row">
              <input v-model="dimension.dimensionName" maxlength="64" :aria-label="`计分维度 ${index + 1}`" placeholder="请输入计分维度名称" />
              <button type="button" :disabled="index === 0" @click="moveDimension(index, -1)">↑</button>
              <button type="button" :disabled="index === form.dimensions.length - 1" @click="moveDimension(index, 1)">↓</button>
              <button type="button" @click="removeDimension(index)">删除</button>
            </div>
          </div>
        </div>
        <footer><button class="secondary-action" type="button" :disabled="uploadingCover" @click="closeDrawer">取消</button><button class="primary-action" type="submit" :disabled="uploadingCover">保存</button></footer>
      </form>
    </div>

    <div v-if="feedback" class="toast" role="status">{{ feedback }}</div>
  </section>
</template>

<style scoped>
.type-page { color: var(--ink, #211d22); }
.type-header { display: flex; align-items: flex-end; justify-content: space-between; gap: 24px; }
.type-header h1 { margin: 0; font: 700 clamp(34px, 4vw, 48px) "STKaiti", "KaiTi", serif; }
.type-header h1 span { color: #b984f2; }
.type-header p:last-child { margin: 10px 0 0; color: #756f78; }
.eyebrow { margin: 0 0 6px; color: #9674c7; font-size: 12px; font-weight: 800; letter-spacing: .14em; }
button, input, select { font: inherit; }
button { cursor: pointer; }
.primary-action, .secondary-action { padding: 12px 22px; border: 1.5px solid #211d22; border-radius: 12px; font-weight: 800; }
.primary-action { background: #ff6c57; color: white; box-shadow: 2px 3px 0 #211d22; }
.secondary-action { background: white; color: #211d22; }
.filter-panel { display: flex; gap: 16px; margin-top: 28px; padding: 20px; border: 1.5px solid #211d22; border-radius: 16px; background: #fffdf9; }
.search-box { display: flex; align-items: center; gap: 8px; width: min(330px, 100%); padding: 0 13px; border: 1px solid #cbc5cc; border-radius: 10px; background: white; }
.search-box span { font-size: 25px; transform: rotate(-20deg); }
.search-box input { width: 100%; padding: 11px 0; border: 0; outline: 0; background: transparent; }
select, .drawer-body input { min-height: 44px; padding: 0 14px; border: 1px solid #cbc5cc; border-radius: 9px; background: white; color: #211d22; }
.total-card { display: flex; align-items: center; gap: 18px; width: 275px; margin: 22px 0 18px; padding: 18px 24px; border: 1.5px solid #211d22; border-radius: 16px; background: linear-gradient(120deg, #fffef9, #eee2ff); }
.clipboard { display: grid; place-items: center; width: 58px; height: 58px; border: 1.5px solid #211d22; border-radius: 15px; background: #fff3a9; color: #9b68dc; font-size: 34px; }
.total-card strong { display: block; margin-top: 2px; font-size: 30px; }.total-card small { font-size: 14px; font-weight: 500; }
.type-table-panel { overflow: auto; padding: 16px; border: 1.5px solid #211d22; border-radius: 16px; background: #fffdf9; }
.type-table { display: grid; grid-template-columns: 28px minmax(190px, 1.6fr) 60px 100px 90px 80px 90px 150px minmax(220px, 1.4fr); align-items: center; gap: 12px; min-width: 1120px; }
.type-table-head { padding: 13px 14px; border-radius: 10px 10px 0 0; background: #faf3eb; font-size: 13px; font-weight: 800; }
.type-row { min-height: 64px; padding: 9px 14px; border-bottom: 1px solid #e8e0d9; font-size: 13px; }
.type-icon { display: grid; place-items: center; width: 40px; height: 40px; overflow: hidden; border: 1px solid #211d22; border-radius: 9px; background: white; color: #a76de9; font-size: 25px; }
.type-icon img, .icon-preview img, .cover-preview img { width: 100%; height: 100%; object-fit: cover; }
.status-pill { justify-self: start; padding: 5px 9px; border: 1px solid #bde7a9; border-radius: 8px; background: #ecfbe5; color: #3c9a39; }
.status-pill.disabled { border-color: #d1ccd3; background: #f3f0f3; color: #777078; }
.home-pill { justify-self: start; padding: 5px 9px; border: 1px solid #ffd076; border-radius: 8px; background: #fff8e5; color: #e98b00; }
.home-pill.focus { border-color: #d6c1ff; background: #f2ebff; color: #8a5cdd; }.home-pill.regular { border-color: #ddd; background: #fff; color: #666; }
.row-actions { display: flex; flex-wrap: wrap; gap: 5px; }.row-actions button { padding: 5px 7px; border: 0; background: transparent; color: #3c3540; font-weight: 700; }.row-actions button:hover { color: #ff543d; }
.empty-result { min-width: 1080px; padding: 46px; text-align: center; color: #878087; }
.table-footer { display: flex; justify-content: flex-end; align-items: center; gap: 10px; min-width: 1120px; padding: 18px 6px 2px; color: #756e77; font-size: 13px; }.table-footer button, .table-footer b, .page-size { min-width: 38px; padding: 8px 10px; border: 1px solid #d3ccd4; border-radius: 8px; background: white; text-align: center; }.table-footer b { border-color: #211d22; background: #d7b4ff; color: #211d22; }
.drawer-backdrop { position: fixed; z-index: 40; inset: 0; background: rgb(33 29 34 / 18%); }
.editor-drawer { position: absolute; top: 0; right: 0; display: grid; grid-template-rows: auto 1fr auto; width: min(420px, 100%); height: 100%; border-left: 1.5px solid #211d22; background: #fffdf9; box-shadow: -12px 0 35px rgb(33 29 34 / 12%); animation: slide-in .2s ease-out; }
.editor-drawer header, .editor-drawer footer { display: flex; align-items: center; justify-content: space-between; gap: 16px; padding: 20px 24px; border-bottom: 1px solid #ddd6de; }.editor-drawer footer { border-top: 1px solid #ddd6de; border-bottom: 0; }.editor-drawer footer button { flex: 1; }
.editor-drawer h2 { margin: 0; font: 700 27px "STKaiti", "KaiTi", serif; }.editor-drawer header button { border: 0; background: transparent; font-size: 31px; }
.drawer-body { overflow: auto; padding: 24px; }.drawer-body > label { display: grid; gap: 8px; margin-top: 24px; font-weight: 800; }.drawer-body > label:first-child { margin-top: 0; }.drawer-body small { display: block; margin-top: 9px; color: #7f7782; }
.upload-row { display: flex; gap: 14px; margin-top: 10px; }.icon-preview, .cover-preview, .upload-box { width: 120px; height: 120px; display: grid; place-items: center; overflow: hidden; border: 1.5px solid #211d22; border-radius: 14px; background: white; color: #a76de9; font-size: 54px; }.cover-preview { width: 190px; color: #777; font-size: 13px; }.upload-box { border-style: dashed; color: #211d22; font-size: 32px; cursor: pointer; }.upload-box span { margin-top: -32px; font-size: 13px; font-weight: 700; }.upload-box input { display: none; }
.dimension-editor { display: grid; gap: 10px; }.dimension-title, .dimension-row { display: flex; align-items: center; gap: 8px; }.dimension-title { justify-content: space-between; }.dimension-title button, .dimension-row button { padding: 7px 10px; border: 1px solid #aaa2aa; border-radius: 8px; background: white; }.dimension-row input { flex: 1; min-width: 0; }
.toast { position: fixed; z-index: 60; right: 28px; bottom: 28px; max-width: calc(100vw - 56px); padding: 13px 18px; border: 1.5px solid #211d22; border-radius: 12px; background: #211d22; color: white; box-shadow: 4px 5px 0 #d9bdff; }
@keyframes slide-in { from { transform: translateX(30px); opacity: .6; } }
@media (max-width: 760px) { .type-header { align-items: stretch; flex-direction: column; }.primary-action { align-self: flex-start; }.filter-panel { flex-direction: column; }.search-box { width: 100%; }.filter-panel select { width: 100%; }.total-card { width: 100%; } }
</style>
