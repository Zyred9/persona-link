// 使用真实组件与状态模块的本地交互样例，不连接业务接口。
import { createServer } from 'node:http'
import { readFileSync } from 'node:fs'
import { parse, compileScript, compileStyle } from '@vue/compiler-sfc'
import ts from 'typescript'

const root = new URL('../', import.meta.url)
const { descriptor } = parse(readFileSync(new URL('src/components/ConfirmDialog.vue', root), 'utf8'))
const compiled = compileScript(descriptor, { id: 'confirm-preview', inlineTemplate: true })
const component = ts.transpileModule(compiled.content, { compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.ESNext } }).outputText
  .replaceAll('"vue"', '"/vue.js"').replaceAll("'vue'", "'/vue.js'")
  .replaceAll('../composables/useConfirm', '/confirm.js')
const state = ts.transpileModule(readFileSync(new URL('src/composables/useConfirm.ts', root), 'utf8'), { compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.ESNext } }).outputText
  .replaceAll('"vue"', '"/vue.js"').replaceAll("'vue'", "'/vue.js'")
const style = descriptor.styles.map(item => compileStyle({ source: item.content, id: 'data-v-confirm-preview', scoped: false }).code).join('\n')
createServer((request, response) => {
  const files = { '/component.js': component, '/confirm.js': state, '/vue.js': readFileSync(new URL('node_modules/vue/dist/vue.esm-browser.js', root), 'utf8') }
  if (files[request.url]) { response.setHeader('Content-Type', 'text/javascript'); response.end(files[request.url]); return }
  response.setHeader('Content-Type', 'text/html; charset=utf-8')
  response.end(`<!doctype html><html lang="zh-CN"><meta name="viewport" content="width=device-width, initial-scale=1"><title>二次确认交互样例</title><style>${readFileSync(new URL('src/styles.css', root), 'utf8')}\n${style}\nbody{padding:40px}button{padding:12px 18px}</style><div id="app"></div><script type="module">
import { createApp, h, ref } from '/vue.js';
import ConfirmDialog from '/component.js';
import { confirmAction } from '/confirm.js';
createApp({ setup() { const result=ref('尚未操作'); return () => h('main', [h('h1','心动测测 · 二次确认样例'), h('button',{onClick:async()=>{result.value=await confirmAction('确认删除选中的 3 个题型？删除后将不再向用户展示，请确认这些内容已不再需要。',{title:'删除选中的题型',confirmText:'确认删除',danger:true})?'已确认（样例，不删除数据）':'已取消'}},'测试删除确认'),h('p',{role:'status'},result.value),h(ConfirmDialog)]) }}).mount('#app');
</script></html>`)
}).listen(8765, '127.0.0.1', () => console.log('Confirmation preview: http://127.0.0.1:8765'))
