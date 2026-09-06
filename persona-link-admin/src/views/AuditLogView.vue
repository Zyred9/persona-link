<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { getContentAudits, type AuditLog } from '../api'

const records = ref<AuditLog[]>([])
const total = ref(0)
const page = ref(1)
const size = 20
const bizType = ref('')
const bizId = ref('')
const operatorId = ref('')
const notice = ref('')
const selected = ref<AuditLog | null>(null)

const bizLabels: Record<number, string> = { 1: '题型', 2: '版本及版本内容', 3: '首页配置' }
const actionLabels: Record<number, string> = { 1: '新增', 2: '修改', 3: '状态变更', 4: '发布', 5: '下线', 6: '删除', 7: '预约发布', 8: '取消预约', 9: '归档' }

async function load() {
  try {
    notice.value = ''
    const result = await getContentAudits({ page: page.value, size, bizType: bizType.value, bizId: bizId.value, operatorId: operatorId.value })
    records.value = result.records
    total.value = result.total
  } catch (error) {
    notice.value = error instanceof Error ? error.message : '审计记录加载失败'
  }
}

async function search() { page.value = 1; await load() }
async function changePage(offset: number) { page.value += offset; await load() }

onMounted(load)
</script>

<template>
  <section class="audit-view">
    <header><div><h1>操作审计</h1><p>追踪内容变更、发布和删除记录</p></div><b>{{ total }} 条</b></header>
    <p v-if="notice" class="notice" role="status">{{ notice }}</p>
    <form class="filters" @submit.prevent="search">
      <label>业务类型<select v-model="bizType"><option value="">全部</option><option v-for="(label, key) in bizLabels" :key="key" :value="key">{{ label }}</option></select></label>
      <label>业务 ID<input v-model="bizId" type="number" min="1" placeholder="全部" /></label>
      <label>操作人 ID<input v-model="operatorId" type="number" min="0" placeholder="全部" /></label>
      <button type="submit">查询</button>
    </form>
    <div class="table-card">
      <div class="table-head"><span>时间</span><span>业务</span><span>动作</span><span>操作人</span><span>原因</span><span>详情</span></div>
      <div v-for="item in records" :key="item.id" class="table-row">
        <span>{{ new Date(item.createDate).toLocaleString() }}</span>
        <span>{{ bizLabels[item.bizType] ?? `类型${item.bizType}` }} #{{ item.bizId }}</span>
        <b>{{ actionLabels[item.actionType] ?? `动作${item.actionType}` }}</b>
        <span>#{{ item.operatorId }}</span>
        <span>{{ item.reason || '—' }}</span>
        <button type="button" @click="selected = item">查看</button>
      </div>
      <p v-if="!records.length" class="empty">暂无审计记录</p>
    </div>
    <footer><span>第 {{ page }} / {{ Math.max(1, Math.ceil(total / size)) }} 页</span><div><button :disabled="page <= 1" @click="changePage(-1)">上一页</button><button :disabled="page * size >= total" @click="changePage(1)">下一页</button></div></footer>

    <div v-if="selected" class="modal admin-modal-backdrop" @click.self="selected = null">
      <article><header><h2>审计详情 #{{ selected.id }}</h2><button @click="selected = null">×</button></header><h3>变更前</h3><pre>{{ selected.beforeSnapshot || '无' }}</pre><h3>变更后</h3><pre>{{ selected.afterSnapshot || '无' }}</pre></article>
    </div>
  </section>
</template>

<style scoped>
.audit-view{display:grid;gap:18px}.audit-view>header{display:flex;align-items:center;justify-content:space-between}.audit-view h1{margin:0;font-size:38px}.audit-view p{margin:6px 0;color:#777}.filters{display:flex;align-items:end;gap:12px;padding:16px;border:1.5px solid #aaa;border-radius:14px;background:#fff}.filters label{display:grid;gap:6px;font-weight:700}.filters input,.filters select{min-width:150px;border:1px solid #bbb;border-radius:9px;padding:9px;font:inherit}.filters button,.table-row button,footer button{border:1.5px solid #222;border-radius:9px;background:#fff;padding:8px 14px;cursor:pointer}.filters>button{background:#ff6652;color:#fff}.table-card{overflow:auto;border:1.5px solid #aaa;border-radius:14px;background:#fff}.table-head,.table-row{display:grid;grid-template-columns:180px 150px 110px 100px minmax(160px,1fr) 80px;align-items:center;gap:12px;min-width:900px;padding:14px 18px}.table-head{background:#f5effe;font-weight:800}.table-row{border-top:1px solid #ddd}.empty{text-align:center!important;padding:24px}footer{display:flex;justify-content:space-between;align-items:center}footer div{display:flex;gap:8px}button:disabled{opacity:.4;cursor:not-allowed}.modal article{width:min(760px,100%);overflow:auto;border:2px solid #222;border-radius:16px;background:#fff;padding:20px}.modal article header{display:flex;justify-content:space-between;align-items:center}.modal h2{margin:0}.modal h3{margin:18px 0 8px}.modal pre{white-space:pre-wrap;overflow-wrap:anywhere;padding:12px;border-radius:10px;background:#f6f3ef}@media(max-width:720px){.filters{align-items:stretch;flex-direction:column}.filters input,.filters select{width:100%}.audit-view h1{font-size:30px}}
</style>
