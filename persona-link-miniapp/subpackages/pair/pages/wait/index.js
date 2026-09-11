const { requestData, authenticatedRequestData } = require('../../../../utils/request');
const { trackEvent } = require('../../../../utils/analytics');
const { resolveImageUrl } = require('../../../../utils/image');

const PAIR_SESSION_KEY = 'personaPairSession';

Page({
  data: {
    state: 'loading',
    joinHeroImageUrl: '',
    partnerCompleted: false,
    partnerStatusText: '待完成',
    waitTitle: '等待对方完成',
    reportReady: false,
    canCancel: false,
    canReshare: false,
    inviteCode: '',
    errorDescription: '暂时无法查询配对进度，请稍后重试。'
  },

  onLoad(options) {
    const stored = wx.getStorageSync(PAIR_SESSION_KEY) || {};
    this.pairSessionId = options.pairSessionId || stored.pairSessionId || '';
    this.loadJoinHeroImage();
  },

  // 与加入页共用同一张配置头图，保持双人流程视觉一致。
  async loadJoinHeroImage() {
    try {
      const config = await requestData({ url: '/api/miniapp/pairs/config' });
      this.setData({ joinHeroImageUrl: resolveImageUrl(config && config.joinHeroImageUrl) });
    } catch (error) {
      // 配置读取失败时保持隐藏头图，不阻塞配对进度。
      this.setData({ joinHeroImageUrl: '' });
    }
  },

  handleJoinHeroImageError() {
    this.setData({ joinHeroImageUrl: '' });
  },

  onShow() {
    this.refreshStatus();
  },

  async refreshStatus() {
    if (!this.pairSessionId) {
      this.setData({ state: 'error', errorDescription: '配对参数缺失，请从邀请页重新进入。' });
      return;
    }
    try {
      const pair = await authenticatedRequestData({
        url: `/api/miniapp/pairs/${encodeURIComponent(this.pairSessionId)}`
      });
      const pairStatus = Number(pair.pairStatus);
      const reportReady = pairStatus === 4;
      const invalid = pairStatus === 5 || pairStatus === 6;
      const partnerCompleted = reportReady || pairStatus === 3 || pair.myRole === 'PARTNER';
      const partnerStatusText = invalid ? '已失效' : partnerCompleted ? '已完成' : Number(pair.pairStatus) === 2 ? '作答中' : '待加入';
      // 只有发起者等待对方加入期间才需要重新分享，对方加入后邀请链接不再可用。
      const inviteCode = typeof pair.inviteToken === 'string' ? pair.inviteToken : '';
      const canReshare = pairStatus === 1 && pair.myRole === 'INITIATOR' && !!inviteCode;
      this.setData({
        state: 'ready',
        partnerCompleted,
        partnerStatusText,
        reportReady,
        canCancel: pairStatus === 1 || pairStatus === 2,
        canReshare,
        inviteCode: canReshare ? inviteCode : '',
        waitTitle: invalid ? '本次配对已失效' : reportReady ? '双方已完成' : pairStatus === 3 ? '报告生成中' : '等待对方完成'
      });
    } catch (error) {
      this.setData({ state: 'error', errorDescription: error.message || '配对状态查询失败' });
    }
  },

  copyPairCode() {
    if (this.data.inviteCode) wx.setClipboardData({ data: this.data.inviteCode });
  },

  onShareAppMessage() {
    if (!this.data.canReshare) {
      return { title: '映见你我', path: '/pages/home/index' };
    }
    trackEvent(5, '/subpackages/pair/pages/wait/index', this.pairSessionId);
    return {
      title: '来和我一起完成双人测试',
      path: `/subpackages/pair/pages/join/index?code=${encodeURIComponent(this.data.inviteCode)}`
    };
  },

  viewResult() {
    wx.navigateTo({ url: `/subpackages/pair/pages/result/index?pairSessionId=${encodeURIComponent(this.pairSessionId)}` });
  },

  cancelPair() {
    wx.showModal({
      title: '取消本次配对？',
      content: '取消后该邀请立即失效。',
      confirmColor: '#ff7469',
      success: async (result) => {
        if (!result.confirm) return;
        try {
          await authenticatedRequestData({
            url: `/api/miniapp/pairs/${encodeURIComponent(this.pairSessionId)}/cancel`,
            method: 'POST'
          });
          wx.removeStorageSync(PAIR_SESSION_KEY);
          getApp().returnToHome();
        } catch (error) {
          wx.showToast({ title: error.message || '取消配对失败', icon: 'none' });
        }
      }
    });
  }
});
