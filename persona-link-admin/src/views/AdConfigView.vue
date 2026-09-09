<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { getAdConfig, saveAdConfig, type AdConfig, type AdConfigInput } from '../api'
import { isAdmin } from '../auth'
import { confirmAction } from '../composables/useConfirm'
import { useUnsavedChanges } from '../composables/useUnsavedChanges'

const saved = ref<AdConfig | null>(null)
const form = ref<AdConfigInput>({ enabled: false, adUnitId: '', failurePolicy: 1 })
const loading = ref(false)
const saving = ref(false)
const notice = ref('')
const failed = ref(false)
const { dirty, markDirty, markSaved } = useUnsavedChanges('广告配置尚未保存，确认放弃修改并离开吗？', saving)
let disposed = false

async function load() {
  if (loading.value || saving.value || disposed) return
  loading.value = true
  notice.value = ''
  failed.value = false
  try {
    const config = await getAdConfig()
    if (disposed) return
    saved.value = config
    form.value = { enabled: config.enabled, adUnitId: config.adUnitId, failurePolicy: config.failurePolicy }
    markSaved()
  } catch (error) {
    if (!disposed) {
      failed.value = true
      notice.value = error instanceof Error ? error.message : '广告配置加载失败，请重试'
    }
  } finally { if (!disposed) loading.value = false }
}

async function save() {
  if (!saved.value || loading.value || saving.value || disposed || !isAdmin()) return
  const payload: AdConfigInput = { ...form.value, adUnitId: form.value.adUnitId.trim() }
  failed.value = false
  notice.value = ''
  if ((payload.enabled && !payload.adUnitId) || (payload.adUnitId && !/^adunit-[A-Za-z0-9]{1,64}$/.test(payload.adUnitId))) {
    failed.value = true
    notice.value = '请填写微信提供的广告位 ID，格式为 adunit- 开头的字母或数字'
    return
  }
  if (payload.failurePolicy !== 1 && payload.failurePolicy !== 2) {
    failed.value = true
    notice.value = '请选择广告加载失败处理方式'
    return
  }
  saving.value = true
  try {
    if (payload.enabled !== saved.value.enabled) {
      const confirmed = await confirmAction(payload.enabled
        ? '开启后，所有题型尚未解锁的新结果需要观看广告。请确认广告位已审核通过并完成真机验证。'
        : '关闭后，后续结果访问请求将直接获得查看权限，已解锁结果不受影响。', {
        title: payload.enabled ? '开启激励视频广告？' : '关闭激励视频广告？',
        confirmText: payload.enabled ? '确认开启' : '确认关闭',
      })
      if (!confirmed || disposed) return
    }
    const config = await saveAdConfig(payload)
    if (disposed) return
    saved.value = config
    form.value = { enabled: config.enabled, adUnitId: config.adUnitId, failurePolicy: config.failurePolicy }
    markSaved()
    notice.value = '已生效，后续结果访问请求将使用新配置。'
  } catch (error) {
    if (!disposed) {
      failed.value = true
      notice.value = error instanceof Error ? error.message : '保存失败，请重试'
    }
  } finally { if (!disposed) saving.value = false }
}

onMounted(load)
onBeforeUnmount(() => { disposed = true })
</script>

<template>
  <section class="ad-config-view">
    <header class="page-heading"><h1>广告配置</h1><p>统一设置所有题型的激励视频广告，保存后生效。</p></header>
    <p v-if="notice" class="config-notice" :class="{ error: failed }" role="status">{{ notice }}</p>
    <div v-if="loading" class="loading-state" role="status">正在加载广告配置…</div>
    <button v-else-if="!saved" type="button" @click="load">重新加载</button>
    <div v-else class="config-layout">
      <form class="config-card" @submit.prevent="save">
        <h2>微信激励视频广告</h2>
        <p class="muted">取得微信广告位 ID 后，在这里填写并开启。</p>
        <fieldset :disabled="saving || !isAdmin()">
          <label class="toggle-row">
            <span><strong>启用广告解锁</strong><small>关闭时，用户直接查看测试结果</small></span>
            <input v-model="form.enabled" type="checkbox" role="switch" @change="markDirty" />
          </label>
          <label class="field"><span>广告位 ID</span>
            <input v-model="form.adUnitId" type="text" maxlength="71" :required="form.enabled" placeholder="adunit-…" autocomplete="off" spellcheck="false" @input="markDirty" />
            <small>填写微信公众平台的激励视频广告位 ID；格式正确不代表已审核通过。</small>
          </label>
          <label class="field"><span>广告加载失败处理</span>
            <select v-model.number="form.failurePolicy" @change="markDirty">
              <option :value="1">免费放行（推荐）</option>
              <option :value="2">提示稍后重试</option>
            </select>
            <small>仅处理广告加载失败或无库存；免费放行每位用户每天最多 3 次，超过后提示重试。主动提前关闭广告不会解锁。</small>
          </label>
        </fieldset>
        <div class="rule-note">适用于所有题型的测试结果解锁。同一份结果解锁后不再重复观看广告。</div>
        <footer class="save-row"><span>{{ dirty ? '有未保存的修改' : '配置已同步' }}</span><button class="save-button" type="submit" :disabled="saving || !dirty || !isAdmin()">{{ saving ? '正在确认 / 保存…' : '保存配置' }}</button></footer>
      </form>
      <aside class="live-card" aria-label="当前生效配置">
        <span class="live-caption">当前生效状态</span>
        <h2><span class="status-dot" :class="{ enabled: saved.enabled }" aria-hidden="true"></span>{{ saved.enabled ? '广告已开启' : '广告已关闭' }}</h2>
        <p>{{ saved.enabled ? '尚未解锁的新结果需要观看广告。' : '用户直接查看测试结果。' }}</p>
        <dl>
          <dt>生效广告位</dt><dd class="ad-unit">{{ saved.adUnitId || '尚未配置' }}</dd>
          <dt>加载失败处理</dt><dd>{{ saved.failurePolicy === 1 ? '免费放行' : '提示稍后重试' }}</dd>
          <dt>最近修改时间</dt><dd>{{ saved.updatedAt ? new Date(saved.updatedAt).toLocaleString() : '尚未修改' }}</dd>
          <dt>修改人</dt><dd>{{ saved.updatedByName || '—' }}</dd>
        </dl>
        <p class="verification-note">这里只表示本系统已保存的配置。广告是否可投放，请在小程序真机验证；实际收益以微信广告数据为准。</p>
      </aside>
    </div>
  </section>
