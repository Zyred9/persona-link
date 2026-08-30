<script setup lang="ts">
import { ref } from 'vue'

type Release = { version: string; status: '当前发布版' | '草稿' | '已下线'; operator: string; time: string }

const releases = ref<Release[]>([
  { version: 'v1.3', status: '草稿', operator: '运营小鹿', time: '2026-08-30 14:20' },
  { version: 'v1.2', status: '当前发布版', operator: '运营小鹿', time: '2026-08-26 10:05' },
  { version: 'v1.1', status: '已下线', operator: '管理员', time: '2026-08-18 16:30' },
])
const showConfirm = ref(false)
const notice = ref('')
const checks = [
  ['题目与抽题数量', '20 道题 / 抽取 12 道', true],
  ['选项与计分维度', '所有题目均已配置', true],
  ['结果规则覆盖', '4 个区间覆盖完整', true],
] as const

function publishDraft() {
  const current = releases.value.find((item) => item.status === '当前发布版')
  const draft = releases.value.find((item) => item.status === '草稿')
  if (!draft) return
  if (current) current.status = '已下线'
  draft.status = '当前发布版'
  draft.time = new Date().toLocaleString('zh-CN', { hour12: false })
  showConfirm.value = false
  notice.value = `${draft.version} 已在本地演示状态中发布`
}
</script>

<template>
  <section class="release-view">
    <header><div><h1>发布排期</h1><p>校验草稿完整性并管理版本状态</p></div><span>演示模式 · 未接后端</span></header>
    <p v-if="notice" class="notice" role="status">{{ notice }}</p>
    <div class="summary-grid">
      <article><small>当前发布版本</small><strong>{{ releases.find((item) => item.status === '当前发布版')?.version }}</strong><p>线上用户使用中的稳定版本</p></article>
      <article><small>编辑中草稿</small><strong>{{ releases.find((item) => item.status === '草稿')?.version ?? '暂无' }}</strong><p>版本号由系统自动生成</p></article>
    </div>
    <article class="validation">
      <div class="section-title"><div><h2>发布前校验</h2><p>发布只替换新答卷版本，不影响进行中的旧答卷</p></div><button :disabled="!releases.some((item) => item.status === '草稿')" @click="showConfirm = true">校验通过，发布草稿</button></div>
      <div v-for="item in checks" :key="item[0]" class="check-row"><span class="check">✓</span><strong>{{ item[0] }}</strong><span>{{ item[1] }}</span><b>通过</b></div>
    </article>
    <article class="history">
      <h2>发布记录</h2>
      <div class="history-head"><span>版本</span><span>状态</span><span>操作人</span><span>操作时间</span></div>
      <div v-for="item in releases" :key="item.version" class="history-row">
        <strong>{{ item.version }}</strong><span class="tag" :class="item.status === '当前发布版' ? 'live' : item.status === '草稿' ? 'draft' : ''">{{ item.status }}</span><span>{{ item.operator }}</span><span>{{ item.time }}</span>
      </div>
    </article>

    <div v-if="showConfirm" class="modal" role="dialog" aria-modal="true" aria-labelledby="publish-title" @click.self="showConfirm = false">
      <div class="dialog"><span class="dialog-icon">🚀</span><h2 id="publish-title">确认发布草稿？</h2><p>该操作仅更新当前页面的演示数据，不会调用后端或影响线上版本。</p><div><button class="cancel" @click="showConfirm = false">取消</button><button @click="publishDraft">确认发布</button></div></div>
    </div>
  </section>
</template>

<style scoped>
.release-view{display:grid;gap:18px}.release-view header,.section-title{display:flex;align-items:center;justify-content:space-between;gap:18px}.release-view h1{margin:0;font-size:38px}.release-view p{margin:6px 0;color:#777}.release-view header>span{padding:6px 10px;border:1px solid #e2b65b;border-radius:99px;background:#fff3ca;color:#79551c;font-size:12px}.notice{padding:10px 14px;border:1px solid #b4d89c;border-radius:10px;background:#f0faeb!important;color:#436c31!important}.summary-grid{display:grid;grid-template-columns:1fr 1fr;gap:14px}.summary-grid article,.validation,.history{border:1.5px solid #aaa;border-radius:15px;background:#fff;padding:18px}.summary-grid small{color:#777}.summary-grid strong{display:block;margin:7px 0 0;font-size:30px}.validation h2,.history h2{margin:0}.section-title button,.dialog button{border:1.5px solid #222;border-radius:10px;background:#ff6652;color:white;padding:10px 17px;font:inherit;cursor:pointer}.section-title button:disabled{opacity:.4}.check-row{display:grid;grid-template-columns:32px 1fr 1fr 70px;align-items:center;gap:10px;padding:14px 0;border-top:1px solid #ddd}.check-row:first-of-type{margin-top:14px}.check{display:grid;place-items:center;width:25px;height:25px;border-radius:50%;background:#e8f9dd;color:#4b8a2e}.check-row>b{color:#4b8a2e}.history-head,.history-row{display:grid;grid-template-columns:1fr 1fr 1fr 1.5fr;gap:12px;padding:13px 8px}.history-head{margin-top:12px;background:#f5effe;font-weight:800}.history-row{border-bottom:1px solid #e2e2e2}.tag{justify-self:start;padding:4px 9px;border-radius:99px;background:#eee;color:#777;font-size:12px}.tag.live{background:#e9f8df;color:#4a872e}.tag.draft{background:#fff0c9;color:#8b681d}.modal{position:fixed;inset:0;z-index:20;display:grid;place-items:center;padding:20px;background:rgba(30,23,38,.38)}.dialog{width:min(420px,100%);border:2px solid #222;border-radius:18px;background:#fffdf8;padding:25px;text-align:center}.dialog-icon{font-size:42px}.dialog p{line-height:1.7}.dialog>div{display:flex;justify-content:center;gap:10px;margin-top:18px}.dialog .cancel{background:white;color:#222}@media(max-width:650px){.release-view header,.section-title{align-items:flex-start;flex-direction:column}.release-view h1{font-size:30px}.summary-grid{grid-template-columns:1fr}.check-row{grid-template-columns:30px 1fr}.check-row span:nth-child(3),.check-row b{grid-column:2}.history{overflow:auto}.history-head,.history-row{min-width:650px}}
</style>
