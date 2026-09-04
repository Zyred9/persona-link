const { ensureSession, TOKEN_STORAGE_KEY } = require('../../../../utils/request');

Page({
  data: {
    selectedPlan: 'monthly',
    showLoginGate: false,
    loggedIn: false
  },

  onLoad() {
    this.setData({ loggedIn: Boolean(wx.getStorageSync(TOKEN_STORAGE_KEY)) });
  },

  selectPlan(event) {
    this.setData({ selectedPlan: event.currentTarget.dataset.plan });
  },

  confirmOpen() {
    if (!this.data.loggedIn) {
      this.setData({ showLoginGate: true });
      return;
    }

    wx.showToast({ title: '演示环境不实际开通', icon: 'none' });
  },

  async continueLogin() {
    try {
      await ensureSession();
      this.setData({ loggedIn: true, showLoginGate: false });
    } catch (error) {
      wx.showToast({ title: error.message || '登录失败，请重试', icon: 'none' });
    }
  },

  stayGuest() {
    this.setData({ showLoginGate: false });
  }
});
