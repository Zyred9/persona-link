<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { getDashboard, getHealth, type AnalyticsOverview } from '../api'
import { niceCeiling } from '../utils/chartScale'

const health = ref<'checking' | 'online' | 'offline'>('checking')
const overview = ref<AnalyticsOverview | null>(null)
const errorMessage = ref('')

const metrics = computed(() => {
  const value = overview.value?.metrics
  const completedRate = value?.startedCount ? value.completedCount / value.startedCount * 100 : 0
  const shareRate = value?.completedCount ? value.shareCount / value.completedCount * 100 : 0
  return [
    { icon: '◉', label: '页面浏览次数', value: formatCount(value?.pv ?? 0), note: `独立访客 UV ${formatCount(value?.uv ?? 0)}`, tone: 'purple' },
    { icon: '▷', label: '开始测试次数', value: formatCount(value?.startedCount ?? 0), note: '已创建答卷', tone: 'coral' },
    { icon: '✓', label: '完成率', value: `${completedRate.toFixed(1)}%`, note: `完成 ${value?.completedCount ?? 0} 次`, tone: 'yellow' },
    { icon: '⌯', label: '分享次数', value: formatCount(value?.shareCount ?? 0), note: `分享/完成 ${shareRate.toFixed(1)}%`, tone: 'mint' },
  ]
})

const usageRows = computed(() => (overview.value?.topTests ?? []).map((item) => ({
  name: item.testName,
  uses: String(item.startedCount),
  completed: String(item.completedCount),
  rate: `${Number(item.completionRate ?? 0).toFixed(1)}%`,
})))

const chartDays = computed(() => (overview.value?.trend ?? []).map((item) => item.statDate.slice(5)))
const hasTrend = computed(() => chartDays.value.length > 0)
const chartMaximum = computed(() => niceCeiling(Math.max(0, ...(overview.value?.trend ?? []).flatMap((item) => [
  item.pv,
  item.startedCount,
  item.completedCount,
]))))
const chartTicks = computed(() => Array.from({ length: 5 }, (_, index) => formatCount(chartMaximum.value * (4 - index) / 4)))

function chartPoints(field: 'pv' | 'startedCount' | 'completedCount'): string {
  const rows = overview.value?.trend ?? []
  return rows.map((item, index) => {
    const x = rows.length <= 1 ? 0 : Math.round(index * 700 / (rows.length - 1))
    return `${x},${Math.round(180 - item[field] / chartMaximum.value * 150)}`
  }).join(' ')
}

function formatCount(value: number): string {
  return new Intl.NumberFormat('zh-CN', { notation: 'compact', maximumFractionDigits: 1 }).format(value)
}

onMounted(async () => {
  try {
    const [healthData, dashboard] = await Promise.all([getHealth(), getDashboard(7)])
    health.value = healthData === 'UP' ? 'online' : 'offline'
    overview.value = dashboard
  } catch (error) {
    health.value = 'offline'
    errorMessage.value = error instanceof Error ? error.message : '运营数据加载失败'
  }
})
</script>

