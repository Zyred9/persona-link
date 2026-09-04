const assert = require('node:assert');
const { execFileSync } = require('node:child_process');
const fs = require('node:fs');
const path = require('node:path');

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

console.log(`MINIAPP_SELF_CHECK_OK routes=${routes.length} js=${javaScriptFiles.length} json=${jsonFiles.length}`);
