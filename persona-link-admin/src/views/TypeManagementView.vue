<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'

type TestType = '单人测试' | '双人测试'
type TestStatus = '启用' | '停用'

interface TestItem {
  id: number
  name: string
  icon: string
  iconUrl?: string
  type: TestType
  version: string
  status: TestStatus
  home: '推荐位' | '焦点位' | '未展示'
  updatedAt: string
}

const router = useRouter()
const keyword = ref('')
const typeFilter = ref('全部类型')
const statusFilter = ref('全部状态')
const drawerOpen = ref(false)
const editingId = ref<number | null>(null)
const feedback = ref('')

const items = ref<TestItem[]>([
  { id: 1, name: '你是哪种牛马性格？', icon: '♡', type: '单人测试', version: 'V1.2', status: '启用', home: '推荐位', updatedAt: '2025-05-01 10:00' },
  { id: 2, name: '双人关系角色测试', icon: '♧', type: '双人测试', version: 'V1.0', status: '启用', home: '焦点位', updatedAt: '2025-05-22 10:00' },
  { id: 3, name: '你的 X 偏好？', icon: '×', type: '单人测试', version: 'V0.1', status: '启用', home: '推荐位', updatedAt: '—' },
  { id: 4, name: '恋爱中的担当测', icon: '☵', type: '单人测试', version: 'V1.1', status: '启用', home: '未展示', updatedAt: '2025-04-20 09:30' },
  { id: 5, name: '我们的默契挑战', icon: '♢', type: '双人测试', version: 'V1.0', status: '停用', home: '未展示', updatedAt: '2025-04-15 08:00' },
])

const form = reactive({ name: '', icon: '♡', iconUrl: '', type: '单人测试' as TestType, status: '启用' as TestStatus })

const filteredItems = computed(() => items.value.filter((item) => {
  const matchesKeyword = item.name.toLowerCase().includes(keyword.value.trim().toLowerCase())
  const matchesType = typeFilter.value === '全部类型' || item.type === typeFilter.value
  const matchesStatus = statusFilter.value === '全部状态' || item.status === statusFilter.value
  return matchesKeyword && matchesType && matchesStatus
}))

function showFeedback(message: string) {
  feedback.value = message
  window.setTimeout(() => { feedback.value = '' }, 2200)
}

function openCreate() {
  editingId.value = null
  Object.assign(form, { name: '', icon: '♡', iconUrl: '', type: '单人测试', status: '启用' })
  drawerOpen.value = true
}

function openEdit(item: TestItem) {
  editingId.value = item.id
  Object.assign(form, { name: item.name, icon: item.icon, iconUrl: item.iconUrl ?? '', type: item.type, status: item.status })
  drawerOpen.value = true
}

function handleIconUpload(event: Event) {
  const file = (event.target as HTMLInputElement).files?.[0]
  if (!file) return
  const reader = new FileReader()
  reader.onload = () => { form.iconUrl = String(reader.result ?? '') }
  reader.readAsDataURL(file)
}

function saveItem() {
  if (!form.name.trim()) {
    showFeedback('请先填写题型名称')
    return
  }
  if (editingId.value) {
    const item = items.value.find(({ id }) => id === editingId.value)
    if (item) Object.assign(item, { ...form, name: form.name.trim(), updatedAt: '刚刚' })
  } else {
    items.value.unshift({
      id: Date.now(),
      name: form.name.trim(),
      icon: form.icon,
      iconUrl: form.iconUrl,
      type: form.type,
      version: 'V0.1',
      status: form.status,
      home: '未展示',
      updatedAt: '刚刚',
    })
  }
  drawerOpen.value = false
  showFeedback(editingId.value ? '题型已更新（仅本地演示）' : '题型已新建（仅本地演示）')
}

function toggleStatus(item: TestItem) {
  item.status = item.status === '启用' ? '停用' : '启用'
  showFeedback(`${item.name} 已${item.status}（仅本地演示）`)
}
</script>

