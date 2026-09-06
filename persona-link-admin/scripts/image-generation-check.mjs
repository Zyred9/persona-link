import assert from 'node:assert/strict'
import fs from 'node:fs'
import vm from 'node:vm'
import { createRequire } from 'node:module'
import { parse } from '@vue/compiler-sfc'
import ts from 'typescript'

const require = createRequire(import.meta.url)
const source = fs.readFileSync(new URL('../src/components/AiImageGenerationDialog.vue', import.meta.url), 'utf8')
const { descriptor } = parse(source)
const compiled = ts.transpileModule(`${descriptor.scriptSetup.content}\nmodule.exports = { prompt, resolution, error, task, generate, apply, refresh, loading, busy };`, { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 } }).outputText
const completed = { id: 9, testId: 2, provider: 1, modelName: 'test', promptText: '测试', taskStatus: 3, coverUrl: '/cover.png', detailImageUrl: '/detail.png', appliedVersionId: null }
const deferred = () => { let resolve; let reject; const promise = new Promise((ok, fail) => { resolve = ok; reject = fail }); return { promise, resolve, reject } }

function mount(overrides = {}, readOnly = false, storage = new Map(), confirmAction = async () => true) {
  let mounted, unmounted, sequence = 0
  const timers = new Map(), events = [], calls = []
  const api = {
    getLatestAiImageTask: async () => null,
    getAiImageTask: async () => completed,
    createAiImageTask: async (_id, payload) => { calls.push(payload); return completed },
    applyAiImageTask: async () => ({ ...completed, taskStatus: 5, appliedVersionId: 12 }),
    retryAiImageTask: async () => ({ ...completed, taskStatus: 2 }),
    ...overrides,
  }
  const context = {
    module: { exports: {} }, exports: {}, AbortController,
    sessionStorage: { getItem: (key) => storage.get(key) ?? null, setItem: (key, value) => storage.set(key, value), removeItem: (key) => storage.delete(key) },
    defineProps: () => ({ testId: 2, testName: '测试题型' }), defineEmits: () => (...args) => events.push(args),
    crypto: { randomUUID: () => `request-${++sequence}` }, window: { confirm: () => true },
    setTimeout: (fn) => { const id = ++sequence; timers.set(id, fn); return id }, clearTimeout: (id) => timers.delete(id),
    require: (name) => name === 'vue' ? { ...require('vue'), onMounted: (fn) => { mounted = fn }, onUnmounted: (fn) => { unmounted = fn } } : name === '../auth' ? { isReadOnly: () => readOnly } : name === '../composables/useConfirm' ? { confirmAction } : api,
  }
  vm.runInNewContext(compiled, context)
  return { ...context.module.exports, mounted, unmounted, timers, events, calls, context }
}

// 重开恢复最近任务，活跃任务不可重复生成，完成后停止轮询。
const confirmation = deferred()
let applyCalls = 0
const awaitingConfirmation = mount({ getLatestAiImageTask: async () => completed, applyAiImageTask: async () => { applyCalls++; return completed } }, false, new Map(), () => confirmation.promise)
await awaitingConfirmation.mounted()
const firstApply = awaitingConfirmation.apply()
await awaitingConfirmation.apply()
assert.equal(applyCalls, 0, '等待确认时不得提前或重复采用')
confirmation.resolve(false)
await firstApply
assert.equal(applyCalls, 0, '取消确认不得采用图片')
assert.equal(awaitingConfirmation.busy.value, false, '取消后必须解除操作锁')

const resumed = mount({ getLatestAiImageTask: async () => ({ ...completed, taskStatus: 2 }) })
await resumed.mounted()
assert.equal(resumed.task.value.taskStatus, 2)
assert.equal(resumed.timers.size, 1)
await resumed.generate()
assert.equal(resumed.calls.length, 0)
await resumed.refresh()
assert.equal(resumed.task.value.taskStatus, 3)
assert.equal(resumed.timers.size, 0)
await resumed.apply()
assert.equal(resumed.task.value.taskStatus, 5)
assert.equal(resumed.events[0][0], 'applied')

// 请求未完成时重复点击不提交第二次；卸载后的响应不能修改状态或重建轮询。
const pending = deferred()
let submits = 0
const detached = mount({ createAiImageTask: () => { submits++; return pending.promise } })
await detached.mounted()
const submit = detached.generate()
await detached.generate()
assert.equal(submits, 1)
detached.unmounted()
pending.resolve({ ...completed, taskStatus: 2 })
await submit
assert.equal(detached.task.value, null)
assert.equal(detached.timers.size, 0)

