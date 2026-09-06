import assert from 'node:assert/strict'
import { fileURLToPath } from 'node:url'
import { loadViewScriptSetup } from './vue-script-test-loader.mjs'

const path = fileURLToPath(new URL('../src/views/HomeConfigView.vue', import.meta.url))
let rows = Array.from({ length: 55 }, (_, index) => ({ id: index + 1, testName: `题型${index + 1}`, homeDisplay: 0, homeSort: 0 }))
let finishUpdate
const gate = new Promise((resolve) => { finishUpdate = resolve })
const home = loadViewScriptSetup(path, '{ load, tests, page, pageSize, pageCount, pagedTests, changePage, changePageSize, markRowDirty, dirtyIds, save, mergeSavedRows }', {
  modules: {
    'vue-router': { useRoute: () => ({ query: { testId: '24' } }) },
    '../api': {
      getTests: async () => ({ records: rows.map((row) => ({ ...row })), total: rows.length, size: 100 }),
      getMiniappHome: async () => ({ categories: [], allTests: [], focusTests: [], recommendedTests: [] }),
      updateTestHomeDisplay: async (id, homeDisplay, homeSort) => {
        await gate
        rows = rows.map((row) => row.id === id ? { ...row, homeDisplay, homeSort } : row)
      },
    },
    '../auth': { isReadOnly: () => false },
    '../composables/useUnsavedChanges': { useUnsavedChanges: () => ({ markDirty() {}, markSaved() {} }) },
  },
})
await home.load()
assert.equal(home.page.value, 3, '入口题型应自动定位到所在页')
assert.ok(home.pagedTests.value.some((row) => row.id === 24))
assert.equal(home.pagedTests.value.length, 10)
assert.equal(home.pageCount.value, 6)
home.changePage(-2)
const first = home.pagedTests.value[0]
first.homeSort = 123
home.markRowDirty(first.id)
home.changePage(1)
const second = home.pagedTests.value[0]
second.homeDisplay = 2
home.markRowDirty(second.id)
home.changePage(-1)
assert.equal(home.pagedTests.value[0].homeSort, 123, '翻页后保留原行草稿')
assert.equal(home.dirtyIds.value.size, 2, '未保存统计覆盖所有页面')
const pendingSave = home.save(first)
home.changePage(1)
home.changePageSize({ target: { value: '20' } })
assert.equal(home.page.value, 1, '保存中禁止切页')
assert.equal(home.pageSize.value, 10, '保存中禁止改变每页条数')
finishUpdate()
await pendingSave
assert.equal(home.tests.value[0].homeSort, 123, '保存行使用服务端最新值')
assert.equal(home.tests.value[10].homeDisplay, 2, '保存刷新不能覆盖其它页面草稿')
assert.equal(home.dirtyIds.value.size, 1)
for (const size of [20, 50, 10]) {
  home.changePageSize({ target: { value: String(size) } })
  assert.equal(home.page.value, 1)
  assert.equal(home.pagedTests.value.length, size)
  assert.equal(home.tests.value[10].homeDisplay, 2)
}
home.changePage(999)
assert.equal(home.page.value, 6)
assert.equal(home.pagedTests.value.length, 5)
home.mergeSavedRows(rows.slice(0, 12))
assert.equal(home.page.value, 2, '数据减少应收敛至最后有效页')
home.mergeSavedRows([])
assert.equal(home.page.value, 1)
assert.equal(home.pageCount.value, 1)
assert.equal(home.pagedTests.value.length, 0)
console.log('home pagination: OK')
