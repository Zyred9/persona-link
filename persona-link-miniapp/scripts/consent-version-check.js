const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const source = (file) => fs.readFileSync(path.join(__dirname, '..', file), 'utf8');
const versions = [1, 2, 3].map((type) => ({ type, version: 1 }));
const storage = new Map();
let app, reply = () => Promise.resolve(versions), checks = 0, logins = 0;
let currentPage = { route: 'subpackages/pair/pages/join/index', options: { code: 'ABCDE' } };
const redirects = [], calls = [];
const wx = {
  getStorageSync: (key) => storage.get(key),
  setStorageSync: (key, value) => storage.set(key, value),
  reLaunch: (options) => { redirects.push(options.url); if (options.complete) options.complete(); },
  switchTab: (options) => { redirects.push(options.url); if (options.complete) options.complete(); },
  login: (options) => { logins += 1; options.success({ code: 'code' }); },
  request: (options) => {
    const url = new URL(options.url).pathname;
    calls.push({ url, method: options.method || 'GET' });
    if (url === '/api/miniapp/auth/wechat') {
      options.success({ statusCode: 200, data: { code: 0, data: { token: 'token' } } });
      return;
    }
    options.success({ statusCode: 200, data: { code: 0, data: ['allowed'] } });
  }
};
const requestDataMock = (options) => {
  checks += 1;
  if (options.url === '/api/miniapp/legal-documents/versions') return reply();
  return Promise.resolve({ type: Number(options.url.slice(-1)), version: 1, content: '协议正文' });
};
vm.runInNewContext(source('app.js'), {
  App(definition) { app = definition; }, wx,
  getCurrentPages: () => [currentPage],
  require: () => ({ requestData: requestDataMock })
});
const requestModule = { exports: {} };
vm.runInNewContext(source('utils/request.js'), {
  module: requestModule, wx, getApp: () => app, require: () => ({ getApiBaseUrl: () => 'https://test.invalid' })
});
const api = requestModule.exports;
let consent;
vm.runInNewContext(source('pages/consent/index.js'), {
  Page(definition) { consent = definition; }, wx, getApp: () => app,
  require: () => ({ requestData: requestDataMock })
});
consent.setData = (patch) => Object.assign(consent.data, patch);

async function flush() { await new Promise(setImmediate); await new Promise(setImmediate); }

async function main() {
  // 1. 未同意协议：校验失败、重定向协议页，不登录、不发业务请求。
  await assert.rejects(app.verifyConsent(), /协议/);
  assert.equal(app.globalData.hasConsent, false);
  assert.equal(logins, 0, '协议校验不得触发登录');
  assert.equal(redirects.at(-1), '/pages/consent/index');
  const callsBefore = calls.length;
  await assert.rejects(api.authenticatedRequestData({ url: '/api/miniapp/assessments' }), /协议/);
  assert.equal(calls.length, callsBefore, '未同意协议不得发起业务请求');
  assert.equal(logins, 0, '未同意协议不得静默登录');

  // 2. 同意后：校验只依赖本地同意记录，业务请求才静默建立会话。
  storage.set('personaLinkConsentVersion', 'v2.0');
  storage.set('personaLinkConsentedDocuments', versions);
  checks = 0;
  let resolve;
  reply = () => new Promise((done) => { resolve = done; });
  const launch = app.verifyConsent();
  const business = api.authenticatedRequestData({ url: '/api/miniapp/assessments' });
  await flush();
  assert.equal(checks, 1, '启动与业务共享一次版本校验');
  assert.equal(logins, 0, '协议校验完成前不得登录');
  resolve(versions);
  await launch;
  assert.deepEqual(await business, ['allowed']);
  assert.equal(logins, 1, '业务请求静默登录');
  assert.equal(app.globalData.hasConsent, true);
  assert.equal(checks, 1, '同一轮校验不重复请求版本');

  // 3. 版本更新：拦截业务、保留分享路径并重定向协议页。
  reply = () => Promise.resolve(versions.map((document) => ({ ...document, version: 2 })));
  const before = calls.length;
  await assert.rejects(api.authenticatedRequestData({ url: '/api/miniapp/events/batch' }), /协议已更新/);
  assert.equal(calls.length, before, '版本更新不得上报或执行业务');
  assert.equal(app.globalData.hasConsent, false);
  assert.equal(app.globalData.pendingLaunchUrl, '/subpackages/pair/pages/join/index?code=ABCDE');
  assert.equal(redirects.at(-1), '/pages/consent/index');

  // 4. 已同意用户打开协议页：不重复展示，直接回到分享路径。
  reply = () => Promise.resolve(versions);
  const navigationCount = redirects.length;
  consent.onLoad();
  consent.onShow();
  await flush();
  assert.equal(redirects.length, navigationCount + 1);
  assert.equal(redirects.at(-1), '/subpackages/pair/pages/join/index?code=ABCDE');

  // 5. 协议失效：加载全文后同意即可放行，全程无需登录。
  storage.delete('personaLinkConsentVersion');
  storage.delete('personaLinkConsentedDocuments');
  app.globalData.pendingLaunchUrl = '';
  currentPage = { route: 'pages/home/index', options: {} };
  consent.started = false;
  consent.loading = false;
  consent.onShow();
  await flush();
  assert.equal(consent.data.state, 'ready');
  assert.equal(logins, 1, '阅读并同意协议不得额外触发登录');
  await consent.agreeAndContinue();
  assert.equal(app.globalData.hasConsent, true);
  assert.equal(storage.get('personaLinkConsentVersion'), 'v2.0');
  assert.equal(redirects.at(-1), '/pages/home/index');

  // 6. 网络失败不得放行。
  reply = () => Promise.reject(new Error('offline'));
  await assert.rejects(app.verifyConsent(), /offline/);
  assert.equal(app.globalData.hasConsent, false, '网络失败不得放行');

  // 7. 旧校验不得撤销新同意。
  reply = () => new Promise((done) => { resolve = done; });
  const stale = app.verifyConsent();
  await flush();
  app.consentEpoch = (app.consentEpoch || 0) + 1;
  app.globalData.hasConsent = true;
  resolve(versions);
  await assert.rejects(stale, /协议状态已变化/);
  assert.equal(app.globalData.hasConsent, true, '旧校验不得撤销新同意');
  console.log('CONSENT_VERSION_CHECK_OK guest-first/silent-login/version-gate/share-return');
}
main().catch((error) => { console.error(error); process.exitCode = 1; });
