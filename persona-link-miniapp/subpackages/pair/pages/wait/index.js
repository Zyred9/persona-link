const PAIR_SESSION_KEY = 'personaPairSession';

Page({
  data: {
    partnerCompleted: false
  },

  onShow() {
    const session = wx.getStorageSync(PAIR_SESSION_KEY) || {};
    this.setData({ partnerCompleted: Boolean(session.partnerCompleted) });
  },

  refreshStatus() {
    const session = wx.getStorageSync(PAIR_SESSION_KEY) || {};
    session.partnerCompleted = true;
    wx.setStorageSync(PAIR_SESSION_KEY, session);
    this.setData({ partnerCompleted: true });
    wx.showToast({ title: '对方已完成', icon: 'success' });
  },

  viewResult() {
    wx.navigateTo({ url: '/subpackages/pair/pages/result/index' });
  },

  cancelPair() {
    wx.showModal({
      title: '取消本次配对？',
      content: '取消后需要重新生成或输入配对码。',
      confirmColor: '#ff7469',
      success: (result) => {
        if (!result.confirm) {
          return;
        }
        wx.removeStorageSync(PAIR_SESSION_KEY);
        wx.redirectTo({ url: '/subpackages/pair/pages/detail/index' });
      }
    });
  }
});
