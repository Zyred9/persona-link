<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { getAnalytics, type AnalyticsOverview } from '../api'
import { niceCeiling } from '../utils/chartScale'

const range = ref('7')
const overview = ref<AnalyticsOverview | null>(null)
const errorMessage = ref('')
let loadSequence = 0
let disposed = false
const metrics = computed(() => ({
  uv: formatCount(overview.value?.metrics.uv ?? 0),
  pv: formatCount(overview.value?.metrics.pv ?? 0),
  started: formatCount(overview.value?.metrics.startedCount ?? 0),
  completed: formatCount(overview.value?.metrics.completedCount ?? 0),
  shared: formatCount(overview.value?.metrics.shareCount ?? 0),
}))
const eventRows = computed(() => {
  const value = overview.value?.metrics
  const rows = [
    { label: '页面访问', value: value?.pv ?? 0 },
    { label: '开始评测', value: value?.startedCount ?? 0 },
    { label: '完成评测', value: value?.completedCount ?? 0 },
    { label: '生成结果卡', value: value?.reportCount ?? 0 },
    { label: '分享', value: value?.shareCount ?? 0 },
  ]
  const maximum = Math.max(1, ...rows.map((item) => item.value))
  return rows.map((item) => ({ ...item, width: item.value / maximum * 100 }))
})
const topTypes = computed(() => (overview.value?.topTests ?? []).map((item) => [
  item.testName, String(item.startedCount), `${Number(item.completionRate ?? 0).toFixed(1)}%`,
]))
const trendDays = computed(() => (overview.value?.trend ?? []).map((item) => item.statDate.slice(5)))
const hasTrend = computed(() => trendDays.value.length > 0)
const trendMaximum = computed(() => niceCeiling(Math.max(0, ...(overview.value?.trend ?? []).flatMap((item) => [
  item.pv,
  item.startedCount,
  item.completedCount,
]))))
const trendTicks = computed(() => Array.from({ length: 5 }, (_, index) => formatCount(trendMaximum.value * (4 - index) / 4)))
const official = computed(() => overview.value?.officialTrend.at(-1))

function trendPoints(field: 'pv' | 'startedCount' | 'completedCount'): string {
  const rows = overview.value?.trend ?? []
  return rows.map((item, index) => {
    const x = rows.length <= 1 ? 0 : Math.round(index * 700 / (rows.length - 1))
    return `${x},${Math.round(180 - item[field] / trendMaximum.value * 150)}`
  }).join(' ')
}

function formatCount(value: number): string {
  return new Intl.NumberFormat('zh-CN', { notation: 'compact', maximumFractionDigits: 1 }).format(value)
}

async function load() {
  const sequence = ++loadSequence
  try {
    errorMessage.value = ''
    const result = await getAnalytics(Number(range.value))
    if (disposed || sequence !== loadSequence) return
    overview.value = result
  } catch (error) {
    if (disposed || sequence !== loadSequence) return
    errorMessage.value = error instanceof Error ? error.message : '数据加载失败'
  }
}

watch(range, load)
onMounted(load)
onBeforeUnmount(() => { disposed = true; ++loadSequence })
</script>

