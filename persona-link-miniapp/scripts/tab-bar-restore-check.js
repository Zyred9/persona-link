const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const root = path.join(__dirname, '..');
const tabBarPatches = [];
let page;

vm.runInNewContext(fs.readFileSync(path.join(root, 'pages/home/index.js'), 'utf8'), {
  Page(value) { page = value; },
  require() {
    return {
      request: async () => ({ code: 0, data: {} }),
      authenticatedRequestData: async () => ({}),
      TOKEN_STORAGE_KEY: 'personaLinkBusinessToken',
      trackEvent() {},
      resolveImageUrl: (value) => value || ''
    };
  }
});

page.data = Object.assign({}, page.data);
page.setData = function (value) { Object.assign(this.data, value); };
page.getTabBar = () => ({ setData: (value) => tabBarPatches.push(value) });

function lastHidden() {
  return tabBarPatches.length ? tabBarPatches[tabBarPatches.length - 1].hidden : undefined;
}

page.openTest({ currentTarget: { dataset: { id: 12, type: 1 } } });
assert.equal(lastHidden(), true, '打开详情必须立即隐藏底部导航');
assert.equal(page.data.containerVisible, true);

tabBarPatches.length = 0;
page.closeTestDetail();
assert.equal(lastHidden(), false, '详情返回必须同步恢复底部导航，不能等待离场动画');
assert.equal(page.data.containerVisible, false);

page.openQuiz({ answerSessionId: 'session-1', answerType: 1 });
assert.equal(lastHidden(), true, '进入答题必须立即隐藏底部导航');

tabBarPatches.length = 0;
page.closeQuiz();
assert.equal(lastHidden(), false, '答题返回必须同步恢复底部导航，不能等待离场动画');
assert.equal(page.data.containerVisible, false);

page.openTest({ currentTarget: { dataset: { id: 12, type: 1 } } });
tabBarPatches.length = 0;
page.clearTestDetail();
assert.equal(lastHidden(), false, '外部回首页重置详情同样必须恢复底部导航');
assert.equal(page.data.containerVisible, false);
assert.equal(page.data.detailVisible, false);
assert.equal(page.data.activeTestId, '');

const template = fs.readFileSync(path.join(root, 'custom-tab-bar/index.wxml'), 'utf8');
assert.ok(!template.includes('wx:if'), '自定义 tabBar 必须常驻渲染，避免返回时重新挂载闪烁');
assert.ok(template.includes('tab-bar--hidden'), '自定义 tabBar 显隐必须走 class');
const style = fs.readFileSync(path.join(root, 'custom-tab-bar/index.wxss'), 'utf8');
assert.match(style, /\.tab-bar\.tab-bar--hidden \{\s*display: none;\s*\}/, '自定义 tabBar 隐藏态必须 display: none，且选择器权重高于 .tab-bar');

console.log('TAB_BAR_RESTORE_CHECK_OK detail-open-hide/detail-close-restore/quiz-close-restore/reset-restore/class-hidden');
