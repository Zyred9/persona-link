const { requestData, authenticatedRequestData, createIdempotencyKey } = require('../../../../utils/request');
const { resolveImageUrl } = require('../../../../utils/image');

const PAIR_SESSION_KEY = 'personaPairSession';

Page({
  data: {
    joinHeroImageUrl: '',
    pairCode: '',
    errorMessage: '',
    joining: false
  },

  onLoad(options) {
    if (options.code) {
      this.setData({ pairCode: String(options.code).trim().toUpperCase().slice(0, 5) });
    }
    this.loadJoinHeroImage();
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
    this.setData({ pairCode, errorMessage: '' });
  },

  clearPairCode() {
    this.setData({ pairCode: '', errorMessage: '' });
  },

  async confirmJoin() {
    if (this.data.joining) return;
    if (!/^[A-Z0-9]{5}$/.test(this.data.pairCode)) {
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