<template>
  <section class="analytics-view">
    <header><div><h1>数据看板</h1><p>查看小程序访问与评测核心转化</p></div><div class="toolbar"><span v-if="errorMessage">{{ errorMessage }}</span><select v-model="range" aria-label="统计周期"><option value="1">今日</option><option value="7">近 7 天</option><option value="30">近 30 天</option></select></div></header>
    <div class="metrics">
      <article><span>访客数 UV</span><strong>{{ metrics.uv }}</strong><small>👤 独立访客</small></article>
      <article><span>访问量 PV</span><strong>{{ metrics.pv }}</strong><small>👀 页面浏览</small></article>
      <article><span>开始评测</span><strong>{{ metrics.started }}</strong><small>✎ 已创建答卷</small></article>
      <article><span>完成评测</span><strong>{{ metrics.completed }}</strong><small>✓ 已生成报告</small></article>
      <article><span>分享次数</span><strong>{{ metrics.shared }}</strong><small>⌯ 分享事件</small></article>
    </div>
    <article class="panel official-panel">
      <div><h2>微信官方数据</h2><p>{{ official ? `${official.statDate} · 同步于 ${new Date(official.syncedAt).toLocaleString()}` : '尚未同步，请配置 XXL-JOB 调用同步接口' }}</p></div>
      <dl v-if="official"><div><dt>打开次数</dt><dd>{{ official.sessionCount }}</dd></div><div><dt>官方 PV</dt><dd>{{ official.visitPv }}</dd></div><div><dt>官方 UV</dt><dd>{{ official.visitUv }}</dd></div><div><dt>新访 UV</dt><dd>{{ official.visitUvNew }}</dd></div><div><dt>人均停留</dt><dd>{{ official.stayTimeUv }}s</dd></div><div><dt>访问深度</dt><dd>{{ official.visitDepth }}</dd></div></dl>
    </article>
    <div class="analysis-grid">
      <article class="panel">
        <h2>关键事件次数</h2><p>按当前周期事件次数展示，不代表独立用户转化</p>
        <div class="event-list">
          <div v-for="item in eventRows" :key="item.label" class="event-row">
            <div><strong>{{ item.label }}</strong><span>{{ item.value.toLocaleString() }} 次</span></div>
            <div class="track"><i :style="{ width: `${item.width}%` }"></i></div>
          </div>
        </div>
      </article>
      <article class="panel top-list">
        <h2>热门题型</h2><p>按开始评测次数排序</p>
        <div v-for="(item, index) in topTypes" :key="item[0]" class="top-row"><b>{{ index + 1 }}</b><div><strong>{{ item[0] }}</strong><span>{{ item[1] }} 次开始</span></div><em>{{ item[2] }} 完成</em></div>
      </article>
    </div>
    <article class="panel trend">
      <h2>访问与评测趋势</h2><p>三条折线共用同一纵轴，按日统计事件次数</p>
      <div v-if="hasTrend" class="trend-chart" aria-label="访问、开始和完成次数趋势图">
        <span v-for="tick in trendTicks" :key="tick" class="trend-axis">{{ tick }}</span>
        <div class="trend-canvas">
          <i v-for="n in 5" :key="n"></i>
          <svg viewBox="0 0 700 190" preserveAspectRatio="none" role="img">
            <polyline :points="trendPoints('pv')" fill="none" stroke="#ff705f" stroke-width="5" stroke-linecap="round" stroke-linejoin="round" />
            <polyline :points="trendPoints('startedCount')" fill="none" stroke="#9b6bd3" stroke-width="4" stroke-linecap="round" stroke-dasharray="8 8" />
            <polyline :points="trendPoints('completedCount')" fill="none" stroke="#59af8d" stroke-width="4" stroke-linecap="round" stroke-linejoin="round" />
          </svg>
          <div class="trend-days"><span v-for="day in trendDays" :key="day">{{ day }}</span></div>
        </div>
      </div>
      <p v-else class="trend-empty">暂无趋势数据</p>
      <div class="trend-legend"><span><i class="visit-dot"></i>访问次数</span><span><i class="start-dot"></i>开始次数</span><span><i class="complete-dot"></i>完成次数</span></div>
      <small class="trend-disclaimer">分享仅有周期汇总次数，接口暂未提供逐日趋势。</small>
    </article>
  </section>
</template>

