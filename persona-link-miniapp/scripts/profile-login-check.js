const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const storage = new Map();
let http, upload, loginFails = false, loginCount = 0, consentCount = 0;
let modals = [], toasts = [];
const wx = {
  getStorageSync: (key) => storage.get(key),
  setStorageSync: (key, value) => storage.set(key, value),
  removeStorageSync: (key) => storage.delete(key),
  showToast: (value) => toasts.push(value),
  showModal: (value) => modals.push(value),
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
let timerSequence = 0;
const auditTimers = new Map();
vm.runInNewContext(source('components/account-center/index.js'), {
  wx, getApp: () => app, Component: (value) => { definition = value; },
  require: (name) => name.endsWith('/image') ? { resolveImageUrl: (url) => url ? `https://example.test${url}` : '' } : api,
  setTimeout(callback) { const id = ++timerSequence; auditTimers.set(id, callback); return id; },
  clearTimeout(id) { auditTimers.delete(id); }
});
function component() {
  return { ...definition.methods, data: { ...definition.data }, componentAttached: true, frames: [],
    setData(value, callback) { Object.assign(this.data, value); this.frames.push({ ...this.data }); if (callback) callback(); }, triggerEvent() {} };
}
const reply = (options, data, statusCode = 200) => options.success({ statusCode, data: { code: 0, data } });
async function run() {
  const markup = source('components/account-center/index.wxml');
  const completedCard = markup.match(/<block wx:if="\{\{profileState === 'ready' && profileCustomized\}\}">([\s\S]*?)<\/block>\s*<block wx:else>/);
  assert.ok(completedCard, '完整资料使用独立展示分支，缺头像或昵称时保留填写入口');
  assert.match(completedCard[1], /<image[^>]*src="\{\{avatarImage\}\}"/);
  assert.match(completedCard[1], /<text class="profile-title">\{\{nickname\}\}<\/text>/);
  assert.doesNotMatch(completedCard[1], /<button|<input|<form|profile-badge|profile-description|profile-hint/);
  assert.match(markup, /<button[^>]*class="profile-face profile-avatar"[^>]*open-type="[^\"]*chooseAvatar/);
  assert.match(markup, /type="nickname"[^>]*bindconfirm="saveProfile"/);
  assert.match(markup, /type="nickname"[^>]*bindblur="handleNicknameBlur"/, '昵称输入失焦直接保存');
  assert.match(markup, /class="paper-card profile-card"\s+bindtap="createGuestSession"/, '游客卡片是显式的重新登录入口');
  assert.match(markup, /disabled="\{\{\(profileState !== 'ready' && profileState !== 'guest'\) \|\| avatarUploading \|\| profileSaving\}\}"/, '游客态头像按钮可点，交由卡片处理建号');
  assert.match(markup, /点击卡片登录后可设置头像和昵称/);
  assert.doesNotMatch(markup, /profile-editor|选择头像并保存|保存昵称|profile-name-form|profile-name-save|form-type="submit"/, '昵称区不再保留表单和保存按钮');
  assert.doesNotMatch(markup, /loading="\{\{\s*avatarUploading\s*\}\}"/, '头像按钮不再使用内置 loading，加载图标不能落在头像框内');
  assert.match(markup, /wx:if="\{\{avatarUploading \|\| profileState === 'loading'\}\}"[^>]*class="profile-avatar__mask"/, '上传和资料加载共用在头像框上的遮罩转圈');
  assert.match(markup, /<input[^>]*bindblur="handleNicknameBlur"[^>]*\/>\s*<text wx:elif="\{\{profileState === 'loading'\}\}" class="profile-title">正在加载个人资料<\/text>\s*<text wx:else class="profile-title">先从一题开始<\/text>/, '昵称输入、加载文案与游客文案依次互斥');
  assert.doesNotMatch(markup, /<page-state[^>]*profileState\s*===\s*'loading'/, '登录中不再显示骨架屏');
  assert.match(source('components/account-center/index.wxss'), /@keyframes profile-avatar-spin/);
  assert.doesNotMatch(source('components/account-center/index.wxss'), /profile-name-form|profile-name-save/, '不再保留昵称表单和保存按钮样式');
  assert.match(source('components/account-center/index.wxss'), /\.profile-card\s*\{[^}]*padding: 32rpx 30rpx;/, '头像上方间距基准是卡片上内边距');
  assert.match(source('components/account-center/index.wxss'), /\.profile-copy\s*\{[^}]*margin-top: 32rpx;/, '头像与昵称的间距与头像上方间距一致');
  assert.match(source('components/account-center/index.wxss'), /\.profile-title\s*\{\s*font-size: 37rpx;/, '昵称不再叠加额外上间距');
  assert.doesNotMatch(markup, /重新加载资料|profile-error|profile-action/, '失败时不再内嵌重试按钮，改由弹窗处理');
  const profile = component();
  let guestRequests = 0;
  http = () => { guestRequests++; };
  definition.lifetimes.attached.call(profile);
  assert.equal(profile.data.profileState, 'guest', '首次 attached 同步展示游客');
  assert.ok(profile.frames.every((frame) => frame.profileState === 'guest'), '游客首帧全过程不进入 loading');
  assert.equal(loginCount, 0);
  assert.equal(consentCount, 0, '游客资料展示不触发同意门禁');
  assert.equal(guestRequests, 0);
  assert.equal(await api.fetchProfile(), null, '共享资料读取不能为游客登录');
  assert.equal(loginCount, 0);
  await api.loginSession();
  http = (options) => reply(options, { nickname: '用户483920', avatarUrl: '', customized: false });
  await profile.loadProfile();
  assert.equal(profile.data.profileState, 'ready');
  assert.equal(profile.data.nickname, '用户483920', '服务端建号即写入生成的默认昵称');
  assert.equal(loginCount, 1, '资料仅复用核心功能建立的会话');
  assert.equal(profile.data.profileCustomized, false, '服务端默认资料保留填写入口');
  assert.equal(storage.get('personaLinkProfileCache'), undefined, '默认资料不写本地资料缓存');
  http = (options) => reply(options, { nickname: '小明', avatarUrl: '', customized: false });
  await profile.loadProfile();
  assert.equal(profile.data.profileCustomized, false, '缺少头像时保留填写入口');
  http = (options) => reply(options, { nickname: '小明', avatarUrl: '/uploads/a.png', customized: true });
  await profile.loadProfile();
  assert.equal(profile.data.profileCustomized, true, '用户提供头像昵称后进入展示态');
  const sessions = loginCount;
  await profile.loadProfile();
  assert.equal(loginCount, sessions, '已有会话仍需GET资料，但不重复wx.login');
  loginFails = true;
  storage.delete(api.TOKEN_STORAGE_KEY);
  await profile.loadProfile();
  assert.equal(profile.data.profileState, 'guest');
  assert.equal(profile.data.nickname, '');
  assert.equal(profile.data.avatarUrl, '');
  assert.equal(profile.data.profileCustomized, false);
  assert.equal(loginCount, sessions, '会话清除后读取资料不能自动登录');
  await assert.rejects(api.loginSession(), /微信登录失败/);
  loginFails = false;
  await api.loginSession();
  http = (options) => reply(options, { nickname: '小明', avatarUrl: '' });
  await profile.loadProfile();
  assert.equal(profile.data.profileState, 'ready');
  let saved, saveCount = 0;
  http = (options) => { saveCount++; saved = options.data; reply(options, options.data); };
  await profile.saveProfile({ detail: { value: { nickname: ' 新昵称 ' } } });
  assert.equal(saved.nickname, '新昵称');
  assert.equal(profile.data.nickname, '新昵称');
  profile.handleNicknameInput({ detail: { value: '微信昵称' } });
  assert.equal(saved.nickname, '新昵称', '输入过程只更新草稿，不自动保存');
  assert.equal(profile.data.draftNickname, '微信昵称');
  await profile.handleNicknameBlur({ detail: { value: ' 微信昵称 ' } });
  assert.equal(saveCount, 2, '失焦直接保存一次');
  assert.equal(saved.nickname, '微信昵称', '失焦值去除首尾空格后保存');
  assert.equal(profile.data.nickname, '微信昵称');
  await profile.handleNicknameBlur({ detail: { value: '微信昵称' } });
  assert.equal(saveCount, 2, '昵称未变化的失焦不重复保存');

  // 失焦保存进行中选头像：等保存完成再上传，不丢弃刚选的头像。
  const raceProfile = component();
  raceProfile.profileToken = storage.get(api.TOKEN_STORAGE_KEY);
  Object.assign(raceProfile.data, { profileState: 'ready', nickname: '旧昵称', draftNickname: '旧昵称', avatarUrl: '/uploads/old.png', profileCustomized: true });
  let raceSaveRequest;
  http = (options) => { raceSaveRequest = options; };
  let raceUploadCalled = false;
  upload = (options) => {
    raceUploadCalled = true;
    options.success({ statusCode: 200, data: JSON.stringify({ code: 0, data: { avatarUrl: '/uploads/old.png', reviewing: true } }) });
  };
  const raceBlur = raceProfile.handleNicknameBlur({ detail: { value: '改名中' } });
  const raceChoose = raceProfile.chooseAvatar({ detail: { avatarUrl: 'wxfile://race-avatar' } });
  await new Promise(setImmediate);
  assert.equal(raceUploadCalled, false, '保存未完成不开始上传');
  reply(raceSaveRequest, { nickname: '改名中', avatarUrl: '/uploads/old.png', customized: true });
  await raceBlur;
  await raceChoose;
  assert.equal(raceUploadCalled, true, '保存完成后继续上传刚选的头像');
  assert.equal(raceProfile.data.avatarReviewing, true);
  raceProfile.clearAvatarAuditRefresh();

  const modalsBeforeLongNickname = modals.length;
  await profile.saveProfile({ detail: { value: '昵'.repeat(33) } });
  assert.equal(modals.length, modalsBeforeLongNickname + 1, '超长昵称改为弹窗提示');
  assert.equal(modals.at(-1).title, '保存失败');
  assert.match(modals.at(-1).content, /昵称不能超过32字/);
  assert.equal(profile.data.profileSaving, false);
  modals.at(-1).complete();
  const cacheBeforeUpload = JSON.stringify(storage.get('personaLinkProfileCache'));
  upload = (options) => {
    assert.equal(options.filePath, 'wxfile://temporary-avatar');
    assert.equal(options.header['content-type'], undefined);
    options.success({ statusCode: 200, data: JSON.stringify({ code: 0, data: { avatarUrl: '', reviewing: true } }) });
  };
  await profile.chooseAvatar({ detail: { avatarUrl: 'wxfile://temporary-avatar' } });
  assert.equal(profile.data.avatarReviewing, true, '上传成功后进入审核中，等待微信推送结论');
  assert.equal(profile.data.avatarUrl, '', '审核通过前不展示新头像');
  assert.equal(profile.data.profileCustomized, false, '审核通过前不视为已提供资料');
  assert.equal(JSON.stringify(storage.get('personaLinkProfileCache')), cacheBeforeUpload, '待审头像不覆盖本地资料缓存');
  profile.clearAvatarAuditRefresh();
  http = (options) => { saved = options.data; reply(options, { nickname: options.data.nickname, avatarUrl: options.data.avatarUrl, customized: false, avatarReviewing: true }); };
  await profile.saveProfile();
  assert.equal(saved.avatarUrl, '', '审核中保存资料仍使用当前生效头像');
  assert.equal(profile.data.avatarReviewing, true, '保存资料不能丢掉审核中状态');
  http = (options) => reply(options, { nickname: '微信昵称', avatarUrl: '/uploads/avatar.png', customized: true, avatarReviewing: false });
  await profile.refreshAvatarAudit();
  assert.equal(profile.data.avatarReviewing, false);
  assert.equal(profile.data.avatarUrl, '/uploads/avatar.png');
  assert.equal(profile.data.avatarImage, 'https://example.test/uploads/avatar.png', '审核通过后展示持久化头像');
  assert.equal(profile.data.profileCustomized, true, '审核通过后视为已提供资料');
  assert.ok(storage.get('personaLinkProfileCache'), '审核通过后才写本地缓存');
  const modalsBeforeAuditReject = modals.length;
  Object.assign(profile.data, { avatarReviewing: true, avatarUrl: '/uploads/avatar.png' });
  http = (options) => reply(options, { nickname: '微信昵称', avatarUrl: '/uploads/avatar.png', customized: false, avatarReviewing: false });
  await profile.refreshAvatarAudit();
  assert.equal(profile.data.avatarReviewing, false);
  assert.equal(modals.length, modalsBeforeAuditReject + 1, '审核未通过改为弹窗提示');
  assert.equal(modals.at(-1).title, '头像审核未通过');
  assert.match(modals.at(-1).content, /请重新选择头像/);
  modals.at(-1).complete();

  for (const operation of ['save', 'upload']) {
    for (const pollFirst of [false, true]) {
      const auditing = component();
      auditing.profileToken = storage.get(api.TOKEN_STORAGE_KEY);
      auditing.profileEpoch = 1;
      Object.assign(auditing.data, { profileState: 'ready', nickname: '审核用户', draftNickname: '审核用户',
        avatarUrl: '/current.png', profileCustomized: true, avatarReviewing: true });
      const requests = [];
      let uploadRequest;
      http = (options) => requests.push(options);
      upload = (options) => { uploadRequest = options; };
      let polling;
      if (pollFirst) {
        polling = auditing.refreshAvatarAudit();
        await new Promise(setImmediate);
        assert.equal(requests.length, 1);
      }
      const mutation = operation === 'save'
        ? auditing.saveProfile({ detail: { value: '修改昵称' } })
        : auditing.chooseAvatar({ detail: { avatarUrl: 'wxfile://new-avatar' } });
      await new Promise(setImmediate);
      const busyField = operation === 'save' ? 'profileSaving' : 'avatarUploading';
      assert.equal(auditing.data[busyField], true);
      if (!pollFirst) {
        const count = requests.length;
        await auditing.refreshAvatarAudit();
        assert.equal(requests.length, count, '保存或上传期间跳过轮询，不能使操作回调失效');
      }
      if (operation === 'save') {
        reply(requests.find((options) => options.method === 'PUT'), {
          nickname: '修改昵称', avatarUrl: '/current.png', customized: true, avatarReviewing: true
        });
      } else {
        uploadRequest.success({ statusCode: 200, data: JSON.stringify({ code: 0,
          data: { avatarUrl: '/stale-upload-response.png', reviewing: true } }) });
      }
      await mutation;
      assert.equal(auditing.data[busyField], false, '操作完成必须解除按钮禁用');
      if (pollFirst) {
        // 模拟上一张头像的结论在新保存/上传完成后才返回。
        reply(requests[0], { nickname: '旧昵称', avatarUrl: '/previous-audit.png', customized: true, avatarReviewing: false });
        await polling;
      }
      assert.equal(auditing.data.avatarUrl, '/current.png', '旧轮询和上传响应中的旧头像不能覆盖当前展示');
      assert.equal(auditing.data.avatarReviewing, true, '旧轮询不能清除新操作的待审状态');
      if (operation === 'save') assert.equal(auditing.data.nickname, '修改昵称');
      assert.equal(storage.get('personaLinkProfileCache').reviewing, true, '待审标记与当前生效头像一并缓存');
      auditing.clearAvatarAuditRefresh();
    }
  }
  const pendingReopened = component();
  definition.lifetimes.attached.call(pendingReopened);
  assert.equal(pendingReopened.data.avatarUrl, '/current.png');
  assert.equal(pendingReopened.data.avatarReviewing, true, '首轮轮询前离开后重进仍恢复待审标记');
  assert.ok(pendingReopened.avatarAuditTimer, '重进缓存资料会继续轮询');
  pendingReopened.clearAvatarAuditRefresh();
  http = (options) => reply(options, { nickname: '微信昵称', avatarUrl: '/uploads/avatar.png', customized: true, avatarReviewing: false });
  await pendingReopened.refreshAvatarAudit();
  assert.equal(pendingReopened.data.avatarUrl, '/uploads/avatar.png', '重进后读取已通过的新头像');
  assert.equal(storage.get('personaLinkProfileCache').reviewing, false, '结论落缓存后不重复等待');
  for (const avatarUrl of ['/immediate-approved.png', '/uploads/avatar.png']) {
    upload = (options) => options.success({ statusCode: 200,
      data: JSON.stringify({ code: 0, data: { avatarUrl, reviewing: false } }) });
    await pendingReopened.chooseAvatar({ detail: { avatarUrl: 'wxfile://immediate-result' } });
    assert.equal(pendingReopened.data.avatarUrl, avatarUrl, '上传返回时已有结论，使用服务端当前生效头像');
    assert.equal(pendingReopened.data.avatarReviewing, false, '即时通过或拒绝都不再进入待审');
    assert.equal(storage.get('personaLinkProfileCache').avatarUrl, avatarUrl);
    assert.equal(storage.get('personaLinkProfileCache').reviewing, false);
    assert.ok(!pendingReopened.avatarAuditTimer, '已完成审核不继续轮询');
  }
  for (const transition of ['history', 'hide-show', 'reschedule']) {
    auditTimers.clear();
    const pollingProfile = component();
    pollingProfile.profileToken = storage.get(api.TOKEN_STORAGE_KEY);
    Object.assign(pollingProfile.data, { profileState: 'ready', avatarReviewing: true, avatarUrl: '/current.png' });
    pollingProfile.loadRecords = () => {};
    let pendingPoll;
    http = (options) => { pendingPoll = options; };
    pollingProfile.scheduleAvatarAuditRefresh();
    const timerId = pollingProfile.avatarAuditTimer;
    const callback = auditTimers.get(timerId);
    auditTimers.delete(timerId);
    const polling = callback();
    await new Promise(setImmediate);
    assert.ok(pendingPoll, '审核查询已发出且尚未返回');
    if (transition === 'history') pollingProfile.setCurrentView('history');
    else if (transition === 'hide-show') {
      definition.pageLifetimes.hide.call(pollingProfile);
      definition.pageLifetimes.show.call(pollingProfile);
    } else pollingProfile.scheduleAvatarAuditRefresh();
    const expectedTimers = transition === 'history' ? 0 : 1;
    assert.equal(auditTimers.size, expectedTimers);
    reply(pendingPoll, { nickname: '用户', avatarUrl: '/cancelled-loop.png', avatarReviewing: false });
    await polling;
    assert.equal(pollingProfile.data.avatarUrl, '/current.png', '已失效循环的查询结果不能覆盖当前资料');
    assert.equal(pollingProfile.data.avatarReviewing, true);
    assert.equal(auditTimers.size, expectedTimers, '已取消或被新循环替换的请求完成后不能追加定时器');
    if (transition === 'history') {
      pollingProfile.setCurrentView('profile');
      assert.equal(auditTimers.size, 1, '从历史返回我的，视图切换完成后恢复待审轮询');
    }
    pollingProfile.clearAvatarAuditRefresh();
  }
  http = (options) => reply(options, { nickname: '微信昵称', avatarUrl: '/uploads/avatar.png', customized: true, avatarReviewing: false });
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

  const restored = component();
  api.writeProfileCache('switched-session', '缓存昵称', '/cached.png');
  definition.lifetimes.attached.call(restored);
  assert.ok(restored.frames.every((frame) => frame.profileState === 'ready'), '同账号缓存首帧不能闪 loading');
  assert.equal(restored.data.nickname, '缓存昵称');

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
  assert.equal(modals.at(-1).title, '资料加载失败', '登录状态变化导致的加载失败同样弹窗');
  modals.at(-1).complete();
  let pendingProfile;
  http = (options) => { pendingProfile = options; };
  const loadingBeforeLogout = profile.loadProfile();
  await new Promise(setImmediate);
  api.clearAccountSession();
  definition.pageLifetimes.show.call(profile);
  assert.equal(profile.data.profileState, 'guest', '资料加载中注销后立即切游客');
  reply(pendingProfile, { nickname: '注销前旧资料', avatarUrl: '/old.png', customized: true });
  await loadingBeforeLogout;
  assert.equal(profile.data.profileState, 'guest');
  assert.equal(profile.data.nickname, '');
  assert.equal(profile.data.profileCustomized, false, '旧响应不能恢复资料');
  storage.set(api.TOKEN_STORAGE_KEY, 'other-account');
  http = (options) => reply(options, { nickname: '新账号', avatarUrl: '' });
  await profile.loadProfile();
  storage.set(api.TOKEN_STORAGE_KEY, 'third-account');
  let writes = 0;
  http = () => { writes++; };
  const modalsBeforeStaleSave = modals.length;
  await profile.saveProfile();
  assert.equal(writes, 0, '旧账号草稿不能保存到新会话');
  assert.equal(modals.length, modalsBeforeStaleSave + 1, '保存失败改为弹窗提示');
  assert.equal(modals.at(-1).title, '保存失败');
  assert.match(modals.at(-1).content, /登录状态已变化/);
  modals.at(-1).complete();

  let uploads = 0;
  upload = (options) => {
    uploads++;
    options.success(uploads === 1 ? { statusCode: 401, data: 'expired' }
      : { statusCode: 200, data: '{"code":0,"data":{"avatarUrl":"/uploads/new.png"}}' });
  };
  const refreshed = await api.authenticatedUploadData({ url: '/api/miniapp/profile/avatar', filePath: 'wxfile://avatar' });
  assert.equal(refreshed.avatarUrl, '/uploads/new.png');
  assert.equal(uploads, 2, '会话失效后静默换发会话并重试一次上传');
  await api.loginSession();
  const previousToken = storage.get(api.TOKEN_STORAGE_KEY);
  await api.loginSession();
  upload = (options) => options.success({ statusCode: 401, data: 'expired' });
  await assert.rejects(api.authenticatedUploadData({ url: '/api/miniapp/profile/avatar', filePath: 'x', expectedToken: previousToken }), /登录状态已变化/);
  await api.loginSession();
  upload = (options) => options.success({ statusCode: 200, data: '<html>broken</html>' });
  await assert.rejects(api.authenticatedUploadData({ url: '/api/miniapp/profile/avatar', filePath: 'x' }), /响应异常/);
  upload = (options) => options.fail({ errMsg: '网络断开' });
  await assert.rejects(api.authenticatedUploadData({ url: '/api/miniapp/profile/avatar', filePath: 'x' }), /网络断开/);

  // 头像上传失败改为弹窗；弹窗未关闭时的再次失败不叠加，用 toast 兜底。
  await api.loginSession();
  const uploadFailed = component();
  uploadFailed.profileToken = storage.get(api.TOKEN_STORAGE_KEY);
  Object.assign(uploadFailed.data, { profileState: 'ready', nickname: '上传用户', draftNickname: '上传用户' });
  const modalsBeforeUploadFail = modals.length;
  await uploadFailed.chooseAvatar({ detail: { avatarUrl: 'wxfile://broken-avatar' } });
  assert.equal(uploadFailed.data.avatarUploading, false);
  assert.equal(modals.length, modalsBeforeUploadFail + 1, '上传失败弹窗提示');
  assert.equal(modals.at(-1).title, '头像上传失败');
  assert.match(modals.at(-1).content, /网络断开/);
  assert.equal(modals.at(-1).confirmText, '知道了');
  const toastsBeforeSuppressed = toasts.length;
  await uploadFailed.chooseAvatar({ detail: { avatarUrl: 'wxfile://broken-avatar' } });
  assert.equal(modals.length, modalsBeforeUploadFail + 1, '弹窗打开时不再叠加弹窗');
  assert.equal(toasts.length, toastsBeforeSuppressed + 1, '被抑制的失败用 toast 兜底');
  assert.equal(toasts.at(-1).title, '网络断开');
  modals.at(-1).complete();
  assert.ok(consentCount > 0);
  const originalConsent = app.verifyConsent;
  let finishConsent;
  app.verifyConsent = () => new Promise((resolve) => { finishConsent = resolve; });
  let profileCalls = 0;
  http = () => { profileCalls++; };
  const checking = api.fetchProfile();
  api.clearAccountSession();
  finishConsent();
  await assert.rejects(checking, /登录状态已变化/);
  assert.equal(profileCalls, 0, '同意校验期间注销不得读取新账号或重新登录');
  app.verifyConsent = originalConsent;

  // 注销后的游客由本人点击卡片并确认登录才建立新会话，仍不允许进入页面自动建号。
  api.clearAccountSession();
  const cancelled = component();
  definition.lifetimes.attached.call(cancelled);
  const loginsBeforeCancel = loginCount;
  const modalsBeforeCancel = modals.length;
  const toastsBeforeCancel = toasts.length;
  const cancelledTap = cancelled.createGuestSession();
  assert.equal(modals.length, modalsBeforeCancel + 1, '点击游客卡片先弹登录确认框');
  modals.at(-1).success({ confirm: false });
  await cancelledTap;
  assert.equal(loginCount, loginsBeforeCancel, '选择不登录不建立会话');
  assert.equal(cancelled.data.profileState, 'guest');
  assert.equal(toasts.length, toastsBeforeCancel, '选择不登录不提示登录成功');
  const failedModalTap = cancelled.createGuestSession();
  modals.at(-1).fail({ errMsg: 'showModal:fail' });
  await failedModalTap;
  assert.equal(loginCount, loginsBeforeCancel, '确认框异常不建立会话');
  assert.equal(cancelled.data.profileState, 'guest');
  assert.equal(toasts.length, toastsBeforeCancel);

  const refused = component();
  definition.lifetimes.attached.call(refused);
  const loginsBeforeRefuse = loginCount;
  const toastsBeforeRefuse = toasts.length;
  app.verifyConsent = () => Promise.reject(new Error('协议已更新，请重新阅读并同意'));
  const refusedTap = refused.createGuestSession();
  modals.at(-1).success({ confirm: true });
  await refusedTap;
  app.verifyConsent = originalConsent;
  assert.equal(refused.data.profileState, 'guest', '协议未通过不能建立会话');
  assert.equal(modals.at(-1).title, '登录失败');
  assert.match(modals.at(-1).content, /协议已更新/);
  assert.equal(modals.at(-1).confirmText, '知道了');
  modals.at(-1).complete();
  assert.equal(loginCount, loginsBeforeRefuse, '协议门禁必须先于建号');
  assert.equal(toasts.length, toastsBeforeRefuse, '协议未通过不提示登录成功');

  api.clearAccountSession();
  let pendingProfileTap;
  const concurrent = component();
  definition.lifetimes.attached.call(concurrent);
  http = (options) => { pendingProfileTap = options; };
  const loginsBeforeTaps = loginCount;
  const modalsBeforeTaps = modals.length;
  const firstTap = concurrent.createGuestSession();
  const secondTap = concurrent.createGuestSession();
  assert.equal(modals.length, modalsBeforeTaps + 1, '等待确认期间重复点击不弹第二个确认框');
  modals.at(-1).success({ confirm: true });
  await new Promise(setImmediate);
  assert.ok(pendingProfileTap, '确认后完成建号并发送资料请求');
  assert.equal(loginCount, loginsBeforeTaps + 1, '加载期间重复点击不重复建号');
  reply(pendingProfileTap, { nickname: '用户660002', avatarUrl: '/uploads/default.png', customized: false });
  await Promise.all([firstTap, secondTap]);
  assert.equal(concurrent.data.profileState, 'ready');
  assert.equal(toasts.at(-1).title, '登录成功', '建号成功后提示登录成功');

  api.clearAccountSession();
  const tapped = component();
  definition.lifetimes.attached.call(tapped);
  assert.equal(tapped.data.profileState, 'guest');
  http = (options) => reply(options, { nickname: '用户660001', avatarUrl: '/uploads/default.png', customized: false });
  const loginsBeforeTap = loginCount;
  const toastsBeforeTap = toasts.length;
  const tapping = tapped.createGuestSession();
  assert.equal(tapped.data.profileState, 'guest', '用户确认前保持游客态');
  modals.at(-1).success({ confirm: true });
  await tapping;
  assert.equal(loginCount, loginsBeforeTap + 1, '游客确认登录后才建立新会话');
  assert.ok(storage.get(api.TOKEN_STORAGE_KEY), '建号成功后写入本机会话');
  assert.equal(tapped.data.profileState, 'ready');
  assert.equal(tapped.data.nickname, '用户660001', '新账号展示服务端默认资料');
  assert.equal(tapped.data.profileCustomized, false, '新账号保留头像昵称填写入口');
  assert.equal(toasts.length, toastsBeforeTap + 1, '登录成功提示只弹一次');
  assert.equal(toasts.at(-1).title, '登录成功', '登录成功后给出提示');
  const modalsAfterTap = modals.length;
  await tapped.createGuestSession();
  assert.equal(loginCount, loginsBeforeTap + 1, '已建号后重复点击不重复登录');
  assert.equal(modals.length, modalsAfterTap, '已建号后点击卡片不再弹确认框');
  api.clearAccountSession();
  loginFails = true;
  const tappedFailed = component();
  definition.lifetimes.attached.call(tappedFailed);
  const modalsBeforeFailed = modals.length;
  const toastsBeforeFailed = toasts.length;
  const failedTap = tappedFailed.createGuestSession();
  assert.equal(modals.length, modalsBeforeFailed + 1);
  modals.at(-1).success({ confirm: true });
  await failedTap;
  loginFails = false;
  assert.equal(tappedFailed.data.profileState, 'guest', '建号失败回到游客态可重试');
  assert.equal(modals.at(-1).title, '登录失败');
  assert.match(modals.at(-1).content, /微信登录失败/);
  modals.at(-1).complete();
  assert.equal(toasts.length, toastsBeforeFailed, '建号失败不提示登录成功');

  // 资料加载失败弹窗：确认重新加载、取消关闭；取消后回到本页自动重试。
  storage.set(api.TOKEN_STORAGE_KEY, 'reload-session');
  let profileRequests = 0;
  const failProfileRequest = (options) => {
    profileRequests++;
    options.success({ statusCode: 500, data: { code: 50000, message: '资料服务异常' } });
  };
  http = failProfileRequest;
  const loadCancelled = component();
  const modalsBeforeLoad = modals.length;
  await loadCancelled.loadProfile();
  assert.equal(loadCancelled.data.profileState, 'error');
  assert.equal(modals.length, modalsBeforeLoad + 1, '加载失败弹窗');
  assert.equal(modals.at(-1).title, '资料加载失败');
  assert.match(modals.at(-1).content, /资料服务异常/);
  assert.equal(modals.at(-1).confirmText, '重新加载');
  assert.equal(modals.at(-1).cancelText, '取消');
  modals.at(-1).success({ confirm: false });
  modals.at(-1).complete();
  await new Promise(setImmediate);
  assert.equal(profileRequests, 1, '取消关闭不重新加载');

  const loadRetried = component();
  await loadRetried.loadProfile();
  assert.equal(profileRequests, 2);
  http = (options) => { profileRequests++; reply(options, { nickname: '重试成功', avatarUrl: '', customized: false }); };
  modals.at(-1).success({ confirm: true });
  modals.at(-1).complete();
  await new Promise(setImmediate);
  assert.equal(loadRetried.data.profileState, 'ready', '确认后重新加载成功');
  assert.equal(loadRetried.data.nickname, '重试成功');

  http = failProfileRequest;
  const showRetried = component();
  await showRetried.loadProfile();
  assert.equal(showRetried.data.profileState, 'error');
  modals.at(-1).complete();
  http = (options) => { profileRequests++; reply(options, { nickname: '回到页面重试', avatarUrl: '', customized: false }); };
  const requestsBeforeShow = profileRequests;
  definition.pageLifetimes.show.call(showRetried);
  await new Promise(setImmediate);
  assert.equal(showRetried.data.profileState, 'ready', '取消失败后回到我的页自动重试');
  assert.equal(showRetried.data.nickname, '回到页面重试');
  assert.equal(profileRequests, requestsBeforeShow + 1, '自动重试只加载一次');

  // 游客匿名提交反馈：不建号、不带令牌；登录用户提交自动携带会话。
  api.clearAccountSession();
  const guestFeedback = component();
  guestFeedback.data.currentView = 'feedback';
  guestFeedback.data.feedbackContent = '游客反馈内容';
  const loginsBeforeFeedback = loginCount;
  let feedbackRequest;
  http = (options) => { feedbackRequest = options; reply(options, { id: 1 }); };
  await guestFeedback.submitFeedback();
  assert.equal(loginCount, loginsBeforeFeedback, '匿名反馈不建立新会话');
  assert.equal(storage.get(api.TOKEN_STORAGE_KEY), undefined, '匿名反馈后仍无本地会话');
  assert.ok(feedbackRequest.url.endsWith('/api/miniapp/feedbacks'));
  assert.equal(feedbackRequest.method, 'POST');
  assert.equal(feedbackRequest.data.content, '游客反馈内容');
  assert.equal(feedbackRequest.header.Authorization, undefined, '匿名反馈不携带令牌');
  assert.equal(guestFeedback.data.feedbackContent, '', '提交成功后清空反馈内容');
  assert.equal(guestFeedback.data.feedbackSubmitting, false);
  assert.equal(toasts.at(-1).title, '提交成功');
  guestFeedback.clearFeedbackRedirect();

  await api.loginSession();
  const loggedFeedback = component();
  loggedFeedback.data.currentView = 'feedback';
  loggedFeedback.data.feedbackContent = '登录反馈内容';
  http = (options) => { feedbackRequest = options; reply(options, { id: 2 }); };
  await loggedFeedback.submitFeedback();
  assert.equal(feedbackRequest.header.Authorization, `Bearer ${storage.get(api.TOKEN_STORAGE_KEY)}`, '登录反馈携带会话令牌');
  loggedFeedback.clearFeedbackRedirect();
  api.clearAccountSession();
  console.log('PROFILE_LOGIN_CHECK_OK guest-first-frame/cache/no-auto-login/logout-during-load/session-isolation/upload/guest-tap-confirm/profile-error-dialog/guest-feedback');
}
const timeout = setTimeout(() => { console.error('FAIL profile check timed out'); process.exit(1); }, 10000);
run().then(() => clearTimeout(timeout)).catch((error) => { clearTimeout(timeout); console.error(error); process.exitCode = 1; });
