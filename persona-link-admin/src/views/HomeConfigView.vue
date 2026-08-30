<script setup lang="ts">
import { computed, ref } from 'vue'

type TestCard = {
  id: number
  title: string
  kind: string
  icon: string
  tone: string
  visible: boolean
}

type Category = { id: number; name: string; tone: string }

const tests = ref<TestCard[]>([
  { id: 1, title: '双人关系角色测试', kind: '双人测试', icon: '💞', tone: 'lavender', visible: true },
  { id: 2, title: '你是哪种牛马性格？', kind: '单人测试', icon: '🐴', tone: 'yellow', visible: true },
  { id: 3, title: '你的 X 偏好？', kind: '单人测试', icon: '📋', tone: 'green', visible: true },
  { id: 4, title: '周末充电方式', kind: '单人测试', icon: '☁️', tone: 'blue', visible: false },
])

const categories = ref<Category[]>([
  { id: 0, name: '全部', tone: 'lavender' },
  { id: 1, name: '职场协作', tone: 'yellow' },
  { id: 2, name: '相处方式', tone: 'green' },
  { id: 3, name: '日常偏好', tone: 'blue' },
])

const focusId = ref<number | null>(1)
const savedMessage = ref('')
const focusTest = computed(() => tests.value.find((item) => item.id === focusId.value) ?? null)
const visibleTests = computed(() => tests.value.filter((item) => item.visible && item.id !== focusId.value))

function move<T>(items: T[], index: number, offset: number) {
  const target = index + offset
  if (target < 0 || target >= items.length) return
  ;[items[index], items[target]] = [items[target], items[index]]
}

function chooseFocus() {
  const candidate = tests.value.find((item) => item.visible && item.id !== focusId.value)
  if (candidate) focusId.value = candidate.id
}

function save() {
  savedMessage.value = `已在本地保存预览配置 · ${new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })}`
}
</script>