<template>
  <section class="type-page">
    <div class="demo-notice"><b>演示模式</b> 当前操作仅保存在页面内存中，刷新后恢复，尚未接入后端接口。</div>

    <header class="type-header">
      <div>
        <p class="eyebrow">CONTENT LIBRARY</p>
        <h1>题型管理 <span>♡</span></h1>
        <p>管理所有测试题型，支持创建、编辑、启停与版本内容配置。</p>
      </div>
      <button class="primary-action" type="button" @click="openCreate">＋ 新建题型</button>
    </header>

    <div class="filter-panel">
      <label class="search-box"><span>⌕</span><input v-model="keyword" type="search" placeholder="搜索题型" /></label>
      <select v-model="typeFilter" aria-label="测试类型">
        <option>全部类型</option><option>单人测试</option><option>双人测试</option>
      </select>
      <select v-model="statusFilter" aria-label="题型状态">
        <option>全部状态</option><option>启用</option><option>停用</option>
      </select>
    </div>

    <article class="total-card"><span class="clipboard">♡</span><div>题型总数<strong>{{ filteredItems.length }}<small> 个</small></strong></div></article>

    <div class="type-table-panel">
      <div class="type-table type-table-head">
        <span>题型名称</span><span>Icon</span><span>类型</span><span>当前版本</span><span>状态</span><span>首页显示</span><span>更新时间</span><span>操作</span>
      </div>
      <div v-for="item in filteredItems" :key="item.id" class="type-table type-row">
        <strong>{{ item.name }}</strong>
        <span class="type-icon"><img v-if="item.iconUrl" :src="item.iconUrl" alt="题型图标" /><template v-else>{{ item.icon }}</template></span>
        <span>{{ item.type }}</span>
        <span>{{ item.version }}</span>
        <button class="status-pill" :class="{ disabled: item.status === '停用' }" type="button" @click="toggleStatus(item)">{{ item.status }}</button>
        <span class="home-pill" :class="item.home === '焦点位' ? 'focus' : item.home === '未展示' ? 'hidden' : ''">{{ item.home }}</span>
        <span>{{ item.updatedAt }}</span>
        <div class="row-actions">
          <button type="button" @click="openEdit(item)">编辑</button>
          <button type="button" @click="router.push(`/types/${item.id}/questions`)">题目</button>
          <button type="button" @click="router.push(`/types/${item.id}/results`)">结果规则</button>
        </div>
      </div>
      <div v-if="!filteredItems.length" class="empty-result">没有匹配的题型，换个条件试试。</div>
      <footer class="table-footer"><span>共 {{ filteredItems.length }} 条</span><span class="page-size">10 条/页</span><button type="button" disabled>‹</button><b>1</b><button type="button" disabled>›</button></footer>
    </div>

    <div v-if="drawerOpen" class="drawer-backdrop" @click.self="drawerOpen = false">
      <aside class="editor-drawer" role="dialog" aria-modal="true" aria-label="题型编辑抽屉">
        <header><h2>{{ editingId ? '编辑题型' : '新建题型' }}</h2><button type="button" aria-label="关闭" @click="drawerOpen = false">×</button></header>
        <div class="drawer-body">
          <label>题型 Icon</label>
          <div class="upload-row">
            <div class="icon-preview"><img v-if="form.iconUrl" :src="form.iconUrl" alt="上传预览" /><template v-else>{{ form.icon }}</template></div>
            <label class="upload-box">⇧<span>点击上传 Icon</span><input type="file" accept="image/png,image/jpeg,image/webp" @change="handleIconUpload" /></label>
          </div>
          <small>支持 PNG、JPG、WebP，建议 1:1；仅在浏览器本地预览。</small>
          <label>题型名称<input v-model="form.name" maxlength="30" placeholder="请输入题型名称" /></label>
          <label>测试类型<select v-model="form.type"><option>单人测试</option><option>双人测试</option></select></label>
          <label>当前状态<select v-model="form.status"><option>启用</option><option>停用</option></select></label>
        </div>
        <footer><button class="secondary-action" type="button" @click="drawerOpen = false">取消</button><button class="primary-action" type="button" @click="saveItem">保存</button></footer>
      </aside>
    </div>

    <div v-if="feedback" class="toast" role="status">{{ feedback }}</div>
  </section>
</template>

