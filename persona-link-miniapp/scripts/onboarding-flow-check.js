const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const root = path.resolve(__dirname, '..');
const source = (file) => fs.readFileSync(path.join(root, file), 'utf8');
const versions = [1, 2, 3].map((type) => ({ type, version: 1 }));

// 仅替换微信平台和 HTTP 边界；业务请求、App 门禁和协议页面使用实际源码。
function harness(initial = {}) {
  const storage = new Map(Object.entries(initial));
  const state = { logins: 0, calls: [], navigation: [], profileCalls: 0, assessments: 0, expiredOnce: false };
  let app;
  let consentPage;
  const respond = (options, data) => options.success({ statusCode: 200, data: { code: 0, data } });
  const wx = {
    getStorageSync: (key) => storage.get(key),
    setStorageSync: (key, value) => storage.set(key, value),
    removeStorageSync: (key) => storage.delete(key),
    getAccountInfoSync: () => ({ miniProgram: { envVersion: 'develop' } }),
    showToast() {},
    login(options) { state.logins += 1; options.success({ code: 'wechat-code' }); },
    request(options) {
      const url = new URL(options.url).pathname;
      state.calls.push({ url, method: options.method || 'GET' });
      if (url === '/api/miniapp/legal-documents/versions') return respond(options, versions);
      if (/\/legal-documents\/[123]$/.test(url)) {
        return respond(options, { type: Number(url.slice(-1)), version: 1, content: '协议正文' });
      }
      if (url === '/api/miniapp/auth/wechat') return respond(options, { token: 'business-token' });
      if (url === '/api/miniapp/home') return respond(options, { focusTests: [], recommendedTests: [], allTests: [] });
      if (url === '/api/miniapp/profile') {
        state.profileCalls += 1;
        return respond(options, { nickname: '', avatarUrl: '' });
      }
      if (url === '/api/miniapp/assessments') {
        state.assessments += 1;
        if (state.expiredOnce) {
          state.expiredOnce = false;
          return options.success({ statusCode: 401, data: { code: 401, message: '登录已失效，请重新登录' } });
        }
        return respond(options, ['allowed']);
      }
      throw new Error(`未定义 HTTP 边界：${url}`);
    }
  };
  for (const method of ['reLaunch', 'switchTab', 'navigateTo']) {
    wx[method] = (options) => {
      state.navigation.push({ method, url: options.url });
      if (options.complete) options.complete();
    };
  }
  const cache = new Map();
  function load(file) {
    const absolute = path.resolve(root, file);
    if (cache.has(absolute)) return cache.get(absolute).exports;
    const module = { exports: {} };
    cache.set(absolute, module);
    const context = { module, exports: module.exports, wx, console,
      getApp: () => app, getCurrentPages: () => [{ route: 'pages/consent/index', options: {} }],
      App: (definition) => { app = definition; },
      Page: (definition) => { consentPage = definition; },
      require: (name) => load(path.resolve(path.dirname(absolute), `${name}.js`)) };
    vm.runInNewContext(fs.readFileSync(absolute, 'utf8'), context, { filename: absolute });
    return module.exports;
  }
  load('app.js');
  const request = load('utils/request.js');
  load('pages/consent/index.js');
  consentPage.setData = (patch) => Object.assign(consentPage.data, patch);
  return { app, request, page: consentPage, state, storage };
}

const flush = async () => {
  await new Promise(setImmediate);
  await new Promise(setImmediate);
};

async function main() {
  const h = harness();

  // 1. 未同意协议前：业务被拦截，不触发登录。
  await assert.rejects(h.request.authenticatedRequestData({ url: '/api/miniapp/assessments' }), /协议/);
  assert.equal(h.state.logins, 0, '未同意协议不得静默登录');
  assert.equal(h.state.assessments, 0);

  // 2. 协议先行：无需登录即可阅读并同意。
  h.page.onLoad();
  h.page.onShow();
  await flush();
  assert.equal(h.page.data.state, 'ready');
  assert.equal(h.state.logins, 0, '阅读与同意协议不得要求登录');
  await h.page.agreeAndContinue();
  assert.equal(h.app.globalData.hasConsent, true);
  assert.equal(h.state.navigation.at(-1).url, '/pages/home/index');

  // 3. 游客浏览公开首页：不登录、不请求个人资料。
  const home = await h.request.requestData({ url: '/api/miniapp/home' });
  assert.deepEqual(home, { focusTests: [], recommendedTests: [], allTests: [] });
  assert.equal(h.state.logins, 0, '公开首页不得静默登录');
  assert.equal(h.state.profileCalls, 0);

  // 4. 核心功能首次使用才静默建立会话，且不要求头像昵称。
  const allowed = await h.request.authenticatedRequestData({ url: '/api/miniapp/assessments' });
  assert.deepEqual(allowed, ['allowed']);
  assert.equal(h.state.logins, 1, '核心功能静默登录一次');
  assert.equal(h.state.profileCalls, 0, '不得强制用户完善资料');
  assert.equal(h.storage.get('personaLinkBusinessToken'), 'business-token');

  // 5. 会话过期自动换发，下一次业务请求继续可用。
  h.state.expiredOnce = true;
  await assert.rejects(h.request.authenticatedRequestData({ url: '/api/miniapp/assessments' }), /登录已失效/);
  assert.equal(h.storage.get('personaLinkBusinessToken'), undefined, '过期会话被清除');
  const retried = await h.request.authenticatedRequestData({ url: '/api/miniapp/assessments' });
  assert.deepEqual(retried, ['allowed']);
  assert.equal(h.state.logins, 2, '过期后下一次业务静默换发会话');
  assert.equal(h.state.profileCalls, 0);

  // 6. 分享冷启动：同意后回到分享路径。
  h.app.globalData.pendingLaunchUrl = '/subpackages/pair/pages/join/index?code=ABC%201';
  h.page.continueToApp();
  assert.equal(h.state.navigation.at(-1).url, '/subpackages/pair/pages/join/index?code=ABC%201');
  console.log('GUEST_ONBOARDING_CHECK_OK consent-first/guest-browse/silent-login/no-profile-wall/session-renew/share-return');
}

const timeout = setTimeout(() => { console.error('FAIL guest onboarding check timed out'); process.exit(1); }, 10000);
main().then(() => clearTimeout(timeout))
  .catch((error) => { clearTimeout(timeout); console.error(error); process.exitCode = 1; });
