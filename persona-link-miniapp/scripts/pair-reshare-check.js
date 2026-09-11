const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const root = path.join(__dirname, '..');
const read = (file) => fs.readFileSync(path.join(root, file), 'utf8');

function makePageInstance(definition) {
  const instance = Object.assign({}, definition, { data: Object.assign({}, definition.data) });
  instance.setData = function (patch, callback) {
    Object.assign(this.data, patch);
    if (callback) callback();
  };
  return instance;
}

const flush = () => new Promise((resolve) => setImmediate(resolve));

async function main() {
  // ===== wait 页：发起者等待中可重新分享，对方加入后隐藏 =====
  let waitDefinition;
  const waitReplies = [];
  const clipboard = [];
  const shareEvents = [];
  vm.runInNewContext(read('subpackages/pair/pages/wait/index.js'), {
    Page(value) { waitDefinition = value; },
    require: (name) => {
      if (name.endsWith('/analytics')) return { trackEvent(...args) { shareEvents.push(args); } };
      if (name.endsWith('/image')) return { resolveImageUrl: (url) => (url ? `https://example.com${url}` : '') };
      return {
        requestData: async () => ({ joinHeroImageUrl: '/uploads/pair-hero.png' }),
        authenticatedRequestData: async () => waitReplies.shift()
      };
    },
    wx: {
      getStorageSync: () => '',
      setClipboardData: ({ data }) => clipboard.push(data),
      navigateTo() {},
      redirectTo() {}
    }
  });
  const wait = makePageInstance(waitDefinition);
  wait.onLoad({ pairSessionId: '99' });
  await flush();
  assert.equal(wait.data.joinHeroImageUrl, 'https://example.com/uploads/pair-hero.png',
    '配对进度页必须复用加入页头图配置');

  waitReplies.push({ pairStatus: 1, myRole: 'INITIATOR', inviteToken: 'ABCDE' });
  await wait.refreshStatus();
  assert.equal(wait.data.canReshare, true, '发起者等待中必须展示邀请入口');
  assert.equal(wait.data.inviteCode, 'ABCDE');
  const share = wait.onShareAppMessage();
  assert.equal(share.path, '/subpackages/pair/pages/join/index?code=ABCDE');
  assert.equal(shareEvents.at(-1)[0], 5, '重新分享必须上报分享事件');
  wait.copyPairCode();
  assert.equal(clipboard.at(-1), 'ABCDE');

  waitReplies.push({ pairStatus: 2, myRole: 'INITIATOR', inviteToken: null });
  await wait.refreshStatus();
  assert.equal(wait.data.canReshare, false, '对方加入后必须隐藏邀请入口');
  assert.equal(wait.data.inviteCode, '');
  assert.equal(wait.onShareAppMessage().path, '/pages/home/index', '无邀请码时转发回首页');

  waitReplies.push({ pairStatus: 1, myRole: 'PARTNER', inviteToken: 'ABCDE' });
  await wait.refreshStatus();
  assert.equal(wait.data.canReshare, false, '受邀者不展示邀请入口');

  const waitTemplate = read('subpackages/pair/pages/wait/index.wxml');
  assert.match(waitTemplate, /wx:if="\{\{canReshare\}\}"/);
  assert.match(waitTemplate, /open-type="share"/);
  assert.match(waitTemplate, /bindtap="copyPairCode"/);
  assert.match(waitTemplate, />分享<\/button>/);
  assert.match(waitTemplate, /wait-hero/);
  assert.match(waitTemplate, /src="\{\{joinHeroImageUrl\}\}"/);
  assert.match(waitTemplate, /binderror="handleJoinHeroImageError"/);

  // ===== 测试记录：等待中加入邀请好友，已配对隐藏 =====
  let accountDefinition;
  const accountNavigations = [];
  let accountReply = null;
  vm.runInNewContext(read('components/account-center/index.js'), {
    Component(value) { accountDefinition = value; },
    require: (name) => name.endsWith('/image')
      ? { resolveImageUrl: (url) => url || '', resolvePreviewUrl: (url) => url || '' }
      : {
        authenticatedRequestData: async () => accountReply,
        authenticatedUploadData: async () => ({}),
        createIdempotencyKey: () => 'id',
        readProfileCache: () => null,
        writeProfileCache: () => {},
        fetchProfile: async () => ({}),
        TOKEN_STORAGE_KEY: 'token'
      },
    wx: {
      navigateTo: ({ url }) => accountNavigations.push(url),
      showToast() {},
      showModal() {},
      getStorageSync: () => ''
    }
  });
  const account = Object.assign({}, accountDefinition.methods, {
    componentAttached: true,
    data: { currentView: 'history', pairRecords: [] },
    setData(patch, callback) { Object.assign(this.data, patch); if (callback) callback(); }
  });
  accountReply = {
    records: [
      { pairSessionId: '1', pairStatus: 1, createdAt: '2026-09-11 10:00:00' },
      { pairSessionId: '2', pairStatus: 2, createdAt: '2026-09-11 10:00:00' },
      { pairSessionId: '3', pairStatus: 4, createdAt: '2026-09-11 10:00:00' }
    ],
    total: 3
  };
  await account.loadHistoryPage(true, 'pair');
  assert.equal(account.data.pairRecords[0].canReshare, true, '等待加入的记录必须展示邀请好友');
  assert.equal(account.data.pairRecords[1].canReshare, false, '对方已加入必须隐藏邀请好友');
  assert.equal(account.data.pairRecords[2].canReshare, false, '报告已生成必须隐藏邀请好友');
  account.resharePair({ currentTarget: { dataset: { pairSessionId: '1' } } });
  assert.equal(accountNavigations.at(-1), '/subpackages/pair/pages/wait/index?pairSessionId=1');
  const accountTemplate = read('components/account-center/index.wxml');
  assert.match(accountTemplate, /wx:if="\{\{item.canReshare\}\}"/);
  assert.match(accountTemplate, /catchtap="resharePair"/);
  assert.match(accountTemplate, /history-record--invalid/);
  const accountStyle = read('components/account-center/index.wxss');
  assert.match(accountStyle, /\.history-record--invalid \.history-record__result \{\s*color: #817d81;/,
    '本次配对已失效必须使用灰色字体');

  // ===== join 页：分享消息重复点击自动分流 =====
  let joinDefinition;
  const joinRedirects = [];
  let joinReplies = [];
  vm.runInNewContext(read('subpackages/pair/pages/join/index.js'), {
    Page(value) { joinDefinition = value; },
    require: (name) => name.endsWith('/image')
      ? { resolveImageUrl: () => '' }
      : {
        requestData: async () => ({}),
        authenticatedRequestData: async () => joinReplies.shift(),
        createIdempotencyKey: () => 'join-id'
      },
    wx: {
      redirectTo: ({ url }) => joinRedirects.push(url),
      navigateTo() {},
      setStorageSync() {},
      setClipboardData() {}
    }
  });
  const openJoin = (reply) => {
    const instance = makePageInstance(joinDefinition);
    joinReplies = [reply];
    instance.onLoad({ code: 'abcde' });
    return instance;
  };

  openJoin({ pairSessionId: '99', pairStatus: 4, myRole: 'PARTNER', answerSessionId: '77', joinable: false });
  await flush();
  assert.equal(joinRedirects.at(-1), '/subpackages/account/pages/review/index?pairSessionId=99',
    '对方已作答再次点击分享消息必须直接进入作答回顾');

  openJoin({ pairSessionId: '99', pairStatus: 2, myRole: 'PARTNER', answerSessionId: '77', joinable: false });
  await flush();
  assert.equal(joinRedirects.at(-1),
    '/subpackages/test/pages/quiz/index?answerSessionId=77&flow=pair-partner&pairSessionId=99',
    '受邀者未答完必须回到答题页');

  openJoin({ pairSessionId: '99', pairStatus: 1, myRole: 'INITIATOR', answerSessionId: '10', joinable: false });
  await flush();
  assert.equal(joinRedirects.at(-1), '/subpackages/pair/pages/wait/index?pairSessionId=99',
    '发起者点击自己的分享消息进入配对进度');

  const count = joinRedirects.length;
  const open = openJoin({ pairSessionId: null, pairStatus: 1, myRole: null, joinable: true });
  await flush();
  assert.equal(joinRedirects.length, count, '可加入时不跳转');
  assert.equal(open.data.inviteBlocked, false);

  const taken = openJoin({ pairSessionId: null, pairStatus: 2, myRole: null, joinable: false });
  await flush();
  assert.equal(taken.data.inviteBlocked, true, '邀请已被加入必须禁用加入按钮');
  assert.equal(taken.data.errorMessage, '邀请已被加入');

  const missing = openJoin(Promise.reject(Object.assign(new Error('邀请不存在'), { statusCode: 404 })));
  await flush();
  assert.equal(missing.data.inviteBlocked, true);
  assert.equal(missing.data.errorMessage, '邀请不存在');

  const joinTemplate = read('subpackages/pair/pages/join/index.wxml');
  assert.match(joinTemplate, /wx:if="\{\{!inviteBlocked\}\}"/);

  // ===== invite 页：对方加入后隐藏失效的分享入口 =====
  let inviteDefinition;
  const inviteReplies = [];
  vm.runInNewContext(read('subpackages/pair/pages/invite/index.js'), {
    Page(value) { inviteDefinition = value; },
    require: (name) => name.endsWith('/analytics')
      ? { trackEvent() {} }
      : {
        authenticatedRequestData: async () => inviteReplies.shift(),
        createIdempotencyKey: () => 'pair-id'
      },
    wx: { getStorageSync: () => '', setStorageSync() {}, setClipboardData() {} }
  });
  const invite = makePageInstance(inviteDefinition);
  invite.pairSessionId = '99';
  inviteReplies.push({ pairStatus: 2 });
  await invite.refreshShareState();
  assert.equal(invite.data.shareable, false, '对方加入后邀请页必须隐藏分享按钮');
  assert.equal(invite.onShareAppMessage().path, '/pages/home/index');
  inviteReplies.push({ pairStatus: 1 });
  await invite.refreshShareState();
  assert.equal(invite.data.shareable, true, '仍在等待时保持分享入口');
  const inviteTemplate = read('subpackages/pair/pages/invite/index.wxml');
  assert.match(inviteTemplate, /wx:if="\{\{shareable\}\}"/);
  assert.match(inviteTemplate, /invite-joined-note/);

  console.log('PAIR_RESHARE_CHECK_OK wait-share/record-entry/join-routing/blocked/invite-shareable/templates');
}

main().catch((error) => { console.error(error); process.exitCode = 1; });
