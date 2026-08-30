const assert = require('node:assert');
const { execFileSync } = require('node:child_process');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '..');

function walk(directory) {
  return fs.readdirSync(directory, { withFileTypes: true }).flatMap((entry) => {
    const target = path.join(directory, entry.name);
    return entry.isDirectory() ? walk(target) : [target];
  });
}

const files = walk(root).filter((file) => !file.includes(`${path.sep}scripts${path.sep}`));
const javaScriptFiles = files.filter((file) => file.endsWith('.js'));
const jsonFiles = files.filter((file) => file.endsWith('.json'));

javaScriptFiles.forEach((file) => execFileSync(process.execPath, ['--check', file], { stdio: 'pipe' }));
jsonFiles.forEach((file) => JSON.parse(fs.readFileSync(file, 'utf8')));

const appConfig = JSON.parse(fs.readFileSync(path.join(root, 'app.json'), 'utf8'));
const routes = [
  ...appConfig.pages,
  ...appConfig.subpackages.flatMap((subpackage) =>
    subpackage.pages.map((page) => `${subpackage.root}/${page}`))
];

const coreRoutes = [
  'pages/consent/index',
  'pages/home/index',
  'pages/profile/index',
  'subpackages/account/pages/history/index',
  'subpackages/account/pages/member/index',
  'subpackages/account/pages/feedback/index',
  'subpackages/test/pages/detail/index',
  'subpackages/test/pages/quiz/index',
  'subpackages/test/pages/result/index',
  'subpackages/pair/pages/detail/index',
  'subpackages/pair/pages/invite/index',
  'subpackages/pair/pages/join/index',
  'subpackages/pair/pages/wait/index',
  'subpackages/pair/pages/result/index'
];

coreRoutes.forEach((route) => assert(routes.includes(route), `核心路由未注册：${route}`));

routes.forEach((route) => {
  ['js', 'json', 'wxml', 'wxss'].forEach((extension) => {
    const file = path.join(root, `${route}.${extension}`);
    if (!fs.existsSync(file)) {
      throw new Error(`路由文件缺失：${path.relative(root, file)}`);
    }
  });
});

files.filter((file) => file.endsWith('.wxml')).forEach((wxmlFile) => {
  const javaScriptFile = wxmlFile.replace(/\.wxml$/, '.js');
  if (!fs.existsSync(javaScriptFile)) {
    return;
  }
  const wxml = fs.readFileSync(wxmlFile, 'utf8');
  const javaScript = fs.readFileSync(javaScriptFile, 'utf8');
  const handlers = [...wxml.matchAll(/\b(?:bind|catch)(?::?[a-z-]+)="([A-Za-z_$][\w$]*)"/g)]
    .map((match) => match[1]);
  handlers.forEach((handler) => {
    assert(
      new RegExp(`\\b${handler}\\s*\\(`).test(javaScript),
      `事件方法缺失：${path.relative(root, wxmlFile)} -> ${handler}`
    );
  });
});

async function checkRuntimeContracts() {
  const storage = { personaLinkBusinessToken: 'test-token' };
  let appDefinition;
  let capturedRequest;
  let lastRelaunchUrl = '';
  let removedToken = false;
  let switchedHome = false;

  global.getCurrentPages = () => [{ route: 'subpackages/account/pages/settings/index' }];
  global.wx = {
    getAccountInfoSync: () => ({ miniProgram: { envVersion: 'develop' } }),
    getStorageSync: (key) => storage[key],
    setStorageSync: (key, value) => {
      storage[key] = value;
    },
    removeStorageSync: (key) => {
      delete storage[key];
      removedToken = true;
    },
    showToast: () => {},
    reLaunch: ({ url, complete }) => {
      lastRelaunchUrl = url;
      if (complete) {
        complete();
      }
    },
    switchTab: ({ url }) => {
      switchedHome = url === '/pages/home/index';
    },
    request: (options) => {
      capturedRequest = options;
      options.success({ statusCode: 200, data: { status: 'UP' } });
    }
  };

  global.App = (definition) => {
    appDefinition = definition;
  };
  require(path.join(root, 'app.js'));
  const launchOptions = {
    path: 'subpackages/account/pages/settings/index',
    query: { source: 'a b', name: '中文' }
  };
  appDefinition.onLaunch(launchOptions);
  appDefinition.onShow(launchOptions);
  assert.strictEqual(lastRelaunchUrl, '/pages/consent/index');
  assert.strictEqual(
    appDefinition.globalData.pendingLaunchUrl,
    '/subpackages/account/pages/settings/index?source=a%20b&name=%E4%B8%AD%E6%96%87'
  );

  let consentPage;
  global.getApp = () => appDefinition;
  global.Page = (definition) => {
    consentPage = definition;
  };
  require(path.join(root, 'pages/consent/index.js'));
  consentPage.agreeAndContinue.call({ data: { consentVersion: 'v1.0' } });
  assert.strictEqual(
    lastRelaunchUrl,
    '/subpackages/account/pages/settings/index?source=a%20b&name=%E4%B8%AD%E6%96%87'
  );

  delete storage.personaLinkConsentVersion;
  appDefinition.onShow({ path: 'pages/profile/index', query: { source: 'hot start' } });
  assert.strictEqual(lastRelaunchUrl, '/pages/consent/index');
  assert.strictEqual(
    appDefinition.globalData.pendingLaunchUrl,
    '/pages/profile/index?source=hot%20start'
  );

  const { request } = require(path.join(root, 'utils/request.js'));
  const health = await request({ url: '/api/health' });
  assert.strictEqual(health.status, 'UP');
  assert.strictEqual(capturedRequest.url, 'http://127.0.0.1:18080/api/health');
  assert.strictEqual(capturedRequest.header.Authorization, 'Bearer test-token');

  wx.request = (options) => options.success({
    statusCode: 401,
    header: { 'x-request-id': 'header-request-1' },
    data: { code: 40101, message: '会话已失效', requestId: 'body-request-1' }
  });
  await assert.rejects(
    request({ url: '/api/protected' }),
    (error) => error.statusCode === 401
      && error.code === 40101
      && error.requestId === 'header-request-1'
      && error.message === '会话已失效'
  );
  assert.strictEqual(removedToken, true);
  assert.strictEqual(switchedHome, true);
}

checkRuntimeContracts()
  .then(() => {
    console.log(`MINIAPP_SELF_CHECK_OK routes=${routes.length} js=${javaScriptFiles.length} json=${jsonFiles.length}`);
  })
  .catch((error) => {
    console.error(error);
    process.exitCode = 1;
  });
