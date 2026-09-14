const assert = require('assert');
const fs = require('fs');
const path = require('path');
const vm = require('vm');

const storage = new Map();
const requests = [];
const app = { globalData: { hasConsent: false } };
let loginCount = 0;
let finishLogin;
let cancellation;
const wx = {
  getStorageSync: (key) => storage.get(key),
  setStorageSync: (key, value) => storage.set(key, value),
  removeStorageSync: (key) => storage.delete(key),
  clearStorageSync: () => storage.clear(),
  showModal: (options) => { cancellation = options.success({ confirm: true }); },
  showToast() {},
  reLaunch: () => home.onShow(),
  login(options) { loginCount += 1; finishLogin = () => options.success({ code: 'test-code' }); },
  getAccountInfoSync: () => ({ miniProgram: { version: 'test' } }),
  getLaunchOptionsSync: () => ({ scene: 1001 }),
  request(options) {
    requests.push(options);
    if (options.header.Authorization === 'Bearer expired-token') return;
    options.success({ statusCode: 200, data: { code: 0, data: options.url.endsWith('/auth/wechat') ? { token: 'test-token' } : {} } });
  }
};

function load(relative, requireModule) {
  const context = { module: { exports: {} }, require: requireModule, wx, getApp: () => app };
  vm.runInNewContext(fs.readFileSync(path.join(__dirname, relative), 'utf8'), context);
  return context.module.exports;
}

const request = load('../utils/request.js', () => ({ getApiBaseUrl: () => 'http://test.invalid' }));
const analytics = load('../utils/analytics.js', () => request);
let home;
let firstVisit;
let homeLoads = 0;
vm.runInNewContext(fs.readFileSync(path.join(__dirname, '../pages/home/index.js'), 'utf8'), {
  wx, getApp: () => app, Page: (definition) => { home = definition; },
  require(name) {
    if (name.endsWith('/request')) return request;
    if (name.endsWith('/analytics')) return { trackEvent(...args) { firstVisit = analytics.trackEvent(...args); } };
    return { getApiBaseUrl: () => 'http://test.invalid' };
  }
});
home.loadHome = () => { homeLoads += 1; };
home.loadCurrentAssessment = () => {};
home.clearTestDetail = () => {};
let privacy;
vm.runInNewContext(fs.readFileSync(path.join(__dirname, '../subpackages/account/pages/privacy/index.js'), 'utf8'), {
  wx, getApp: () => app, Page: (definition) => { privacy = definition; }, require: () => request
});
privacy.setData = (patch) => Object.assign(privacy.data, patch);