<style scoped>
.analytics-view{display:grid;gap:18px}.analytics-view header,.toolbar{display:flex;align-items:center;justify-content:space-between;gap:12px}.analytics-view h1{margin:0;font-size:38px}.analytics-view p{margin:6px 0;color:#777}.toolbar span{padding:6px 10px;border:1px solid #e2b65b;border-radius:99px;background:#fff3ca;color:#79551c;font-size:12px}.toolbar select{border:1.5px solid #222;border-radius:9px;background:white;padding:8px 11px}.metrics{display:grid;grid-template-columns:repeat(auto-fit,minmax(170px,1fr));gap:13px}.metrics article,.panel{border:1.5px solid #aaa;border-radius:15px;background:#fff;padding:18px}.metrics article:nth-child(1){background:#f1e7ff}.metrics article:nth-child(2){background:#fff1c9}.metrics article:nth-child(3){background:#eaf6df}.metrics article:nth-child(4){background:#e7f3f7}.metrics article:nth-child(5){background:#fff0eb}.metrics span,.metrics small{display:block;color:#666}.metrics strong{display:block;margin:8px 0;font-size:30px}.panel h2{margin:0}.official-panel{display:flex;align-items:center;justify-content:space-between;gap:20px}.official-panel dl{display:flex;flex-wrap:wrap;margin:0}.official-panel dl div{min-width:100px;padding:5px 16px;border-left:1px solid #ddd;text-align:center}.official-panel dt{color:#777;font-size:12px}.official-panel dd{margin:5px 0 0;font-weight:900}.analysis-grid{display:grid;grid-template-columns:1.25fr .75fr;gap:14px}.event-list{display:grid;gap:14px;margin-top:18px}.event-row{display:grid;grid-template-columns:140px 1fr;align-items:center;gap:12px}.event-row>div:first-child{display:grid}.event-row span{font-size:12px;color:#777}.track{height:19px;border-radius:99px;background:#eee;overflow:hidden}.track i{display:block;height:100%;border-radius:inherit;background:linear-gradient(90deg,#9d74db,#ff8373)}.top-list{display:grid;align-content:start}.top-row{display:grid;grid-template-columns:30px 1fr auto;align-items:center;gap:9px;padding:14px 0;border-top:1px solid #ddd}.top-row>b{display:grid;place-items:center;width:27px;height:27px;border-radius:50%;background:#ebdcff}.top-row div{display:grid}.top-row span{font-size:12px;color:#777}.top-row em{font-size:12px;color:#4d8034}.trend-chart{display:grid;grid-template:repeat(5,1fr) / 42px 1fr;height:230px;margin-top:18px}.trend-axis{align-self:start;color:#9b9199;font-size:11px}.trend-canvas{position:relative;grid-area:1 / 2 / 6 / 3;display:grid;align-content:space-between;padding-bottom:24px}.trend-canvas>i{display:block;border-top:1px dashed #d8cfc5}.trend-canvas svg{position:absolute;inset:4px 0 24px;width:100%;height:calc(100% - 28px);overflow:visible}.trend-days{position:absolute;right:0;bottom:0;left:0;display:flex;justify-content:space-between;color:#817981;font-size:11px}.trend-legend{display:flex;justify-content:center;gap:20px;color:#746b73;font-size:12px}.trend-legend span{display:flex;align-items:center;gap:6px}.trend-legend i{width:9px;height:9px;border-radius:50%}.visit-dot{background:#ff705f}.start-dot{background:#9b6bd3}.complete-dot{background:#59af8d}.trend-disclaimer{display:block;margin-top:9px;color:#8c848d;text-align:center}.trend-empty{display:grid;place-items:center;height:230px}.top-list{min-width:0}@media(max-width:900px){.official-panel{align-items:flex-start;flex-direction:column}.analysis-grid{grid-template-columns:1fr}}@media(max-width:650px){.analytics-view header{align-items:flex-start;flex-direction:column}.analytics-view h1{font-size:30px}.toolbar{flex-wrap:wrap}.metrics{grid-template-columns:1fr}.event-row{grid-template-columns:110px 1fr}.trend-chart{grid-template-columns:34px 1fr}.trend-days{font-size:10px}}
</style>
