<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { getHealth } from '../api'

const health = ref<'checking' | 'online' | 'offline'>('checking')

const metrics = [
  { icon: '◉', label: '题型曝光', value: '18,642', note: '较上周 +12.4%', tone: 'purple' },
  { icon: '▷', label: '开始测试', value: '6,281', note: '转化率 33.7%', tone: 'coral' },
  { icon: '✓', label: '完成率', value: '82.4%', note: '较上周 +3.1%', tone: 'yellow' },
  { icon: '⌯', label: '分享率', value: '12.6%', note: '分享 651 次', tone: 'mint' },
]

const usageRows = [
  { name: '恋爱性格测试', uses: '2,418', completed: '2,061', rate: '85.2%' },
  { name: '职场人格图鉴', uses: '1,703', completed: '1,332', rate: '78.2%' },
  { name: '朋友默契挑战', uses: '1,126', completed: '946', rate: '84.0%' },
]

onMounted(async () => {
  try {
    health.value = (await getHealth()) === 'UP' ? 'online' : 'offline'
  } catch {
    health.value = 'offline'
  }
})
</script>

<template>
  <div class="page-stack">
    <div class="welcome-row">
      <div>
        <p class="eyebrow">TODAY'S OVERVIEW</p>
        <h1>早上好，运营小鹿 <span>♡</span></h1>
        <p>先看今日表现，再处理内容发布与配置。</p>
      </div>
      <div class="welcome-actions">
        <span class="data-note">以下业务指标为演示数据</span>
        <div class="health-chip" :class="health">
          <i></i>{{ health === 'checking' ? '服务检查中' : health === 'online' ? '服务在线' : '服务未连接' }}
        </div>
      </div>
    </div>

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
        <div class="chart-wrap" aria-label="近七日访问量演示折线图">
          <span class="chart-axis">4k</span><span class="chart-axis">3k</span><span class="chart-axis">2k</span><span class="chart-axis">1k</span>
          <div class="chart-canvas">
            <i v-for="n in 4" :key="n"></i>
            <svg viewBox="0 0 700 190" preserveAspectRatio="none" role="img">
              <polyline points="0,150 115,130 230,138 345,90 460,102 575,54 700,32" fill="none" stroke="#ff705f" stroke-width="5" stroke-linecap="round" stroke-linejoin="round" />
              <polyline points="0,170 115,158 230,160 345,130 460,138 575,112 700,92" fill="none" stroke="#9b6bd3" stroke-width="4" stroke-linecap="round" stroke-dasharray="8 8" />
            </svg>
            <div class="chart-days"><span>周一</span><span>周二</span><span>周三</span><span>周四</span><span>周五</span><span>周六</span><span>周日</span></div>
          </div>
        </div>
        <div class="chart-legend"><span><i class="coral-dot"></i>曝光量</span><span><i class="purple-dot"></i>开始测试</span></div>
      </article>

      <article class="panel pending-panel">
        <div class="panel-title"><h2>待处理事项</h2><span class="count-badge">3 项</span></div>
        <div class="task-list">
          <RouterLink to="/types/1/questions"><span class="task-icon coral">!</span><div><strong>恋爱性格测试</strong><small>还有 2 道题待完善</small></div><b>›</b></RouterLink>
          <RouterLink to="/releases"><span class="task-icon purple">□</span><div><strong>职场人格图鉴</strong><small>计划今天 18:00 发布</small></div><b>›</b></RouterLink>
          <RouterLink to="/home-config"><span class="task-icon yellow">⌂</span><div><strong>首页推荐位</strong><small>有未保存的演示配置</small></div><b>›</b></RouterLink>
        </div>
      </article>

      <article class="panel usage-panel">
        <div class="panel-title"><h2>各题型使用次数</h2><span>本周</span></div>
        <div class="data-table usage-table">
          <div class="data-row data-head"><span>题型</span><span>开始次数</span><span>完成次数</span><span>完成率</span></div>
          <div v-for="row in usageRows" :key="row.name" class="data-row">
            <strong>{{ row.name }}</strong><span>{{ row.uses }}</span><span>{{ row.completed }}</span><span class="rate-pill">{{ row.rate }}</span>
          </div>
        </div>
      </article>

      <article class="panel release-panel">
        <div class="panel-title"><h2>最近发布</h2><RouterLink class="text-link" to="/releases">全部记录</RouterLink></div>
        <div class="recent-release">
          <span class="release-mark">✦</span>
          <div><strong>朋友默契挑战 · v1.3</strong><p>8 月 29 日 16:20 · 运营小鹿</p><small>首页推荐位同步更新</small></div>
        </div>
        <div class="insight-note"><b>小小发现</b><p>周末的测试完成率更高，适合安排重点题型发布。</p></div>
      </article>
    </div>
  </div>
</template>
