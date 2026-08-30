const DEMO_LOGIN_KEY = 'persona_link_demo_logged_in';

Page({
  data: {
    selectedPlan: 'monthly',
    showLoginGate: false,
    loggedIn: false
  },

  onLoad() {
    this.setData({ loggedIn: Boolean(wx.getStorageSync(DEMO_LOGIN_KEY)) });
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

  continueLogin() {
    wx.login({
      success: () => {
        wx.setStorageSync(DEMO_LOGIN_KEY, true);
        this.setData({ loggedIn: true, showLoginGate: false });
        wx.showToast({ title: '已完成演示授权', icon: 'none' });
      },
      fail: () => {
        wx.showToast({ title: '登录失败，请重试', icon: 'none' });
      }
    });
  },

  stayGuest() {
    this.setData({ showLoginGate: false });
  }
});
