import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { compileScriptSetup } from './vue-script-test-loader.mjs'

const source = readFileSync(new URL('../src/views/TypeManagementView.vue', import.meta.url), 'utf8')
assert.match(source, /<select v-model.number="pageSize"[^>]*aria-label="每页条数"[^>]*@change="changePageSize"/)
assert.match(source, /v-for="size in \[10, 20, 50, 100\]"/)
const requests = []
let unmount
const view = compileScriptSetup(source, '{ page, pageSize, pageCount, total, items, selectedIds, keyword, typeFilter, statusFilter, feedback, load, changePageSize, changePage }', {
  vue: { onBeforeUnmount: (handler) => { unmount = handler } },
  modules: {
    '../api': { getTests: (query) => new Promise((resolve, reject) => { requests.push({ query, resolve, reject }) }) },
    '../auth': { isReadOnly: () => false },
    '../composables/useUnsavedChanges': { useUnsavedChanges: () => ({}) },
    '../composables/useConfirm': { confirmAction: async () => true },
    '../components/AiImageGenerationDialog.vue': {},
  },
})
const settle = () => new Promise((resolve) => setImmediate(resolve))
const result = (id, total) => ({ records: [{ id, testName: `题型 ${id}`, testType: 1, status: 1 }], total })

assert.equal(view.pageSize.value, 10)
view.total.value = 101
view.keyword.value = ' 职场 '
view.typeFilter.value = '双人测试'
view.statusFilter.value = '启用'
for (const size of [10, 20, 50, 100]) {
  view.page.value = 3
  view.selectedIds.value = [1]
  view.pageSize.value = size
  view.changePageSize()
  assert.equal(view.page.value, 1)
  assert.equal(view.selectedIds.value.length, 0)
  assert.equal(view.pageCount.value, Math.ceil(101 / size))
  const request = requests.at(-1)
  assert.deepEqual({ ...request.query }, { page: 1, size, keyword: '职场', testType: 2, status: 1 })
  request.resolve(result(size, 101))
  await settle()
}
view.changePage(1)
assert.equal(requests.at(-1).query.page, 2)
assert.equal(requests.at(-1).query.size, 100)
requests.at(-1).resolve(result(2, 101))
await settle()
const count = requests.length
view.changePage(1)
assert.equal(requests.length, count, '不得跳出最后一页')

const stale = view.load()
const staleRequest = requests.at(-1)
view.pageSize.value = 20
view.changePageSize()
requests.at(-1).resolve(result(20, 41))
await settle()
staleRequest.resolve(result(99, 999))
await stale
assert.equal(view.items.value[0].id, 20, '旧请求不能覆盖新列表')
assert.equal(view.total.value, 41)
assert.equal(view.pageCount.value, 3)

const staleFailure = view.load()
const failedRequest = requests.at(-1)
view.changePageSize()
requests.at(-1).resolve(result(21, 41))
await settle()
failedRequest.reject(new Error('旧请求失败'))
await staleFailure
assert.equal(view.feedback.value, '', '旧请求错误不应干扰最新结果')
const pending = view.load()
unmount()
requests.at(-1).resolve(result(77, 77))
await pending
assert.equal(view.items.value[0].id, 21, '卸载后不更新列表')
console.log('题型分页回归通过：每页条数、页码重置、筛选参数、页数边界和过期响应')
