const assert = require('node:assert');
const { execFileSync } = require('node:child_process');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const root = path.resolve(__dirname, '..');

function walk(directory) {
  return fs.readdirSync(directory, { withFileTypes: true }).flatMap((entry) => {
    const target = path.join(directory, entry.name);
    return entry.isDirectory() ? walk(target) : [target];
  });
}

const files = walk(root).filter((file) => !file.includes(`${path.sep}scripts${path.sep}`));
const javaScriptFiles = files.filter((file) => file.endsWith('.js'));
const jsonFiles = files.filter((file) => file.endsWith('.json'));

javaScriptFiles.forEach((file) => execFileSync(process.execPath, ['--check', file], { stdio: 'pipe' }));
jsonFiles.forEach((file) => JSON.parse(fs.readFileSync(file, 'utf8')));

const appConfig = JSON.parse(fs.readFileSync(path.join(root, 'app.json'), 'utf8'));
const routes = [
  ...appConfig.pages,
  ...appConfig.subpackages.flatMap((subpackage) =>
    subpackage.pages.map((page) => `${subpackage.root}/${page}`))
];

const coreRoutes = [
  'pages/consent/index',
  'pages/home/index',
  'pages/profile/index',
  'subpackages/account/pages/history/index',
  'subpackages/test/pages/detail/index',
  'subpackages/test/pages/quiz/index',
  'subpackages/test/pages/result/index',
  'subpackages/pair/pages/detail/index',
  'subpackages/pair/pages/invite/index',
  'subpackages/pair/pages/join/index',
  'subpackages/pair/pages/wait/index',
  'subpackages/pair/pages/result/index'
];

coreRoutes.forEach((route) => assert(routes.includes(route), `核心路由未注册：${route}`));

routes.forEach((route) => {
  ['js', 'json', 'wxml', 'wxss'].forEach((extension) => {
    const file = path.join(root, `${route}.${extension}`);
    if (!fs.existsSync(file)) {
      throw new Error(`路由文件缺失：${path.relative(root, file)}`);
    }
  });
});

files.filter((file) => file.endsWith('.wxml')).forEach((wxmlFile) => {
  const javaScriptFile = wxmlFile.replace(/\.wxml$/, '.js');
  if (!fs.existsSync(javaScriptFile)) {
    return;
  }
  const wxml = fs.readFileSync(wxmlFile, 'utf8');
  const javaScript = fs.readFileSync(javaScriptFile, 'utf8');
  const handlers = [...wxml.matchAll(/\b(?:bind|catch)(?::?[a-z-]+)="([A-Za-z_$][\w$]*)"/g)]
    .map((match) => match[1]);
  handlers.forEach((handler) => {
    assert(
      new RegExp(`\\b${handler}\\s*\\(`).test(javaScript),
      `事件方法缺失：${path.relative(root, wxmlFile)} -> ${handler}`
    );
  });
});

const endpointContracts = [
  ['pages/home/index.js', '/api/miniapp/home'],
  ['components/test-detail/index.js', '/api/miniapp/tests/'],
  ['components/quiz-runner/index.js', '/answers'],
  ['subpackages/test/pages/result/index.js', '/api/miniapp/reports/'],
  ['subpackages/pair/pages/invite/index.js', '/api/miniapp/pairs'],
  ['subpackages/pair/pages/join/index.js', '/api/miniapp/pairs/join'],
  ['subpackages/pair/pages/wait/index.js', '/cancel'],
  ['subpackages/pair/pages/result/index.js', '/report'],
  ['utils/analytics.js', '/api/miniapp/events/batch']
];

endpointContracts.forEach(([file, endpoint]) => {
  const source = fs.readFileSync(path.join(root, file), 'utf8');
  assert(source.includes(endpoint), `接口链路缺失：${file} -> ${endpoint}`);
});