<style scoped>
.type-page { color: var(--ink, #211d22); }
.demo-notice { margin-bottom: 22px; padding: 11px 15px; border: 1.5px dashed #9b74dc; border-radius: 12px; background: #f3ebff; color: #6d4c9d; font-size: 13px; }
.demo-notice b { margin-right: 8px; color: #ff543d; }
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
.type-table { display: grid; grid-template-columns: minmax(190px, 1.6fr) 60px 100px 90px 80px 90px 150px minmax(220px, 1.4fr); align-items: center; gap: 12px; min-width: 1080px; }
.type-table-head { padding: 13px 14px; border-radius: 10px 10px 0 0; background: #faf3eb; font-size: 13px; font-weight: 800; }
.type-row { min-height: 64px; padding: 9px 14px; border-bottom: 1px solid #e8e0d9; font-size: 13px; }
.type-icon { display: grid; place-items: center; width: 40px; height: 40px; overflow: hidden; border: 1px solid #211d22; border-radius: 9px; background: white; color: #a76de9; font-size: 25px; }
.type-icon img, .icon-preview img { width: 100%; height: 100%; object-fit: cover; }
.status-pill { justify-self: start; padding: 5px 9px; border: 1px solid #bde7a9; border-radius: 8px; background: #ecfbe5; color: #3c9a39; }
.status-pill.disabled { border-color: #d1ccd3; background: #f3f0f3; color: #777078; }
.home-pill { justify-self: start; padding: 5px 9px; border: 1px solid #ffd076; border-radius: 8px; background: #fff8e5; color: #e98b00; }
.home-pill.focus { border-color: #d6c1ff; background: #f2ebff; color: #8a5cdd; }.home-pill.hidden { border-color: transparent; background: transparent; color: #858087; }
.row-actions { display: flex; flex-wrap: wrap; gap: 5px; }.row-actions button { padding: 5px 7px; border: 0; background: transparent; color: #3c3540; font-weight: 700; }.row-actions button:hover { color: #ff543d; }
.empty-result { min-width: 1080px; padding: 46px; text-align: center; color: #878087; }
.table-footer { display: flex; justify-content: flex-end; align-items: center; gap: 10px; min-width: 1080px; padding: 18px 6px 2px; color: #756e77; font-size: 13px; }.table-footer button, .table-footer b, .page-size { min-width: 38px; padding: 8px 10px; border: 1px solid #d3ccd4; border-radius: 8px; background: white; text-align: center; }.table-footer b { border-color: #211d22; background: #d7b4ff; color: #211d22; }
.drawer-backdrop { position: fixed; z-index: 40; inset: 0; background: rgb(33 29 34 / 18%); }
.editor-drawer { position: absolute; top: 0; right: 0; display: grid; grid-template-rows: auto 1fr auto; width: min(420px, 100%); height: 100%; border-left: 1.5px solid #211d22; background: #fffdf9; box-shadow: -12px 0 35px rgb(33 29 34 / 12%); animation: slide-in .2s ease-out; }
.editor-drawer header, .editor-drawer footer { display: flex; align-items: center; justify-content: space-between; gap: 16px; padding: 20px 24px; border-bottom: 1px solid #ddd6de; }.editor-drawer footer { border-top: 1px solid #ddd6de; border-bottom: 0; }.editor-drawer footer button { flex: 1; }
.editor-drawer h2 { margin: 0; font: 700 27px "STKaiti", "KaiTi", serif; }.editor-drawer header button { border: 0; background: transparent; font-size: 31px; }
.drawer-body { overflow: auto; padding: 24px; }.drawer-body > label { display: grid; gap: 8px; margin-top: 24px; font-weight: 800; }.drawer-body > label:first-child { margin-top: 0; }.drawer-body small { display: block; margin-top: 9px; color: #7f7782; }
.upload-row { display: flex; gap: 14px; margin-top: 10px; }.icon-preview, .upload-box { width: 120px; height: 120px; display: grid; place-items: center; overflow: hidden; border: 1.5px solid #211d22; border-radius: 14px; background: white; color: #a76de9; font-size: 54px; }.upload-box { border-style: dashed; color: #211d22; font-size: 32px; cursor: pointer; }.upload-box span { margin-top: -32px; font-size: 13px; font-weight: 700; }.upload-box input { display: none; }
.toast { position: fixed; z-index: 60; right: 28px; bottom: 28px; max-width: calc(100vw - 56px); padding: 13px 18px; border: 1.5px solid #211d22; border-radius: 12px; background: #211d22; color: white; box-shadow: 4px 5px 0 #d9bdff; }
@keyframes slide-in { from { transform: translateX(30px); opacity: .6; } }
@media (max-width: 760px) { .type-header { align-items: stretch; flex-direction: column; }.primary-action { align-self: flex-start; }.filter-panel { flex-direction: column; }.search-box { width: 100%; }.filter-panel select { width: 100%; }.total-card { width: 100%; }.demo-notice { line-height: 1.6; } }
</style>