<template>
  <section class="ops-view">
    <header class="page-heading">
      <div>
        <div class="title-line">
          <h1>首页配置 <span>♡</span></h1>
          <span class="demo-badge">演示模式 · 未接后端</span>
        </div>
        <p>管理小程序首页推荐内容与展示顺序</p>
      </div>
      <div class="heading-actions">
        <button class="secondary" type="button" @click="savedMessage = '右侧预览已刷新'">◉ 预览首页</button>
        <button class="primary" type="button" @click="save">保存配置</button>
      </div>
    </header>

    <p v-if="savedMessage" class="feedback" role="status">{{ savedMessage }}</p>

    <div class="config-grid">
      <div class="config-stack">
        <article class="panel">
          <div class="panel-title"><h2>焦点推荐位</h2><span>仅展示 1 个</span></div>
          <div v-if="focusTest" class="focus-row">
            <div class="focus-card">
              <div class="mascot">{{ focusTest.icon }}</div>
              <div><strong>{{ focusTest.title }}</strong><small>焦点位</small></div>
            </div>
            <div class="row-actions">
              <button type="button" @click="chooseFocus">更换题型</button>
              <button class="danger" type="button" @click="focusId = null">下架</button>
            </div>
          </div>
          <button v-else class="empty-focus" type="button" @click="chooseFocus">＋ 选择一个焦点题型</button>
        </article>

        <article class="panel">
          <div class="panel-title">
            <div><h2>推荐题型</h2><span>使用箭头调整展示顺序</span></div>
            <button class="small-primary" type="button" @click="tests[tests.length - 1].visible = true">＋ 添加推荐</button>
          </div>
          <div class="sortable-list">
            <div v-for="(item, index) in tests" :key="item.id" class="sort-row" :class="{ muted: !item.visible }">
              <span class="drag" aria-hidden="true">⠿</span>
              <span class="thumb" :class="item.tone">{{ item.icon }}</span>
              <div class="row-copy"><strong>{{ item.title }}</strong><small>{{ item.kind }}</small></div>
              <span class="status" :class="{ off: !item.visible }">{{ item.visible ? '展示中' : '未展示' }}</span>
              <button type="button" :disabled="index === 0" aria-label="上移" @click="move(tests, index, -1)">↑</button>
              <button type="button" :disabled="index === tests.length - 1" aria-label="下移" @click="move(tests, index, 1)">↓</button>
              <button class="icon-button" type="button" :aria-label="item.visible ? '取消展示' : '设为展示'" @click="item.visible = !item.visible">
                {{ item.visible ? '×' : '+' }}
              </button>
            </div>
          </div>
        </article>

        <article class="panel">
          <div class="panel-title"><div><h2>首页分类</h2><span>“全部”固定在首位</span></div></div>
          <div class="category-grid">
            <div v-for="(item, index) in categories" :key="item.id" class="category-card" :class="item.tone">
              <span v-if="index > 0" class="drag">⠿</span>
              <strong>{{ item.name }}</strong>
              <div v-if="index > 0" class="mini-actions">
                <button type="button" :disabled="index === 1" @click="move(categories, index, -1)">←</button>
                <button type="button" :disabled="index === categories.length - 1" @click="move(categories, index, 1)">→</button>
              </div>
              <span v-else class="fixed">✓ 固定</span>
            </div>
          </div>
        </article>
      </div>

      <aside class="preview-panel">
        <h2>小程序首页预览</h2>
        <div class="phone">
          <div class="phone-status"><strong>9:41</strong><span>▮▮▮ ᴡɪꜰɪ ▰</span></div>
          <div class="mini-brand">心动测测 <span>♡</span></div>
          <h3>把此刻的你，<br />收进一张小卡片。</h3>
          <div v-if="focusTest" class="preview-focus">
            <span>{{ focusTest.icon }}</span>
            <div><strong>你和 TA，<br />是哪种相处搭子？</strong><button type="button">和 TA 一起测 ›</button></div>
          </div>
          <div v-else class="preview-empty">焦点推荐位暂未配置</div>
          <div class="preview-cards">
            <div v-for="item in visibleTests.slice(0, 2)" :key="item.id" :class="item.tone">
              <strong>{{ item.title }}</strong><span>{{ item.icon }}</span>
            </div>
          </div>
          <div class="preview-tabs">
            <span v-for="item in categories" :key="item.id">{{ item.name }}</span>
          </div>
          <footer><b>⌂<small>首页</small></b><span>♙<small>我的</small></span></footer>
        </div>
      </aside>
    </div>
  </section>
</template>

