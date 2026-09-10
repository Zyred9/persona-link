const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const root = path.resolve(__dirname, '..');
const TOKEN = 'personaLinkBusinessToken';
const versions = [1, 2, 3].map((type) => ({ type, version: 1 }));

// 仅替换微信平台和 HTTP 边界；业务请求、App 门禁和引导页面使用实际源码。
function harness(initial = {}) {
  const storage = new Map(Object.entries(initial));
  const state = { profile: {}, loginCalls: 0, loginFails: 0, calls: [], navigation: [],
    pages: [{ route: 'pages/home/index', options: {} }] };
  let app;
  let page;
  const wx = {
    getStorageSync: (key) => storage.get(key),
    setStorageSync: (key, value) => storage.set(key, value),
    removeStorageSync: (key) => storage.delete(key),
    getAccountInfoSync: () => ({ miniProgram: { envVersion: 'develop' } }),
    showToast() {},
    login(options) {
      state.loginCalls += 1;
      if (state.loginFails-- > 0) options.fail({ errMsg: '登录失败，请重试' });
      else options.success({ code: 'wechat-code' });
    },
    request(options) {
      const url = new URL(options.url).pathname;
      state.calls.push({ url, method: options.method, data: options.data });
      let data;
      if (url === '/api/miniapp/auth/wechat') data = { token: 'business-token' };
      else if (url === '/api/miniapp/profile') {
        if (options.method === 'PUT') state.profile = { ...options.data };
        data = { ...state.profile };
      } else if (url === '/api/miniapp/legal-documents/versions') data = versions;
      else if (/\/legal-documents\/[123]$/.test(url)) {
        data = { type: Number(url.slice(-1)), version: 1, content: '协议正文' };
      } else if (url === '/api/miniapp/assessments') data = ['allowed'];
      else throw new Error(`未定义 HTTP 边界：${url}`);
      const respond = () => options.success({ statusCode: 200, data: { code: 0, data } });
      if (state.deferProfile && url === '/api/miniapp/profile' && options.method === 'GET') state.respondProfile = respond;
      else respond();
    },
    uploadFile(options) {
      assert.equal(options.filePath, 'wxfile://selected-avatar');
      assert.equal(options.header.Authorization, 'Bearer business-token');
      options.success({ statusCode: 200, data: JSON.stringify({ code: 0,
        data: { avatarUrl: '/avatar/me.png' } }) });
    }
  };
  for (const method of ['reLaunch', 'switchTab', 'navigateTo']) {
    wx[method] = (options) => {
      state.navigation.push({ method, url: options.url });
      const next = { route: options.url.split('?')[0].slice(1), options: {} };
      if (method === 'navigateTo') state.pages.push(next);
      else state.pages = [next];
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
      getApp: () => app, getCurrentPages: () => state.pages,
      App: (definition) => { app = definition; },
      Page: (definition) => { page = definition; },
      require: (name) => load(path.resolve(path.dirname(absolute), `${name}.js`)) };
    vm.runInNewContext(fs.readFileSync(absolute, 'utf8'), context, { filename: absolute });
    return module.exports;
  }
  load('app.js');
  const request = load('utils/request.js');
  load('pages/consent/index.js');
  page.setData = (data) => Object.assign(page.data, data);
  page.onLoad();
  return { app, request, page, state, storage,
    business: () => request.authenticatedRequestData({ url: '/api/miniapp/assessments' }) };
}

