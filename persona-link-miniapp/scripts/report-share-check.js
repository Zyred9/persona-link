const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const pageDir = path.join(__dirname, '../subpackages/test/pages/result');
const source = fs.readFileSync(path.join(pageDir, 'index.js'), 'utf8');
const template = fs.readFileSync(path.join(pageDir, 'index.wxml'), 'utf8');
const flush = () => new Promise((resolve) => setImmediate(resolve));

function createPage({ fail = false, serverShareToken = '' } = {}) {
  let definition;
  const requests = [];
  let accessRuns = 0;
  vm.runInNewContext(source, {
    Page(value) { definition = value; },
    require(name) {
      if (name.endsWith('/analytics')) return { trackEvent() {} };
      if (name.endsWith('/report-access')) return {
        createReportAccess(page) {
          return {
            async run(load) {
              accessRuns += 1;
              try { await load(() => true); }
              catch (error) { page.setData({ state: 'error', errorDescription: error.message }); }
            },
            dispose() {},
            setHidden() {}
          };
        }
      };
      if (name.endsWith('/request')) return {
        async authenticatedRequestData({ url }) {
          requests.push(url);
          if (fail) throw new Error('网络连接失败');
          const marker = url.match(/[?&]shareToken=([^&]+)/);
          return {
            resultSnapshot: {
              resultName: '测试结果',
              basicResult: { text: '基础报告' },
              shareCopy: { text: '分享文案' }
            },
            shareToken: marker ? decodeURIComponent(marker[1]) : serverShareToken
          };
        }
      };
      throw new Error(`Unexpected module: ${name}`);
    },
    getApp: () => ({ returnToHome() {} })
  });
  const page = Object.assign({}, definition, {
    data: Object.assign({}, definition.data),
    setData(patch) { Object.assign(this.data, patch); }
  });
  return { page, requests, get accessRuns() { return accessRuns; } };
}

async function main() {
  // 分享链接访客：凭分享令牌直接读取，不经过广告解锁流程。
  let env = createPage({ serverShareToken: 'unused' });
  env.page.onLoad({ reportId: '100', shareToken: 'guest-token' });
  await flush();
  assert.deepEqual(env.requests, ['/api/miniapp/reports/100?shareToken=guest-token']);
  assert.equal(env.accessRuns, 0, '分享访问不得查询广告权限');
  assert.equal(env.page.data.state, 'ready');
  assert.equal(env.page.data.result.description, '基础报告');
  const shared = env.page.onShareAppMessage();
  assert.equal(shared.path, '/subpackages/test/pages/result/index?reportId=100&shareToken=guest-token',
    '分享链接必须携带报告 ID 与分享令牌');
  assert.equal(shared.title, '分享文案');

  // 本人正常加载：保留广告权限流程，并捕获响应中的分享令牌。
  env = createPage({ serverShareToken: 'owner-token' });
  env.page.onLoad({ reportId: '100' });
  await flush();
  assert.deepEqual(env.requests, ['/api/miniapp/reports/100']);
  assert.equal(env.accessRuns, 1, '本人加载必须保留广告权限流程');
  assert.equal(env.page.onShareAppMessage().path,
    '/subpackages/test/pages/result/index?reportId=100&shareToken=owner-token');

  // 服务端未返回令牌时回退首页，不产出无效分享链接。
  env = createPage({ serverShareToken: '' });
  env.page.onLoad({ reportId: '100' });
  await flush();
  assert.equal(env.page.onShareAppMessage().path, '/pages/home/index', '缺少令牌时分享回首页');

  // 分享链接加载失败进入错误态。
  env = createPage({ fail: true });
  env.page.onLoad({ reportId: '100', shareToken: 'guest-token' });
  await flush();
  assert.equal(env.page.data.state, 'error');

  assert.match(template, /open-type="share"/, '结果页必须保留微信分享入口');

  console.log('Report share checks passed.');
}

main().catch((error) => { console.error(error); process.exitCode = 1; });
