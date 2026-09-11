const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

async function check({ consent = true, token = '', overlay = false, fail = false } = {}) {
  let definition;
  let stopped = 0;
  const requests = [];
  const pending = [];
  const respond = (url, response) => {
    requests.push(url);
    return new Promise((resolve, reject) => pending.push(() => fail
      ? reject(new Error('Network failed')) : resolve(response)));
  };
  vm.runInNewContext(fs.readFileSync(path.join(__dirname, '../pages/home/index.js'), 'utf8'), {
    Page(value) { definition = value; },
    getApp: () => ({ globalData: { hasConsent: consent } }),
    wx: {
      getStorageSync: () => token,
      stopPullDownRefresh() { stopped += 1; }
    },
    require(name) {
      if (name === '../../utils/request') return {
        TOKEN_STORAGE_KEY: 'token',
        request: ({ url }) => respond(url, {
          code: 0, data: { allTests: [{ testId: 'new-test', categoryId: 'all' }] }
        }),
        authenticatedRequestData: ({ url }) => respond(url, { answerSessionId: 'new-session' })
      };
      if (name === '../../utils/analytics') return { trackEvent() {} };
      if (name === '../../utils/image') return require('../utils/image');
      throw new Error(`Unexpected module: ${name}`);
    }
  });
  const page = {
    ...definition,
    data: { ...definition.data, containerVisible: overlay },
    setData(patch) { Object.assign(this.data, patch); }
  };
  const refreshing = page.onPullDownRefresh();
  if (consent && !overlay) {
    assert.deepEqual(requests, token
      ? ['/api/miniapp/home', '/api/miniapp/assessments/current'] : ['/api/miniapp/home']);
    assert.equal(stopped, 0, '等待请求结束后才收起刷新动画');
    pending[0]();
    if (token) {
      await new Promise((resolve) => setImmediate(resolve));
      assert.equal(stopped, 0, '继续等待答卷请求结束');
      pending[1]();
    }
    await refreshing;
    assert.equal(page.data.state, fail ? 'error' : 'ready');
    if (!fail) assert.equal(page.data.tests[0].testId, 'new-test');
    if (token) assert.equal(page.data.currentAssessment?.answerSessionId, fail ? undefined : 'new-session');
  } else {
    await refreshing;
    assert.deepEqual(requests, [], '未同意隐私或弹层打开时不刷新底层');
  }
  assert.equal(stopped, 1, '每次下拉都必须结束刷新动画');
}

async function checkStableRefresh() {
  let definition;
  const statePatches = [];
  const requests = [];
  const pending = [];
  vm.runInNewContext(fs.readFileSync(path.join(__dirname, '../pages/home/index.js'), 'utf8'), {
    Page(value) { definition = value; },
    getApp: () => ({ globalData: { hasConsent: true } }),
    wx: {
      getStorageSync: () => '',
      stopPullDownRefresh() {}
    },
    require(name) {
      if (name === '../../utils/request') return {
        TOKEN_STORAGE_KEY: 'token',
        request: ({ url }) => {
          requests.push(url);
          return new Promise((resolve) => pending.push(resolve));
        },
        authenticatedRequestData: () => Promise.resolve(null)
      };
      if (name === '../../utils/analytics') return { trackEvent() {} };
      if (name === '../../utils/image') return require('../utils/image');
      throw new Error(`Unexpected module: ${name}`);
    }
  });
  const page = {
    ...definition,
    data: {
      ...definition.data,
      state: 'ready',
      titleImageUrl: 'https://example.com/title-old.png',
      tests: [{ testId: 'old-test', categoryId: 'all' }]
    },
    setData(patch) {
      if (patch.state) statePatches.push(patch.state);
      Object.assign(this.data, patch);
    }
  };
  const first = page.onPullDownRefresh();
  const second = page.onPullDownRefresh();
  assert.deepEqual(requests, ['/api/miniapp/home', '/api/miniapp/home'], '连续下拉必须各自发起刷新请求');
  assert.ok(!statePatches.includes('loading'), '已展示内容时刷新不得切回 loading 卸载头图');
  pending[1]({ code: 0, data: {
    titleImageUrl: 'https://example.com/title-new.png',
    allTests: [{ testId: 'new-test', categoryId: 'all' }]
  } });
  await second;
  pending[0]({ code: 0, data: {
    titleImageUrl: 'https://example.com/title-stale.png',
    allTests: [{ testId: 'stale-test', categoryId: 'all' }]
  } });
  await first;
  assert.equal(page.data.tests[0].testId, 'new-test', '过期响应不得覆盖最新刷新结果');
  assert.equal(page.data.titleImageUrl, 'https://example.com/title-new.png');
  assert.ok(!statePatches.includes('loading'), '刷新全过程不得出现 loading 状态卸载头图');
  console.log('Home refresh stability checks passed.');
}

async function main() {
  const config = JSON.parse(fs.readFileSync(path.join(__dirname, '../pages/home/index.json'), 'utf8'));
  assert.equal(config.enablePullDownRefresh, true);
  await check();
  await check({ token: 'logged-in' });
  await check({ fail: true, token: 'logged-in' });
  await check({ consent: false, token: 'logged-in' });
  await check({ overlay: true, token: 'logged-in' });
  await checkStableRefresh();
  console.log('Home pull-down refresh checks passed.');
}

main().catch((error) => { console.error(error); process.exitCode = 1; });
