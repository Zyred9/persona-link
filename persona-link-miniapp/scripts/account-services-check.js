const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const storage = new Map();
let calls = [], toasts = [], redirects = [], modal, reply;
const app = { verifyConsent: async () => {}, globalData: { consentVersion: 'v2.0', consentStorageKey: 'consent' } };
const api = {
  TOKEN_STORAGE_KEY: 'personaLinkBusinessToken',
  authenticatedRequestData: (options) => { calls.push(options); return reply(options); },
  requestData: (options) => { calls.push(options); return reply(options); },
  createIdempotencyKey: (() => { let id = 0; return () => `key-${++id}`; })(),
  invalidateTestRecordRequests: () => calls.push('invalidate')
};
const wx = {
  showToast: (value) => toasts.push(value), showModal: (value) => { modal = value; },
  getStorageSync: (key) => storage.get(key), setStorageSync: (key, value) => storage.set(key, value),
  getStorageInfoSync: () => ({ keys: [...storage.keys()] }), removeStorageSync: (key) => storage.delete(key),
  reLaunch: (value) => redirects.push(value), switchTab: (value) => redirects.push(value)
};
function load(file, component = false) {
  let definition;
  vm.runInNewContext(fs.readFileSync(path.join(__dirname, '..', file), 'utf8'), {
    require: (name) => name === '../../utils/markdown'
      ? require('../subpackages/account/utils/markdown') : api,
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
  reply = () => Promise.resolve({ type: 2, content: '<script>纯文本</script>', version: 1 });
  await legal.loadDocument(); assert.equal(legal.data.state, 'ready');
  assert.ok(legal.data.contentNodes.includes('&lt;script&gt;'));

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
  // 删除后的旧 HTTP 响应不能让调用方重新写入双人缓存。
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
  console.log('ACCOUNT_SERVICES_CHECK_OK feedback/legal/consent/deletion');
}
run().catch((error) => { console.error(error); process.exitCode = 1; });
