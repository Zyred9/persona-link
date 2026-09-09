<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { getFeedbacks, type FeedbackItem } from '../api'

const records = ref<FeedbackItem[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const loading = ref(false)
const notice = ref('')
let disposed = false

async function load(nextPage = page.value, nextSize = size.value) {
  if (loading.value || disposed) return
  loading.value = true
  notice.value = ''
  try {
    const result = await getFeedbacks(nextPage, nextSize)
    if (disposed) return
    records.value = result.records
    total.value = result.total
    page.value = result.page
    size.value = result.size
  } catch (error) {
    if (!disposed) notice.value = error instanceof Error ? error.message : '反馈加载失败，请重试'
  } finally { if (!disposed) loading.value = false }
}
function changeSize(event: Event) {
  const input = event.target as HTMLSelectElement
  const nextSize = Number(input.value)
  input.value = String(size.value)
  void load(1, nextSize)
}
onMounted(() => load())
onBeforeUnmount(() => { disposed = true })
</script>

<template>
  <section class="feedback-view">
    <header><div><h1>反馈管理</h1><p>查看小程序用户提交的反馈与举报，点击内容展开全文。</p></div><button :disabled="loading" @click="load()">{{ loading ? '加载中…' : '刷新列表' }}</button></header>
    <p v-if="notice" class="notice" role="alert">{{ notice }}</p>
    <div class="table-card" :aria-busy="loading">
      <table><thead><tr><th>编号</th><th class="submitter">提交用户（OpenID）</th><th>反馈内容</th><th>提交时间</th></tr></thead>
        <tbody><tr v-for="item in records" :key="item.id"><td>{{ item.id }}</td><td class="submitter">{{ item.openId || '—' }}</td><td><details><summary>{{ item.content.slice(0, 100) }}{{ item.content.length > 100 ? '…' : '' }}</summary><p class="full-content">{{ item.content }}</p></details></td><td>{{ new Date(item.createdAt).toLocaleString() }}</td></tr></tbody>
      </table><p v-if="!records.length" class="empty">{{ loading ? '正在加载…' : notice ? '未能加载反馈，请重试' : '暂无反馈' }}</p>
    </div>
    <footer><span>共 {{ total }} 条 · 第 {{ page }} / {{ Math.max(1, Math.ceil(total / size)) }} 页</span><div><label>每页 <select :value="size" :disabled="loading" @change="changeSize"><option v-for="count in [10, 20, 50, 100]" :key="count" :value="count">{{ count }} 条</option></select></label><button :disabled="loading || page <= 1" @click="load(page - 1)">上一页</button><button :disabled="loading || page * size >= total" @click="load(page + 1)">下一页</button></div></footer>
  </section>
</template>

<style scoped>
.submitter{width:240px;user-select:text;word-break:break-all}.feedback-view table{min-width:960px}
.feedback-view{display:grid;gap:20px}.feedback-view header,footer,footer>div{display:flex;align-items:center;justify-content:space-between;gap:12px;flex-wrap:wrap}h1{margin:0;font-size:38px}header p{color:#77707f}.table-card{overflow:auto;border:1.5px solid #27222b;border-radius:18px;background:#fffdf8}table{width:100%;border-collapse:collapse;table-layout:fixed;min-width:650px}th,td{text-align:left;padding:18px;border-bottom:1px solid #e8e0d6;vertical-align:top;overflow-wrap:anywhere}th{background:#f3eaff}th:first-child{width:100px}th:last-child{width:200px}summary{cursor:pointer;line-height:1.7}.full-content{white-space:pre-wrap;overflow-wrap:anywhere;line-height:1.8;background:#f8f4ed;padding:16px;border-radius:10px}.empty{text-align:center;padding:24px;color:#77707f}button,select{font:inherit;border:1.5px solid #27222b;border-radius:10px;padding:10px 16px;background:#fffdf8;color:#27222b}header button{background:#c9a7ff}button,select{cursor:pointer}button:disabled,select:disabled{opacity:.5;cursor:not-allowed}button:focus-visible,select:focus-visible,summary:focus-visible{outline:3px solid #8257bc;outline-offset:3px}.notice{padding:14px;background:#fff0ea;color:#a83c30;border-radius:10px}
</style>