async function main() {
  await analytics.trackEvent(2, '/pages/home/index');
  await home.onShow();
  assert.strictEqual(loginCount, 0, '未同意隐私时不应登录或上报');
  assert.strictEqual(requests.length, 0);
  assert.strictEqual(homeLoads, 1, '未同意也可浏览公开首页');
  app.globalData.hasConsent = true;
  await analytics.trackEvent(2, '/pages/home/index');
  assert.strictEqual(loginCount, 0, '统计不能偷偷创建登录会话');
  const login = request.loginSession();
  const duplicateLogin = request.loginSession();
  assert.strictEqual(loginCount, 1, '重复点击共用一次微信登录');
  finishLogin();
  await Promise.all([login, duplicateLogin]);
  await home.onShow(); // 仅等待协议校验，不能等待尚未完成的登录上报。
  assert.strictEqual(homeLoads, 2, '完成登录后加载首页');
  const start = request.authenticatedRequestData({ url: '/api/miniapp/assessments', method: 'POST' });
  assert.strictEqual(loginCount, 1, '并发统计与答题复用已建立的会话');
  await Promise.all([firstVisit, start]);
  const events = requests.filter((item) => item.url.endsWith('/events/batch'));
  assert.strictEqual(events.length, 1, '无旧 token 的首次首页访问必须上报');
  assert.strictEqual(events[0].header.Authorization, 'Bearer test-token');
  await analytics.trackEvent(2, '/pages/home/index');
  assert.strictEqual(loginCount, 1, '后续访问复用已登录会话');
  storage.set(request.TOKEN_STORAGE_KEY, 'expired-token');
  const staleA = assert.rejects(request.authenticatedRequestData({ url: '/expired-a' }), /expired/);
  const staleB = assert.rejects(request.authenticatedRequestData({ url: '/expired-b' }), /expired/);
  await new Promise(setImmediate);
  const failedRequests = requests.filter((item) => item.header.Authorization === 'Bearer expired-token');
  assert.strictEqual(failedRequests.length, 2);
  failedRequests[0].success({ statusCode: 401, data: { message: 'expired' } });
  await new Promise(setImmediate);
  assert.strictEqual(loginCount, 1, '过期请求不能自动刷新登录');
  const relogin = request.loginSession();
  finishLogin();
  await Promise.all([staleA, relogin]);
  failedRequests[1].success({ statusCode: 401, data: { message: 'expired' } });
  await new Promise(setImmediate);
  assert.strictEqual(storage.get(request.TOKEN_STORAGE_KEY), 'test-token', '延迟返回的旧 401 不能清除新会话');
  assert.strictEqual(loginCount, 2, '旧 401 应复用新会话，不能再次登录');
  await staleB;
  storage.set(request.TOKEN_STORAGE_KEY, 'expired-token');
  const expiredEvent = analytics.trackEvent(2, '/pages/home/index');
  requests.at(-1).success({ statusCode: 401, data: { message: 'expired' } });
  await expiredEvent;
  assert.strictEqual(loginCount, 2, '过期埋点不得重新登录');
  assert.strictEqual(storage.get(request.TOKEN_STORAGE_KEY), undefined);

  storage.set(request.TOKEN_STORAGE_KEY, 'test-token');
  storage.set('personaLinkConsentVersion', 'v2.0');
  const beforeCancel = requests.length;
  privacy.cancelAccount();
  await cancellation;
  await new Promise(setImmediate);
  assert.strictEqual(storage.get(request.TOKEN_STORAGE_KEY), undefined, '注销返回首页仍为游客');
  assert.strictEqual(loginCount, 2, '注销后的首页埋点不得建号');
  assert.deepStrictEqual(requests.slice(beforeCancel).map((item) => item.method + ' ' + new URL(item.url).pathname), ['DELETE /api/miniapp/me']);
  const pendingLogin = request.loginSession();
  request.clearAccountSession();
  const cancelledLogin = assert.rejects(pendingLogin, /登录已取消/);
  const beforeCallback = requests.length;
  finishLogin();
  await cancelledLogin;
  assert.strictEqual(requests.length, beforeCallback, '拒绝后旧微信回调不再提交登录');
  assert.strictEqual(storage.get(request.TOKEN_STORAGE_KEY), undefined);
  const liveRequest = wx.request;
  let finishSession;
  wx.request = (options) => { finishSession = () => options.success({ statusCode: 200, data: { code: 0, data: { token: 'late-token' } } }); };
  const pendingSession = request.loginSession();
  finishLogin();
  await new Promise(setImmediate);
  request.clearAccountSession();
  const cancelledSession = assert.rejects(pendingSession, /登录已取消/);
  finishSession();
  await cancelledSession;
  wx.request = liveRequest;
  assert.strictEqual(storage.get(request.TOKEN_STORAGE_KEY), undefined, '已发出的登录响应不得恢复身份');
  console.log('ANALYTICS_FIRST_VISIT_CHECK_OK guest/no-auto-login/expired-event/cancel-return');
}

const timeout = setTimeout(() => { console.error('FAIL analytics check timed out'); process.exit(1); }, 10000);
main().then(() => clearTimeout(timeout)).catch((error) => { clearTimeout(timeout); console.error(error); process.exitCode = 1; });
