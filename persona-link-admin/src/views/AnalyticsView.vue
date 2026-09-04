<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { getAnalytics, type AnalyticsOverview } from '../api'

const range = ref('7')
const overview = ref<AnalyticsOverview | null>(null)
const errorMessage = ref('')
const metrics = computed(() => ({
  uv: String(overview.value?.metrics.uv ?? 0),
  pv: String(overview.value?.metrics.pv ?? 0),
  started: String(overview.value?.metrics.startedCount ?? 0),
  completed: String(overview.value?.metrics.completedCount ?? 0),
}))
const funnel = computed(() => [
  { label: '访问首页', value: Number(metrics.value.uv), rate: '100%' },
  { label: '开始评测', value: Number(metrics.value.started), rate: percentage(Number(metrics.value.started), Number(metrics.value.uv)) },
  { label: '完成评测', value: Number(metrics.value.completed), rate: percentage(Number(metrics.value.completed), Number(metrics.value.started)) },
  { label: '生成结果卡', value: overview.value?.metrics.reportCount ?? 0, rate: percentage(overview.value?.metrics.reportCount ?? 0, Number(metrics.value.completed)) },
])
const topTypes = computed(() => (overview.value?.topTests ?? []).map((item) => [
  item.testName, String(item.startedCount), `${Number(item.completionRate ?? 0).toFixed(1)}%`,
]))
const trend = computed(() => {
  const points = overview.value?.trend ?? []
  const maximum = Math.max(1, ...points.map((item) => item.pv))
  return points.map((item) => ({
    label: item.statDate.slice(5),
    height: Math.max(3, Math.round(item.pv / maximum * 100)),
  }))
})
const official = computed(() => overview.value?.officialTrend.at(-1))

function percentage(value: number, total: number): string { return `${(total ? value / total * 100 : 0).toFixed(1)}%` }

async function load() {
  try {
    errorMessage.value = ''
    overview.value = await getAnalytics(Number(range.value))
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '数据加载失败'
  }
}

watch(range, load)
onMounted(load)
</script>

<template>
  <section class="analytics-view">
    <header><div><h1>数据看板</h1><p>查看小程序访问与评测核心转化</p></div><div class="toolbar"><span v-if="errorMessage">{{ errorMessage }}</span><select v-model="range" aria-label="统计周期"><option value="1">今日</option><option value="7">近 7 天</option><option value="30">近 30 天</option></select></div></header>
    <div class="metrics">
      <article><span>访客数 UV</span><strong>{{ metrics.uv }}</strong><small>👤 独立访客</small></article>
      <article><span>访问量 PV</span><strong>{{ metrics.pv }}</strong><small>👀 页面浏览</small></article>
      <article><span>开始评测</span><strong>{{ metrics.started }}</strong><small>✎ 已创建答卷</small></article>
      <article><span>完成评测</span><strong>{{ metrics.completed }}</strong><small>✓ 已生成报告</small></article>
    </div>
    <article class="panel official-panel">
      <div><h2>微信官方数据</h2><p>{{ official ? `${official.statDate} · 同步于 ${new Date(official.syncedAt).toLocaleString()}` : '尚未同步，请配置 XXL-JOB 调用同步接口' }}</p></div>
      <dl v-if="official"><div><dt>打开次数</dt><dd>{{ official.sessionCount }}</dd></div><div><dt>官方 PV</dt><dd>{{ official.visitPv }}</dd></div><div><dt>官方 UV</dt><dd>{{ official.visitUv }}</dd></div><div><dt>新访 UV</dt><dd>{{ official.visitUvNew }}</dd></div><div><dt>人均停留</dt><dd>{{ official.stayTimeUv }}s</dd></div><div><dt>访问深度</dt><dd>{{ official.visitDepth }}</dd></div></dl>
    </article>
    <div class="analysis-grid">
      <article class="panel">
        <h2>核心转化漏斗</h2><p>每一步比例按上一环节计算</p>
        <div class="funnel">
          <div v-for="(item, index) in funnel" :key="item.label" class="funnel-row">
            <div><strong>{{ item.label }}</strong><span>{{ item.value.toLocaleString() }} 人</span></div>
            <div class="track"><i :style="{ width: `${100 - index * 14}%` }"></i></div>
            <b>{{ item.rate }}</b>
          </div>
        </div>
      </article>
      <article class="panel top-list">
        <h2>热门题型</h2><p>按开始评测人数排序</p>
        <div v-for="(item, index) in topTypes" :key="item[0]" class="top-row"><b>{{ index + 1 }}</b><div><strong>{{ item[0] }}</strong><span>{{ item[1] }} 次开始</span></div><em>{{ item[2] }} 完成</em></div>
      </article>
    </div>
    <article class="panel trend"><h2>访问趋势</h2><p>按日统计页面浏览量</p><div class="bars"><i v-for="item in trend" :key="item.label" :style="{ height: `${item.height}%` }"></i></div><div class="days"><span v-for="item in trend" :key="item.label">{{ item.label }}</span></div></article>
  </section>
