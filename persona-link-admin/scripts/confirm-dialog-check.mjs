import assert from 'node:assert/strict'
import { fileURLToPath } from 'node:url'
import { loadTypeScriptModule } from './vue-script-test-loader.mjs'

const { confirmAction, confirmState, settleConfirm } = loadTypeScriptModule(fileURLToPath(new URL('../src/composables/useConfirm.ts', import.meta.url)))
const first = confirmAction('删除后无法继续使用该内容', { title: '删除题型', confirmText: '确认删除', danger: true })
let resolved = false
first.then(() => { resolved = true })
await Promise.resolve()
assert.equal(resolved, false, '点击确认之前不能执行业务操作')
assert.equal(await confirmAction('重复点击'), false, '已有确认时拒绝重复请求')
assert.equal(confirmState.message, '删除后无法继续使用该内容')
settleConfirm(false)
assert.equal(await first, false)
const next = confirmAction('保存为草稿')
settleConfirm(true)
settleConfirm(false)
assert.equal(await next, true, '一次确认只能结算一次')
const canceled = confirmAction('离开页面')
settleConfirm(false)
assert.equal(await canceled, false)
console.log('confirm dialog state checks: OK')
