const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const storage = new Map();
let calls = [], toasts = [], redirects = [], modal, reply, pullDownStops = 0;
const app = { verifyConsent: async () => {}, globalData: { consentVersion: 'v2.0', consentStorageKey: 'consent' } };
const api = {
  TOKEN_STORAGE_KEY: 'personaLinkBusinessToken',
  authenticatedRequestData: (options) => { calls.push(options); return reply(options); },
  requestData: (options) => { calls.push(options); return reply(options); },
  createIdempotencyKey: (() => { let id = 0; return () => `key-${++id}`; })(),
  invalidateTestRecordRequests: () => calls.push('invalidate'),
  clearAccountSession: () => {}
};
const wx = {
  showToast: (value) => toasts.push(value), showModal: (value) => { modal = value; },
  getStorageSync: (key) => storage.get(key), setStorageSync: (key, value) => storage.set(key, value),
  getStorageInfoSync: () => ({ keys: [...storage.keys()] }), removeStorageSync: (key) => storage.delete(key),
  clearStorageSync: () => storage.clear(),
  stopPullDownRefresh: () => { pullDownStops++; },
  reLaunch: (value) => redirects.push(value), switchTab: (value) => redirects.push(value)
};
const modules = {
  '../../utils/markdown': require('../subpackages/account/utils/markdown'),
  '../../utils/format': require('../subpackages/account/utils/format')
};
function load(file, component = false) {
  let definition;
  vm.runInNewContext(fs.readFileSync(path.join(__dirname, '..', file), 'utf8'), {
    require: (name) => Object.prototype.hasOwnProperty.call(modules, name) ? modules[name] : api,
    wx, getApp: () => app, Page: (value) => { definition = value; },
    Component: (value) => { definition = value; }, setTimeout: () => 1, clearTimeout() {}
  });
  const instance = { ...definition, ...(definition.methods || {}), data: { ...definition.data },
    setData(value, callback) { Object.assign(this.data, value); if (callback) callback(); }, triggerEvent() {} };
  if (component) { instance.componentAttached = true; instance.data.currentView = 'feedback'; }
  return instance;
}
async function run() {
  const feedback = load('components/account-center/index.js', true);
  feedback.data.feedbackContent = ' 问题反馈 ';
  reply = () => Promise.reject(new Error('offline'));
  await feedback.submitFeedback();
  assert.equal(feedback.data.feedbackContent, ' 问题反馈 ');
  const firstId = calls[0].data.requestId;
  let resolve;
  reply = () => new Promise((done) => { resolve = done; });
  const pending = feedback.submitFeedback();
  await feedback.submitFeedback();
  assert.equal(calls.length, 2);
  assert.equal(calls[1].data.requestId, firstId);
  resolve({ id: 1 }); await pending;
  assert.equal(feedback.data.feedbackContent, '');
  assert.equal(toasts.at(-1).title, '提交成功');
  feedback.data.feedbackContent = '新问题';
  const late = feedback.submitFeedback();
  await new Promise(setImmediate);
  assert.notEqual(calls.at(-1).data.requestId, firstId);
  feedback.pageLifetimes.hide.call(feedback);
  const toastCount = toasts.length;
  resolve({ id: 2 }); await late;
  assert.equal(feedback.data.feedbackContent, '新问题');
  assert.equal(toasts.length, toastCount);

  const legal = load('subpackages/account/pages/legal/index.js');
  legal.type = 2;
  reply = () => Promise.reject(new Error('未配置'));
  await legal.loadDocument(); assert.equal(legal.data.state, 'error');
  reply = () => Promise.resolve({ type: 2, content: '<script>纯文本</script>', version: 1, updatedAt: '2026-09-10T13:58:02' });
  await legal.loadDocument(); assert.equal(legal.data.state, 'ready');
  assert.ok(legal.data.contentNodes.includes('&lt;script&gt;'));
  assert.equal(legal.data.document.updatedAt, '2026-09-10 13:58:02', '更新时间必须格式化为 年-月-日 时:分:秒');
  reply = () => Promise.resolve({ type: 2, content: '更新后的协议', version: 2, updatedAt: '2026-09-10T14:30:00' });
  await legal.onPullDownRefresh();
  assert.equal(legal.data.document.version, 2, '下拉刷新必须重新拉取协议');
  assert.equal(pullDownStops, 1, '下拉刷新结束后必须收起刷新动画');

  const consent = load('pages/consent/index.js');
  consent.profileToken = 'token';
  storage.set('personaLinkBusinessToken', 'token');
  reply = () => Promise.reject(new Error('未配置'));
  await consent.loadDocuments(); await consent.agreeAndContinue();
  assert.equal(storage.has('consent'), false);
  reply = (options) => Promise.resolve({ type: Number(options.url.slice(-1)), version: 3, content: '正式内容' });
  await consent.loadDocuments(); await consent.agreeAndContinue();
  assert.equal(storage.get('personaLinkConsentedDocuments').length, 3);
  assert.equal(storage.get('consent'), 'v2.0');

  const privacy = load('subpackages/account/pages/privacy/index.js');
  storage.set('personaPairSession', {}); storage.set('pairCreateRequest:12', 'old');
  storage.set('personaLinkBusinessToken', 'token');
  redirects = [];
  reply = () => Promise.reject(new Error('删除失败'));
  privacy.requestDeletion(); await modal.success({ confirm: true }); modal.complete();
  assert.equal(storage.has('personaPairSession'), true); assert.equal(redirects.length, 0);
  reply = () => Promise.resolve();
  privacy.requestDeletion(); await modal.success({ confirm: true }); modal.complete();
  assert.equal(storage.has('personaPairSession'), false);
  assert.equal(storage.has('pairCreateRequest:12'), false);
  assert.equal(storage.get('personaLinkBusinessToken'), 'token');
  assert.equal(storage.get('consent'), 'v2.0');
  assert.equal(redirects[0].url, '/pages/home/index');

  // 注销账号：二次确认后删除账号数据，本机清空并只保留协议同意记录。
  storage.set('personaLinkBusinessToken', 'token');
  storage.set('personaLinkProfileCache', { nickname: '旧昵称', avatarUrl: '/a.png' });
  storage.set('personaPairSession', {});
  storage.set('personaLinkConsentVersion', 'v2.0');
  storage.set('personaLinkConsentedDocuments', [{ type: 1, version: 1 }]);
  redirects = [];
  reply = () => Promise.reject(new Error('注销失败'));
  privacy.cancelAccount(); await modal.success({ confirm: true }); modal.complete();
  assert.equal(storage.has('personaLinkBusinessToken'), true, '注销失败时保留本机登录态');
  assert.equal(redirects.length, 0);
  reply = () => Promise.resolve();
  privacy.cancelAccount(); await modal.success({ confirm: true }); modal.complete();
  const cancelCall = calls.filter((call) => call && call.url === '/api/miniapp/me').at(-1);
  assert.equal(cancelCall.method, 'DELETE');
  assert.equal(storage.has('personaLinkBusinessToken'), false, '注销后清除本地登录态');
  assert.equal(storage.has('personaLinkProfileCache'), false, '注销后清除本地资料缓存');
  assert.equal(storage.has('personaPairSession'), false, '注销后清除本机配对缓存');
  assert.equal(storage.get('personaLinkConsentVersion'), 'v2.0', '注销保留本机协议同意记录');
  assert.equal(redirects.at(-1).url, '/pages/home/index');
  // 删除后的旧 HTTP 响应不能让调用方重新写入双人缓存。
  storage.set('personaLinkBusinessToken', 'token');
  const requestModule = { exports: {} };
  let respond;
  vm.runInNewContext(fs.readFileSync(path.join(__dirname, '..', 'utils/request.js'), 'utf8'), {
    module: requestModule, require: () => ({ getApiBaseUrl: () => 'https://example.test' }),
    wx: { ...wx, request: (options) => { respond = options.success; } }
  });
  const stale = requestModule.exports.authenticatedRequestData({ url: '/api/miniapp/pairs' });
  await Promise.resolve();
  requestModule.exports.invalidateTestRecordRequests();
  respond({ statusCode: 200, data: { code: 0, data: { pairSessionId: 1 } } });
  await assert.rejects(stale, /测试记录已删除/);
  console.log('ACCOUNT_SERVICES_CHECK_OK feedback/legal/legal-time/legal-refresh/consent/deletion/account-cancel');
}
run().catch((error) => { console.error(error); process.exitCode = 1; });
