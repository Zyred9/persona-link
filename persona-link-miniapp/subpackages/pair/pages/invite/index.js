const { authenticatedRequestData, createIdempotencyKey } = require('../../../../utils/request');
const { trackEvent } = require('../../../../utils/analytics');

const PAIR_SESSION_KEY = 'personaPairSession';

Page({
  data: {
    state: 'loading',
    pairCode: '',
    errorDescription: '暂时无法创建邀请，请稍后重试。'
  },

  onLoad(options) {
    this.answerSessionId = options.answerSessionId || '';
    this.createPair();
  },

  async createPair() {
    if (!this.answerSessionId) {
      this.setData({ state: 'error', errorDescription: '答卷参数缺失，请完成双人答题后重试。' });
      return;
    }
    this.setData({ state: 'loading' });
    const requestKey = `pairCreateRequest:${this.answerSessionId}`;
    const createRequestId = wx.getStorageSync(requestKey) || createIdempotencyKey('pair');
    wx.setStorageSync(requestKey, createRequestId);
    try {
      const result = await authenticatedRequestData({
        url: '/api/miniapp/pairs',
        method: 'POST',
        data: { answerSessionId: this.answerSessionId, createRequestId }
      });
      this.pairSessionId = result.pair.pairSessionId;
      wx.setStorageSync(PAIR_SESSION_KEY, {
        pairSessionId: result.pair.pairSessionId,
        pairCode: result.inviteToken,
        myRole: result.pair.myRole
      });
      this.setData({ state: 'ready', pairCode: result.inviteToken });
    } catch (error) {
      this.setData({ state: 'error', errorDescription: error.message || '邀请创建失败' });
    }
  },

  copyPairCode() {
    if (this.data.pairCode) wx.setClipboardData({ data: this.data.pairCode });
  },

  onShareAppMessage() {
    trackEvent(5, '/subpackages/pair/pages/invite/index', this.pairSessionId);
    return {
      title: '来和我一起完成双人测试',
      path: `/subpackages/pair/pages/join/index?code=${encodeURIComponent(this.data.pairCode)}`
    };
  },

  retry() {
    this.createPair();
  }
});