</template>

<style scoped>
.toggle-row input[type="checkbox"]{appearance:none;width:46px;height:26px;margin:0;border:1px solid #9d8eaa;border-radius:20px;background:#ddd3e6;position:relative;cursor:pointer}.toggle-row input[type="checkbox"]::after{content:"";position:absolute;top:3px;left:3px;width:18px;height:18px;border-radius:50%;background:#fff;box-shadow:0 1px 3px #27222b33}.toggle-row input[type="checkbox"]:checked{background:#8257bc;border-color:#8257bc}.toggle-row input[type="checkbox"]:checked::after{left:23px}
.ad-config-view{max-width:1200px;display:grid;gap:20px;color:#27222b}.page-heading h1{margin:0;font-size:38px}.page-heading p{margin:8px 0 0;color:#77707f}.config-layout{display:grid;grid-template-columns:minmax(0,1.65fr) minmax(280px,1fr);gap:24px;align-items:start}.config-card,.live-card{border:1.5px solid #27222b;border-radius:20px;padding:28px;background:#fffdf8}.config-card h2,.live-card h2{margin:0;font-size:22px}.muted{color:#77707f;margin:8px 0 24px;line-height:1.6}fieldset{margin:0;padding:0;border:0;min-width:0}.toggle-row{display:flex;align-items:center;justify-content:space-between;gap:20px;padding:18px;background:#f3eaff;border-radius:12px;margin-bottom:24px;cursor:pointer}.toggle-row strong,.toggle-row small{display:block}.toggle-row small{margin-top:6px;color:#77707f;line-height:1.5}.toggle-row input{width:24px;height:24px;accent-color:#8257bc;flex-shrink:0}.field{display:grid;gap:9px;margin-bottom:24px}.field>span{font-weight:700}.field input,.field select{width:100%;min-height:46px;border:1px solid #cfc5d4;border-radius:10px;background:#fff;padding:10px 12px;font:inherit;color:inherit}.field small{color:#77707f;line-height:1.6}.rule-note{border-top:1px solid #e8e0d6;padding-top:18px;color:#77707f;font-size:13px;line-height:1.8}.save-row{margin-top:24px;display:flex;gap:16px;align-items:center;justify-content:space-between}.save-row>span{color:#77707f;font-size:13px}button{min-height:44px;border:1.5px solid #27222b;border-radius:10px;padding:10px 20px;font:inherit;cursor:pointer;background:#fffdf8;color:inherit}.save-button{background:#c9a7ff;font-weight:700}button:disabled,fieldset:disabled{opacity:.6}button:disabled{cursor:not-allowed}button:focus-visible,input:focus-visible,select:focus-visible{outline:3px solid #8257bc;outline-offset:3px}.live-card{background:#f3eaff}.live-caption{display:block;color:#6d5287;font-size:13px;font-weight:700;margin-bottom:18px}.live-card h2{display:flex;gap:10px;align-items:center}.status-dot{width:10px;height:10px;border-radius:50%;background:#8b8294}.status-dot.enabled{background:#36866a}.live-card>p{line-height:1.7;color:#6c6275}.live-card dl{border-top:1px solid #dac9ed;padding-top:18px;margin:22px 0}.live-card dt{font-size:12px;color:#776a85;margin-top:16px}.live-card dd{margin:6px 0 0;font-size:14px}.ad-unit{font-family:monospace;overflow-wrap:anywhere}.live-card .verification-note{font-size:12px;border-top:1px solid #dac9ed;padding-top:18px;margin-bottom:0}.config-notice,.loading-state{margin:0;padding:14px 18px;background:#edf6ef;border-radius:10px;line-height:1.6}.config-notice.error{background:#fff0ea;color:#a83c30}@media(max-width:800px){.config-layout{grid-template-columns:1fr}.config-card,.live-card{padding:20px}.page-heading h1{font-size:30px}.save-row{flex-wrap:wrap}.save-button{flex:1}}
</style>
