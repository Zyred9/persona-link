<script setup lang="ts">
import { ref } from 'vue'

type Category = { id: number; name: string; count: number; enabled: boolean }

const categories = ref<Category[]>([
  { id: 1, name: '职场协作', count: 4, enabled: true },
  { id: 2, name: '相处方式', count: 6, enabled: true },
  { id: 3, name: '日常偏好', count: 3, enabled: true },
  { id: 4, name: '趣味脑洞', count: 0, enabled: false },
])
const newName = ref('')
const notice = ref('')

function addCategory() {
  const name = newName.value.trim()
  if (!name) return
  categories.value.push({ id: Date.now(), name, count: 0, enabled: true })
  newName.value = ''
  notice.value = `已在本地新增“${name}”`
}

function move(index: number, offset: number) {
  const target = index + offset
  if (target < 0 || target >= categories.value.length) return
  ;[categories.value[index], categories.value[target]] = [categories.value[target], categories.value[index]]
  notice.value = '排序已在本地更新'
}
</script>

<template>
  <section class="category-view">
    <header>
      <div><h1>分类标签</h1><p>维护小程序首页筛选分类与展示顺序</p></div>
      <span>演示模式 · 未接后端</span>
    </header>
    <p v-if="notice" class="notice" role="status">{{ notice }}</p>
    <form class="create" @submit.prevent="addCategory">
      <label>新增分类 <input v-model="newName" maxlength="12" placeholder="输入分类名称" /></label>
      <button type="submit">＋ 新增</button>
    </form>
    <div class="table-card">
      <div class="table-head"><span>排序</span><span>分类名称</span><span>关联题型</span><span>状态</span><span>操作</span></div>
      <div v-for="(item, index) in categories" :key="item.id" class="table-row">
        <div class="order"><b>{{ index + 1 }}</b><button :disabled="index === 0" @click="move(index, -1)">↑</button><button :disabled="index === categories.length - 1" @click="move(index, 1)">↓</button></div>
        <input v-model="item.name" aria-label="分类名称" maxlength="12" />
        <span>{{ item.count }} 个</span>
        <label class="switch"><input v-model="item.enabled" type="checkbox" /><i></i>{{ item.enabled ? '启用' : '停用' }}</label>
        <button class="save" @click="notice = `“${item.name}”已在本地保存`">保存</button>
      </div>
    </div>
  </section>
</template>

<style scoped>
.category-view{display:grid;gap:18px}.category-view header{display:flex;align-items:center;justify-content:space-between}.category-view h1{margin:0;font-size:38px}.category-view p{margin:6px 0;color:#777}.category-view header span{padding:6px 10px;border:1px solid #e2b65b;border-radius:99px;background:#fff3ca;color:#79551c;font-size:12px}.notice{padding:10px 14px;border:1px solid #b4d89c;border-radius:10px;background:#f0faeb!important;color:#436c31!important}.create,.create label{display:flex;align-items:center;gap:12px}.create{padding:16px;border:1.5px solid #aaa;border-radius:14px;background:#fff}.create label{flex:1;font-weight:700}.create input,.table-row>input{border:1px solid #bbb;border-radius:9px;padding:9px 11px;font:inherit}.create input{flex:1}.create button,.save{border:1.5px solid #222;border-radius:9px;background:#ff6652;color:white;padding:9px 17px;cursor:pointer}.table-card{overflow:auto;border:1.5px solid #aaa;border-radius:14px;background:#fff}.table-head,.table-row{display:grid;grid-template-columns:180px minmax(180px,1fr) 120px 150px 100px;align-items:center;gap:14px;min-width:800px;padding:14px 18px}.table-head{background:#f5effe;font-weight:800}.table-row{border-top:1px solid #ddd}.order{display:flex;align-items:center;gap:7px}.order b{display:grid;place-items:center;width:28px;height:28px;border-radius:50%;background:#eadcff}.order button{border:1px solid #aaa;border-radius:7px;background:white;padding:4px 8px}.order button:disabled{opacity:.35}.switch{display:flex;align-items:center;gap:8px}.switch input{position:absolute;opacity:0}.switch i{width:38px;height:21px;border-radius:99px;background:#ccc;position:relative}.switch i:after{content:"";position:absolute;top:3px;left:3px;width:15px;height:15px;border-radius:50%;background:#fff;transition:.2s}.switch input:checked+i{background:#8e65d0}.switch input:checked+i:after{left:20px}@media(max-width:650px){.category-view header{align-items:flex-start;flex-direction:column;gap:10px}.category-view h1{font-size:30px}.create,.create label{align-items:stretch;flex-direction:column}}
</style>