async function main() {
  const h = harness();
  await assert.rejects(h.request.ensureSession(), /微信登录/);
  await assert.rejects(h.business(), /微信登录/);
  assert.equal(h.state.loginCalls, 0);
  assert.equal(h.state.calls.length, 0);
  assert.equal(h.state.navigation[0].url, '/pages/consent/index');
  console.log('PASS 无 token 不静默登录、业务请求被阻止');

  await h.page.login();
  assert.equal(h.state.loginCalls, 1);
  assert.equal(h.page.data.step, 'profile');
  await h.page.saveProfile();
  await h.page.agreeAndContinue();
  assert.equal(h.storage.has('personaLinkConsentedDocuments'), false);
  await assert.rejects(h.business(), /头像和昵称/);
  console.log('PASS 显式登录后，缺头像昵称不能同意或使用业务');

  await h.page.chooseAvatar({ detail: { avatarUrl: 'wxfile://selected-avatar' } });
  h.page.handleNicknameInput({ detail: { value: '映见用户' } });
  await h.page.saveProfile();
  assert.equal(h.page.data.step, 'consent');
  assert.equal(h.state.profile.nickname, '映见用户');
  assert.equal(h.state.profile.avatarUrl, '/avatar/me.png');
  await assert.rejects(h.business(), /协议/);
  console.log('PASS 微信头像昵称保存成功，协议前仍拒绝业务');

  h.app.globalData.pendingLaunchUrl = '/subpackages/pair/pages/join/index?code=ABC%201';
  const navigationCount = h.state.navigation.length;
  h.page.openLegal({ currentTarget: { dataset: { type: 1 } } });
  h.app.ensureConsent({ path: 'subpackages/account/pages/legal/index' });
  h.state.pages.pop();
  h.app.ensureConsent({ path: 'pages/consent/index' });
  assert.equal(h.state.navigation.length, navigationCount + 1);
  assert.equal(h.page.data.step, 'consent');
  assert.equal(h.app.globalData.pendingLaunchUrl, '/subpackages/pair/pages/join/index?code=ABC%201');
  console.log('PASS 阅读协议返回保留步骤和分享目的地');

  await h.page.agreeAndContinue();
  assert.equal(h.app.globalData.hasConsent, true);
  assert.equal(h.state.navigation.at(-1).url, '/subpackages/pair/pages/join/index?code=ABC%201');
  assert.deepEqual(Array.from(await h.business()), ['allowed']);
  console.log('PASS 同意当前协议后开放业务并回到分享路径');

  h.state.deferProfile = true;
  const pendingVerification = h.app.verifyConsent();
  assert.equal(h.app.globalData.hasConsent, false);
  await Promise.resolve();
  assert.equal(typeof h.state.respondProfile, 'function');
  h.state.respondProfile();
  await pendingVerification;
  h.state.deferProfile = false;
  assert.equal(h.app.globalData.hasConsent, true);
  console.log('PASS 异步重验期间先关闭旧放行状态');

  const old = { personaLinkConsentVersion: 'v2.0', personaLinkConsentedDocuments: versions,
    personaLinkConsentToken: 'business-token' };
  for (const token of ['', 'business-token']) {
    const legacy = harness({ ...old, [TOKEN]: token });
    await assert.rejects(legacy.business(), token ? /头像和昵称/ : /微信登录/);
    assert.equal(legacy.state.loginCalls, 0);
    assert.equal(legacy.app.globalData.hasConsent, false);
  }
  console.log('PASS 旧本地协议无法绕过登录或完整资料要求');

  const retry = harness();
  retry.state.loginFails = 1;
  await retry.page.login();
  assert.match(retry.page.data.error, /登录失败/);
  assert.equal(retry.page.data.busy, false);
  assert.equal(retry.storage.has(TOKEN), false);
  await retry.page.login();
  assert.equal(retry.state.loginCalls, 2);
  assert.equal(retry.page.data.step, 'profile');
  console.log('PASS 登录失败后可重新点击登录');

  const shared = harness();
  shared.app.ensureConsent({ scene: 1007, path: 'subpackages/pair/pages/join/index', query: { code: 'A B' } });
  if (shared.app.consentCheck) await assert.rejects(shared.app.consentCheck);
  assert.equal(shared.app.globalData.pendingLaunchUrl, '/subpackages/pair/pages/join/index?code=A%20B');
  console.log('PASS 冷启动分享路径和参数保留');
}

// 未 resolve 的异步探针必须超时失败，不能随空事件循环静默退出。
const timeout = setTimeout(() => { console.error('FAIL onboarding flow check timed out'); process.exit(1); }, 10000);
main().then(() => { clearTimeout(timeout); console.log('9 onboarding flow checks passed'); })
  .catch((error) => { clearTimeout(timeout); console.error(error); process.exitCode = 1; });
