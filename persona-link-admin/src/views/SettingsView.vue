<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { createAdminAccount, getAdminAccounts, resetAdminPassword, updateAdminAccount, type AdminAccount } from '../api'

const accounts = ref<AdminAccount[]>([])
const total = ref(0)
const page = ref(1)
const size = 20
const keyword = ref('')
const roleType = ref('')
const status = ref('')
const notice = ref('')
const editing = ref<AdminAccount | null>(null)
const showCreate = ref(false)
const createForm = ref({ username: '', password: '', displayName: '', roleType: 2, status: 1 })

const roleLabels: Record<number, string> = { 1: '管理员', 2: '内容运营', 3: '只读查看' }

async function load() {
  try {
    const result = await getAdminAccounts({ page: page.value, size, keyword: keyword.value, roleType: roleType.value, status: status.value })
    accounts.value = result.records
    total.value = result.total
  } catch (error) { notice.value = error instanceof Error ? error.message : '账号加载失败' }
}

async function search() { page.value = 1; await load() }
async function changePage(offset: number) { page.value += offset; await load() }

async function createAccount() {
  try {
    await createAdminAccount(createForm.value)
    showCreate.value = false
    createForm.value = { username: '', password: '', displayName: '', roleType: 2, status: 1 }
    notice.value = '账号已新增'
    await load()
  } catch (error) { notice.value = error instanceof Error ? error.message : '新增失败' }
}

async function saveAccount() {
  if (!editing.value) return
  try {
    await updateAdminAccount(editing.value.id, editing.value)
    editing.value = null
    notice.value = '账号已保存'
    await load()
  } catch (error) { notice.value = error instanceof Error ? error.message : '保存失败' }
}

async function resetPassword(item: AdminAccount) {
  const password = window.prompt(`请输入 ${item.username} 的新密码（至少 8 位）`)
  if (!password) return
  try {
    await resetAdminPassword(item.id, password)
    notice.value = '密码已重置，该账号的已登录会话已全部注销'
  } catch (error) { notice.value = error instanceof Error ? error.message : '密码重置失败' }
}

onMounted(load)
</script>

