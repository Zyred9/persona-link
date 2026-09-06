// 本地视觉检查：渲染真实弹窗模板与样式，使用样例数据，不连接业务接口。
import { createServer } from 'node:http'
import { readFileSync } from 'node:fs'
import { parse, compileStyle } from '@vue/compiler-sfc'
import { baseParse, compile } from '@vue/compiler-dom'

const views = ['views/TypeManagementView.vue', 'views/ResultRulesView.vue', 'views/SettingsView.vue', 'components/AiImageGenerationDialog.vue', 'views/HomeConfigView.vue']
const root = new URL('../', import.meta.url)
const sample = {
  drawerOpen: true, editingId: 1, editingRuleKey: 'example', uploadingCover: false, saving: false,
  editorLocked: false, selectedId: 1, showCreate: false,
  testName: '你是哪种短剧女主命？', prompt: '温暖手绘风、奶油黄背景、可爱职场人物，画面简洁、同一套人物与配色。',
  readOnly: false, busy: false, active: false, loading: false, error: '', statusText: '生成完成，待采用',
  resolution: { coverWidth: 800, coverHeight: 800, detailWidth: 1100, detailHeight: 500 },
  task: { id: 1, provider: 1, modelName: '视觉检查样例', taskStatus: 3,
    coverUrl: '/sample-cover.svg', detailImageUrl: '/sample-detail.svg' },
  editing: { username: 'content_editor', displayName: '内容运营', roleType: 2, status: 1 },
  createForm: { username: '', password: '', displayName: '', roleType: 2, status: 1 },
  categories: [{ id: 1, categoryName: '日常偏好' }],
  dimensions: [{ id: 1, name: '决策风格', rules: [] }], selected: { name: '决策风格', rules: [{}] },
  form: { name: '日常决策偏好测试', categoryId: 1, type: '单人测试', status: '启用',
    description: '看看你在日常选择中的自然倾向。', coverUrl: '', detailImageUrl: '',
    drawQuestionCount: 5, estimatedMinutes: 3, dimensions: [{ id: 1, dimensionName: '决策风格' }],
    min: 0, max: 50, copy: '你习惯先整理信息，再做出自己的选择。', deepCopy: '', shareCopy: '', position: 1 },
}
function findModal(node) {
  if (node.type === 1 && node.props.some((prop) => prop.name === 'class' && prop.value?.content.includes('admin-modal-backdrop'))) return node
  for (const child of node.children ?? []) { const found = findModal(child); if (found) return found }
}
createServer((request, response) => {
  if (request.url === '/sample-cover.svg' || request.url === '/sample-detail.svg') {
    const [width, height] = request.url === '/sample-cover.svg' ? [800, 800] : [1100, 500]
    response.setHeader('Content-Type', 'image/svg+xml')
    response.end(`<svg xmlns="http://www.w3.org/2000/svg" width="${width}" height="${height}" viewBox="0 0 ${width} ${height}"><rect width="100%" height="100%" rx="24" fill="#eee2ff"/><text x="50%" y="50%" dominant-baseline="middle" text-anchor="middle" font-family="sans-serif" font-size="40" fill="#534365">${width} × ${height} 布局占位</text></svg>`)
    return
  }
  if (request.url === '/vue.js') {
    response.setHeader('Content-Type', 'text/javascript')
    response.end(readFileSync(new URL('node_modules/vue/dist/vue.global.prod.js', root)))
    return
  }
  const index = Number(new URL(request.url, 'http://localhost').searchParams.get('view') || 0)
  if (!views[index]) { response.writeHead(404); response.end(); return }
  const { descriptor } = parse(readFileSync(new URL(`src/${views[index]}`, root), 'utf8'))
  const modal = index === 4 ? null : findModal(baseParse(descriptor.template.content))
  const pageClass = ['type-page', 'rules-page', 'settings-view', 'image-preview-page', 'home-preview-page'][index]
  const render = compile(`<section class="${pageClass}">${index === 4 ? descriptor.template.content : modal.loc.source}</section>`, { mode: 'function' }).code
  const scopeId = 'data-v-layout-preview'
  const styles = readFileSync(new URL('src/styles.css', root), 'utf8') + descriptor.styles.map((style) =>
    compileStyle({ source: style.content, filename: views[index], id: scopeId, scoped: style.scoped }).code).join('\n')
  response.setHeader('Content-Type', 'text/html; charset=utf-8')
  response.end(`<!doctype html><html lang="zh-CN"><meta name="viewport" content="width=device-width,initial-scale=1"><title>编辑表单布局检查</title><style>${styles}\n.home-preview-page{padding:32px}</style>
    <div id="app"></div><script src="/vue.js"></script><script>
    const sample = ${JSON.stringify(sample)};
    const handlers = Object.fromEntries(['closeDrawer','saveItem','markDirty','handleImageUpload','addDimension','moveDimension','removeDimension','markRuleFormDirty','changeDimension','removeRule','saveRule','saveAccount','createAccount','emit','generate','apply','refresh'].map(name => [name, () => {}]));
    Vue.createApp({ __scopeId: '${scopeId}', render: new Function('Vue', ${JSON.stringify(render)})(Vue),
      setup: () => {
        const result = { ...Vue.toRefs(Vue.reactive(sample)), ...handlers, assetUrl: value => value };
        if (${index} === 4) {
          const tests=Vue.ref(Array.from({length:123},(_,i)=>({id:i+1,testName:'职场沟通风格测试 '+(i+1),testType:1,status:1,currentVersionNo:2,coverUrl:'/sample-cover.svg',homeDisplay:0,homeSort:i})));
          const page=Vue.ref(1),pageSize=Vue.ref(10),listScroll=Vue.ref(null);
          Object.assign(result,{tests,page,pageSize,listScroll,pageCount:Vue.computed(()=>Math.ceil(tests.value.length/pageSize.value)),pagedTests:Vue.computed(()=>tests.value.slice((page.value-1)*pageSize.value,page.value*pageSize.value)),loading:false,message:'',savingId:null,targetId:0,dirtyIds:new Set(),preview:null,previewCategories:[],previewTests:[],selectedCategoryId:'all',isRowDirty:()=>false,markRowDirty:()=>{},save:()=>{},changePage:offset=>{page.value+=offset;if(listScroll.value)listScroll.value.scrollTop=0},changePageSize:event=>{pageSize.value=Number(event.target.value);page.value=1;if(listScroll.value)listScroll.value.scrollTop=0}});
        }
        return result;
      } }).mount('#app');
    </script></html>`)
}).listen(8765, '127.0.0.1', () => console.log('Layout preview: http://127.0.0.1:8765/?view=0 (0=题型, 1=结果规则, 2=账号, 3=AI生图, 4=首页分页)'))