// 查询取消及旧题型加载响应隔离。
const oldResponse = deferred()
let signal
const old = mount({ getLatestAiImageTask: (_id, abortSignal) => { signal = abortSignal; return oldResponse.promise } })
const oldLoad = old.mounted()
old.unmounted()
assert.equal(signal.aborted, true)
oldResponse.resolve(completed)
await oldLoad
assert.equal(old.task.value, null)

// 网络提交失败重试复用幂等键；已知成功后的重新生成使用新键。
const requests = []
const retry = mount({ createAiImageTask: async (_id, payload) => { requests.push(payload.requestId); if (requests.length === 1) throw new Error('网络中断'); return completed } })
await retry.mounted()
await retry.generate()
await retry.generate()
await retry.generate()
assert.equal(requests[0], requests[1])
assert.notEqual(requests[1], requests[2])

// 弹窗关闭/重开后不确定的提交仍复用原幂等键。
const storage = new Map(), reopeningRequests = []
const firstOpen = mount({ createAiImageTask: async (_id, payload) => { reopeningRequests.push(payload.requestId); throw new Error('超时') } }, false, storage)
await firstOpen.mounted()
firstOpen.resolution.value.detailWidth = 1440
firstOpen.resolution.value.detailHeight = 720
await firstOpen.generate()
firstOpen.unmounted()
const secondOpen = mount({ createAiImageTask: async (_id, payload) => { reopeningRequests.push(payload.requestId); return completed } }, false, storage)
await secondOpen.mounted()
assert.equal(secondOpen.resolution.value.detailWidth, 1440)
assert.equal(secondOpen.resolution.value.detailHeight, 720)
await secondOpen.generate()
assert.equal(reopeningRequests[0], reopeningRequests[1])
assert.equal(storage.size, 0)

// 自定义尺寸随请求发送并从任务恢复；改变尺寸不能复用旧幂等键。
const custom = mount({ getLatestAiImageTask: async () => ({ ...completed, coverWidth: 1024, coverHeight: 1024, detailWidth: 1600, detailHeight: 800 }) })
await custom.mounted()
assert.equal(custom.resolution.value.detailWidth, 1600)
await custom.generate()
assert.equal(custom.calls[0].coverWidth, 1024)
assert.equal(custom.calls[0].detailHeight, 800)
custom.resolution.value.coverWidth = 0
await custom.generate()
assert.equal(custom.calls.length, 1)
assert.ok(custom.error.value.includes('宽高'))
custom.resolution.value.coverWidth = 300
custom.resolution.value.coverHeight = 300
await custom.generate()
assert.equal(custom.calls.length, 1)
custom.resolution.value.coverWidth = 2048
custom.resolution.value.coverHeight = 512
await custom.generate()
assert.equal(custom.calls.length, 1)

const changedRequests = []
const changed = mount({ createAiImageTask: async (_id, payload) => { changedRequests.push(payload); throw new Error('超时') } })
await changed.mounted()
await changed.generate()
changed.resolution.value.detailWidth = 1200
await changed.generate()
assert.notEqual(changedRequests[0].requestId, changedRequests[1].requestId)
assert.equal(changedRequests[1].detailWidth, 1200)

const large = mount()
await large.mounted()
Object.assign(large.resolution.value, { coverWidth: 2000, coverHeight: 2000, detailWidth: 2200, detailHeight: 1000 })
await large.generate()
assert.equal(large.calls.length, 1)
assert.equal(large.calls[0].coverWidth, 2000)
assert.equal(large.calls[0].detailWidth, 2200)
assert.equal(large.calls[0].detailHeight, 1000)
large.resolution.value.coverWidth = 2200
large.resolution.value.coverHeight = 2200
await large.generate()
assert.equal(large.calls.length, 1)
assert.ok(large.error.value.includes('4194304'))
assert.ok(!source.includes('max="2048"'))

// 预览标注必须来自原任务，不能把输入框中尚未生成的新尺寸显示为成品尺寸。
assert.ok(source.includes('task.coverWidth ?? 800'))
assert.ok(source.includes('task.detailHeight ?? 500'))

const denied = mount({}, true)
await denied.mounted()
await denied.generate()
denied.task.value = completed
await denied.apply()
assert.equal(denied.calls.length, 0)
assert.equal(denied.events.length, 0)
assert.ok(source.includes('800 × 800') && source.includes('1100 × 500'))
console.log('AI image generation checks passed: restore, polling, duplicate submission, unmount race, idempotency, apply, read-only.')
