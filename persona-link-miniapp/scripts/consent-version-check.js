const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const source = file => fs.readFileSync(path.join(__dirname, '..', file), 'utf8');
const versions = [1, 2, 3].map(type => ({ type, version: 1 }));
const storage = new Map([['personaLinkConsentVersion', 'v2.0'], ['personaLinkConsentedDocuments', versions], ['personaLinkBusinessToken', 'token'], ['personaLinkConsentToken', 'token']]);
let app, reply = () => Promise.resolve(versions), checks = 0, logins = 0;
let currentPage = { route: 'subpackages/pair/pages/join/index', options: { code: 'ABCDE' } };
const redirects = [], businessCalls = [];
const wx = {
  getStorageSync: key => storage.get(key),
  setStorageSync: (key, value) => storage.set(key, value),
  reLaunch: options => { redirects.push(options.url); options.complete?.(); },
  login: options => { logins++; options.success({ code: 'code' }); },
  request: options => { businessCalls.push(options.url); options.success({ statusCode: 200, data: { code: 0, data: { token: 'token' } } }); }
};
vm.runInNewContext(source('app.js'), {
  App(d) { app = d; }, wx,
  getCurrentPages: () => [currentPage],
  require: () => ({ TOKEN_STORAGE_KEY: 'personaLinkBusinessToken', onboardingProfile: async () => ({ nickname: '名字', avatarUrl: '/avatar' }), isProfileComplete: () => true, writeProfileCache: () => {}, primeProfileCache: () => {}, requestData: () => { checks++; return reply(); } })
});
const requestModule = { exports: {} };
vm.runInNewContext(source('utils/request.js'), {
  module: requestModule, wx, getApp: () => app, require: () => ({ getApiBaseUrl: () => 'https://test.invalid' })
});

async function main() {
  let resolve;
  reply = () => new Promise(done => { resolve = done; });
  const launch = app.verifyConsent();
  const core = requestModule.exports.authenticatedRequestData({ url: '/api/miniapp/assessments' });
  await new Promise(setImmediate);
  assert.equal(checks, 1, '启动与业务共享校验');
  assert.equal(logins, 0, '校验中不得登录');
  resolve(versions);
  await Promise.all([launch, core]);
  assert.equal(logins, 0, '有效会话校验不能重复登录');
  assert.equal(app.globalData.hasConsent, true);
  let home, currentLoads = 0;
  const analyticsModule = { exports: {} };
  wx.getAccountInfoSync = () => ({ miniProgram: { version: 'test' } });
  wx.getLaunchOptionsSync = () => ({ scene: 1001 });
  vm.runInNewContext(source('utils/analytics.js'), {
    module: analyticsModule, wx, getApp: () => app, require: () => requestModule.exports
  });
  vm.runInNewContext(source('pages/home/index.js'), {
    Page(d) { home = d; }, wx, getApp: () => app,
    require: name => name.endsWith('/analytics') ? analyticsModule.exports : requestModule.exports
  });
  home.loadHome = () => {};
  home.loadCurrentAssessment = () => { currentLoads++; };
  reply = () => Promise.resolve(versions);
  await home.onShow();
  assert.equal(currentLoads, 1, '真实埋点校验不得阻断首页续答加载');
  await new Promise(setImmediate);
  reply = () => Promise.resolve(versions.map(d => ({ ...d, version: 2 })));
  const before = businessCalls.length;
  await assert.rejects(requestModule.exports.authenticatedRequestData({ url: '/api/miniapp/events/batch' }), /协议已更新/);
  assert.equal(businessCalls.length, before, '版本更新不得上报或执行业务');
  assert.equal(app.globalData.hasConsent, false);
  assert.equal(app.globalData.pendingLaunchUrl, '/subpackages/pair/pages/join/index?code=ABCDE');
  assert.equal(redirects.at(-1), '/pages/consent/index');
  let consent;
  vm.runInNewContext(source('pages/consent/index.js'), {
    Page(d) { consent = d; }, wx, getApp: () => app, require: () => ({})
  });
  consent.setData = patch => Object.assign(consent.data, patch);
  const redirectCount = redirects.length;
  consent.onLoad();
  assert.equal(redirects.length, redirectCount, '旧固定版本不得让重确认页跳回首页');
  reply = () => Promise.resolve(versions.slice(0, 2));
  await assert.rejects(app.verifyConsent(), /协议已更新/);
  reply = () => Promise.reject(new Error('offline'));
  await assert.rejects(app.verifyConsent(), /offline/);
  assert.equal(app.globalData.hasConsent, false, '网络失败不得放行');
  reply = () => new Promise(done => { resolve = done; });
  const stale = app.verifyConsent();
  await new Promise(setImmediate);
  app.consentEpoch = 1;
  app.globalData.hasConsent = true;
  resolve(versions.map(d => ({ ...d, version: 2 })));
  await assert.rejects(stale, /协议状态已变化/);
  assert.equal(app.globalData.hasConsent, true, '旧校验不得撤销新同意');
  reply = () => Promise.resolve(versions.map(d => ({ ...d, version: 2 })));
  currentPage = { route: 'pages/home/index', options: {} };
  app.ensureConsent({ path: 'subpackages/pair/pages/join/index', query: { code: 'NEW12' } });
  await assert.rejects(app.consentCheck, /协议已更新/);
  assert.equal(app.globalData.pendingLaunchUrl, '/subpackages/pair/pages/join/index?code=NEW12', '热启动分享优先于旧home页');
  assert.equal(app.consentLaunchUrl, '', '分享来源只保留本次校验');
  console.log('CONSENT_VERSION_CHECK_OK versions/concurrency/core-gate/missing/offline/stale/share-return');
}
main().catch(error => { console.error(error); process.exitCode = 1; });
