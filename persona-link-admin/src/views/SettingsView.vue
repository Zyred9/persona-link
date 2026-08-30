<script setup lang="ts">
import { ref } from 'vue'

const demoMode = ref(true)
const maintenance = ref(false)
const siteName = ref('心动测测 · 运营后台')
const supportEmail = ref('ops@persona-link.local')
const notice = ref('')

function save() {
  notice.value = `设置已在当前页面保存 · ${new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })}`
}
</script>

<template>
  <section class="settings-view">
    <header><div><h1>系统设置</h1><p>管理后台显示信息与演示开关</p></div><span>演示模式 · 未接后端</span></header>
    <p v-if="notice" class="notice" role="status">{{ notice }}</p>
    <div class="settings-grid">
      <article class="panel">
        <h2>基础信息</h2>
        <label>后台名称<input v-model="siteName" maxlength="30" /></label>
        <label>运营联系邮箱<input v-model="supportEmail" type="email" /></label>
      </article>
      <article class="panel">
        <h2>运行开关</h2>
        <label class="setting-row"><div><strong>演示数据</strong><p>使用本地数据展示页面交互，不发起业务接口请求。</p></div><input v-model="demoMode" type="checkbox" /></label>
        <label class="setting-row"><div><strong>维护提示</strong><p>仅切换当前页面状态；接入服务端后才会影响小程序。</p></div><input v-model="maintenance" type="checkbox" /></label>
      </article>
      <article class="panel full">
        <h2>接入说明</h2>
        <div class="callout"><b>当前边界</b><p>后台业务接口尚未提供，页面内的保存、发布、排序和启停操作仅保存在 Vue 运行时，刷新页面后恢复演示数据。</p></div>
        <ul><li>接入登录接口后，替换演示会话并启用真实权限校验。</li><li>接入配置与发布接口后，再开放线上保存和发布按钮。</li><li>接入统计接口后，用真实 UV、PV 与漏斗数据替换演示值。</li></ul>
      </article>
    </div>
    <button class="save" type="button" @click="save">保存本地设置</button>
  </section>
</template>

<style scoped>
.settings-view{display:grid;gap:18px}.settings-view header{display:flex;align-items:center;justify-content:space-between}.settings-view h1{margin:0;font-size:38px}.settings-view header p{margin:6px 0;color:#777}.settings-view header>span{padding:6px 10px;border:1px solid #e2b65b;border-radius:99px;background:#fff3ca;color:#79551c;font-size:12px}.notice{padding:10px 14px;border:1px solid #b4d89c;border-radius:10px;background:#f0faeb;color:#436c31}.settings-grid{display:grid;grid-template-columns:1fr 1fr;gap:14px}.panel{display:grid;align-content:start;gap:14px;border:1.5px solid #aaa;border-radius:15px;background:#fff;padding:20px}.panel.full{grid-column:1/-1}.panel h2{margin:0}.panel>label:not(.setting-row){display:grid;gap:7px;font-weight:700}.panel input:not([type=checkbox]){border:1px solid #aaa;border-radius:9px;padding:10px 12px;font:inherit}.setting-row{display:flex;align-items:center;justify-content:space-between;gap:20px;padding:14px 0;border-top:1px solid #ddd}.setting-row p{margin:5px 0 0;color:#777;line-height:1.5}.setting-row input{width:21px;height:21px;accent-color:#8e65d0}.callout{border-left:5px solid #9368d2;border-radius:8px;background:#f4ecff;padding:13px 15px}.callout p{margin:6px 0 0;color:#614c7d;line-height:1.6}.panel ul{margin:0;padding-left:22px;line-height:1.9;color:#555}.save{justify-self:end;border:1.5px solid #222;border-radius:10px;background:#ff6652;color:white;padding:10px 20px;font:inherit;cursor:pointer}@media(max-width:650px){.settings-view header{align-items:flex-start;flex-direction:column;gap:10px}.settings-view h1{font-size:30px}.settings-grid{grid-template-columns:1fr}.panel.full{grid-column:auto}.setting-row{align-items:flex-start}.save{justify-self:stretch}}
</style>