<template>
  <div class="page-stack">
    <div class="welcome-row">
      <div>
        <p class="eyebrow">LAST 7 DAYS</p>
        <h1>近 7 日运营总览 <span>♡</span></h1>
        <p>集中查看近 7 日访问、开始、完成与分享表现。</p>
      </div>
      <div class="welcome-actions">
        <div class="health-chip" :class="health">
          <i></i>{{ health === 'checking' ? '服务检查中' : health === 'online' ? '服务在线' : '服务未连接' }}
        </div>
      </div>
    </div>

    <p v-if="errorMessage" class="data-note">{{ errorMessage }}</p>

    <div class="metric-grid">
      <article v-for="metric in metrics" :key="metric.label" class="metric-card">
        <div class="metric-icon" :class="metric.tone">{{ metric.icon }}</div>
        <div><p>{{ metric.label }}</p><strong>{{ metric.value }}</strong><small>{{ metric.note }}</small></div>
      </article>
    </div>

    <div class="dashboard-grid">
      <article class="panel trend-panel">
        <div class="panel-title">
          <div><p class="eyebrow">WEEKLY TREND</p><h2>近 7 日趋势</h2></div>
          <RouterLink class="text-link" to="/analytics">查看完整数据 →</RouterLink>
        </div>
        <div v-if="hasTrend" class="chart-wrap" aria-label="近七日页面浏览与测试次数折线图">
          <span v-for="tick in chartTicks" :key="tick" class="chart-axis">{{ tick }}</span>
          <div class="chart-canvas">
            <i v-for="n in 5" :key="n"></i>
            <svg viewBox="0 0 700 190" preserveAspectRatio="none" role="img">
              <polyline :points="chartPoints('pv')" fill="none" stroke="#ff705f" stroke-width="5" stroke-linecap="round" stroke-linejoin="round" />
              <polyline :points="chartPoints('startedCount')" fill="none" stroke="#9b6bd3" stroke-width="4" stroke-linecap="round" stroke-dasharray="8 8" />
              <polyline :points="chartPoints('completedCount')" fill="none" stroke="#59af8d" stroke-width="4" stroke-linecap="round" stroke-linejoin="round" />
            </svg>
            <div class="chart-days"><span v-for="day in chartDays" :key="day">{{ day }}</span></div>
          </div>
        </div>
        <p v-else class="chart-empty">暂无近 7 日趋势数据</p>
        <div class="chart-legend"><span><i class="coral-dot"></i>页面浏览</span><span><i class="purple-dot"></i>开始次数</span><span><i class="completed-dot"></i>完成次数</span></div>
        <small class="chart-disclaimer">分享仅有近 7 日汇总次数，接口暂未提供逐日趋势。</small>
      </article>

      <article class="panel pending-panel">
        <div class="panel-title"><h2>快捷操作</h2></div>
        <div class="task-list">
          <RouterLink to="/types"><span class="task-icon coral">▤</span><div><strong>题型管理</strong><small>维护题型、题目和结果规则</small></div><b>›</b></RouterLink>
          <RouterLink to="/releases"><span class="task-icon purple">□</span><div><strong>发布排期</strong><small>检查并发布题型版本</small></div><b>›</b></RouterLink>
          <RouterLink to="/home-config"><span class="task-icon yellow">⌂</span><div><strong>首页题型</strong><small>设置题型首页位置与顺序</small></div><b>›</b></RouterLink>
        </div>
      </article>

      <article class="panel usage-panel">
        <div class="panel-title"><h2>近 7 日各题型使用次数</h2><span>7 天</span></div>
        <div class="data-table usage-table">
          <div class="data-row data-head"><span>题型</span><span>开始次数</span><span>完成次数</span><span>完成率</span></div>
          <div v-for="row in usageRows" :key="row.name" class="data-row">
            <strong>{{ row.name }}</strong><span>{{ row.uses }}</span><span>{{ row.completed }}</span><span class="rate-pill">{{ row.rate }}</span>
          </div>
        </div>
      </article>

      <article class="panel release-panel">
        <div class="panel-title"><h2>统计状态</h2><RouterLink class="text-link" to="/analytics">查看数据</RouterLink></div>
        <div class="recent-release"><span class="release-mark">✦</span><div><strong>数据更新时间</strong><p>{{ overview?.updatedAt ?? '暂无数据' }}</p><small>统计结果来自服务端</small></div></div>
      </article>
    </div>
  </div>
</template>

<style scoped>
.chart-wrap{grid-template-rows:repeat(5,1fr)}
.chart-canvas{grid-area:1 / 2 / 6 / 3}
.completed-dot{background:#59af8d}
.chart-empty{display:grid;place-items:center;height:215px;margin:18px 0 0;color:#8c848d}
.chart-disclaimer{display:block;margin-top:9px;color:#8c848d;text-align:center}
</style>
