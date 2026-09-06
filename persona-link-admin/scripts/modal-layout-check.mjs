import assert from 'node:assert/strict'
import { readFileSync, readdirSync } from 'node:fs'
import { parse } from '@vue/compiler-sfc'
import postcss from 'postcss'

const css = postcss.parse(readFileSync(new URL('../src/styles.css', import.meta.url), 'utf8'))
function declarations(selector) {
  const values = {}
  css.walkRules(selector, (rule) => {
    if (rule.parent.type !== 'root') return
    rule.walkDecls((decl) => { values[decl.prop] = decl.value })
  })
  return values
}
const backdrop = declarations('.admin-modal-backdrop')
assert.equal(backdrop.position, 'fixed')
assert.equal(backdrop.inset, '0')
assert.equal(backdrop['place-items'], 'center')
assert.ok(Number(backdrop['z-index']) > 50 && Number(backdrop['z-index']) < 60,
  '遮罩必须高于导航，但低于操作反馈')
const panel = declarations('.admin-modal-backdrop > .admin-editor-dialog')
assert.equal(panel['grid-template-rows'], 'auto minmax(0, 1fr) auto')
assert.equal(panel.height, 'auto')
assert.equal(panel.overflow, 'hidden')
assert.equal(declarations('.admin-modal-backdrop > :first-child')['max-height'],
  'calc(100dvh - 2 * var(--modal-gap))')

const expected = new Map([
  ['TypeManagementView.vue', 1], ['ResultRulesView.vue', 1],
  ['ReleasesView.vue', 2], ['SettingsView.vue', 1], ['AuditLogView.vue', 1],
])
for (const file of readdirSync(new URL('../src/views/', import.meta.url))) {
  if (!file.endsWith('.vue')) continue
  const source = readFileSync(new URL(`../src/views/${file}`, import.meta.url), 'utf8')
  const { descriptor } = parse(source)
  const template = descriptor.template?.content ?? ''
  assert.equal((template.match(/\badmin-modal-backdrop\b/g) ?? []).length, expected.get(file) ?? 0,
    `${file} 弹窗必须使用统一居中遮罩`)
  const isEditor = ['TypeManagementView.vue', 'ResultRulesView.vue', 'SettingsView.vue'].includes(file)
  assert.equal((template.match(/\badmin-editor-dialog\b/g) ?? []).length, isEditor ? 1 : 0)
  if (isEditor) {
    assert.match(template, /class="admin-dialog-header"/)
    assert.match(template, /class="admin-dialog-footer"/)
    assert.match(template, /class="admin-form-grid"/)
    assert.match(template, /class="drawer-body"/)
  }
  assert.doesNotMatch(template, /class="drawer-backdrop"|aria-label="[^"]*抽屉/)
  for (const style of descriptor.styles) {
    postcss.parse(style.content).walkRules((rule) => {
      assert.ok(!['.modal', '.drawer-backdrop', '.editor-drawer', '.rule-drawer'].includes(rule.selector),
        `${file} 不得用局部布局覆盖共用弹窗布局`)
    })
    assert.doesNotMatch(style.content, /slide-in/)
  }
}
console.log('MODAL_LAYOUT_CHECK_OK dialogs=6')