<style scoped>
.ops-view{display:grid;gap:18px}.page-heading,.title-line,.heading-actions,.panel-title,.focus-row,.sort-row,.phone-status{display:flex;align-items:center}.page-heading{justify-content:space-between;gap:20px}.page-heading h1{margin:0;font-size:38px;letter-spacing:-1px}.page-heading h1 span,.mini-brand span{color:#8f63dc}.page-heading p{margin:6px 0 0;color:#777}.title-line{gap:14px}.demo-badge{padding:6px 10px;border:1px solid #e8bd6b;border-radius:99px;background:#fff4ce;color:#775217;font-size:12px}.heading-actions{gap:12px}button{border:1.5px solid #181818;border-radius:10px;background:#fff;padding:9px 15px;font:inherit;cursor:pointer}button:disabled{opacity:.35;cursor:not-allowed}.primary,.small-primary{background:#ff654d;color:#fff}.small-primary{background:#cba6ff;color:#181818}.feedback{margin:0;padding:10px 14px;border:1px solid #a9d690;border-radius:10px;background:#f0faeb;color:#3f692d}.config-grid{display:grid;grid-template-columns:minmax(0,1fr) 390px;gap:18px}.config-stack{display:grid;gap:14px}.panel,.preview-panel{border:1.5px solid #a6a6a6;border-radius:16px;background:rgba(255,255,255,.75);padding:18px}.panel-title{justify-content:space-between;gap:12px;margin-bottom:14px}.panel-title>div{display:flex;align-items:center;gap:10px}.panel-title h2,.preview-panel h2{margin:0;font-size:21px}.panel-title span{color:#8260c5;font-size:13px}.focus-row{gap:16px}.focus-card{display:flex;align-items:center;gap:18px;flex:1;min-height:115px;padding:12px 24px;border:1.5px solid #8362ba;border-radius:14px;background:linear-gradient(135deg,#e5d3ff,#f6e9ff)}.mascot{font-size:54px}.focus-card strong{display:block;font-size:21px}.focus-card small{display:inline-block;margin-top:8px;padding:3px 8px;border-radius:6px;background:#dbc3ff;color:#7252ae}.row-actions{display:grid;gap:10px}.danger{border-color:#ff5b4d;color:#f04436}.empty-focus{width:100%;min-height:90px;border-style:dashed;color:#7051aa}.sortable-list{display:grid;gap:9px}.sort-row{gap:10px;padding:10px 12px;border:1px solid #ccc;border-radius:12px;background:#fff}.sort-row.muted{opacity:.6}.drag{color:#7252ae}.thumb{display:grid;place-items:center;width:70px;height:48px;border:1px solid #bbb;border-radius:9px;font-size:27px}.row-copy{display:grid;gap:3px;flex:1}.row-copy small{color:#8a8a8a}.status{padding:4px 8px;border-radius:7px;background:#eefbe5;color:#4f8d32;font-size:12px}.status.off{background:#f2f2f2;color:#777}.sort-row button,.mini-actions button{padding:5px 9px}.icon-button{border:0;background:transparent;font-size:20px}.category-grid{display:grid;grid-template-columns:repeat(4,1fr);gap:10px}.category-card{min-height:78px;padding:12px;border:1px solid #bbb;border-radius:12px;display:flex;align-items:center;gap:8px}.category-card strong{flex:1}.fixed{font-size:11px;color:#7355ac}.mini-actions{display:flex}.lavender{background:#eee3ff}.yellow{background:#fff0c3}.green{background:#eaf5df}.blue{background:#e6f3f7}.preview-panel{align-self:start}.phone{width:min(100%,320px);margin:12px auto 0;border:2px solid #171717;border-radius:35px;background:#fffdf7;padding:16px 14px 12px;overflow:hidden}.phone-status{justify-content:space-between;font-size:11px}.mini-brand{margin-top:14px;font-weight:900;font-size:17px}.phone h3{font-size:24px;line-height:1.35}.preview-focus{display:flex;align-items:center;min-height:150px;padding:14px;border:1.5px solid #392d4b;border-radius:14px;background:linear-gradient(135deg,#d9bcff,#f2dbff);transform:rotate(1deg)}.preview-focus>span{font-size:52px}.preview-focus strong{font-size:18px}.preview-focus button{display:block;margin-top:14px;padding:6px 10px;background:#ff6b5b;color:white}.preview-empty{display:grid;place-items:center;height:120px;border:1px dashed #aaa;border-radius:14px;color:#888}.preview-cards{display:grid;grid-template-columns:1fr 1fr;gap:8px;margin-top:10px}.preview-cards>div{display:flex;justify-content:space-between;min-height:78px;padding:10px;border:1px solid #777;border-radius:11px}.preview-cards span{font-size:25px}.preview-tabs{display:flex;gap:6px;overflow:hidden;margin:12px 0}.preview-tabs span{white-space:nowrap;border:1px solid #777;border-radius:99px;padding:3px 9px;font-size:10px}.phone footer{display:flex;justify-content:space-around;border-top:1px solid #aaa;padding-top:9px}.phone footer>*{display:grid;text-align:center}.phone footer small{font-size:10px}.phone footer b{color:#8c61d0}@media(max-width:1050px){.config-grid{grid-template-columns:1fr}.preview-panel{order:-1}.phone{width:310px}.category-grid{grid-template-columns:repeat(2,1fr)}}@media(max-width:650px){.page-heading,.focus-row{align-items:stretch;flex-direction:column}.heading-actions{flex-wrap:wrap}.page-heading h1{font-size:30px}.category-grid{grid-template-columns:1fr}.sort-row{flex-wrap:wrap}.row-copy{min-width:150px}.status{margin-left:auto}}
</style>
