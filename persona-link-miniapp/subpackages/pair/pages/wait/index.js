const { authenticatedRequestData } = require('../../../../utils/request');

const PAIR_SESSION_KEY = 'personaPairSession';

Page({
  data: {
    state: 'loading',
    partnerCompleted: false,
    partnerStatusText: '待完成',
    waitTitle: '等待对方完成',
    reportReady: false,
    canCancel: false,
    errorDescription: '暂时无法查询配对进度，请稍后重试。'
  },

  onLoad(options) {
    const stored = wx.getStorageSync(PAIR_SESSION_KEY) || {};
    this.pairSessionId = options.pairSessionId || stored.pairSessionId || '';
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
      this.setData({
        state: 'ready',
        partnerCompleted,
        partnerStatusText,
        reportReady,
        canCancel: pairStatus === 1 || pairStatus === 2,
        waitTitle: invalid ? '本次配对已失效' : reportReady ? '双方已完成' : pairStatus === 3 ? '报告生成中' : '等待对方完成'
      });
    } catch (error) {
      this.setData({ state: 'error', errorDescription: error.message || '配对状态查询失败' });
    }
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
