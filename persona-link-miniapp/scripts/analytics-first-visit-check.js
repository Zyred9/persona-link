const assert = require('assert');
const fs = require('fs');
const path = require('path');
const vm = require('vm');

const storage = new Map();
const requests = [];
const app = { globalData: { hasConsent: false } };
let loginCount = 0;
let finishLogin;
const wx = {
  getStorageSync: (key) => storage.get(key),
  setStorageSync: (key, value) => storage.set(key, value),
  removeStorageSync: (key) => storage.delete(key),
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

async function main() {
  await analytics.trackEvent(2, '/pages/home/index');
  home.onShow();
  assert.strictEqual(loginCount, 0, '未同意隐私时不应登录或上报');
  assert.strictEqual(requests.length, 0);
  assert.strictEqual(homeLoads, 0);
  app.globalData.hasConsent = true;
  await home.onShow(); // 仅等待协议校验，不能等待尚未完成的登录上报。
  assert.strictEqual(homeLoads, 1, '登录完成前首页数据应立即开始加载');
  const start = request.authenticatedRequestData({ url: '/api/miniapp/assessments', method: 'POST' });
  assert.strictEqual(loginCount, 1, '并发统计与答题应共用一次登录');
  finishLogin();
  await Promise.all([firstVisit, start]);
  const events = requests.filter((item) => item.url.endsWith('/events/batch'));
  assert.strictEqual(events.length, 1, '无旧 token 的首次首页访问必须上报');
  assert.strictEqual(events[0].header.Authorization, 'Bearer test-token');
  await analytics.trackEvent(2, '/pages/home/index');
  assert.strictEqual(loginCount, 1, '后续访问复用已登录会话');
  storage.set(request.TOKEN_STORAGE_KEY, 'expired-token');
  const staleA = request.authenticatedRequestData({ url: '/expired-a' });
  const staleB = request.authenticatedRequestData({ url: '/expired-b' });
  await new Promise(setImmediate);
  const failedRequests = requests.filter((item) => item.header.Authorization === 'Bearer expired-token');
  assert.strictEqual(failedRequests.length, 2);
  failedRequests[0].success({ statusCode: 401, data: { message: 'expired' } });
  await new Promise(setImmediate);
  assert.strictEqual(loginCount, 2, '首个过期请求触发一次刷新登录');
  finishLogin();
  await staleA;
  failedRequests[1].success({ statusCode: 401, data: { message: 'expired' } });
  await new Promise(setImmediate);
  assert.strictEqual(storage.get(request.TOKEN_STORAGE_KEY), 'test-token', '延迟返回的旧 401 不能清除新会话');
  assert.strictEqual(loginCount, 2, '旧 401 应复用新会话，不能再次登录');
  await staleB;
  console.log('ANALYTICS_FIRST_VISIT_CHECK_OK');
}

main().catch((error) => { console.error(error); process.exitCode = 1; });
