const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const storage = new Map();
let http, upload, loginFails = false, loginCount = 0, consentCount = 0;
const wx = {
  getStorageSync: (key) => storage.get(key),
  setStorageSync: (key, value) => storage.set(key, value),
  removeStorageSync: (key) => storage.delete(key),
  showToast() {},
  login(options) { loginCount++; loginFails ? options.fail({ errMsg: '微信登录失败' }) : options.success({ code: 'wx-code' }); },
  request(options) {
    if (options.url.endsWith('/auth/wechat')) options.success({ statusCode: 200, data: { code: 0, data: { token: `session-${loginCount}` } } });
    else http(options);
  },
  uploadFile(options) { upload(options); }
};
const app = { verifyConsent: async () => { consentCount++; } };
const requestModule = { exports: {} };
const source = (file) => fs.readFileSync(path.join(__dirname, '..', file), 'utf8');
vm.runInNewContext(source('utils/request.js'), { module: requestModule, wx, getApp: () => app, require: () => ({ getApiBaseUrl: () => 'https://example.test' }) });
const api = requestModule.exports;
let definition;
vm.runInNewContext(source('components/account-center/index.js'), {
  wx, getApp: () => app, Component: (value) => { definition = value; },
  require: (name) => name.endsWith('/image') ? { resolveImageUrl: (url) => url ? `https://example.test${url}` : '' } : api,
  setTimeout, clearTimeout
});
function component() {
  return { ...definition.methods, data: { ...definition.data }, componentAttached: true,
    setData(value, callback) { Object.assign(this.data, value); if (callback) callback(); }, triggerEvent() {} };
}
const reply = (options, data, statusCode = 200) => options.success({ statusCode, data: { code: 0, data } });
async function run() {
  const markup = source('components/account-center/index.wxml');
  const completedCard = markup.match(/<block wx:if="\{\{profileState === 'ready' && avatarUrl && nickname\}\}">([\s\S]*?)<\/block>\s*<block wx:else>/);
  assert.ok(completedCard, '完整资料使用独立展示分支，缺头像或昵称时保留填写入口');
  assert.match(completedCard[1], /<image[^>]*src="\{\{avatarImage\}\}"/);
  assert.match(completedCard[1], /<text class="profile-title">\{\{nickname\}\}<\/text>/);
  assert.doesNotMatch(completedCard[1], /<button|<input|<form|profile-badge|profile-description|profile-hint/);
  assert.match(markup, /<button[^>]*class="profile-face profile-avatar"[^>]*open-type="[^\"]*chooseAvatar/);
  assert.match(markup, /type="nickname"[^>]*bindconfirm="saveProfile"/);
  assert.doesNotMatch(markup, /profile-editor|选择头像并保存|保存昵称/);
  assert.match(source('components/account-center/index.wxss'), /\.profile-name-form\s*\{\s*display: block;/);
  const profile = component();
  loginFails = true;
  await profile.loadProfile();
  assert.equal(profile.data.profileState, 'error');
  assert.match(profile.data.profileError, /请先点击微信登录/);
  assert.equal(loginCount, 0, '资料读取不能触发静默登录');
  await assert.rejects(api.loginSession(), /微信登录失败/);
  loginFails = false;
  await api.loginSession();
  http = (options) => reply(options, { nickname: '小明', avatarUrl: '' });
  await profile.loadProfile();
  assert.equal(profile.data.profileState, 'ready');
  assert.equal(profile.data.nickname, '小明');
  const sessions = loginCount;
  await profile.loadProfile();
  assert.equal(loginCount, sessions, '已有会话仍需GET资料，但不重复wx.login');
  let saved;
  http = (options) => { saved = options.data; reply(options, options.data); };
  await profile.saveProfile({ detail: { value: { nickname: ' 新昵称 ' } } });
  assert.equal(saved.nickname, '新昵称');
  assert.equal(profile.data.nickname, '新昵称');
  profile.handleNicknameInput({ detail: { value: '微信昵称' } });
  assert.equal(saved.nickname, '新昵称', '昵称输入或失焦只更新草稿，不自动保存');
  await profile.saveProfile({ detail: { value: ' 微信昵称 ' } });
  assert.equal(saved.nickname, '微信昵称', '键盘完成直接使用原生 nickname 输入值');
  upload = (options) => {
    assert.equal(options.filePath, 'wxfile://temporary-avatar');
    assert.equal(options.header['content-type'], undefined);
    options.success({ statusCode: 200, data: JSON.stringify({ code: 0, data: { avatarUrl: '/uploads/avatar.png' } }) });
  };
  await profile.chooseAvatar({ detail: { avatarUrl: 'wxfile://temporary-avatar' } });
  assert.equal(profile.data.avatarUrl, '/uploads/avatar.png');
  await profile.saveProfile();
  assert.equal(saved.avatarUrl, '/uploads/avatar.png');
  http = (options) => reply(options, saved);
  const reopened = component();
  await reopened.loadProfile();
  assert.equal(reopened.data.nickname, '微信昵称');
  assert.equal(reopened.data.avatarUrl, '/uploads/avatar.png');

  const cached = component();
  const currentToken = storage.get(api.TOKEN_STORAGE_KEY);
  cached.profileToken = currentToken;
  Object.assign(cached.data, {
    currentView: 'history',
    profileState: 'ready',
    nickname: '微信昵称',
    avatarUrl: '/uploads/avatar.png',
    avatarImage: 'https://example.test/uploads/avatar.png'
  });
  let profileReads = 0;
  http = () => { profileReads++; };
  cached.handleBack();
  assert.equal(cached.data.currentView, 'profile');
  assert.equal(cached.data.profileState, 'ready');
  assert.equal(cached.data.nickname, '微信昵称');
  assert.equal(cached.data.avatarUrl, '/uploads/avatar.png');
  assert.equal(profileReads, 0, '测试记录返回我的页面时不能清空并重复加载当前账号资料');
  storage.set(api.TOKEN_STORAGE_KEY, 'switched-session');
  cached.data.currentView = 'history';
  http = (options) => { profileReads++; reply(options, { nickname: '切换账号', avatarUrl: '' }); };
  cached.handleBack();
  await new Promise(setImmediate);
  assert.equal(profileReads, 1, '登录账号变化后返回我的页面必须重新加载资料');
  assert.equal(cached.data.nickname, '切换账号');

  const pendingLoads = [];
  http = (options) => pendingLoads.push(options);
  const older = profile.loadProfile();
  await new Promise(setImmediate);
  const newer = profile.loadProfile();
  await new Promise(setImmediate);
  reply(pendingLoads[1], { nickname: '最新资料', avatarUrl: '' });
  await newer;
  reply(pendingLoads[0], { nickname: '旧响应', avatarUrl: '' });
  await older;
  assert.equal(profile.data.nickname, '最新资料', '慢响应不能覆盖新资料');

  let late;
  http = (options) => { late = options; };
  const oldLoad = profile.loadProfile();
  await new Promise(setImmediate);
  storage.set(api.TOKEN_STORAGE_KEY, 'other-account');
  reply(late, { nickname: '旧账号', avatarUrl: '' });
  await oldLoad;
  assert.equal(profile.data.profileState, 'error');
  assert.equal(profile.data.nickname, '');
  http = (options) => reply(options, { nickname: '新账号', avatarUrl: '' });
  await profile.loadProfile();
  storage.set(api.TOKEN_STORAGE_KEY, 'third-account');
  let writes = 0;
  http = () => { writes++; };
  await profile.saveProfile();
  assert.equal(writes, 0, '旧账号草稿不能保存到新会话');
  assert.match(profile.data.profileError, /登录状态已变化/);

  let uploads = 0;
  upload = (options) => {
    uploads++;
    options.success(uploads === 1 ? { statusCode: 401, data: 'expired' }
      : { statusCode: 200, data: '{"code":0,"data":{"avatarUrl":"/uploads/new.png"}}' });
  };
  await assert.rejects(api.authenticatedUploadData({ url: '/api/miniapp/profile/avatar', filePath: 'wxfile://avatar' }), /请先点击微信登录/);
  assert.equal(uploads, 1, '会话失效后不能静默重登上传');
  await api.loginSession();
  const previousToken = storage.get(api.TOKEN_STORAGE_KEY);
  upload = (options) => options.success({ statusCode: 401, data: 'expired' });
  await assert.rejects(api.authenticatedUploadData({ url: '/api/miniapp/profile/avatar', filePath: 'x', expectedToken: previousToken }), /请先点击微信登录/);
  await api.loginSession();
  upload = (options) => options.success({ statusCode: 200, data: '<html>broken</html>' });
  await assert.rejects(api.authenticatedUploadData({ url: '/api/miniapp/profile/avatar', filePath: 'x' }), /响应异常/);
  upload = (options) => options.fail({ errMsg: '网络断开' });
  await assert.rejects(api.authenticatedUploadData({ url: '/api/miniapp/profile/avatar', filePath: 'x' }), /网络断开/);
  assert.ok(consentCount > 0);
  console.log('PROFILE_LOGIN_CHECK_OK login/retry/profile/upload/persistence/session-isolation');
}
run().catch((error) => { console.error(error); process.exitCode = 1; });