const homeSource = fs.readFileSync(path.join(root, 'pages/home/index.js'), 'utf8');
const homeTemplate = fs.readFileSync(path.join(root, 'pages/home/index.wxml'), 'utf8');
const tabBarSource = fs.readFileSync(path.join(root, 'custom-tab-bar/index.js'), 'utf8');
const tabBarTemplate = fs.readFileSync(path.join(root, 'custom-tab-bar/index.wxml'), 'utf8');
const profileTemplate = fs.readFileSync(path.join(root, 'pages/profile/index.wxml'), 'utf8');
const profileSource = fs.readFileSync(path.join(root, 'pages/profile/index.js'), 'utf8');
const accountSource = fs.readFileSync(path.join(root, 'components/account-center/index.js'), 'utf8');
const accountTemplate = fs.readFileSync(path.join(root, 'components/account-center/index.wxml'), 'utf8');
const detailSource = fs.readFileSync(path.join(root, 'components/test-detail/index.js'), 'utf8');
const quizRunnerSource = fs.readFileSync(path.join(root, 'components/quiz-runner/index.js'), 'utf8');
const quizTemplate = fs.readFileSync(
  path.join(root, 'subpackages/test/pages/quiz/index.wxml'),
  'utf8'
);
const singleDetailTemplate = fs.readFileSync(
  path.join(root, 'subpackages/test/pages/detail/index.wxml'),
  'utf8'
);
const pairDetailTemplate = fs.readFileSync(
  path.join(root, 'subpackages/pair/pages/detail/index.wxml'),
  'utf8'
);
const openTestSource = homeSource.slice(
  homeSource.indexOf('openTest(event)'),
  homeSource.indexOf('continueAssessment()')
);
const continueAssessmentSource = homeSource.slice(
  homeSource.indexOf('continueAssessment()'),
  homeSource.indexOf('closeQuiz()')
);
const accountNavigationSource = homeSource.slice(
  homeSource.indexOf('switchRootTab(index)'),
  homeSource.indexOf('openTest(event)')
);
assert(openTestSource.includes('detailVisible: true'), '首页题型未在当前页打开');
assert(!openTestSource.includes('wx.navigateTo'), '首页题型仍会创建新 PageFrame');
assert(homeTemplate.includes('<page-container'), '首页缺少详情页面容器');
assert(homeTemplate.includes('<test-detail'), '首页未复用题型详情组件');
assert(homeSource.includes('categories.length > 1'), '首页未在仅有业务分类时展示分类栏');
assert(homeSource.includes("'/subpackages/test/pages/detail/index'"), '单人详情分享路由缺失');
assert(homeSource.includes("'/subpackages/pair/pages/detail/index'"), '双人详情分享路由缺失');
assert(singleDetailTemplate.includes('<test-detail'), '单人详情路由未复用详情组件');
assert(pairDetailTemplate.includes('<test-detail'), '双人详情路由未复用详情组件');
assert(detailSource.includes('requestVersion'), '详情请求缺少会话隔离');
assert(detailSource.includes('Number(test.testType) !== expectedTestType'), '详情路由未校验题型');
assert(continueAssessmentSource.includes('this.openQuiz(assessment)'), '首页继续答题未复用当前答卷');
assert(!continueAssessmentSource.includes('wx.navigateTo'), '首页继续答题仍会创建新 PageFrame');
assert(homeTemplate.includes("containerMode === 'quiz'"), '首页缺少同页答题容器');
assert(homeTemplate.includes('<quiz-runner'), '首页未复用答题组件');
assert(quizTemplate.includes('<quiz-runner'), '独立答题路由未复用答题组件');
assert(quizRunnerSource.includes('source.assessment'), '答题组件未复用预加载答卷');
const homeContext = {
  require(moduleName) {
    if (moduleName.endsWith('/image')) return require('../utils/image');
    if (moduleName.endsWith('/env')) return { getApiBaseUrl: () => 'http://127.0.0.1:8080' };
    return {};
  },
  Page() {}
};
vm.runInNewContext(`${homeSource}\nglobalThis.resolveImageUrlForCheck = resolveImageUrl;`, homeContext);
assert.strictEqual(
  homeContext.resolveImageUrlForCheck('https://pgcloud.aitici.com/common/cover.png'),
  'https://pgcloud.aitici.com/common/cover.png?x-oss-process=image/resize,w_1200/format,webp',
  'OSS 首页图片未生成缩放后的 WebP 地址'
);
assert.strictEqual(
  homeContext.resolveImageUrlForCheck('https://example.com/cover.png'),
  'https://example.com/cover.png',
  '非项目 OSS 图片不应改写'
);
assert((homeTemplate.match(/lazy-load="{{true}}"/g) || []).length === 2, '首页非首屏图片未启用懒加载');
assert(
  fs.statSync(path.join(root, 'assets/images/home-title.png')).size < 600 * 1024,
  '首页标题整图体积回退，可能再次阻塞页面交互'
);
assert(
  appConfig.preloadRule['pages/home/index'].packages.includes('subpackages/account')
    && appConfig.preloadRule['pages/profile/index'].packages.includes('subpackages/account'),
  '账户分包未在首页和我的页预加载'
);
assert(tabBarTemplate.includes('bindtap="switchTab"'), '底部导航点击入口缺失');
assert(tabBarSource.includes('currentPage.switchRootTab(index)'), '首页底部导航未复用当前 PageFrame');
let tabBarDefinition;
let switchedRootTab = null;
let nativeTabUrl = '';
const currentPage = { route: 'pages/home/index', switchRootTab: (index) => { switchedRootTab = index; } };
vm.runInNewContext(tabBarSource, {
  Component(value) { tabBarDefinition = value; },
  getCurrentPages: () => [currentPage],
  wx: { switchTab: ({ url }) => { nativeTabUrl = url; } }
});
tabBarDefinition.methods.switchTab.call(
  { data: tabBarDefinition.data },
  { currentTarget: { dataset: { index: 1 } } }
);
assert.strictEqual(switchedRootTab, 1, '首页点击我的未同步切换当前 PageFrame');
assert.strictEqual(nativeTabUrl, '', '首页点击我的仍调用了原生页面路由');
assert(accountNavigationSource.includes("containerMode: 'account'"), '首页未在当前 PageFrame 打开我的页');
assert(!accountNavigationSource.includes('wx.switchTab'), '首页打开我的页仍会创建新 PageFrame');
assert(homeTemplate.includes('<account-center'), '首页未挂载共享账户中心');
assert(homeSource.includes('reopenAccountAfterLeave'), '账户子视图未保留系统返回层级');
assert(homeSource.includes('accountVisible ? 1 : 0'), '首页恢复前台时未保持我的页选中态');
assert(profileTemplate.includes('<account-center'), '我的页路由未复用共享账户中心');
assert(profileTemplate.includes('<page-container'), '我的页缺少系统返回容器');
assert(profileSource.includes('reopenAccountAfterLeave'), '我的页子视图未保留系统返回层级');
['history', 'feedback', 'settings'].forEach((view) => {
  assert(accountTemplate.includes(`data-view="${view}"`), `账户中心入口缺失：${view}`);
  const wrapper = fs.readFileSync(
    path.join(root, `subpackages/account/pages/${view}/index.wxml`),
    'utf8'
  );
  assert(new RegExp(`<account-center\\b[^>]*initial-view="${view}"[^>]*>`).test(wrapper), `账户页未复用共享组件：${view}`);
});
assert(accountSource.includes('this.setCurrentView(view)'), '账户中心未在当前 PageFrame 切换视图');

console.log(`MINIAPP_SELF_CHECK_OK routes=${routes.length} js=${javaScriptFiles.length} json=${jsonFiles.length}`);