</template>

<style scoped>
.analytics-view{display:grid;gap:18px}.analytics-view header,.toolbar{display:flex;align-items:center;justify-content:space-between;gap:12px}.analytics-view h1{margin:0;font-size:38px}.analytics-view p{margin:6px 0;color:#777}.toolbar span{padding:6px 10px;border:1px solid #e2b65b;border-radius:99px;background:#fff3ca;color:#79551c;font-size:12px}.toolbar select{border:1.5px solid #222;border-radius:9px;background:white;padding:8px 11px}.metrics{display:grid;grid-template-columns:repeat(4,1fr);gap:13px}.metrics article,.panel{border:1.5px solid #aaa;border-radius:15px;background:#fff;padding:18px}.metrics article:nth-child(1){background:#f1e7ff}.metrics article:nth-child(2){background:#fff1c9}.metrics article:nth-child(3){background:#eaf6df}.metrics article:nth-child(4){background:#e7f3f7}.metrics span,.metrics small{display:block;color:#666}.metrics strong{display:block;margin:8px 0;font-size:30px}.panel h2{margin:0}.official-panel{display:flex;align-items:center;justify-content:space-between;gap:20px}.official-panel dl{display:flex;flex-wrap:wrap;margin:0}.official-panel dl div{min-width:100px;padding:5px 16px;border-left:1px solid #ddd;text-align:center}.official-panel dt{color:#777;font-size:12px}.official-panel dd{margin:5px 0 0;font-weight:900}.analysis-grid{display:grid;grid-template-columns:1.25fr .75fr;gap:14px}.funnel{display:grid;gap:14px;margin-top:18px}.funnel-row{display:grid;grid-template-columns:140px 1fr 60px;align-items:center;gap:12px}.funnel-row>div:first-child{display:grid}.funnel-row span{font-size:12px;color:#777}.track{height:19px;border-radius:99px;background:#eee;overflow:hidden}.track i{display:block;height:100%;border-radius:inherit;background:linear-gradient(90deg,#9d74db,#ff8373)}.funnel-row>b{color:#7552ae}.top-list{display:grid;align-content:start}.top-row{display:grid;grid-template-columns:30px 1fr auto;align-items:center;gap:9px;padding:14px 0;border-top:1px solid #ddd}.top-row>b{display:grid;place-items:center;width:27px;height:27px;border-radius:50%;background:#ebdcff}.top-row div{display:grid}.top-row span{font-size:12px;color:#777}.top-row em{font-size:12px;color:#4d8034}.trend{height:260px}.bars{display:flex;align-items:end;justify-content:space-around;height:150px;border-bottom:1px solid #aaa}.bars i{width:7%;border-radius:8px 8px 0 0;background:linear-gradient(#c5a5f3,#8961c8)}.days{display:flex;justify-content:space-around;padding-top:8px;color:#777;font-size:12px}@media(max-width:900px){.metrics{grid-template-columns:repeat(2,1fr)}.official-panel{align-items:flex-start;flex-direction:column}.analysis-grid{grid-template-columns:1fr}}@media(max-width:650px){.analytics-view header{align-items:flex-start;flex-direction:column}.analytics-view h1{font-size:30px}.toolbar{flex-wrap:wrap}.metrics{grid-template-columns:1fr}.funnel-row{grid-template-columns:110px 1fr}.funnel-row>b{grid-column:2}.trend{height:230px}}
</style>
