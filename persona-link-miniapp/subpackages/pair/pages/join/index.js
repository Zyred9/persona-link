const { requestData, authenticatedRequestData, createIdempotencyKey } = require('../../../../utils/request');
const { resolveImageUrl } = require('../../../../utils/image');

const PAIR_SESSION_KEY = 'personaPairSession';
const PAIR_CODE_PATTERN = /^[A-Z0-9]{5}$/;

Page({
  data: {
    joinHeroImageUrl: '',
    pairCode: '',
    errorMessage: '',
    inviteBlocked: false,
    joining: false
  },

  onLoad(options) {
    if (options.code) {
      this.setData({ pairCode: String(options.code).trim().toUpperCase().slice(0, 5) });
      this.resolveInvite();
    }
    this.loadJoinHeroImage();
  },

  // 分享消息可被重复点击：报告生成后搭子直接进结果页，已配对的非搭子回首页，未加入的保留手动加入表单。
  async resolveInvite() {
    const pairCode = this.data.pairCode;
    if (!PAIR_CODE_PATTERN.test(pairCode)) return;
    try {
      const invite = await authenticatedRequestData({
        url: `/api/miniapp/pairs/invite/${encodeURIComponent(pairCode)}`
      });
      if (this.data.pairCode !== pairCode) return;
      this.routeInvite(invite);
    } catch (error) {
      if (this.data.pairCode !== pairCode) return;
      if (error.statusCode === 404) {
        this.setData({ errorMessage: error.message || '邀请不存在', inviteBlocked: true });
      }
      // 网络异常时保留表单，用户仍可手动确认加入。
    }
  },

  routeInvite(invite) {
    if (!invite || typeof invite !== 'object') return;
    const pairSessionId = invite.pairSessionId ? String(invite.pairSessionId) : '';
    const pairStatus = Number(invite.pairStatus);
    // 邀请已失效或已取消，任何点击者都没有可继续的流程，统一回首页。
    if (pairStatus === 5 || pairStatus === 6) {
      getApp().returnToHome();
      return;
    }
    const isParticipant = (invite.myRole === 'INITIATOR' || invite.myRole === 'PARTNER') && !!pairSessionId;
    if (isParticipant) {
      // 报告已生成，搭子重复点击分享消息直接进入结果页。
      if (pairStatus === 4) {
        wx.redirectTo({ url: `/subpackages/pair/pages/result/index?pairSessionId=${encodeURIComponent(pairSessionId)}` });
        return;
      }
      if (invite.myRole === 'INITIATOR') {
        wx.redirectTo({ url: `/subpackages/pair/pages/wait/index?pairSessionId=${encodeURIComponent(pairSessionId)}` });
        return;
      }
      if (pairStatus === 2 && invite.answerSessionId) {
        wx.redirectTo({
          url: `/subpackages/test/pages/quiz/index?answerSessionId=${encodeURIComponent(invite.answerSessionId)}&flow=pair-partner&pairSessionId=${encodeURIComponent(pairSessionId)}`
        });
        return;
      }
      wx.redirectTo({ url: `/subpackages/account/pages/review/index?pairSessionId=${encodeURIComponent(pairSessionId)}` });
      return;
    }
    // 非搭子：仅未配对且可加入时保留加入表单，其余已配对场景统一回首页。
    if (!invite.joinable) {
      getApp().returnToHome();
    }
  },

  async loadJoinHeroImage() {
    try {
      const config = await requestData({ url: '/api/miniapp/pairs/config' });
      this.setData({ joinHeroImageUrl: resolveImageUrl(config && config.joinHeroImageUrl) });
    } catch (error) {
      // 配置读取失败时保持隐藏头图，不阻塞加入流程。
      this.setData({ joinHeroImageUrl: '' });
    }
  },

  handleJoinHeroImageError() {
    this.setData({ joinHeroImageUrl: '' });
  },

  updatePairCode(event) {
    const pairCode = event.detail.value.toUpperCase().replace(/[^A-Z0-9]/g, '').slice(0, 5);
    this.setData({ pairCode, errorMessage: '', inviteBlocked: false });
  },

  clearPairCode() {
    this.setData({ pairCode: '', errorMessage: '', inviteBlocked: false });
  },

  async confirmJoin() {
    if (this.data.joining || this.data.inviteBlocked) return;
    if (!PAIR_CODE_PATTERN.test(this.data.pairCode)) {
      this.setData({ errorMessage: '请输入 5 位字母或数字配对码' });
      return;
    }
    this.setData({ joining: true, errorMessage: '' });
    this.createRequestId = this.createRequestId || createIdempotencyKey('pair-join');
    try {
      const pair = await authenticatedRequestData({
        url: '/api/miniapp/pairs/join',
        method: 'POST',
        data: { inviteToken: this.data.pairCode, createRequestId: this.createRequestId }
      });
      wx.setStorageSync(PAIR_SESSION_KEY, {
        pairSessionId: pair.pairSessionId,
        pairCode: this.data.pairCode,
        myRole: pair.myRole
      });
      wx.navigateTo({
        url: `/subpackages/test/pages/quiz/index?answerSessionId=${encodeURIComponent(pair.partnerAnswerSessionId)}&flow=pair-partner&pairSessionId=${encodeURIComponent(pair.pairSessionId)}`,
        complete: () => this.setData({ joining: false })
      });
    } catch (error) {
      this.setData({ joining: false, errorMessage: error.message || '加入配对失败' });
    }
  }
});
