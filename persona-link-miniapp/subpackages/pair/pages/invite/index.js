const { authenticatedRequestData, createIdempotencyKey } = require('../../../../utils/request');
const { trackEvent } = require('../../../../utils/analytics');

const PAIR_SESSION_KEY = 'personaPairSession';

Page({
  data: {
    state: 'loading',
    pairCode: '',
    shareable: true,
    errorDescription: '暂时无法创建邀请，请稍后重试。'
  },

  onLoad(options) {
    this.answerSessionId = options.answerSessionId || '';
    this.createPair();
  },

  onShow() {
    this.refreshShareState();
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
      this.shareStateVersion = (this.shareStateVersion || 0) + 1;
      wx.setStorageSync(PAIR_SESSION_KEY, {
        pairSessionId: result.pair.pairSessionId,
        pairCode: result.inviteToken,
        myRole: result.pair.myRole
      });
      this.setData({ state: 'ready', pairCode: result.inviteToken, shareable: true });
    } catch (error) {
      this.setData({ state: 'error', errorDescription: error.message || '邀请创建失败' });
    }
  },

  // 页面可能被长时间挂起，返回时对方也许已经加入，需要隐藏失效的分享入口。
  async refreshShareState() {
    if (!this.pairSessionId) return;
    const version = this.shareStateVersion = (this.shareStateVersion || 0) + 1;
    try {
      const pair = await authenticatedRequestData({
        url: `/api/miniapp/pairs/${encodeURIComponent(this.pairSessionId)}`
      });
      if (version !== this.shareStateVersion) return;
      const shareable = Number(pair.pairStatus) === 1;
      if (shareable !== this.data.shareable) this.setData({ shareable });
    } catch (error) {
      // 状态查询失败时保留当前分享入口，不阻塞分享。
    }
  },

  copyPairCode() {
    if (this.data.shareable && this.data.pairCode) wx.setClipboardData({ data: this.data.pairCode });
  },

  onShareAppMessage() {
    if (!this.data.shareable) {
      return { title: '映见你我', path: '/pages/home/index' };
    }
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
