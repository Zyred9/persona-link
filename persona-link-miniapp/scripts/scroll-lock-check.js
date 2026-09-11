const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const root = path.join(__dirname, '..');
const read = (file) => fs.readFileSync(path.join(root, file), 'utf8');

const lockedPages = [
  'subpackages/test/pages/quiz/index',
  'subpackages/test/pages/detail/index',
  'subpackages/pair/pages/detail/index',
  'subpackages/account/pages/feedback/index',
  'subpackages/account/pages/settings/index',
  'subpackages/account/pages/privacy/index',
  'subpackages/account/pages/review/index',
  'subpackages/pair/pages/join/index'
];
lockedPages.forEach((page) => {
  const config = JSON.parse(read(`${page}.json`));
  assert.equal(config.enablePullDownRefresh, false, `${page} 必须禁止下拉刷新`);
  assert.equal(config.disableScroll, true, `${page} 必须禁止页面滚动`);
  assert.match(read(`${page}.wxss`), /page\s*\{[^}]*height:\s*100%[^}]*overflow:\s*hidden/, `${page} 必须锁定页面高度`);
});

const homeTemplate = read('pages/home/index.wxml');
assert.match(homeTemplate, /catchtouchmove="blockContainerScroll"/, '首页容器必须锁定纵向滑动');
assert.match(homeTemplate, /bindscrolltolower="loadMoreAccountRecords"/, '首页容器必须保留触底分页接线');
assert.doesNotMatch(homeTemplate, /\bscroll-y\b/, '首页容器不允许纵向滚动');
assert.match(read('pages/home/index.js'), /blockContainerScroll\s*\(/, '首页容器缺少滚动拦截方法');

const profileTemplate = read('pages/profile/index.wxml');
assert.match(profileTemplate, /bindscrolltolower="loadMoreAccountRecords"/, '我的页必须保留触底分页接线');
assert.doesNotMatch(profileTemplate, /\bscroll-y\b/, '我的页不允许纵向滚动');

const quizTemplate = read('components/quiz-runner/index.wxml');
assert.match(quizTemplate, /class="paper-page quiz-page \{\{questionSlideClass\}\}"/);
assert.match(quizTemplate, /bindtouchstart="handleQuizTouchStart"/);
assert.match(quizTemplate, /bindtouchend="handleQuizTouchEnd"/);
const quizStyle = read('components/quiz-runner/index.wxss');
assert.match(quizStyle, /@keyframes quiz-panel-in-right/);
assert.match(quizStyle, /@keyframes quiz-panel-in-left/);

const legalConfig = JSON.parse(read('subpackages/account/pages/legal/index.json'));
assert.equal(legalConfig.enablePullDownRefresh, true, '协议页必须支持下拉刷新');
assert.notEqual(legalConfig.disableScroll, true, '协议页必须使用页面滚动才能触发下拉刷新');
assert.match(read('subpackages/account/pages/legal/index.js'), /onPullDownRefresh\s*\(/, '协议页缺少下拉刷新处理');

assert.match(
  read('components/account-center/index.wxss'),
  /\.history-page\s*\{[^}]*min-height:\s*0/,
  '测试记录内容未满屏时不得产生空滚动'
);

console.log('SCROLL_LOCK_CHECK_OK quiz/detail/profile/join/feedback/settings/privacy/home-container/quiz-swipe/legal-pull-down/history-no-empty-scroll');