<template>
  <section class="settings-view">
    <header><div><h1>系统设置</h1><p>管理后台登录账号、角色与启用状态</p></div><button @click="showCreate = true">＋ 新增账号</button></header>
    <p v-if="notice" class="notice" role="status">{{ notice }}</p>
    <form class="filters" @submit.prevent="search">
      <input v-model="keyword" placeholder="搜索账号或名称" />
      <select v-model="roleType"><option value="">全部角色</option><option v-for="(label, key) in roleLabels" :key="key" :value="key">{{ label }}</option></select>
      <select v-model="status"><option value="">全部状态</option><option value="1">启用</option><option value="0">禁用</option></select>
      <button type="submit">查询</button>
    </form>
    <div class="table-card">
      <div class="table-head"><span>登录账号</span><span>显示名称</span><span>角色</span><span>状态</span><span>最后登录</span><span>操作</span></div>
      <div v-for="item in accounts" :key="item.id" class="table-row">
        <b>{{ item.username }}</b><span>{{ item.displayName }}</span><span>{{ roleLabels[item.roleType] }}</span><span :class="item.status ? 'enabled' : 'disabled'">{{ item.status ? '启用' : '禁用' }}</span><span>{{ item.lastLoginAt ? new Date(item.lastLoginAt).toLocaleString() : '从未登录' }}</span>
        <div><button @click="editing = { ...item }">编辑</button><button @click="resetPassword(item)">重置密码</button></div>
      </div>
    </div>
    <footer><span>第 {{ page }} / {{ Math.max(1, Math.ceil(total / size)) }} 页</span><div><button :disabled="page <= 1" @click="changePage(-1)">上一页</button><button :disabled="page * size >= total" @click="changePage(1)">下一页</button></div></footer>

    <div v-if="showCreate || editing" class="modal admin-modal-backdrop" @click.self="showCreate = false; editing = null">
      <form class="admin-editor-dialog account-editor-dialog" role="dialog" aria-modal="true" aria-label="账号编辑弹窗" @submit.prevent="editing ? saveAccount() : createAccount()">
        <header class="admin-dialog-header"><div><h2>{{ editing ? '编辑账号' : '新增账号' }}</h2><p>设置登录信息、显示名称和后台访问权限。</p></div><button type="button" aria-label="关闭" @click="showCreate = false; editing = null">×</button></header>
        <div class="drawer-body">
          <section class="admin-form-section">
            <h3>登录信息</h3><p>{{ editing ? '登录账号不可修改；如需更换密码，请在账号列表重置。' : '请填写登录账号，并设置至少 8 位的初始密码。' }}</p>
            <div class="admin-form-grid">
              <template v-if="editing">
                <div class="admin-field"><span>登录账号</span><strong class="account-username">{{ editing.username }}</strong></div>
                <label class="admin-field"><span>显示名称</span><input v-model="editing.displayName" required maxlength="64" placeholder="请输入显示名称" /></label>
              </template>
              <template v-else>
                <label class="admin-field"><span>登录账号</span><input v-model="createForm.username" required maxlength="64" placeholder="请输入登录账号" /></label>
                <label class="admin-field"><span>初始密码</span><input v-model="createForm.password" required minlength="8" maxlength="72" type="password" autocomplete="new-password" placeholder="至少 8 位" /></label>
                <label class="admin-field admin-field-wide"><span>显示名称</span><input v-model="createForm.displayName" required maxlength="64" placeholder="请输入显示名称" /></label>
              </template>
            </div>
          </section>
          <section class="admin-form-section">
            <h3>访问权限</h3><p>按实际职责选择角色，并设置账号是否启用。</p>
            <div class="admin-form-grid">
              <template v-if="editing">
                <label class="admin-field"><span>角色</span><select v-model="editing.roleType"><option :value="1">管理员</option><option :value="2">内容运营</option><option :value="3">只读查看</option></select></label>
                <label class="admin-field"><span>状态</span><select v-model="editing.status"><option :value="1">启用</option><option :value="0">禁用</option></select></label>
              </template>
              <template v-else>
                <label class="admin-field"><span>角色</span><select v-model="createForm.roleType"><option :value="1">管理员</option><option :value="2">内容运营</option><option :value="3">只读查看</option></select></label>
                <label class="admin-field"><span>状态</span><select v-model="createForm.status"><option :value="1">启用</option><option :value="0">禁用</option></select></label>
              </template>
            </div>
          </section>
        </div>
        <footer class="admin-dialog-footer"><span class="admin-footer-note">请确认角色与账号状态。</span><div class="admin-footer-actions"><button class="secondary-action" type="button" @click="showCreate = false; editing = null">取消</button><button class="primary" type="submit">保存账号</button></div></footer>
      </form>
    </div>
  </section>
</template>

<style scoped>
.settings-view{display:grid;gap:18px}.settings-view>header{display:flex;align-items:center;justify-content:space-between}.settings-view h1{margin:0;font-size:38px}.settings-view>header p{margin:6px 0;color:#777}.settings-view button{border:1.5px solid #222;border-radius:9px;background:#fff;padding:8px 14px;cursor:pointer}.settings-view>header button,.primary{background:#ff6652!important;color:#fff}.filters{display:flex;gap:10px;padding:16px;border:1.5px solid #aaa;border-radius:14px;background:#fff}.filters input,.filters select{border:1px solid #bbb;border-radius:9px;padding:9px;font:inherit}.filters input{min-width:240px}.table-card{overflow:auto;border:1.5px solid #aaa;border-radius:14px;background:#fff}.table-head,.table-row{display:grid;grid-template-columns:150px 150px 110px 90px minmax(170px,1fr) 190px;align-items:center;gap:12px;min-width:900px;padding:14px 18px}.table-head{background:#f5effe;font-weight:800}.table-row{border-top:1px solid #ddd}.table-row>div{display:flex;gap:8px}.enabled{color:#348533}.disabled{color:#aa4435}.settings-view>footer{display:flex;justify-content:space-between}.settings-view>footer div{display:flex;gap:8px}button:disabled{opacity:.4}.account-editor-dialog{--editor-width:640px}.account-username{display:flex;align-items:center;min-height:44px;padding:10px 12px;border:1px solid #e3ddd5;border-radius:9px;background:#f7f3ed;overflow-wrap:anywhere}@media(max-width:720px){.settings-view>header,.filters{align-items:stretch;flex-direction:column}.filters input{min-width:0}.settings-view h1{font-size:30px}}
</style>
